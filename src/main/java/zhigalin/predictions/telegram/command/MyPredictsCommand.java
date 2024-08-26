package zhigalin.predictions.telegram.command;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import com.rometools.rome.io.FeedException;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

public class MyPredictsCommand implements Command {

    private final SendBotMessageService messageService;
    private final PredictionService predictionService;

    private static final String MESSAGE = "Выбери тур";
    private static final String REGEX = "(?<numbers>\\D+)";

    public MyPredictsCommand(SendBotMessageService messageService, PredictionService predictionService) {
        this.messageService = messageService;
        this.predictionService = predictionService;
    }


    @Override
    public void execute(Update update) throws FeedException, IOException, ParseException {
        Message message = update.getMessage();
        String chatId = message.getChatId().toString();
        messageService.sendTourKeyBoard(chatId, MESSAGE, "predictTour");
    }

    @Override
    public void executeCallback(CallbackQuery callback) {
        String chatId = callback.getMessage().getChatId().toString();
        Integer messageId = callback.getMessage().getMessageId();
        String message = callback.getData();

        int weekId = Integer.parseInt(message.split(REGEX)[1]);

        List<MatchPrediction> matchPredictions = predictionService.getAllWeeklyPredictionsByUserTelegramId(weekId, chatId);
        if (matchPredictions.isEmpty()) {
            messageService.sendMessageDeletingKeyboard(messageId, chatId, "Прогнозов на " + weekId + " тур нет");
        } else {
            messageService.sendWeeklyPredictsByUserKeyBoard(messageId, chatId, "Прогнозы " + weekId + " тура", matchPredictions);
        }
    }
}
