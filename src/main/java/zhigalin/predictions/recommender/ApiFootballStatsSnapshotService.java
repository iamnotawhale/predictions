package zhigalin.predictions.recommender;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.input.Response;
import zhigalin.predictions.model.input.Score;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.LeagueAveragesSnapshot;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.api.ApiFootballClient;
import zhigalin.predictions.util.DaoUtil;

@Service
public class ApiFootballStatsSnapshotService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final int EPL_LEAGUE_ID = 39;
    private static final int MIN_TEAMS = 15;
    private static final int MAX_XG_FETCH_PER_REFRESH = 25;
    private static final int XPTS_MAX_GOALS = 10;
    private static final int MIN_XG_SAMPLES_FOR_METRICS = 8;
    private static final double MIN_XG_COVERAGE = 0.8;
    private static final Set<String> FINISHED = Set.of("FT", "AET", "PEN");

    private final ApiFootballClient apiFootballClient;
    private final ApiFootballFixtureXgDao fixtureXgDao;

    public ApiFootballStatsSnapshotService(ApiFootballClient apiFootballClient, ApiFootballFixtureXgDao fixtureXgDao) {
        this.apiFootballClient = apiFootballClient;
        this.fixtureXgDao = fixtureXgDao;
    }

    public LeagueStatsSnapshot fetchSnapshot() {
        Instant fetchedAt = Instant.now();
        List<Response> fixtures = apiFootballClient.fetchLeagueFixtures(EPL_LEAGUE_ID, DataInitService.SEASON);
        List<FinishedMatch> finished = new ArrayList<>();
        for (Response r : fixtures) {
            FinishedMatch m = toFinished(r);
            if (m != null) {
                finished.add(m);
            }
        }
        finished.sort(Comparator.comparingLong(FinishedMatch::timestamp));
        if (finished.isEmpty()) {
            throw new IllegalStateException("ApiFootball: no finished EPL fixtures for season " + DataInitService.SEASON);
        }

        ensureXgCache(finished);

        Map<Integer, ApiFootballFixtureXgDao.XgRow> xgByFixture = new HashMap<>();
        for (ApiFootballFixtureXgDao.XgRow row : fixtureXgDao.findAll()) {
            if (row.hasXg()) {
                xgByFixture.put(row.matchId(), row);
            }
        }

        Map<Integer, TeamAgg> byTeam = new HashMap<>();
        for (FinishedMatch m : finished) {
            ApiFootballFixtureXgDao.XgRow xg = xgByFixture.get(m.fixtureId());
            Double homeXg = xg != null ? xg.homeXg() : null;
            Double awayXg = xg != null ? xg.awayXg() : null;
            byTeam.computeIfAbsent(m.homeTeamId(), TeamAgg::new).addHome(m, homeXg, awayXg);
            byTeam.computeIfAbsent(m.awayTeamId(), TeamAgg::new).addAway(m, awayXg, homeXg);
        }

        List<TeamStatsSnapshot> teams = new ArrayList<>();
        for (TeamAgg agg : byTeam.values()) {
            TeamStatsSnapshot snap = agg.toSnapshot(fetchedAt);
            if (snap != null) {
                teams.add(snap);
            }
        }
        if (teams.size() < MIN_TEAMS) {
            throw new IllegalStateException(
                    "ApiFootball snapshot parsed only " + teams.size() + " teams (expected >= " + MIN_TEAMS + ")"
            );
        }

        LeagueAveragesSnapshot league = buildLeagueSnapshot(teams, fetchedAt);
        log.info(
                "ApiFootball snapshot complete: {} teams, {} finished fixtures, {} xG rows cached",
                teams.size(),
                finished.size(),
                xgByFixture.size()
        );
        return new LeagueStatsSnapshot(teams, league);
    }

    void ensureXgCache(List<FinishedMatch> finished) {
        List<Integer> ids = finished.stream().map(FinishedMatch::fixtureId).toList();
        Set<Integer> cached = new java.util.HashSet<>(fixtureXgDao.findCachedFixtureIds(ids));
        int attempts = 0;
        int stored = 0;
        int tombstones = 0;
        for (FinishedMatch m : finished) {
            if (cached.contains(m.fixtureId())) {
                continue;
            }
            if (attempts >= MAX_XG_FETCH_PER_REFRESH) {
                log.warn(
                        "ApiFootball xG backfill capped at {} statistics calls this refresh ({} still missing)",
                        MAX_XG_FETCH_PER_REFRESH,
                        ids.size() - cached.size()
                );
                break;
            }
            attempts++;
            JsonNode stats = apiFootballClient.fetchFixtureStatistics(m.fixtureId());
            if (stats == null || isTransportLikeStats(stats)) {
                continue;
            }
            Instant now = Instant.now();
            DoublePair xg = parseExpectedGoals(stats, m.homeTeamId(), m.awayTeamId());
            if (xg == null) {
                fixtureXgDao.upsertUnavailable(m.fixtureId(), m.homeTeamId(), m.awayTeamId(), now);
                tombstones++;
            } else {
                fixtureXgDao.upsert(m.fixtureId(), m.homeTeamId(), m.awayTeamId(), xg.home(), xg.away(), now);
            }
            cached.add(m.fixtureId());
            stored++;
        }
        if (attempts > 0) {
            log.info(
                    "ApiFootball xG cache attempts={} stored={} unavailable={}",
                    attempts,
                    stored,
                    tombstones
            );
        }
    }

    static boolean xgMetricsReady(int xgSamples, int matchesPlayed) {
        return matchesPlayed > 0
                && xgSamples >= MIN_XG_SAMPLES_FOR_METRICS
                && (double) xgSamples / matchesPlayed >= MIN_XG_COVERAGE;
    }

    static boolean isTransportLikeStats(JsonNode root) {
        if (root == null) {
            return true;
        }
        JsonNode errors = root.get("errors");
        if (errors != null && !errors.isNull() && !errors.isMissingNode()) {
            if (errors.isObject() && !errors.isEmpty()) {
                return true;
            }
            if (errors.isArray() && !errors.isEmpty()) {
                return true;
            }
            if (errors.isTextual() && !errors.asText().isBlank()) {
                return true;
            }
        }
        JsonNode arr = root.path("response");
        return !arr.isArray() || arr.size() < 2;
    }

    static DoublePair parseExpectedGoals(JsonNode root, int homeTeamId, int awayTeamId) {
        if (root == null) {
            return null;
        }
        JsonNode arr = root.path("response");
        if (!arr.isArray() || arr.size() < 2) {
            return null;
        }
        Double home = null;
        Double away = null;
        for (JsonNode teamNode : arr) {
            int teamId = teamNode.path("team").path("id").asInt(0);
            Double xg = null;
            for (JsonNode stat : teamNode.path("statistics")) {
                if ("expected_goals".equalsIgnoreCase(stat.path("type").asText())) {
                    JsonNode value = stat.path("value");
                    if (value.isNumber()) {
                        xg = value.asDouble();
                    } else if (value.isTextual()) {
                        try {
                            xg = Double.parseDouble(value.asText());
                        } catch (NumberFormatException ignored) {
                            xg = null;
                        }
                    } else if (!value.isNull() && !value.isMissingNode()) {
                        try {
                            xg = Double.parseDouble(value.asText());
                        } catch (NumberFormatException ignored) {
                            xg = null;
                        }
                    }
                    break;
                }
            }
            if (xg == null) {
                continue;
            }
            if (teamId == homeTeamId) {
                home = xg;
            } else if (teamId == awayTeamId) {
                away = xg;
            }
        }
        if (home == null || away == null) {
            return null;
        }
        return new DoublePair(home, away);
    }

    private static FinishedMatch toFinished(Response r) {
        if (r == null || r.getFixture() == null || r.getTeams() == null || r.getGoals() == null) {
            return null;
        }
        String status = r.getFixture().getStatus() != null ? r.getFixture().getStatus().getMyshort() : null;
        if (status == null || !FINISHED.contains(status)) {
            return null;
        }
        Integer hg = r.getGoals().getHome();
        Integer ag = r.getGoals().getAway();
        if (hg == null || ag == null) {
            return null;
        }
        int homeId = r.getTeams().getHome() != null ? r.getTeams().getHome().getId() : 0;
        int awayId = r.getTeams().getAway() != null ? r.getTeams().getAway().getId() : 0;
        if (homeId <= 0 || awayId <= 0) {
            return null;
        }
        Integer htH = null;
        Integer htA = null;
        Score score = r.getScore();
        if (score != null && score.getHalftime() != null) {
            htH = score.getHalftime().getHome();
            htA = score.getHalftime().getAway();
        }
        long ts = r.getFixture().getTimestamp() != null ? r.getFixture().getTimestamp() : 0L;
        return new FinishedMatch(r.getFixture().getPublicId(), homeId, awayId, hg, ag, htH, htA, ts);
    }

    private static LeagueAveragesSnapshot buildLeagueSnapshot(List<TeamStatsSnapshot> teams, Instant fetchedAt) {
        double homeScored = average(teams.stream().map(TeamStatsSnapshot::scoredHome).toList());
        double awayScored = average(teams.stream().map(TeamStatsSnapshot::scoredAway).toList());
        double homeConceded = average(teams.stream().map(TeamStatsSnapshot::concededHome).toList());
        double awayConceded = average(teams.stream().map(TeamStatsSnapshot::concededAway).toList());
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
        return new LeagueAveragesSnapshot(0, homeScored, awayScored, homeConceded, awayConceded, fetchedAt);
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    record FinishedMatch(
            int fixtureId,
            int homeTeamId,
            int awayTeamId,
            int homeGoals,
            int awayGoals,
            Integer htHome,
            Integer htAway,
            long timestamp
    ) {
    }

    record DoublePair(double home, double away) {
    }

    private static final class TeamAgg {
        private final int teamId;
        private int mp;
        private int homeMp;
        private int awayMp;
        private int gf;
        private int ga;
        private int homeGf;
        private int homeGa;
        private int awayGf;
        private int awayGa;
        private int wins;
        private int draws;
        private int homeWins;
        private int homeDraws;
        private int awayWins;
        private int awayDraws;
        private int btts;
        private int bttsHome;
        private int bttsAway;
        private int cs;
        private int csHome;
        private int csAway;
        private int fts;
        private int ftsHome;
        private int ftsAway;
        private int over25;
        private int over25Home;
        private int over25Away;
        private int under25;
        private int under25Home;
        private int under25Away;
        private int htPoints;
        private int leadingAtHt;
        private int htSample;
        private double xgFor;
        private double xgAgainst;
        private double xgHome;
        private double xgAway;
        private double xgaHome;
        private double xgaAway;
        private int xgSamples;
        private int xgHomeSamples;
        private int xgAwaySamples;
        private double xPtsSum;
        private int actualPtsOnXg;
        private final List<FinishedMatch> chronHome = new ArrayList<>();
        private final List<FinishedMatch> chronAway = new ArrayList<>();
        private final List<FinishedMatch> chronAll = new ArrayList<>();

        private TeamAgg(int teamId) {
            this.teamId = teamId;
        }

        private void addHome(FinishedMatch m, Double xgForMatch, Double xgAgainstMatch) {
            chronAll.add(m);
            chronHome.add(m);
            mp++;
            homeMp++;
            gf += m.homeGoals();
            ga += m.awayGoals();
            homeGf += m.homeGoals();
            homeGa += m.awayGoals();
            applyResult(m.homeGoals(), m.awayGoals(), true);
            applyXg(xgForMatch, xgAgainstMatch, true);
            applyXpts(xgForMatch, xgAgainstMatch, m.homeGoals(), m.awayGoals());
            applyHt(m, true);
        }

        private void addAway(FinishedMatch m, Double xgForMatch, Double xgAgainstMatch) {
            chronAll.add(m);
            chronAway.add(m);
            mp++;
            awayMp++;
            gf += m.awayGoals();
            ga += m.homeGoals();
            awayGf += m.awayGoals();
            awayGa += m.homeGoals();
            applyResult(m.awayGoals(), m.homeGoals(), false);
            applyXg(xgForMatch, xgAgainstMatch, false);
            applyXpts(xgForMatch, xgAgainstMatch, m.awayGoals(), m.homeGoals());
            applyHt(m, false);
        }

        private void applyResult(int scored, int conceded, boolean home) {
            if (scored > conceded) {
                wins++;
                if (home) {
                    homeWins++;
                } else {
                    awayWins++;
                }
            } else if (scored == conceded) {
                draws++;
                if (home) {
                    homeDraws++;
                } else {
                    awayDraws++;
                }
            }
            if (scored > 0 && conceded > 0) {
                btts++;
                if (home) {
                    bttsHome++;
                } else {
                    bttsAway++;
                }
            }
            if (conceded == 0) {
                cs++;
                if (home) {
                    csHome++;
                } else {
                    csAway++;
                }
            }
            if (scored == 0) {
                fts++;
                if (home) {
                    ftsHome++;
                } else {
                    ftsAway++;
                }
            }
            int total = scored + conceded;
            if (total > 2) {
                over25++;
                if (home) {
                    over25Home++;
                } else {
                    over25Away++;
                }
            } else {
                under25++;
                if (home) {
                    under25Home++;
                } else {
                    under25Away++;
                }
            }
        }

        private void applyXg(Double xgForMatch, Double xgAgainstMatch, boolean home) {
            if (xgForMatch == null || xgAgainstMatch == null) {
                return;
            }
            xgFor += xgForMatch;
            xgAgainst += xgAgainstMatch;
            xgSamples++;
            if (home) {
                xgHome += xgForMatch;
                xgaHome += xgAgainstMatch;
                xgHomeSamples++;
            } else {
                xgAway += xgForMatch;
                xgaAway += xgAgainstMatch;
                xgAwaySamples++;
            }
        }

        private void applyXpts(Double xgForMatch, Double xgAgainstMatch, int scored, int conceded) {
            if (xgForMatch == null || xgAgainstMatch == null) {
                return;
            }
            xPtsSum += MarketScoreMatrix.expectedPoints(xgForMatch, xgAgainstMatch, XPTS_MAX_GOALS);
            actualPtsOnXg += points(scored, conceded);
        }

        private void applyHt(FinishedMatch m, boolean home) {
            if (m.htHome() == null || m.htAway() == null) {
                return;
            }
            htSample++;
            int htFor = home ? m.htHome() : m.htAway();
            int htAgainst = home ? m.htAway() : m.htHome();
            htPoints += points(htFor, htAgainst);
            if (htFor > htAgainst) {
                leadingAtHt++;
            }
        }

        private static int points(int forGoals, int againstGoals) {
            if (forGoals > againstGoals) {
                return 3;
            }
            if (forGoals == againstGoals) {
                return 1;
            }
            return 0;
        }

        private TeamStatsSnapshot toSnapshot(Instant fetchedAt) {
            Team team = DaoUtil.TEAMS.get(teamId);
            if (team == null || team.getCode() == null || team.getCode().isBlank()) {
                log.warn("ApiFootball snapshot: unknown team id {}", teamId);
                return null;
            }
            double scoredOverall = perMatch(gf, mp);
            double scoredHome = perMatch(homeGf, homeMp);
            double scoredAway = perMatch(awayGf, awayMp);
            double concededOverall = perMatch(ga, mp);
            double concededHome = perMatch(homeGa, homeMp);
            double concededAway = perMatch(awayGa, awayMp);

            boolean xgReady = xgMetricsReady(xgSamples, mp);
            Double xgOverall = xgReady ? xgFor / xgSamples : null;
            Double xgaOverall = xgReady ? xgAgainst / xgSamples : null;
            Double xgdOverall = xgOverall != null && xgaOverall != null ? xgOverall - xgaOverall : null;
            Double xgHomeAvg = xgReady && xgHomeSamples > 0 ? xgHome / xgHomeSamples : null;
            Double xgAwayAvg = xgReady && xgAwaySamples > 0 ? xgAway / xgAwaySamples : null;

            FormStats form = formStats(lastN(chronAll, 6));
            FormStats formHome = formStats(lastN(chronHome, 6));
            FormStats formAway = formStats(lastN(chronAway, 6));

            double homePpg = perMatch(homeWins * 3 + homeDraws, homeMp);
            double awayPpg = perMatch(awayWins * 3 + awayDraws, awayMp);
            Double homeAdvantage = homeMp > 0 && awayMp > 0 && awayPpg > 0
                    ? ((homePpg / awayPpg) - 1.0) * 100.0
                    : null;

            Double xPts = xgReady ? xPtsSum : null;
            Double actualPts = xgReady ? (double) actualPtsOnXg : null;
            Double xPtsDelta = xPts != null && actualPts != null ? actualPts - xPts : null;

            ExtendedTeamMetrics ext = ExtendedTeamMetrics.builder()
                    .formBtts(form.bttsPct, formHome.bttsPct, formAway.bttsPct)
                    .formCs(form.csPct, formHome.csPct, formAway.csPct)
                    .xPts(xPts, actualPts, xPtsDelta)
                    .homeAdvantage(homeAdvantage)
                    .seasonBtts(pct(btts, mp), pct(bttsHome, homeMp), pct(bttsAway, awayMp))
                    .seasonCs(pct(cs, mp), pct(csHome, homeMp), pct(csAway, awayMp))
                    .ftsHome(pct(ftsHome, homeMp))
                    .ftsAway(pct(ftsAway, awayMp))
                    .drawPct(pct(draws, mp), pct(homeDraws, homeMp), pct(awayDraws, awayMp))
                    .seasonScored(scoredOverall, scoredHome, scoredAway)
                    .seasonConceded(concededOverall, concededHome, concededAway)
                    .over25(pct(over25, mp), pct(over25Home, homeMp), pct(over25Away, awayMp))
                    .under25(pct(under25, mp), pct(under25Home, homeMp), pct(under25Away, awayMp))
                    .homePpg(homePpg)
                    .awayPpg(awayPpg)
                    .htPpg(htSample > 0 ? (double) htPoints / htSample : null)
                    .winningAtHtPct(pct(leadingAtHt, htSample))
                    .build();

            return new TeamStatsSnapshot(
                    team.getCode(),
                    scoredOverall,
                    scoredHome,
                    scoredAway,
                    concededOverall,
                    concededHome,
                    concededAway,
                    xgOverall,
                    xgaOverall,
                    xgdOverall,
                    xgHomeAvg,
                    xgAwayAvg,
                    ext,
                    fetchedAt
            );
        }

        private static List<FinishedMatch> lastN(List<FinishedMatch> all, int n) {
            if (all.size() <= n) {
                return List.copyOf(all);
            }
            return List.copyOf(all.subList(all.size() - n, all.size()));
        }

        private FormStats formStats(List<FinishedMatch> matches) {
            if (matches.isEmpty()) {
                return FormStats.empty();
            }
            int n = 0;
            int btts = 0;
            int cs = 0;
            for (FinishedMatch m : matches) {
                boolean home = m.homeTeamId() == teamId;
                int gf = home ? m.homeGoals() : m.awayGoals();
                int ga = home ? m.awayGoals() : m.homeGoals();
                n++;
                if (gf > 0 && ga > 0) {
                    btts++;
                }
                if (ga == 0) {
                    cs++;
                }
            }
            return new FormStats(pct(btts, n), pct(cs, n));
        }

        private static double perMatch(int value, int matches) {
            return matches > 0 ? (double) value / matches : 0;
        }

        private static Double pct(int count, int matches) {
            if (matches <= 0) {
                return null;
            }
            return 100.0 * count / matches;
        }
    }

    private record FormStats(Double bttsPct, Double csPct) {
        static FormStats empty() {
            return new FormStats(null, null);
        }
    }
}
