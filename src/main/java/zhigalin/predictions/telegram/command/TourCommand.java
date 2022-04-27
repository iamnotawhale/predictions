package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.telegram.service.SendBotMessageService;

public class TourCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    public TourCommand(SendBotMessageService sendBotMessageService) {
        this.sendBotMessageService = sendBotMessageService;
    }

    @Override
    public void execute(Update update) {
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Выберете тур");
    }
}
