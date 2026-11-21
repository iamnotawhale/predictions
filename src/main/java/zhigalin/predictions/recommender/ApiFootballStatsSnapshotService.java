package zhigalin.predictions.recommender;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.recommender.FotMobClient.FotMobFixture;
import zhigalin.predictions.recommender.UnderstatClient.UnderstatMatch;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.LeagueAveragesSnapshot;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.api.EspnTeamSeasonStatsService;
import zhigalin.predictions.service.api.EspnTeamSeasonStatsService.TeamSeasonExtras;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.DaoUtil;

@Service
public class ApiFootballStatsSnapshotService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final int MIN_TEAMS = 15;
    private static final int XPTS_MAX_GOALS = 10;
    private static final int MIN_XG_SAMPLES_FOR_METRICS = 8;
    private static final double MIN_XG_COVERAGE = 0.8;
    private static final double MIN_XG_MATCH_COVERAGE = 0.5;
    private static final int MAX_XG_FETCH_PER_REFRESH = 25;
    private static final int RECENT_XG_WINDOW = 6;
    private static final int MIN_RECENT_XG_SAMPLES = 4;
    private static final int MIN_HT_SAMPLE = 4;
    private static final Duration KICKOFF_MATCH_WINDOW = Duration.ofHours(36);

    private final MatchService matchService;
    private final FixtureXgDao fixtureXgDao;
    private final UnderstatClient understatClient;
    private final FotMobClient fotMobClient;
    private final EspnTeamSeasonStatsService espnTeamSeasonStatsService;

    public ApiFootballStatsSnapshotService(
            MatchService matchService,
            FixtureXgDao fixtureXgDao,
            UnderstatClient understatClient,
            FotMobClient fotMobClient,
            EspnTeamSeasonStatsService espnTeamSeasonStatsService
    ) {
        this.matchService = matchService;
        this.fixtureXgDao = fixtureXgDao;
        this.understatClient = understatClient;
        this.fotMobClient = fotMobClient;
        this.espnTeamSeasonStatsService = espnTeamSeasonStatsService;
    }

    public LeagueStatsSnapshot fetchSnapshot() {
        Instant fetchedAt = Instant.now();
        LocalDate seasonStart = DataInitService.seasonStartDate();
        List<FinishedMatch> finished = new ArrayList<>();
        for (Match match : matchService.findFinishedMatches()) {
            FinishedMatch m = toFinished(match, seasonStart);
            if (m != null) {
                finished.add(m);
            }
        }
        finished.sort(Comparator.comparingLong(FinishedMatch::timestamp));
        if (finished.isEmpty()) {
            throw new IllegalStateException(
                    "No finished EPL fixtures in DB for season starting " + seasonStart
            );
        }

        syncUnderstatXg(finished);
        syncFotMobXg(finished);

        Map<Integer, FixtureXgDao.XgRow> xgByMatch = new HashMap<>();
        for (FixtureXgDao.XgRow row : fixtureXgDao.findAllWithXg()) {
            xgByMatch.put(row.matchId(), row);
        }
        int xgCovered = 0;
        for (FinishedMatch m : finished) {
            if (xgByMatch.containsKey(m.fixtureId())) {
                xgCovered++;
            }
        }
        boolean xgCoverageOk = finished.size() > 0
                && (double) xgCovered / finished.size() >= MIN_XG_MATCH_COVERAGE;

        Map<Integer, TeamAgg> byTeam = new HashMap<>();
        for (FinishedMatch m : finished) {
            FixtureXgDao.XgRow xg = xgByMatch.get(m.fixtureId());
            Double homeXg = xg != null ? xg.homeXg() : null;
            Double awayXg = xg != null ? xg.awayXg() : null;
            byTeam.computeIfAbsent(m.homeTeamId(), TeamAgg::new).addHome(m, homeXg, awayXg);
            byTeam.computeIfAbsent(m.awayTeamId(), TeamAgg::new).addAway(m, awayXg, homeXg);
        }

        List<TeamStatsSnapshot> teams = new ArrayList<>();
        int withMatchXg = 0;
        int withEspnFallbackXg = 0;
        for (TeamAgg agg : byTeam.values()) {
            TeamStatsSnapshot snap = agg.toSnapshot(fetchedAt);
            if (snap == null) {
                continue;
            }
            if (snap.xgOverall() != null) {
                withMatchXg++;
                teams.add(snap);
                continue;
            }
            if (!xgCoverageOk) {
                TeamStatsSnapshot withEspn = applyEspnSeasonXgFallback(snap);
                if (withEspn.xgOverall() != null) {
                    withEspnFallbackXg++;
                }
                teams.add(withEspn);
            } else {
                teams.add(snap);
            }
        }
        if (teams.size() < MIN_TEAMS) {
            throw new IllegalStateException(
                    "Season snapshot parsed only " + teams.size() + " teams (expected >= " + MIN_TEAMS + ")"
            );
        }

        LeagueAveragesSnapshot league = buildLeagueSnapshot(teams, fetchedAt);
        log.info(
                "Season snapshot complete: {} teams, {} finished fixtures, {} match xG, {} ESPN xG fallback",
                teams.size(),
                finished.size(),
                withMatchXg,
                withEspnFallbackXg
        );
        return new LeagueStatsSnapshot(teams, league);
    }

    void syncUnderstatXg(List<FinishedMatch> finished) {
        Set<Integer> cached = new HashSet<>(fixtureXgDao.findCachedFixtureIds(
                finished.stream().map(FinishedMatch::fixtureId).toList()
        ));
        boolean preferFresh = cached.size() < finished.size();
        List<UnderstatMatch> understatMatches =
                understatClient.fetchFinishedWithXg(DataInitService.SEASON, preferFresh);
        if (understatMatches.isEmpty()) {
            return;
        }

        Map<String, Integer> teamIdByCode = teamIdByCode();
        Instant now = Instant.now();
        int stored = 0;
        int ambiguous = 0;
        int unmatched = 0;
        for (UnderstatMatch u : understatMatches) {
            Integer homeId = teamIdByCode.get(u.homeCode());
            Integer awayId = teamIdByCode.get(u.awayCode());
            if (homeId == null || awayId == null) {
                continue;
            }
            List<FinishedMatch> candidates = new ArrayList<>();
            for (FinishedMatch m : finished) {
                if (m.homeTeamId() != homeId || m.awayTeamId() != awayId) {
                    continue;
                }
                if (m.kickoff() == null) {
                    continue;
                }
                long hours = Math.abs(Duration.between(m.kickoff(), u.kickoff()).toHours());
                if (hours <= KICKOFF_MATCH_WINDOW.toHours()) {
                    candidates.add(m);
                }
            }
            if (candidates.isEmpty()) {
                unmatched++;
                continue;
            }
            if (candidates.size() != 1) {
                ambiguous++;
                log.warn(
                        "Understat xG skip ambiguous match {}-{} @ {}: {} DB candidates",
                        u.homeCode(),
                        u.awayCode(),
                        u.kickoff(),
                        candidates.size()
                );
                continue;
            }
            FinishedMatch match = candidates.getFirst();
            fixtureXgDao.upsert(
                    match.fixtureId(),
                    match.homeTeamId(),
                    match.awayTeamId(),
                    u.homeXg(),
                    u.awayXg(),
                    now
            );
            cached.add(match.fixtureId());
            stored++;
        }
        if (stored > 0 || ambiguous > 0 || unmatched > 0) {
            log.info(
                    "Understat xG sync stored={} ambiguous={} unmatched={} cached={}",
                    stored,
                    ambiguous,
                    unmatched,
                    cached.size()
            );
        }
    }

    void syncFotMobXg(List<FinishedMatch> finished) {
        Set<Integer> cached = new HashSet<>(fixtureXgDao.findCachedFixtureIds(
                finished.stream().map(FinishedMatch::fixtureId).toList()
        ));
        boolean preferFresh = cached.size() < finished.size();
        List<FotMobFixture> fotmobFixtures =
                fotMobClient.fetchFinishedFixtures(DataInitService.SEASON, preferFresh);
        if (fotmobFixtures.isEmpty()) {
            return;
        }

        Map<String, Integer> teamIdByCode = teamIdByCode();
        Instant now = Instant.now();
        int attempts = 0;
        int stored = 0;
        int ambiguous = 0;
        int unmatched = 0;
        int noXg = 0;
        for (FotMobFixture f : fotmobFixtures) {
            Integer homeId = teamIdByCode.get(f.homeCode());
            Integer awayId = teamIdByCode.get(f.awayCode());
            if (homeId == null || awayId == null) {
                continue;
            }
            List<FinishedMatch> candidates = new ArrayList<>();
            for (FinishedMatch m : finished) {
                if (m.homeTeamId() != homeId || m.awayTeamId() != awayId) {
                    continue;
                }
                if (m.kickoff() == null) {
                    continue;
                }
                long hours = Math.abs(Duration.between(m.kickoff(), f.kickoff()).toHours());
                if (hours <= KICKOFF_MATCH_WINDOW.toHours()) {
                    candidates.add(m);
                }
            }
            if (candidates.isEmpty()) {
                unmatched++;
                continue;
            }
            if (candidates.size() != 1) {
                ambiguous++;
                log.warn(
                        "FotMob xG skip ambiguous match {}-{} @ {}: {} DB candidates",
                        f.homeCode(),
                        f.awayCode(),
                        f.kickoff(),
                        candidates.size()
                );
                continue;
            }
            FinishedMatch match = candidates.getFirst();
            if (cached.contains(match.fixtureId())) {
                continue;
            }
            if (attempts >= MAX_XG_FETCH_PER_REFRESH) {
                log.warn(
                        "FotMob xG backfill capped at {} matchDetails this refresh ({} still missing)",
                        MAX_XG_FETCH_PER_REFRESH,
                        finished.size() - cached.size()
                );
                break;
            }
            attempts++;
            var xg = fotMobClient.fetchMatchXg(f.fotmobId());
            if (xg.isEmpty()) {
                noXg++;
                continue;
            }
            fixtureXgDao.upsert(
                    match.fixtureId(),
                    match.homeTeamId(),
                    match.awayTeamId(),
                    xg.get().homeXg(),
                    xg.get().awayXg(),
                    now
            );
            cached.add(match.fixtureId());
            stored++;
        }
        if (attempts > 0 || ambiguous > 0 || unmatched > 0) {
            log.info(
                    "FotMob xG sync attempts={} stored={} noXg={} ambiguous={} unmatched={} cached={}",
                    attempts,
                    stored,
                    noXg,
                    ambiguous,
                    unmatched,
                    cached.size()
            );
        }
    }

    private TeamStatsSnapshot applyEspnSeasonXgFallback(TeamStatsSnapshot snap) {
        TeamSeasonExtras extras = espnTeamSeasonStatsService.forInternalCode(snap.teamCode());
        if (extras == null || (extras.avgXg() == null && extras.avgXga() == null)) {
            return snap;
        }
        Double xg = extras.avgXg();
        Double xga = extras.avgXga();
        Double xgd = xg != null && xga != null ? xg - xga : null;
        return new TeamStatsSnapshot(
                snap.teamCode(),
                snap.scoredOverall(),
                snap.scoredHome(),
                snap.scoredAway(),
                snap.concededOverall(),
                snap.concededHome(),
                snap.concededAway(),
                xg,
                xga,
                xgd,
                xg,
                xg,
                snap.extendedOrEmpty().withoutRecentXg(),
                snap.fetchedAt()
        );
    }

    static boolean xgMetricsReady(int xgSamples, int matchesPlayed) {
        return matchesPlayed > 0
                && xgSamples >= MIN_XG_SAMPLES_FOR_METRICS
                && (double) xgSamples / matchesPlayed >= MIN_XG_COVERAGE;
    }

    private static Map<String, Integer> teamIdByCode() {
        Map<String, Integer> out = new HashMap<>();
        for (Team team : DaoUtil.TEAMS.values()) {
            if (team != null && team.getCode() != null && !team.getCode().isBlank()) {
                out.put(team.getCode(), team.getId());
            }
        }
        return out;
    }

    private static FinishedMatch toFinished(Match match, LocalDate seasonStart) {
        if (match == null || !"ft".equals(match.getStatus())) {
            return null;
        }
        if (match.getHomeScore() == null || match.getAwayScore() == null) {
            return null;
        }
        if (match.getStartsAt() == null
                || match.getStartsAt().toLocalDate().isBefore(seasonStart)) {
            return null;
        }
        long ts = match.getStartsAt().atZone(AppTimeZones.DISPLAY).toEpochSecond();
        return new FinishedMatch(
                match.getId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getHtHome(),
                match.getHtAway(),
                match.getStartsAt(),
                ts
        );
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
            LocalDateTime kickoff,
            long timestamp
    ) {
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
        private final List<XgSample> chronXg = new ArrayList<>();

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
            chronXg.add(new XgSample(xgForMatch, xgAgainstMatch));
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
                log.warn("Season snapshot: unknown team id {}", teamId);
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
            RecentXg recentXg = recentXg(chronXg);

            boolean htReady = htSample >= MIN_HT_SAMPLE;
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
                    .htPpg(htReady ? (double) htPoints / htSample : null)
                    .winningAtHtPct(htReady ? pct(leadingAtHt, htSample) : null)
                    .recentXg(recentXg.xgPerMatch(), recentXg.xgaPerMatch(), recentXg.samples())
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

        private static <T> List<T> lastNList(List<T> all, int n) {
            if (all.size() <= n) {
                return List.copyOf(all);
            }
            return List.copyOf(all.subList(all.size() - n, all.size()));
        }

        private static RecentXg recentXg(List<XgSample> chronXg) {
            if (chronXg.isEmpty()) {
                return RecentXg.none();
            }
            List<XgSample> window = lastNList(chronXg, RECENT_XG_WINDOW);
            int n = window.size();
            if (n < MIN_RECENT_XG_SAMPLES) {
                return new RecentXg(null, null, n);
            }
            double xgSum = 0;
            double xgaSum = 0;
            for (XgSample sample : window) {
                xgSum += sample.xgFor();
                xgaSum += sample.xgAgainst();
            }
            return new RecentXg(xgSum / n, xgaSum / n, n);
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

    private record XgSample(double xgFor, double xgAgainst) {
    }

    private record RecentXg(Double xgPerMatch, Double xgaPerMatch, Integer samples) {
        static RecentXg none() {
            return new RecentXg(null, null, null);
        }
    }
}
