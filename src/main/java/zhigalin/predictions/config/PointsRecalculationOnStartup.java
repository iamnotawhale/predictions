package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import zhigalin.predictions.service.predict.PredictionService;

@Component
public class PointsRecalculationOnStartup {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final PredictionService predictionService;

    public PointsRecalculationOnStartup(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Async
    @Order(50)
    @EventListener(ApplicationReadyEvent.class)
    public void recalculateWhenReady() {
        try {
            log.info("Startup points recalculation started (background)");
            predictionService.recalculateFinishedMatchPoints();
        } catch (Exception e) {
            log.error("Startup points recalculation failed", e);
        }
    }
}
