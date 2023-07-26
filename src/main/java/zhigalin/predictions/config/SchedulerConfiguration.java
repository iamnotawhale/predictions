package zhigalin.predictions.config;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zhigalin.predictions.service.DataInitService;

@Component
@RequiredArgsConstructor
public class SchedulerConfiguration {
    private static final int SIX_MINUTES_DELAY = 360000;
    private final DataInitService service;

    @Scheduled(fixedDelay = SIX_MINUTES_DELAY)
    public void start() {
        service.allInit();
    }
}
