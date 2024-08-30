package zhigalin.predictions.telegram.command;

import java.io.IOException;
import java.text.ParseException;

import com.rometools.rome.io.FeedException;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.telegram.service.SendBotMessageService;

public class MenuCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    public MenuCommand(SendBotMessageService sendBotMessageService) {
        this.sendBotMessageService = sendBotMessageService;
    }

    @Override
    public void execute(Update update) throws FeedException, IOException, ParseException {
        String chatId = update.getMessage().getChatId().toString();
        String message = "Главное меню";
        sendBotMessageService.sendMainMenuMessage(chatId, message);
    }

    @Override
    public void executeCallback(CallbackQuery callbackQuery) {
        String chatId = callbackQuery.getMessage().getChatId().toString();
        String message = "Главное меню";
        sendBotMessageService.sendMainMenuMessage(chatId, message);
    }
}
