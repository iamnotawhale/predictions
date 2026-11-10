package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.notification.NotificationService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

public class WidgetDemoCommand implements Command {

    private final SendBotMessageService messageService;
    private final NotificationService notificationService;
    private final long adminChatId;

    public WidgetDemoCommand(
            SendBotMessageService messageService,
            NotificationService notificationService,
            long adminChatId
    ) {
        this.messageService = messageService;
        this.notificationService = notificationService;
        this.adminChatId = adminChatId;
    }

    @Override
    public void execute(Update update) {
        Long chatId = update.getMessage().getChatId();
        if (chatId == null || chatId != adminChatId) {
            return;
        }
        Integer messageId = notificationService.sendReminderWidgetDemo(chatId.toString());
        if (messageId != null) {
            messageService.sendMessage(chatId.toString(), "Widget demo ok, id " + messageId);
        } else {
            messageService.sendMessage(chatId.toString(), "Widget demo failed");
        }
    }
}
