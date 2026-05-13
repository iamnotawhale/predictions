package zhigalin.predictions.service.notification;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final NotificationService notifications;
    private final MatchService matchService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public NotificationScheduler(NotificationService notifications, MatchService matchService) {
        this.notifications = notifications;
        this.matchService = matchService;
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void morningMatches() {
        log.info("Morning matches run");
        notifications.sendTodayMatchNotification();
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void fullTimeListener() {
        if (isProcessing.compareAndSet(false, true)) {
            executor.submit(() -> {
                try {
                    matchService.listenForMatchUpdates();
                    List<Match> matches = matchService.processBatch();
                    if (!matches.isEmpty()) {
                        log.info("Full time matches processed");
                        matches.forEach(notifications::sendFullTime);
                    }
                } catch (Exception e) {
                    log.error("fullTimeListener error: {}", e.getMessage());
                } finally {
                    isProcessing.set(false);
                }
            });
        } else {
            log.warn("Skipping execution: previous task still running");
        }
    }
}
