package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.DataInitService;

@Component
@ConditionalOnProperty(name = "predictions.startup.sync-match-times", havingValue = "true")
public class MatchTimesSyncOnStartup {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final DataInitService dataInitService;
    private final PanicSender panicSender;

    public MatchTimesSyncOnStartup(DataInitService dataInitService, PanicSender panicSender) {
        this.dataInitService = dataInitService;
        this.panicSender = panicSender;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncWhenReady() {
        try {
            log.info("Syncing match times from API on startup");
            dataInitService.syncMatchTimesFromApi();
            log.info("Match times synced from API on startup");
        } catch (Exception e) {
            log.error("Startup match time sync failed: {}", e.getMessage(), e);
            panicSender.sendPanic("Startup match time sync", e);
        }
    }
}
