package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class StartCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    public static final String START_MESSAGE = "Привет! Открой приложение, чтобы сделать прогнозы на АПЛ.";

    @Override
    public void execute(Update update) {
        sendBotMessageService.sendMainMenuMessage(update.getMessage().getChatId().toString(), START_MESSAGE);
    }
}
