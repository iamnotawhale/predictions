package zhigalin.predictions.config;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.NotificationService;

@Component
@RequiredArgsConstructor
public class SchedulerConfiguration {
    private static final int SIX_MINUTES_DELAY = 360000;
    private final DataInitService dataInitService;
    private final NotificationService notificationService;
    private final PanicSender panicSender;

    @Scheduled(fixedDelay = SIX_MINUTES_DELAY)
    public void start() {
        try {
            dataInitService.allInit();
//        notificationService.check();
        } catch (Exception e) {
            panicSender.sendPanic(e);
        }
    }
}
