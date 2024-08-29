package zhigalin.predictions.telegram.command;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TourCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final PredictionService predictionService;

    private static final String MESSAGE = "Выбери тур";

    @Override
    public void execute(Update update) {
        String chatId = update.getMessage().getChatId().toString();
        List<Integer> weeksIds = predictionService.getPredictableWeeksByUserTelegramId(chatId);
        sendBotMessageService.sendTourKeyBoard(chatId, weeksIds, MESSAGE, "tour");
    }

    @Override
    public void executeCallback(CallbackQuery callbackQuery) {
        String chatId = callbackQuery.getMessage().getChatId().toString();
        List<Integer> weeksIds = predictionService.getPredictableWeeksByUserTelegramId(chatId);
        sendBotMessageService.sendTourKeyBoard(chatId, weeksIds, MESSAGE, "tour");
    }
}
