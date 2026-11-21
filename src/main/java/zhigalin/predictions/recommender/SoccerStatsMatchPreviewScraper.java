package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SoccerStatsMatchPreviewScraper {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String BASE = "https://www.soccerstats.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final long FETCH_PAUSE_MS = 900L;
    private static final Pattern ROUND = Pattern.compile("Round\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VS = Pattern.compile("(.+?)\\s+vs\\s+(.+)", Pattern.CASE_INSENSITIVE);

    private volatile boolean cloudflareBlocked;

    public record FixturePair(String homeCode, String awayCode, String homeLabel, String awayLabel) {
    }

    public record PreviewLine(
            String homeCode,
            String awayCode,
            String homeScope,
            String awayScope,
            String homeStats,
            String awayStats,
            List<String> homeCells,
            List<String> awayCells
    ) {
        public PreviewLine(
                String homeCode,
                String awayCode,
                String homeScope,
                String awayScope,
                String homeStats,
                String awayStats
        ) {
            this(homeCode, awayCode, homeScope, awayScope, homeStats, awayStats, List.of(), List.of());
        }
    }

    public List<FixturePair> fetchMatchlistRound(int round) {
        cloudflareBlocked = false;
        String url = BASE + "/en-gb/leagues/england/premier-league/england/matchlist/r" + round;
        String html = fetchHtml(url);
        if (html == null) {
            return List.of();
        }
        Integer pageRound = parseRound(html);
        if (pageRound == null || pageRound != round) {
            log.warn("SoccerSTATS matchlist round mismatch: expected {}, got {}", round, pageRound);
            return List.of();
        }
        List<FixturePair> out = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        for (Element row : doc.select("tr")) {
            List<String> cells = cellTexts(row);
            String joined = String.join(" ", cells);
            Matcher m = VS.matcher(joined);
            if (!m.find()) {
                continue;
            }
            String homeLabel = m.group(1).trim();
            String awayLabel = m.group(2).trim();
            Optional<String> home = SoccerStatsTeamNameMapper.toTeamCode(homeLabel);
            Optional<String> away = SoccerStatsTeamNameMapper.toTeamCode(awayLabel);
            if (home.isEmpty() || away.isEmpty()) {
                continue;
            }
            out.add(new FixturePair(home.get(), away.get(), homeLabel, awayLabel));
        }
        return out;
    }

    public Map<String, PreviewLine> fetchHomeAwayPreviews() {
        Map<String, PreviewLine> out = new LinkedHashMap<>();
        if (cloudflareBlocked) {
            return out;
        }
        String html = fetchHtml(BASE + "/leaguepreviews.asp?league=england&pmtype=homeaway");
        if (html == null) {
            return out;
        }
        out.putAll(parsePreviewPairs(html));
        return out;
    }

    public Map<String, PreviewLine> fetchOverallPreviews() {
        Map<String, PreviewLine> out = new LinkedHashMap<>();
        if (cloudflareBlocked) {
            return out;
        }
        String html = fetchHtml(BASE + "/leaguepreviews.asp?league=england&pmtype=overall");
        if (html == null) {
            return out;
        }
        out.putAll(parsePreviewPairs(html));
        return out;
    }

    Map<String, PreviewLine> parsePreviewPairs(String html) {
        Map<String, PreviewLine> out = new LinkedHashMap<>();
        Document doc = Jsoup.parse(html);
        List<Element> rows = doc.select("tr");
        for (int i = 0; i + 1 < rows.size(); i++) {
            List<String> a = cellTexts(rows.get(i));
            List<String> b = cellTexts(rows.get(i + 1));
            if (a.size() < 4 || b.size() < 4) {
                continue;
            }
            Optional<Integer> homeIdx = teamCellIndex(a);
            Optional<Integer> awayIdx = teamCellIndex(b);
            if (homeIdx.isEmpty() || awayIdx.isEmpty()) {
                continue;
            }
            String homeCode = SoccerStatsTeamNameMapper.toTeamCode(a.get(homeIdx.get())).orElse(null);
            String awayCode = SoccerStatsTeamNameMapper.toTeamCode(b.get(awayIdx.get())).orElse(null);
            if (homeCode == null || awayCode == null) {
                continue;
            }
            String homeScope = scopeOf(a);
            String awayScope = scopeOf(b);
            String key = homeCode + "-" + awayCode;
            if (out.containsKey(key)) {
                continue;
            }
            List<String> homeCells = usefulCells(a, homeIdx.get());
            List<String> awayCells = usefulCells(b, awayIdx.get());
            out.put(key, new PreviewLine(
                    homeCode,
                    awayCode,
                    homeScope,
                    awayScope,
                    String.join(" · ", homeCells),
                    String.join(" · ", awayCells),
                    homeCells,
                    awayCells
            ));
            i++;
        }
        return out;
    }

    private static List<String> usefulCells(List<String> cells, int teamIdx) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (i == teamIdx) {
                continue;
            }
            String c = cells.get(i);
            if (c.length() > 64) {
                continue;
            }
            if (c.equalsIgnoreCase("stats") || c.equalsIgnoreCase("all")
                    || c.equalsIgnoreCase("home") || c.equalsIgnoreCase("away")) {
                continue;
            }
            if (looksLikeKickoffDate(c)) {
                continue;
            }
            parts.add(c);
            if (parts.size() >= 12) {
                break;
            }
        }
        return List.copyOf(parts);
    }

    static boolean looksLikeKickoffDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String c = raw.trim();
        return c.matches("(?i)(Mo|Tu|We|Th|Fr|Sa|Su)\\s+\\d{1,2}\\s+[A-Za-z]{3}.*")
                || c.matches("\\d{1,2}/\\d{1,2}(/\\d{2,4})?")
                || c.matches("\\d{1,2}-\\d{1,2}(-\\d{2,4})?");
    }

    private static String scopeOf(List<String> cells) {
        for (String c : cells) {
            String lower = c.toLowerCase(Locale.ROOT);
            if (lower.equals("home") || lower.equals("away") || lower.equals("all")) {
                return lower;
            }
        }
        return "";
    }

    private static Integer parseRound(String html) {
        Matcher m = ROUND.matcher(html);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private String fetchHtml(String url) {
        if (cloudflareBlocked) {
            return null;
        }
        try {
            HttpResponse<String> response = Unirest.get(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-GB,en;q=0.9")
                    .header("Referer", BASE + "/")
                    .connectTimeout(12_000)
                    .socketTimeout(25_000)
                    .asString();
            String body = response.getBody();
            if (!response.isSuccess() || body == null || body.isBlank()) {
                throw new IllegalStateException("status " + response.getStatus());
            }
            if (isCloudflareChallenge(body)) {
                cloudflareBlocked = true;
                log.warn("SoccerSTATS Cloudflare challenge, stopping further fetches for this job");
                return null;
            }
            return body;
        } catch (Exception e) {
            log.warn("SoccerSTATS preview fetch failed for {}: {}", url, e.getMessage());
            return null;
        } finally {
            pause();
        }
    }

    private static boolean isCloudflareChallenge(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        boolean marker = lower.contains("just a moment")
                || lower.contains("cf-browser-verification")
                || lower.contains("challenge-platform")
                || lower.contains("attention required");
        return marker && !lower.contains("<table");
    }

    private static void pause() {
        try {
            Thread.sleep(FETCH_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> cellTexts(Element row) {
        Elements cells = row.select("th, td");
        List<String> texts = new ArrayList<>(cells.size());
        for (Element cell : cells) {
            String text = cell.text()
                    .replace('\u00a0', ' ')
                    .replace('\u200b', ' ')
                    .trim()
                    .replaceAll("\\s+", " ");
            if (!text.isEmpty()) {
                texts.add(text);
            }
        }
        return texts;
    }

    private static Optional<Integer> teamCellIndex(List<String> cells) {
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        if (SoccerStatsTeamNameMapper.toTeamCode(cells.get(0)).isPresent()) {
            return Optional.of(0);
        }
        if (cells.size() > 1 && SoccerStatsTeamNameMapper.toTeamCode(cells.get(1)).isPresent()) {
            return Optional.of(1);
        }
        for (int i = 0; i < cells.size(); i++) {
            if (SoccerStatsTeamNameMapper.toTeamCode(cells.get(i)).isPresent()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
