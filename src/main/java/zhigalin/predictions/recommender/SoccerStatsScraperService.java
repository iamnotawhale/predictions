package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.stereotype.Service;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;

/**
 * Scrapes high-value SoccerSTATS.com game-state metrics and merges them into team snapshots.
 */
@Service
public class SoccerStatsScraperService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String BASE = "https://www.soccerstats.com";
    private static final String LEAGUE = "england";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final long FETCH_PAUSE_MS = 900L;

    private static final Pattern OUT_OF = Pattern.compile("(\\d+)\\s+out\\s+of\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    public List<FootyStatsTeamSnapshot> enrich(List<FootyStatsTeamSnapshot> teams) {
        if (teams == null || teams.isEmpty()) {
            return teams;
        }
        Map<String, FootyStatsExtendedMetrics.Builder> patches = new LinkedHashMap<>();
        for (FootyStatsTeamSnapshot team : teams) {
            patches.put(team.teamCode(), FootyStatsExtendedMetrics.builder());
        }

        mergeSafe("firstgoal", () -> mergeFirstGoal(patches, fetchDocument(BASE + "/firstgoal.asp?league=" + LEAGUE)));
        mergeSafe("scored-first", () -> mergeScoredConcededFirst(patches, fetchHtml(BASE + "/table.asp?league=" + LEAGUE + "&tid=sc")));
        mergeSafe("lead-durations", () -> mergeLeadDurations(patches, fetchDocument(BASE + "/table.asp?league=" + LEAGUE + "&tid=t")));
        mergeSafe("equalisers-scored", () -> mergeEqualisers(patches, fetchDocument(BASE + "/table.asp?league=" + LEAGUE + "&tid=x"), true));
        mergeSafe("equalisers-conceded", () -> mergeEqualisers(patches, fetchDocument(BASE + "/table.asp?league=" + LEAGUE + "&tid=w"), false));
        mergeSafe("favourite-ppg", () -> mergeFavouritePpg(patches, fetchDocument(BASE + "/fstats.asp?league=" + LEAGUE)));

        List<FootyStatsTeamSnapshot> enriched = new ArrayList<>(teams.size());
        int withSoccerStats = 0;
        for (FootyStatsTeamSnapshot team : teams) {
            FootyStatsExtendedMetrics patch = patches.get(team.teamCode()).build();
            if (hasAnySoccerStats(patch)) {
                withSoccerStats++;
            }
            enriched.add(team.withExtended(patch));
        }
        log.info("SoccerSTATS enrich complete: {} / {} teams with game-state metrics", withSoccerStats, teams.size());
        return enriched;
    }

    private void mergeSafe(String label, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("SoccerSTATS merge failed ({}): {}", label, e.getMessage());
        }
    }

    private void mergeFirstGoal(Map<String, FootyStatsExtendedMetrics.Builder> patches, Document doc) {
        if (doc == null) {
            return;
        }
        Map<String, Double[]> ogs = parseOpeningGoalTable(doc, "OGS:", patches.keySet());
        Map<String, Double[]> ogc = parseOpeningGoalTable(doc, "OGC:", patches.keySet());
        for (String code : patches.keySet()) {
            Double[] scored = ogs.get(code);
            Double[] conceded = ogc.get(code);
            Double ogsPct = scored != null ? scored[0] : null;
            Double avgMin = scored != null ? scored[1] : null;
            Double ogcPct = conceded != null ? conceded[0] : null;
            if (ogsPct != null || ogcPct != null || avgMin != null) {
                patches.get(code).ssFirstGoal(ogsPct, ogcPct, avgMin);
            }
        }
    }

    /**
     * Opening-goal tables: team, GP, OGS/OGC, avg minute, …
     * Returns code → [pct of matches, avg minute].
     */
    private Map<String, Double[]> parseOpeningGoalTable(Document doc, String marker, Set<String> known) {
        Map<String, Double[]> out = new LinkedHashMap<>();
        String html = doc.html();
        int start = html.indexOf(marker);
        if (start < 0) {
            return out;
        }
        // Prefer Overall block: first ~20 mapped teams after the marker.
        Document chunk = Jsoup.parse(html.substring(start, Math.min(html.length(), start + 25_000)));
        for (Element row : chunk.select("tr")) {
            List<String> cells = cellTexts(row);
            if (cells.size() < 4) {
                continue;
            }
            Optional<String> code = SoccerStatsTeamNameMapper.toTeamCode(cells.get(0));
            if (code.isEmpty() || out.containsKey(code.get()) || !known.contains(code.get())) {
                continue;
            }
            Double gp = parseNumber(cells.get(1));
            Double opening = parseNumber(cells.get(2));
            Double avgMin = parseNumber(cells.get(3));
            if (gp == null || gp <= 0 || opening == null) {
                continue;
            }
            out.put(code.get(), new Double[]{opening / gp * 100.0, avgMin});
            if (out.size() >= 24) {
                break;
            }
        }
        return out;
    }

    private void mergeScoredConcededFirst(Map<String, FootyStatsExtendedMetrics.Builder> patches, String html) {
        if (html == null || html.isBlank()) {
            return;
        }
        Map<String, Double[]> scored = parseWhenFirstSection(html, "When team scored first Total");
        Map<String, Double[]> conceded = parseWhenFirstSection(html, "When team conceded first Total");
        if (conceded.isEmpty()) {
            conceded = parseWhenFirstSection(html, "When team conceded first");
        }
        for (Map.Entry<String, FootyStatsExtendedMetrics.Builder> entry : patches.entrySet()) {
            Double[] s = scored.get(entry.getKey());
            Double[] c = conceded.get(entry.getKey());
            if (s != null) {
                entry.getValue().ssScoredFirst(s[0], s[1]);
            }
            if (c != null) {
                entry.getValue().ssConcededFirst(c[0], c[1]);
            }
        }
    }

    /** Section rows: team | "X out of Y" | pct | W | D | L | goals | pts | ppg */
    private Map<String, Double[]> parseWhenFirstSection(String html, String heading) {
        Map<String, Double[]> out = new LinkedHashMap<>();
        int start = indexOfIgnoreCase(html, heading);
        if (start < 0) {
            return out;
        }
        int end = html.length();
        for (String next : List.of(
                "When team scored first Home",
                "When team scored first Away",
                "When team conceded first Total",
                "When team conceded first Home",
                "When team conceded first Away",
                "When team conceded first"
        )) {
            int idx = indexOfIgnoreCase(html, next, start + heading.length());
            if (idx > start && idx < end) {
                end = idx;
            }
        }
        Document chunk = Jsoup.parse(html.substring(start, end));
        for (Element row : chunk.select("tr")) {
            List<String> cells = cellTexts(row);
            if (cells.size() < 3) {
                continue;
            }
            Optional<String> code = SoccerStatsTeamNameMapper.toTeamCode(cells.get(0));
            if (code.isEmpty() || out.containsKey(code.get())) {
                continue;
            }
            Double pct = parsePercent(cells.get(2));
            if (pct == null && cells.size() > 1) {
                Matcher m = OUT_OF.matcher(cells.get(1));
                if (m.find()) {
                    double done = Double.parseDouble(m.group(1));
                    double total = Double.parseDouble(m.group(2));
                    if (total > 0) {
                        pct = done / total * 100.0;
                    }
                }
            }
            Double ppg = cells.size() >= 9 ? parseNumber(cells.get(8)) : null;
            if (pct == null && ppg == null) {
                continue;
            }
            out.put(code.get(), new Double[]{pct, ppg});
            if (out.size() >= 24) {
                break;
            }
        }
        return out;
    }

    private void mergeLeadDurations(Map<String, FootyStatsExtendedMetrics.Builder> patches, Document doc) {
        if (doc == null) {
            return;
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Element row : doc.select("tr")) {
            List<String> cells = cellTexts(row);
            if (cells.size() < 5) {
                continue;
            }
            Optional<String> code = SoccerStatsTeamNameMapper.toTeamCode(cells.get(0));
            if (code.isEmpty() || seen.containsKey(code.get()) || !patches.containsKey(code.get())) {
                continue;
            }
            // team, GP, Leading%, Level%, Trailing%
            Double lead = parsePercent(cells.get(2));
            Double level = parsePercent(cells.get(3));
            Double trail = parsePercent(cells.get(4));
            if (lead == null && level == null && trail == null) {
                continue;
            }
            patches.get(code.get()).ssLeadDurations(lead, level, trail);
            seen.put(code.get(), true);
            if (seen.size() >= 24) {
                break;
            }
        }
    }

    private void mergeEqualisers(Map<String, FootyStatsExtendedMetrics.Builder> patches, Document doc, boolean scored) {
        if (doc == null) {
            return;
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Element row : doc.select("tr")) {
            List<String> cells = cellTexts(row);
            if (cells.size() < 7) {
                continue;
            }
            Optional<String> code = SoccerStatsTeamNameMapper.toTeamCode(cells.get(0));
            if (code.isEmpty() || seen.containsKey(code.get()) || !patches.containsKey(code.get())) {
                continue;
            }
            // last meaningful cell is equaliser %
            Double pct = parsePercent(cells.get(cells.size() - 1));
            if (pct == null) {
                pct = parsePercent(cells.get(6));
            }
            if (pct == null) {
                continue;
            }
            if (scored) {
                patches.get(code.get()).ssEqualiserScoredPct(pct);
            } else {
                patches.get(code.get()).ssEqualiserConcededPct(pct);
            }
            seen.put(code.get(), true);
            if (seen.size() >= 24) {
                break;
            }
        }
    }

    private void mergeFavouritePpg(Map<String, FootyStatsExtendedMetrics.Builder> patches, Document doc) {
        if (doc == null) {
            return;
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Element row : doc.select("tr")) {
            List<String> cells = cellTexts(row);
            if (cells.size() < 4) {
                continue;
            }
            Optional<String> code = SoccerStatsTeamNameMapper.toTeamCode(cells.get(0));
            if (code.isEmpty() || seen.containsKey(code.get()) || !patches.containsKey(code.get())) {
                continue;
            }
            // Favourite PPG rows look like: team | homePpg | awayPpg | totalPpg (decimals or N/A)
            if (!looksLikePpgRow(cells)) {
                continue;
            }
            Double total = parseNumber(cells.get(3));
            if (total == null) {
                total = firstNumber(cells.get(1), cells.get(2));
            }
            if (total == null) {
                continue;
            }
            patches.get(code.get()).ssFavouritePpg(total);
            seen.put(code.get(), true);
            if (seen.size() >= 24) {
                break;
            }
        }
    }

    private static boolean looksLikePpgRow(List<String> cells) {
        int numericish = 0;
        for (int i = 1; i < Math.min(4, cells.size()); i++) {
            String c = cells.get(i);
            if ("N/A".equalsIgnoreCase(c) || parseNumber(c) != null) {
                numericish++;
            }
        }
        // Played-as-favourite rows are small integers like 1,0,1 — skip those.
        if (numericish < 2) {
            return false;
        }
        Double a = parseNumber(cells.get(1));
        Double b = cells.size() > 2 ? parseNumber(cells.get(2)) : null;
        Double c = cells.size() > 3 ? parseNumber(cells.get(3)) : null;
        boolean anyDecimal = (a != null && a != Math.rint(a))
                || (b != null && b != Math.rint(b))
                || (c != null && c != Math.rint(c))
                || "N/A".equalsIgnoreCase(cells.get(1))
                || (cells.size() > 2 && "N/A".equalsIgnoreCase(cells.get(2)));
        return anyDecimal || (c != null && c >= 0 && c <= 3.01);
    }

    private static boolean hasAnySoccerStats(FootyStatsExtendedMetrics m) {
        return m.ssScoredFirstPct() != null
                || m.ssLeadPct() != null
                || m.ssEqualiserScoredPct() != null
                || m.ssEqualiserConcededPct() != null
                || m.ssOgsPct() != null
                || m.ssFavouritePpg() != null;
    }

    private String fetchHtml(String url) {
        HttpResponse<String> response = Unirest.get(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .connectTimeout(12_000)
                .socketTimeout(25_000)
                .asString();
        if (!response.isSuccess() || response.getBody() == null || response.getBody().isBlank()) {
            throw new IllegalStateException("SoccerSTATS request failed for " + url + ": " + response.getStatus());
        }
        pause();
        return response.getBody();
    }

    private Document fetchDocument(String url) {
        try {
            return Jsoup.parse(fetchHtml(url));
        } catch (Exception e) {
            log.warn("SoccerSTATS fetch skipped for {}: {}", url, e.getMessage());
            return null;
        }
    }

    private void pause() {
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

    private static Double parsePercent(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw) || "N/A".equalsIgnoreCase(raw)) {
            return null;
        }
        String cleaned = raw.replace("%", "").replace(",", ".").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseNumber(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw) || "N/A".equalsIgnoreCase(raw)) {
            return null;
        }
        String cleaned = raw.replace("%", "").replace(",", ".").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double firstNumber(String... values) {
        for (String value : values) {
            Double n = parseNumber(value);
            if (n != null) {
                return n;
            }
        }
        return null;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return indexOfIgnoreCase(haystack, needle, 0);
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
    }
}
