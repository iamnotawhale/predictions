package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import zhigalin.predictions.service.api.TeamLogoCacheService;

@Component
public class TeamLogoCacheWarmup {
    private static final Logger log = LoggerFactory.getLogger("server");

    private final TeamLogoCacheService teamLogoCacheService;

    public TeamLogoCacheWarmup(TeamLogoCacheService teamLogoCacheService) {
        this.teamLogoCacheService = teamLogoCacheService;
    }

    @Async
    @Order(100)
    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        try {
            teamLogoCacheService.warmEplTeams();
        } catch (Exception e) {
            log.warn("Team logo warmup failed: {}", e.getMessage());
        }
    }
}
