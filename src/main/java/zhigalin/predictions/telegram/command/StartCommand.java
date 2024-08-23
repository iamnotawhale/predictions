package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class StartCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    public static final String START_MESSAGE = "Привет. Я eplinfobot. Я помогу тебе быть в курсе последних " +
                                               "новостей АПЛ. Я еще маленький и только учусь.";

    @Override
    public void execute(Update update) {
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), START_MESSAGE);
    }
}
