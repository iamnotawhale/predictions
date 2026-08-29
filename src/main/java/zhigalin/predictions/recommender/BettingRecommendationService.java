package zhigalin.predictions.recommender;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;
import zhigalin.predictions.recommender.model.MatchRecommendationSnapshot;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.util.DaoUtil;

@Service
public class BettingRecommendationService {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final FootyStatsScraperService scraperService;
    private final SoccerStatsScraperService soccerStatsScraperService;
    private final FootyStatsStatsDao statsDao;
    private final MatchService matchService;
    private final OddsService oddsService;
    private final ConcurrentHashMap<Integer, CompletableFuture<Integer>> refreshInFlight = new ConcurrentHashMap<>();

    public BettingRecommendationService(
            FootyStatsScraperService scraperService,
            SoccerStatsScraperService soccerStatsScraperService,
            FootyStatsStatsDao statsDao,
            MatchService matchService,
            OddsService oddsService
    ) {
        this.scraperService = scraperService;
        this.soccerStatsScraperService = soccerStatsScraperService;
        this.statsDao = statsDao;
        this.matchService = matchService;
        this.oddsService = oddsService;
    }

    public int refreshForWeek(int weekId) {
        CompletableFuture<Integer> created = new CompletableFuture<>();
        CompletableFuture<Integer> existing = refreshInFlight.putIfAbsent(weekId, created);
        if (existing != null) {
            return joinRefresh(existing, weekId);
        }
        try {
            int stored = doRefreshForWeek(weekId);
            created.complete(stored);
            return stored;
        } catch (Exception e) {
            created.completeExceptionally(e);
            throw unwrapRefreshFailure(e, weekId);
        } finally {
            refreshInFlight.remove(weekId, created);
        }
    }

    private int joinRefresh(CompletableFuture<Integer> future, int weekId) {
        try {
            return future.join();
        } catch (CompletionException e) {
            throw unwrapRefreshFailure(e.getCause() != null ? e.getCause() : e, weekId);
        }
    }

    private static IllegalStateException unwrapRefreshFailure(Throwable cause, int weekId) {
        if (cause instanceof IllegalStateException ise) {
            return ise;
        }
        return new IllegalStateException(
                "Не удалось обновить рекомендации для тура " + weekId + ": " + cause.getMessage(),
                cause
        );
    }

    private int doRefreshForWeek(int weekId) {
        try {
            FootyStatsScraperService.ParsedSnapshot snapshot = scraperService.fetchSnapshot();
            List<FootyStatsTeamSnapshot> teams;
            try {
                teams = soccerStatsScraperService.enrich(snapshot.teams());
            } catch (Exception e) {
                log.warn("SoccerSTATS enrich skipped: {}", e.getMessage());
                teams = snapshot.teams();
            }
            statsDao.replaceTeamStats(weekId, teams);
            statsDao.saveLeagueSnapshot(snapshot.leagueForWeek(weekId));
            statsDao.deleteRecommendationsForWeek(weekId);

            List<Match> matches = matchService.findAllByWeekId(weekId);
            oddsService.ensureFresh(matches);
            Optional<FootyStatsLeagueSnapshot> league = statsDao.findLeagueSnapshot(weekId);
            if (league.isEmpty()) {
                log.warn("Betting recommender: no league snapshot for week {}", weekId);
                return 0;
            }
            Map<String, FootyStatsTeamSnapshot> statsByCode = statsDao.findTeamStats(weekId).stream()
                    .collect(Collectors.toMap(FootyStatsTeamSnapshot::teamCode, Function.identity(), (a, b) -> a));
            List<MatchRecommendationSnapshot> toStore = new ArrayList<>();
            for (Match match : matches) {
                Optional<MatchRecommendationSnapshot> recommendation =
                        computeAndStore(match, weekId, league.get(), statsByCode);
                recommendation.ifPresent(toStore::add);
            }
            statsDao.saveRecommendations(toStore);
            log.info("Betting recommender refreshed for week {} ({} / {} matches)", weekId, toStore.size(), matches.size());
            return toStore.size();
        } catch (Exception e) {
            log.warn("Betting recommender refresh failed for week {}: {}", weekId, e.getMessage());
            throw new IllegalStateException("Не удалось обновить рекомендации для тура " + weekId + ": " + e.getMessage(), e);
        }
    }

    public void ensureCurrentWeekRecommendations() {
        int weekId = DaoUtil.currentWeekId;
        if (weekId <= 0) {
            return;
        }
        if (!statsDao.hasRecommendationsForWeek(weekId)) {
            try {
                refreshForWeek(weekId);
            } catch (Exception e) {
                log.warn("Betting recommender ensure failed for week {}: {}", weekId, e.getMessage());
            }
        }
    }

    public Optional<MatchRecommendationSnapshot> recommendationForMatch(int matchPublicId) {
        return statsDao.findRecommendation(matchPublicId);
    }

    private Optional<MatchRecommendationSnapshot> computeAndStore(
            Match match,
            int weekId,
            FootyStatsLeagueSnapshot league,
            Map<String, FootyStatsTeamSnapshot> statsByCode
    ) {
        String homeCode = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
        String awayCode = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();
        FootyStatsTeamSnapshot home = statsByCode.get(homeCode);
        FootyStatsTeamSnapshot away = statsByCode.get(awayCode);
        if (home == null || away == null) {
            return Optional.empty();
        }

        Double oddHome = null;
        Double oddDraw = null;
        Double oddAway = null;
        OddsService.Odd odd = oddsService.getOdd(match.getPublicId());
        if (odd != null) {
            oddHome = odd.home();
            oddDraw = odd.draw();
            oddAway = odd.away();
        }

        PoissonScoreModel.Result result = PoissonScoreModel.recommend(
                oddHome,
                oddDraw,
                oddAway,
                home,
                away,
                league
        );

        return Optional.of(new MatchRecommendationSnapshot(
                match.getPublicId(),
                weekId,
                result.recommendedHome(),
                result.recommendedAway(),
                result.lambdaHome(),
                result.lambdaAway(),
                result.scoreProbability(),
                result.explanationLines(),
                result.summary(),
                Instant.now()
        ));
    }
}
