package zhigalin.predictions.config;

import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import zhigalin.predictions.service.predict.PredictionService;

@Component
@Order(100)
public class PointsRecalculationOnStartup {

    private final PredictionService predictionService;

    public PointsRecalculationOnStartup(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted() {
        predictionService.recalculateFinishedMatchPoints();
    }
}
