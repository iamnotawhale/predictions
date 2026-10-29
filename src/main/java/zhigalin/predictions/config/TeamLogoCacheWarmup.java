package zhigalin.predictions.config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.service.api.TeamLogoCacheService;

/**
 * Keeps every crest the Mini App can show on disk: EPL clubs and the clubs of upcoming cup matches.
 * Runs at startup and then hourly, which also retries downloads that failed earlier.
 */
@Component
public class TeamLogoCacheWarmup {
    private static final Logger log = LoggerFactory.getLogger("server");

    private final TeamLogoCacheService teamLogoCacheService;
    private final CupMatchDao cupMatchDao;

    public TeamLogoCacheWarmup(TeamLogoCacheService teamLogoCacheService, CupMatchDao cupMatchDao) {
        this.teamLogoCacheService = teamLogoCacheService;
        this.cupMatchDao = cupMatchDao;
    }

    @Async
    @Order(100)
    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        warmAll();
    }

    @Scheduled(initialDelay = 3_600_000L, fixedDelay = 3_600_000L)
    public void warmPeriodically() {
        warmAll();
    }

    void warmAll() {
        try {
            teamLogoCacheService.warmEplTeams();
        } catch (Exception e) {
            log.warn("Team logo warmup failed: {}", e.getMessage());
        }
        try {
            List<String> urls = new ArrayList<>();
            for (CupMatch match : cupMatchDao.findAllFrom(LocalDate.now().minusDays(1))) {
                urls.add(match.getHomeLogoUrl());
                urls.add(match.getAwayLogoUrl());
            }
            int ok = teamLogoCacheService.warmRemote(urls);
            log.info("Cup logo cache warm: {} of {} urls", ok, urls.stream().filter(u -> u != null && !u.isBlank()).count());
        } catch (Exception e) {
            log.warn("Cup logo warmup failed: {}", e.getMessage());
        }
    }
}
