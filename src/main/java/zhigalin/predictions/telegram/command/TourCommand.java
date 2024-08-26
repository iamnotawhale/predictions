package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TourCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private static final String MESSAGE = "Выбери тур";

    @Override
    public void execute(Update update) {
        sendBotMessageService.sendTourKeyBoard(update.getMessage().getChatId().toString(), MESSAGE, "tour");
    }
}
