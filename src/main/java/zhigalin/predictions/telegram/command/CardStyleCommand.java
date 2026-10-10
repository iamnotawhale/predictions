package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.notification.CardStyleSettings;
import zhigalin.predictions.telegram.service.SendBotMessageService;

/** Admin only: {@code /cardstyle} shows the current card style, {@code /cardstyle paper} switches it. */
public class CardStyleCommand implements Command {

    private final SendBotMessageService messageService;
    private final CardStyleSettings settings;
    private final long adminChatId;

    public CardStyleCommand(SendBotMessageService messageService, CardStyleSettings settings, long adminChatId) {
        this.messageService = messageService;
        this.settings = settings;
        this.adminChatId = adminChatId;
    }

    @Override
    public void execute(Update update) {
        Long chatId = update.getMessage().getChatId();
        if (chatId == null || chatId != adminChatId) {
            return;
        }
        String[] parts = update.getMessage().getText().trim().split("\\s+");
        String reply;
        if (parts.length < 2) {
            reply = describe("Стиль карточек: " + settings.current());
        } else if (settings.set(parts[1])) {
            reply = describe("Стиль карточек переключён: " + settings.current());
        } else {
            reply = describe("Неизвестный стиль «" + parts[1] + "».");
        }
        messageService.sendMessage(chatId.toString(), reply);
    }

    private String describe(String head) {
        return head + "\nДоступно: " + String.join(", ", settings.available())
                + "\nСменить: /cardstyle <название>";
    }
}
