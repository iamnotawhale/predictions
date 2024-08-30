package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import zhigalin.predictions.telegram.service.SendBotMessageService;

public class CancelMessageCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    public CancelMessageCommand(SendBotMessageService sendBotMessageService) {
        this.sendBotMessageService = sendBotMessageService;
    }

    @Override
    public void executeCallback(CallbackQuery callbackQuery) {
        sendBotMessageService.deletePreviousMessage(callbackQuery.getMessage().getChatId().toString());
    }
}
