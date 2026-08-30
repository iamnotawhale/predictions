package zhigalin.predictions.recommender;

import java.io.IOException;
import java.time.Instant;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;

@Service
public class FootyStatsScraperService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String BASE = "https://footystats.org/england/premier-league";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final long FETCH_PAUSE_MS = 1_800L;
    private static final int MAX_FETCH_ATTEMPTS = 4;
    private static final Set<Integer> RETRY_HTTP_STATUSES = Set.of(403, 429, 502, 503);
    private static final int MIN_FORM_TABLE_TEAMS = 15;

    private static final Pattern TEAM_NAME_PATTERN = Pattern.compile("<a href='/clubs/[^']+'>([^<]+?)<div");
    private static final Pattern SCORED_PATTERN = Pattern.compile(
            "<td class='bold'>Scored</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>"
    );
    private static final Pattern CONCEDED_PATTERN = Pattern.compile(
            "<td class='bold'>Conceded</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>"
    );
    private static final Pattern BTTS_PATTERN = Pattern.compile(
            "<td class='bold'>BTTS</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>"
    );
    private static final Pattern CS_PATTERN = Pattern.compile(
            "<td class='bold'>CS</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>"
    );
    private static final Pattern AVG_PATTERN = Pattern.compile(
            "<td class='bold'>AVG</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>"
    );
    private static final Pattern WIN_PATTERN = Pattern.compile(
            "<td class='bold'>Win %</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>\\s*<td>([0-9.%]+)</td>"
    );

    public ParsedSnapshot fetchSnapshot() {
        Instant fetchedAt = Instant.now();
        Map<String, FootyStatsTeamSnapshot> teams = parseFormTable(fetchHtml(BASE + "/form-table"), fetchedAt);
        if (teams.size() < MIN_FORM_TABLE_TEAMS) {
            throw new IllegalStateException(
                    "FootyStats form-table parsed only " + teams.size() + " teams (expected >= " + MIN_FORM_TABLE_TEAMS + ")"
            );
        }
        mergeTable(teams, fetchDocument(BASE + "/xg"), this::mergeXg);
        mergeTable(teams, fetchDocument(BASE + "/xpts"), this::mergeXpts);
        mergeTable(teams, fetchDocument(BASE + "/home-advantage-table"), this::mergeHomeAdvantage);
        mergeTable(teams, fetchDocument(BASE + "/btts"), this::mergeSeasonBtts);
        mergeTable(teams, fetchDocument(BASE + "/failed-to-score-table"), this::mergeFailedToScore);
        mergeTable(teams, fetchDocument(BASE + "/clean-sheets-table"), this::mergeCleanSheets);
        mergeTable(teams, fetchDocument(BASE + "/draws"), this::mergeDraws);
        mergeTable(teams, fetchDocument(BASE + "/average-total-goals-table"), this::mergeAvgTotalGoals);
        mergeTable(teams, fetchDocument(BASE + "/goals-scored-table"), this::mergeGoalsScored);
        mergeTable(teams, fetchDocument(BASE + "/goals-conceded-table"), this::mergeGoalsConceded);
        mergeTable(teams, fetchDocument(BASE + "/over-25-goals-table"), this::mergeOver25);
        mergeTable(teams, fetchDocument(BASE + "/under-x-tables"), this::mergeUnder25);
        mergeTable(teams, fetchDocument(BASE + "/home-away-league-table"), this::mergeHomeAwayPpg);
        mergeTable(teams, fetchDocument(BASE + "/half-time-table"), this::mergeHalfTime);
        mergeTable(teams, fetchDocument(BASE + "/2nd-half-table"), this::mergeSecondHalf);
        mergeTable(teams, fetchDocument(BASE + "/winning-losing-half-time-table"), this::mergeWinningLosingHt);

        List<FootyStatsTeamSnapshot> teamList = new ArrayList<>(teams.values());
        FootyStatsLeagueSnapshot league = buildLeagueSnapshot(teamList, fetchedAt);
        log.info("FootyStats snapshot complete: {} teams", teamList.size());
        return new ParsedSnapshot(teamList, league);
    }

    private interface TableMerger {
        void merge(Map<String, FootyStatsTeamSnapshot> teams, Document doc);
    }

    private void mergeTable(Map<String, FootyStatsTeamSnapshot> teams, Document doc, TableMerger merger) {
        if (doc == null) {
            return;
        }
        try {
            merger.merge(teams, doc);
        } catch (Exception e) {
            log.warn("FootyStats table merge failed: {}", e.getMessage());
        }
    }

    private String fetchHtml(String url) {
        IllegalStateException lastError = null;
        for (int attempt = 1; attempt <= MAX_FETCH_ATTEMPTS; attempt++) {
            try {
                String body = fetchHtmlOnce(url);
                pause();
                return body;
            } catch (IllegalStateException e) {
                lastError = e;
                Integer status = httpStatusFromMessage(e.getMessage());
                if (status == null || !RETRY_HTTP_STATUSES.contains(status) || attempt == MAX_FETCH_ATTEMPTS) {
                    break;
                }
                log.warn("FootyStats {} blocked (HTTP {}), retry {}/{}", url, status, attempt, MAX_FETCH_ATTEMPTS);
                retryPause(attempt);
            }
        }
        try {
            String body = fetchHtmlJsoup(url);
            pause();
            log.info("FootyStats Jsoup fallback succeeded for {}", url);
            return body;
        } catch (IOException e) {
            log.warn("FootyStats Jsoup fallback failed for {}: {}", url, e.getMessage());
        }
        throw lastError != null
                ? lastError
                : new IllegalStateException("FootyStats request failed for " + url);
    }

    private String fetchHtmlOnce(String url) {
        HttpResponse<String> response = Unirest.get(url)
                .header("User-Agent", USER_AGENT)
                .connectTimeout(12_000)
                .socketTimeout(25_000)
                .asString();
        int status = response.getStatus();
        String body = response.getBody();
        if (!response.isSuccess() || body == null || body.isBlank()) {
            throw new IllegalStateException("FootyStats request failed for " + url + ": " + status);
        }
        return body;
    }

    private String fetchHtmlJsoup(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(25_000)
                .ignoreContentType(true)
                .followRedirects(true)
                .execute()
                .body();
    }

    static boolean shouldRetryHttpStatus(int status) {
        return RETRY_HTTP_STATUSES.contains(status);
    }

    static Integer httpStatusFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        int colon = message.lastIndexOf(':');
        if (colon < 0 || colon >= message.length() - 1) {
            return null;
        }
        try {
            return Integer.parseInt(message.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void retryPause(int attempt) {
        long backoffMs = FETCH_PAUSE_MS * (1L << Math.min(attempt, 3));
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Document fetchDocument(String url) {
        try {
            return Jsoup.parse(fetchHtml(url));
        } catch (Exception e) {
            log.warn("FootyStats fetch skipped for {}: {}", url, e.getMessage());
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

    private Map<String, FootyStatsTeamSnapshot> parseFormTable(String html, Instant fetchedAt) {
        int start = html.indexOf("id='last6'");
        int end = html.indexOf("id='last5'");
        String chunk = start >= 0 ? html.substring(start, end > start ? end : html.length()) : html;
        String[] parts = chunk.split("class='team bold");
        Map<String, FootyStatsTeamSnapshot> teams = new LinkedHashMap<>();
        for (String part : parts) {
            Matcher nameMatcher = TEAM_NAME_PATTERN.matcher(part);
            if (!nameMatcher.find()) {
                continue;
            }
            Optional<String> code = FootyStatsTeamNameMapper.toTeamCode(nameMatcher.group(1).trim());
            if (code.isEmpty() || teams.containsKey(code.get())) {
                continue;
            }
            Matcher scored = SCORED_PATTERN.matcher(part);
            Matcher conceded = CONCEDED_PATTERN.matcher(part);
            if (!scored.find() || !conceded.find()) {
                continue;
            }
            FootyStatsExtendedMetrics.Builder builder = FootyStatsExtendedMetrics.builder();
            applyTriple(BTTS_PATTERN, part, builder::formBtts);
            applyTriple(CS_PATTERN, part, builder::formCs);
            applyTriple(AVG_PATTERN, part, builder::formAvgGoals);
            applyTriple(WIN_PATTERN, part, builder::formWinPct);

            teams.put(code.get(), new FootyStatsTeamSnapshot(
                    code.get(),
                    parseDouble(scored.group(1)),
                    parseDouble(scored.group(2)),
                    parseDouble(scored.group(3)),
                    parseDouble(conceded.group(1)),
                    parseDouble(conceded.group(2)),
                    parseDouble(conceded.group(3)),
                    null,
                    null,
                    null,
                    null,
                    null,
                    builder.build(),
                    fetchedAt
            ));
        }
        log.info("FootyStats form-table parsed: {} teams", teams.size());
        return teams;
    }

    private interface TripleConsumer {
        void accept(Double overall, Double home, Double away);
    }

    private void applyTriple(Pattern pattern, String part, TripleConsumer consumer) {
        Matcher matcher = pattern.matcher(part);
        if (matcher.find()) {
            consumer.accept(
                    FootyStatsTableParser.parsePercent(matcher.group(1)),
                    FootyStatsTableParser.parsePercent(matcher.group(2)),
                    FootyStatsTableParser.parsePercent(matcher.group(3))
            );
        }
    }

    private void mergeXg(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "xg-all");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            Double xg = FootyStatsTableParser.cellAfterMp(cells, 0);
            Double xga = FootyStatsTableParser.cellAfterMp(cells, 1);
            Double xgd = FootyStatsTableParser.cellAfterMp(cells, 2);
            Double xgVsActual = FootyStatsTableParser.cellAfterMp(cells, 5);
            teams.put(entry.getKey(), existing.withXg(xg, xga, xgd, xgVsActual));
        }
    }

    private void mergeXpts(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "xpts-all");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            Double xPts = FootyStatsTableParser.cellAfterMp(cells, 3);
            Double actualPts = FootyStatsTableParser.cellAfterMp(cells, 4);
            Double delta = FootyStatsTableParser.cellAfterMp(cells, 5);
            if (delta == null && xPts != null && actualPts != null) {
                delta = actualPts - xPts;
            }
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .xPts(xPts, actualPts, delta)
                    .build()));
        }
    }

    private void mergeHomeAdvantage(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "home-advantage-table");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            Double ha = FootyStatsTableParser.cellNumber(entry.getValue(), 0);
            teams.put(entry.getKey(), existing.withExtended(
                    FootyStatsExtendedMetrics.builder().homeAdvantage(ha).build()
            ));
        }
    }

    private void mergeSeasonBtts(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "btts mobify-table", "1h");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .seasonBtts(
                            FootyStatsTableParser.cellPercentAfterMp(cells, 1),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 2),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 3)
                    )
                    .build()));
        }
    }

    private void mergeFailedToScore(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "failed-to-score-table");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .ftsHome(FootyStatsTableParser.cellPercent(cells, 0))
                    .ftsAway(FootyStatsTableParser.cellPercent(cells, 1))
                    .build()));
        }
    }

    private void mergeCleanSheets(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "clean-sheets-table mobify");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .seasonCs(
                            FootyStatsTableParser.cellPercentAfterMp(cells, 1),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 2),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 3)
                    )
                    .build()));
        }
    }

    private void mergeDraws(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "draws");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .drawPct(
                            FootyStatsTableParser.cellPercentAfterMp(cells, 0),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 1),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 2)
                    )
                    .build()));
        }
    }

    private void mergeAvgTotalGoals(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "average-total-goals-table");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .seasonAvgTotal(
                            FootyStatsTableParser.cellAfterMp(cells, 1),
                            FootyStatsTableParser.cellAfterMp(cells, 2)
                    )
                    .build()));
        }
    }

    private void mergeGoalsScored(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "goals-scored-table mobify");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .seasonScored(
                            FootyStatsTableParser.cellAfterMp(cells, 0),
                            FootyStatsTableParser.cellAfterMp(cells, 1),
                            FootyStatsTableParser.cellAfterMp(cells, 2)
                    )
                    .build()));
        }
    }

    private void mergeGoalsConceded(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "goals-conceded-table");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .seasonConceded(
                            FootyStatsTableParser.cellAfterMp(cells, 0),
                            FootyStatsTableParser.cellAfterMp(cells, 1),
                            FootyStatsTableParser.cellAfterMp(cells, 2)
                    )
                    .build()));
        }
    }

    private void mergeOver25(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "over-25-goals-table");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .over25(
                            FootyStatsTableParser.cellPercentAfterMp(cells, 1),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 2),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 3)
                    )
                    .build()));
        }
    }

    private void mergeUnder25(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "under-x-tables");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .under25(
                            FootyStatsTableParser.cellPercentAfterMp(cells, 0),
                            null,
                            null
                    )
                    .build()));
        }
        Map<String, List<String>> detailed = FootyStatsTableParser.parseTableByClassContains(doc, "under-25-goals-table");
        for (Map.Entry<String, List<String>> entry : detailed.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            FootyStatsExtendedMetrics ext = existing.extendedOrEmpty();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .under25(
                            ext.under25Overall(),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 1),
                            FootyStatsTableParser.cellPercentAfterMp(cells, 2)
                    )
                    .build()));
        }
    }

    private void mergeHomeAwayPpg(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> homeRows = FootyStatsTableParser.parseTableAfterHeading(doc, "Home Table");
        Map<String, List<String>> awayRows = FootyStatsTableParser.parseTableAfterHeading(doc, "Away Table");
        for (Map.Entry<String, List<String>> entry : homeRows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            Double ppg = ppgFromHomeAwayRow(cells);
            teams.put(entry.getKey(), existing.withExtended(
                    FootyStatsExtendedMetrics.builder().homePpg(ppg).build()
            ));
        }
        for (Map.Entry<String, List<String>> entry : awayRows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            Double ppg = ppgFromHomeAwayRow(entry.getValue());
            teams.put(entry.getKey(), existing.withExtended(
                    FootyStatsExtendedMetrics.builder().awayPpg(ppg).build()
            ));
        }
    }

    private Double ppgFromHomeAwayRow(List<String> cells) {
        int offset = FootyStatsTableParser.mpOffset(cells);
        Double played = FootyStatsTableParser.cellNumber(cells, offset);
        Double points = null;
        for (int i = cells.size() - 1; i >= offset; i--) {
            Double value = FootyStatsTableParser.cellNumber(cells, i);
            if (value != null && value <= 100) {
                points = value;
                break;
            }
        }
        if (points == null) {
            points = FootyStatsTableParser.cellNumber(cells, offset + 4);
        }
        if (points != null && played != null && played > 0) {
            return points / played;
        }
        return null;
    }

    private void mergeHalfTime(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "half-time-table mobify");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            Double pts = FootyStatsTableParser.cellNumber(entry.getValue(), 0);
            if (pts != null) {
                teams.put(entry.getKey(), existing.withExtended(
                        FootyStatsExtendedMetrics.builder().htPpg(pts / 10.0).build()
                ));
            }
        }
    }

    private void mergeSecondHalf(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "2nd-half-table");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            Double pts = FootyStatsTableParser.cellNumber(entry.getValue(), 0);
            if (pts != null) {
                teams.put(entry.getKey(), existing.withExtended(
                        FootyStatsExtendedMetrics.builder().secondHalfPpg(pts / 10.0).build()
                ));
            }
        }
    }

    private void mergeWinningLosingHt(Map<String, FootyStatsTeamSnapshot> teams, Document doc) {
        Map<String, List<String>> rows = FootyStatsTableParser.parseTableByClassContains(doc, "winning-losing-half-time-table");
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            FootyStatsTeamSnapshot existing = teams.get(entry.getKey());
            if (existing == null) {
                continue;
            }
            List<String> cells = entry.getValue();
            teams.put(entry.getKey(), existing.withExtended(FootyStatsExtendedMetrics.builder()
                    .winningAtHtPct(FootyStatsTableParser.cellPercentAfterMp(cells, 0))
                    .losingAtHtPct(FootyStatsTableParser.cellPercentAfterMp(cells, 1))
                    .build()));
        }
    }

    private FootyStatsLeagueSnapshot buildLeagueSnapshot(List<FootyStatsTeamSnapshot> teams, Instant fetchedAt) {
        double homeScored = average(teams.stream().map(FootyStatsTeamSnapshot::scoredHome).toList());
        double awayScored = average(teams.stream().map(FootyStatsTeamSnapshot::scoredAway).toList());
        double homeConceded = average(teams.stream().map(FootyStatsTeamSnapshot::concededHome).toList());
        double awayConceded = average(teams.stream().map(FootyStatsTeamSnapshot::concededAway).toList());
        if (homeScored <= 0) {
            homeScored = 1.48;
        }
        if (awayScored <= 0) {
            awayScored = 1.30;
        }
        if (homeConceded <= 0) {
            homeConceded = homeScored;
        }
        if (awayConceded <= 0) {
            awayConceded = awayScored;
        }
        return new FootyStatsLeagueSnapshot(0, homeScored, awayScored, homeConceded, awayConceded, fetchedAt);
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double parseDouble(String value) {
        Double parsed = FootyStatsTableParser.parseNullable(value);
        return parsed != null ? parsed : 0;
    }

    public record ParsedSnapshot(List<FootyStatsTeamSnapshot> teams, FootyStatsLeagueSnapshot league) {
        public FootyStatsLeagueSnapshot leagueForWeek(int weekId) {
            return new FootyStatsLeagueSnapshot(
                    weekId,
                    league.avgHomeScored(),
                    league.avgAwayScored(),
                    league.avgHomeConceded(),
                    league.avgAwayConceded(),
                    league.fetchedAt()
            );
        }
    }
}
