package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import zhigalin.predictions.service.predict.PredictionService;

@Component
@Order(100)
public class PointsRecalculationOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final PredictionService predictionService;

    public PointsRecalculationOnStartup(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            predictionService.recalculateFinishedMatchPoints();
        } catch (Exception e) {
            log.error("Startup points recalculation failed (bot keeps running)", e);
        }
    }
}
