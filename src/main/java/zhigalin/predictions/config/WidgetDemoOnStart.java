package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import zhigalin.predictions.service.notification.NotificationService;

@Component
public class WidgetDemoOnStart implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final NotificationService notificationService;
    private final String chatId;

    public WidgetDemoOnStart(
            NotificationService notificationService,
            @Value("${notification.widget-demo-on-start:}") String chatId
    ) {
        this.notificationService = notificationService;
        this.chatId = chatId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        Integer messageId = notificationService.sendReminderWidgetDemo(chatId.trim());
        log.info("widget demo on start chatId={} messageId={}", chatId, messageId);
    }
}
