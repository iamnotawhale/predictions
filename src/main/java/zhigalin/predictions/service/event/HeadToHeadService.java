package zhigalin.predictions.service.event;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.repository.event.HeadToHeadDao;

@Service
public class HeadToHeadService {
    private static final Logger log = LoggerFactory.getLogger("server");
    private static final int H2H_LIMIT = 7;

    private final HeadToHeadDao headToHeadDao;
    private final MatchService matchService;
    private final int season;

    public HeadToHeadService(
            HeadToHeadDao headToHeadDao,
            MatchService matchService,
            @Value("${season:2026}") int season
    ) {
        this.headToHeadDao = headToHeadDao;
        this.matchService = matchService;
        this.season = season;
    }

    public void save(HeadToHead h2h) {
        headToHeadDao.save(h2h);
    }

    public void saveFromFinishedMatch(Match match) {
        if (match == null || match.getStatus() == null || !"ft".equalsIgnoreCase(match.getStatus())) {
            return;
        }
        if (match.getHomeTeamScore() == null || match.getAwayTeamScore() == null || match.getLocalDateTime() == null) {
            return;
        }
        save(HeadToHead.builder()
                .homeTeamId(match.getHomeTeamId())
                .awayTeamId(match.getAwayTeamId())
                .homeTeamScore(match.getHomeTeamScore())
                .awayTeamScore(match.getAwayTeamScore())
                .localDateTime(match.getLocalDateTime())
                .leagueName("PL " + season)
                .build());
    }

    public int backfillFinishedSeasonMatches() {
        int processed = 0;
        for (Match match : matchService.findFinishedMatches()) {
            saveFromFinishedMatch(match);
            processed++;
        }
        return processed;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillFinishedMatchesOnStartup() {
        int processed = backfillFinishedSeasonMatches();
        log.info("H2H backfill on startup: processed {} finished matches (season PL {})", processed, season);
    }

    public List<HeadToHead> findAllByTwoTeamsCode(String homeTeamCode, String awayTeamCode) {
        return headToHeadDao.getH2hByTeamsCode(homeTeamCode, awayTeamCode).stream()
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(H2H_LIMIT)
                .toList();
    }

    public List<HeadToHead> findAllByMatch(Match match) {
        return headToHeadDao.getAllByTeamsIds(match.getHomeTeamId(), match.getAwayTeamId()).stream()
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(H2H_LIMIT)
                .toList();
    }

    /** Broader H2H sample for the recommender (UI still uses {@link #H2H_LIMIT}). */
    public List<HeadToHead> findForRecommender(int homeTeamId, int awayTeamId) {
        return headToHeadDao.getAllByTeamsIds(homeTeamId, awayTeamId).stream()
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(20)
                .toList();
    }
}

