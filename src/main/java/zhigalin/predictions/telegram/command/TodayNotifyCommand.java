package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.notification.NotificationService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TodayNotifyCommand implements Command {

    private static final long ADMIN_CHAT_ID = 739299L;

    private final SendBotMessageService messageService;
    private final NotificationService notificationService;
    private final PanicSender panicSender;

    @Override
    public void execute(Update update) {
        if (update.getMessage().getChatId() != ADMIN_CHAT_ID) {
            return;
        }
        try {
            if (notificationService.sendTodayMatchNotification()) {
                messageService.sendMessage(
                        String.valueOf(ADMIN_CHAT_ID),
                        "Отправлено уведомление «Сегодняшние матчи» в общий чат"
                );
            } else {
                messageService.sendMessage(
                        String.valueOf(ADMIN_CHAT_ID),
                        "Сегодня матчей нет или не удалось сформировать картинку"
                );
            }
        } catch (Exception e) {
            panicSender.sendPanic("Can't send today matches notification", e);
            messageService.sendMessage(String.valueOf(ADMIN_CHAT_ID), "Ошибка отправки: " + e.getMessage());
        }
    }
}
