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
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;

@Service
public class SoccerStatsScraperService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String BASE = "https://www.soccerstats.com";
    private static final String LEAGUE = "england";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final long FETCH_PAUSE_MS = 900L;

    private static final Pattern OUT_OF = Pattern.compile("(\\d+)\\s+out\\s+of\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAIR = Pattern.compile("(\\d+)\\s*[-–]\\s*(\\d+)");
    private static final Pattern AVG_MIN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*min", Pattern.CASE_INSENSITIVE);

    public List<TeamStatsSnapshot> enrich(List<TeamStatsSnapshot> teams) {
        if (teams == null || teams.isEmpty()) {
            return teams;
        }
        Map<String, ExtendedTeamMetrics.Builder> patches = new LinkedHashMap<>();
        Map<String, TeamStatsSnapshot> byCode = new LinkedHashMap<>();
        for (TeamStatsSnapshot team : teams) {
            patches.put(team.teamCode(), ExtendedTeamMetrics.builder());
            byCode.put(team.teamCode(), team);
        }

        mergeSafe("firstgoal", () -> mergeFirstGoal(patches, fetchDocument(BASE + "/firstgoal.asp?league=" + LEAGUE)));
        mergeSafe("scored-first", () -> mergeScoredConcededFirst(patches, fetchHtml(BASE + "/table.asp?league=" + LEAGUE + "&tid=sc")));
        mergeSafe("lead-durations", () -> mergeLeadDurations(patches, fetchDocument(BASE + "/table.asp?league=" + LEAGUE + "&tid=t")));
        mergeSafe("equalisers-scored", () -> mergeEqualisers(patches, fetchDocument(BASE + "/table.asp?league=" + LEAGUE + "&tid=x"), true));
        mergeSafe("equalisers-conceded", () -> mergeEqualisers(patches, fetchDocument(BASE + "/table.asp?league=" + LEAGUE + "&tid=w"), false));
        mergeSafe("favourite-ppg", () -> mergeFavouritePpg(patches, fetchDocument(BASE + "/fstats.asp?league=" + LEAGUE)));
        mergeSafe("timing", () -> mergeTiming(patches, fetchHtml(BASE + "/timing.asp?league=" + LEAGUE)));
        mergeSafe("formtable", () -> mergeFormLast8(patches, fetchHtml(BASE + "/formtable.asp?league=" + LEAGUE)));
        mergeSafe("halftime", () -> mergeHalftime(patches, byCode, fetchHtml(BASE + "/halftime.asp?league=" + LEAGUE)));

        List<TeamStatsSnapshot> enriched = new ArrayList<>(teams.size());
        int withSoccerStats = 0;
        for (TeamStatsSnapshot team : teams) {
            ExtendedTeamMetrics patch = patches.get(team.teamCode()).build();
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

    private void mergeFirstGoal(Map<String, ExtendedTeamMetrics.Builder> patches, Document doc) {
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

    private Map<String, Double[]> parseOpeningGoalTable(Document doc, String marker, Set<String> known) {
        Map<String, Double[]> out = new LinkedHashMap<>();
        String html = doc.html();
        int start = html.indexOf(marker);
        if (start < 0) {
            return out;
        }
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

    private void mergeScoredConcededFirst(Map<String, ExtendedTeamMetrics.Builder> patches, String html) {
        if (html == null || html.isBlank()) {
            return;
        }
        Map<String, Double[]> scored = parseWhenFirstSection(html, "When team scored first Total");
        Map<String, Double[]> conceded = parseWhenFirstSection(html, "When team conceded first Total");
        if (conceded.isEmpty()) {
            conceded = parseWhenFirstSection(html, "When team conceded first");
        }
        for (Map.Entry<String, ExtendedTeamMetrics.Builder> entry : patches.entrySet()) {
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

    private void mergeLeadDurations(Map<String, ExtendedTeamMetrics.Builder> patches, Document doc) {
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
            Double lead = parsePercent(cells.get(2));
            Double trail = parsePercent(cells.get(4));
            if (lead == null && trail == null) {
                continue;
            }
            patches.get(code.get()).ssLeadDurations(lead, trail);
            seen.put(code.get(), true);
            if (seen.size() >= 24) {
                break;
            }
        }
    }

    private void mergeEqualisers(Map<String, ExtendedTeamMetrics.Builder> patches, Document doc, boolean scored) {
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

    private void mergeFavouritePpg(Map<String, ExtendedTeamMetrics.Builder> patches, Document doc) {
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

    void mergeTiming(Map<String, ExtendedTeamMetrics.Builder> patches, String html) {
        Map<String, TimingRow> rows = parseTimingOverall15(html);
        for (Map.Entry<String, TimingRow> entry : rows.entrySet()) {
            ExtendedTeamMetrics.Builder builder = patches.get(entry.getKey());
            if (builder == null) {
                continue;
            }
            TimingRow row = entry.getValue();
            builder.ssTiming(
                    row.lateScoredPct,
                    row.lateConcededPct,
                    row.secondHalfGfSharePct,
                    row.avgGfMinute,
                    row.avgGaMinute
            );
        }
    }

    Map<String, TimingRow> parseTimingOverall15(String html) {
        Map<String, TimingRow> out = new LinkedHashMap<>();
        if (html == null || html.isBlank()) {
            return out;
        }
        int start = findTimingOverallStart(html);
        if (start < 0) {
            return out;
        }
        int end = findNextHeading(html, start + 1, List.of(
                "Goals scored / conceded per 15 min. (Home)",
                "Goals scored / conceded per 15 min. (Away)",
                "Goals per 10-minute segments",
                "Goals scored / conceded per 10 min."
        ));
        Document chunk = Jsoup.parse(html.substring(start, end));
        for (Element row : chunk.select("tr")) {
            List<String> cells = cellTexts(row);
            if (cells.size() < 10) {
                continue;
            }
            Optional<String> code = SoccerStatsTeamNameMapper.toTeamCode(cells.get(0));
            if (code.isEmpty() || out.containsKey(code.get())) {
                continue;
            }
            TimingRow parsed = timingFromCells(cells);
            if (parsed == null) {
                continue;
            }
            out.put(code.get(), parsed);
            if (out.size() >= 20) {
                break;
            }
        }
        return out;
    }

    private static int findTimingOverallStart(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lower.length()) {
            int idx = lower.indexOf("goals scored / conceded per 15", from);
            if (idx < 0) {
                return -1;
            }
            int windowEnd = Math.min(lower.length(), idx + 80);
            String window = lower.substring(idx, windowEnd);
            if (window.contains("total")) {
                return idx;
            }
            from = idx + 1;
        }
        return -1;
    }

    private static TimingRow timingFromCells(List<String> cells) {
        int pairStart = 2;
        if (parseNumber(cells.get(1)) == null) {
            pairStart = 1;
        }
        if (cells.size() < pairStart + 8) {
            return null;
        }
        int[][] buckets = new int[6][2];
        for (int i = 0; i < 6; i++) {
            int[] pair = parsePair(cells.get(pairStart + i));
            if (pair == null) {
                return null;
            }
            buckets[i] = pair;
        }
        int[] firstHalf = parsePair(cells.get(pairStart + 6));
        int[] secondHalf = parsePair(cells.get(pairStart + 7));
        if (firstHalf == null || secondHalf == null) {
            return null;
        }
        int totalGf = 0;
        int totalGa = 0;
        for (int[] bucket : buckets) {
            totalGf += bucket[0];
            totalGa += bucket[1];
        }
        int lateGf = buckets[5][0];
        int lateGa = buckets[5][1];
        Double lateScored = sharePct(lateGf, totalGf);
        Double lateConceded = sharePct(lateGa, totalGa);
        Double secondHalfShare = sharePct(secondHalf[0], firstHalf[0] + secondHalf[0]);
        Double avgGf = null;
        Double avgGa = null;
        if (cells.size() > pairStart + 8) {
            avgGf = parseAvgMinute(cells.get(pairStart + 8));
        }
        if (cells.size() > pairStart + 9) {
            avgGa = parseAvgMinute(cells.get(pairStart + 9));
        }
        return new TimingRow(lateScored, lateConceded, secondHalfShare, avgGf, avgGa, totalGf, totalGa);
    }

    void mergeFormLast8(Map<String, ExtendedTeamMetrics.Builder> patches, String html) {
        Map<String, Form8Row> rows = parseFormLast8(html);
        for (Map.Entry<String, Form8Row> entry : rows.entrySet()) {
            ExtendedTeamMetrics.Builder builder = patches.get(entry.getKey());
            if (builder == null) {
                continue;
            }
            Form8Row row = entry.getValue();
            builder.ssForm8(row.ppg, row.gfPm, row.gaPm, row.oppPpg);
        }
    }

    Map<String, Form8Row> parseFormLast8(String html) {
        Map<String, Form8Row> out = new LinkedHashMap<>();
        if (html == null || html.isBlank()) {
            return out;
        }
        int start = findOverallLast8Start(html);
        String slice;
        if (start >= 0) {
            int end = findNextHeading(html, start + 1, List.of(
                    "Last 4 matches",
                    "Last 6 matches",
                    "Last 10 matches",
                    "Last 12 matches",
                    "Last 8 matches - Home",
                    "Last 8 matches Home",
                    "Last 8 matches - Away",
                    "Last 8 matches Away",
                    "Home form table",
                    "Away form table"
            ));
            slice = html.substring(start, end);
        } else {
            slice = html;
        }
        boolean oppColumn = hasOpponentPpgHeader(slice);
        Document chunk = Jsoup.parse(slice);
        boolean started = false;
        for (Element row : chunk.select("tr")) {
            List<String> cells = cellTexts(row);
            Optional<Integer> teamIdx = teamCellIndex(cells);
            if (teamIdx.isEmpty()) {
                continue;
            }
            int t = teamIdx.get();
            if (cells.size() < t + 9) {
                continue;
            }
            String code = SoccerStatsTeamNameMapper.toTeamCode(cells.get(t)).orElse(null);
            if (code == null) {
                continue;
            }
            Double gp = parseNumber(cells.get(t + 1));
            if (gp == null) {
                continue;
            }
            if (!started) {
                if (Math.abs(gp - 8.0) > 0.01) {
                    continue;
                }
                started = true;
            } else if (Math.abs(gp - 8.0) > 0.01) {
                break;
            }
            if (out.containsKey(code)) {
                continue;
            }
            Double gf = parseNumber(cells.get(t + 5));
            Double ga = parseNumber(cells.get(t + 6));
            Double pts = parseNumber(cells.get(t + 8));
            if (gf == null || ga == null || pts == null) {
                continue;
            }
            Double opp = null;
            if (oppColumn && cells.size() > t + 9) {
                opp = parseNumber(cells.get(t + 9));
            }
            out.put(code, new Form8Row(pts / gp, gf / gp, ga / gp, opp, gf, pts));
            if (out.size() >= 20) {
                break;
            }
        }
        return out;
    }

    private static int findOverallLast8Start(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lower.length()) {
            int idx = lower.indexOf("last 8 matches", from);
            if (idx < 0) {
                return -1;
            }
            int windowEnd = Math.min(lower.length(), idx + 40);
            String window = lower.substring(idx, windowEnd);
            if (window.contains("home") || window.contains("away")) {
                from = idx + 1;
                continue;
            }
            return idx;
        }
        return -1;
    }

    private static boolean hasOpponentPpgHeader(String slice) {
        String lower = slice.toLowerCase(Locale.ROOT);
        int headEnd = Math.min(slice.length(), 2_500);
        String head = lower.substring(0, headEnd);
        return (head.contains("opponent") || head.contains("opponents"))
                && head.contains("ppg");
    }

    void mergeHalftime(
            Map<String, ExtendedTeamMetrics.Builder> patches,
            Map<String, TeamStatsSnapshot> byCode,
            String html
    ) {
        Map<String, HalfRow> firstHalf = parseHalfTable(html, true);
        Map<String, HalfRow> secondHalf = parseHalfTable(html, false);
        for (String code : patches.keySet()) {
            TeamStatsSnapshot snap = byCode.get(code);
            ExtendedTeamMetrics existing = snap != null ? snap.extendedOrEmpty() : ExtendedTeamMetrics.empty();
            ExtendedTeamMetrics.Builder builder = patches.get(code);
            HalfRow fh = firstHalf.get(code);
            if (fh != null) {
                if (existing.htPpg() == null) {
                    builder.htPpg(fh.ppg);
                }
                if (existing.winningAtHtPct() == null) {
                    builder.winningAtHtPct(fh.winPct);
                }
            }
            HalfRow sh = secondHalf.get(code);
            if (sh != null) {
                builder.ssSecondHalfPpg(sh.ppg);
            }
        }
    }

    Map<String, HalfRow> parseHalfTable(String html, boolean firstHalf) {
        Map<String, HalfRow> out = new LinkedHashMap<>();
        if (html == null || html.isBlank()) {
            return out;
        }
        String startHeading = firstHalf
                ? "First Half table (1-45 min.)"
                : "Second Half table (46-90 min.)";
        int start = indexOfIgnoreCase(html, startHeading);
        if (start < 0) {
            start = indexOfIgnoreCase(html, firstHalf ? "First Half table" : "Second Half table");
        }
        if (start < 0) {
            return out;
        }
        List<String> stops = firstHalf
                ? List.of(
                "Second Half table (46-90 min.)",
                "Second Half table",
                "First Half table (AT HOME",
                "First Half table (AWAY",
                "Second Half table (AT HOME",
                "Second Half table (AWAY")
                : List.of(
                "First Half table (AT HOME",
                "First Half table (AWAY",
                "Second Half table (AT HOME",
                "Second Half table (AWAY",
                "AT HOME, 1-45",
                "AT HOME, 46-90");
        int end = findNextHeading(html, start + startHeading.length(), stops);
        Document chunk = Jsoup.parse(html.substring(start, end));
        for (Element row : chunk.select("tr")) {
            List<String> cells = cellTexts(row);
            Optional<Integer> teamIdx = teamCellIndex(cells);
            if (teamIdx.isEmpty()) {
                continue;
            }
            int t = teamIdx.get();
            if (cells.size() < t + 8) {
                continue;
            }
            String code = SoccerStatsTeamNameMapper.toTeamCode(cells.get(t)).orElse(null);
            if (code == null || out.containsKey(code)) {
                continue;
            }
            Double gp = parseNumber(cells.get(t + 1));
            Double w = parseNumber(cells.get(t + 2));
            Double pts = parseNumber(cells.get(cells.size() - 1));
            if (gp == null || gp <= 0 || w == null || pts == null) {
                continue;
            }
            out.put(code, new HalfRow(pts / gp, w / gp * 100.0));
            if (out.size() >= 20) {
                break;
            }
        }
        return out;
    }

    private static Optional<Integer> teamCellIndex(List<String> cells) {
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        if (SoccerStatsTeamNameMapper.toTeamCode(cells.get(0)).isPresent()) {
            return Optional.of(0);
        }
        if (cells.size() > 1 && isRankCell(cells.get(0))
                && SoccerStatsTeamNameMapper.toTeamCode(cells.get(1)).isPresent()) {
            return Optional.of(1);
        }
        return Optional.empty();
    }

    private static boolean isRankCell(String raw) {
        Double n = parseNumber(raw);
        return n != null && n == Math.rint(n) && n >= 1 && n <= 24;
    }

    private static boolean hasAnySoccerStats(ExtendedTeamMetrics m) {
        return m.ssScoredFirstPct() != null
                || m.ssLeadPct() != null
                || m.ssEqualiserScoredPct() != null
                || m.ssEqualiserConcededPct() != null
                || m.ssOgsPct() != null
                || m.ssFavouritePpg() != null
                || m.ssLateScoredPct() != null
                || m.ssLateConcededPct() != null
                || m.ssSecondHalfGfSharePct() != null
                || m.ssAvgGfMinute() != null
                || m.ssAvgGaMinute() != null
                || m.ssForm8Ppg() != null
                || m.ssSecondHalfPpg() != null;
    }

    private String fetchHtml(String url) {
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
                throw new IllegalStateException("SoccerSTATS request failed for " + url + ": " + response.getStatus());
            }
            if (isCloudflareChallenge(body)) {
                throw new IllegalStateException("SoccerSTATS Cloudflare challenge for " + url);
            }
            return body;
        } finally {
            pause();
        }
    }

    private Document fetchDocument(String url) {
        try {
            return Jsoup.parse(fetchHtml(url));
        } catch (Exception e) {
            log.warn("SoccerSTATS fetch skipped for {}: {}", url, e.getMessage());
            return null;
        }
    }

    private static boolean isCloudflareChallenge(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        boolean marker = lower.contains("just a moment")
                || lower.contains("cf-browser-verification")
                || lower.contains("challenge-platform")
                || lower.contains("attention required");
        if (!marker) {
            return false;
        }
        return !lower.contains("<table");
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
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
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

    private static int[] parsePair(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = PAIR.matcher(raw);
        if (!m.find()) {
            return null;
        }
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    private static Double parseAvgMinute(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = AVG_MIN.matcher(raw);
        if (m.find()) {
            return Double.parseDouble(m.group(1).replace(',', '.'));
        }
        return parseNumber(raw);
    }

    private static Double sharePct(int part, int total) {
        if (total <= 0) {
            return null;
        }
        return part * 100.0 / total;
    }

    private static int findNextHeading(String html, int from, List<String> headings) {
        int end = html.length();
        for (String heading : headings) {
            int idx = indexOfIgnoreCase(html, heading, from);
            if (idx >= from && idx < end) {
                end = idx;
            }
        }
        return end;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return indexOfIgnoreCase(haystack, needle, 0);
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
    }

    private static boolean looksLikePpgRow(List<String> cells) {
        int numericish = 0;
        for (int i = 1; i < Math.min(4, cells.size()); i++) {
            String c = cells.get(i);
            if ("N/A".equalsIgnoreCase(c) || parseNumber(c) != null) {
                numericish++;
            }
        }
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

    record TimingRow(
            Double lateScoredPct,
            Double lateConcededPct,
            Double secondHalfGfSharePct,
            Double avgGfMinute,
            Double avgGaMinute,
            int totalGf,
            int totalGa
    ) {
    }

    record Form8Row(Double ppg, Double gfPm, Double gaPm, Double oppPpg, double gf, double pts) {
    }

    record HalfRow(Double ppg, Double winPct) {
    }
}
