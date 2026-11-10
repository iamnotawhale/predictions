package zhigalin.predictions.telegram;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.telegram.model.EPLInfoBot;

@Component
public class WidgetAwareUpdatePoller {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final List<String> ALLOWED = List.of("message", "callback_query", "widget_query");
    private static final int POLL_TIMEOUT_SEC = 25;

    private final boolean enabled;
    private final ApiClient api;
    private final ReminderWidgetQueryHandler widgetQueryHandler;
    private final ObjectProvider<EPLInfoBot> botProvider;
    private final ObjectMapper updateMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread pollThread;

    public WidgetAwareUpdatePoller(
            @Value("${notification.reminder.widget.enabled:false}") boolean enabled,
            ApiClient api,
            ReminderWidgetQueryHandler widgetQueryHandler,
            ObjectProvider<EPLInfoBot> botProvider
    ) {
        this.enabled = enabled;
        this.api = api;
        this.widgetQueryHandler = widgetQueryHandler;
        this.botProvider = botProvider;
        this.updateMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void start() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        api.deleteWebhook();
        pollThread = new Thread(this::loop, "telegram-widget-poller");
        pollThread.setDaemon(true);
        pollThread.start();
        log.info("Widget-aware Telegram getUpdates poller started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
        }
    }

    private void loop() {
        long offset = 0L;
        EPLInfoBot bot = botProvider.getObject();
        while (running.get()) {
            try {
                JsonNode root = api.getUpdates(offset, POLL_TIMEOUT_SEC, ALLOWED);
                if (root == null || !root.path("ok").asBoolean(false)) {
                    if (root != null) {
                        log.warn("getUpdates not ok: {}", root.path("description").asText(""));
                    }
                    Thread.sleep(1000L);
                    continue;
                }
                for (JsonNode updateNode : root.path("result")) {
                    long updateId = updateNode.path("update_id").asLong(offset);
                    offset = Math.max(offset, updateId + 1);
                    try {
                        if (updateNode.has("widget_query")) {
                            widgetQueryHandler.handle(updateNode.get("widget_query"));
                        } else {
                            Update update = updateMapper.treeToValue(updateNode, Update.class);
                            bot.onUpdateReceived(update);
                        }
                    } catch (Exception e) {
                        log.error("Update {} handler error: {}", updateId, e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                log.warn("getUpdates loop: {}", e.getMessage());
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
