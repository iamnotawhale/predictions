package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import zhigalin.predictions.recommender.BettingRecommendationService;

@Component
@ConditionalOnProperty(name = "predictions.startup.warm-recommender", havingValue = "true", matchIfMissing = true)
public class RecommenderWarmupOnStartup {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final BettingRecommendationService bettingRecommendationService;

    public RecommenderWarmupOnStartup(BettingRecommendationService bettingRecommendationService) {
        this.bettingRecommendationService = bettingRecommendationService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmWhenReady() {
        try {
            log.info("Startup betting recommender warm started (background)");
            bettingRecommendationService.ensureCurrentWeekRecommendations();
            log.info("Startup betting recommender warm finished");
        } catch (Exception e) {
            log.warn("Startup betting recommender warm failed: {}", e.getMessage());
        }
    }
}
