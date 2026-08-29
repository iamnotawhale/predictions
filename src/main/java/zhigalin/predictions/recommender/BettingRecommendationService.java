package zhigalin.predictions.recommender;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
            int stored = 0;
            for (Match match : matches) {
                Optional<MatchRecommendationSnapshot> recommendation = computeAndStore(match, weekId, league.get());
                if (recommendation.isPresent()) {
                    statsDao.saveRecommendation(recommendation.get());
                    stored++;
                }
            }
            log.info("Betting recommender refreshed for week {} ({} / {} matches)", weekId, stored, matches.size());
            return stored;
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
            FootyStatsLeagueSnapshot league
    ) {
        String homeCode = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
        String awayCode = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();
        Optional<FootyStatsTeamSnapshot> homeStats = statsDao.findTeamStats(weekId, homeCode);
        Optional<FootyStatsTeamSnapshot> awayStats = statsDao.findTeamStats(weekId, awayCode);
        if (homeStats.isEmpty() || awayStats.isEmpty()) {
            return Optional.empty();
        }

        FootyStatsTeamSnapshot home = homeStats.get();
        FootyStatsTeamSnapshot away = awayStats.get();

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
