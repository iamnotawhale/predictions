package zhigalin.predictions.telegram.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class PredictKeyboardCommand implements Command {

    private final SendBotMessageService messageService;
    private final PredictionService predictionService;
    private final Pattern teamsPattern = Pattern.compile("^.([a-zA-Z]{3}).([a-zA-Z]{3})$");

    @Override
    public void executeCallback(CallbackQuery callbackQuery) {
        String chatId = callbackQuery.getMessage().getChatId().toString();
        String data = callbackQuery.getData();
        String homeTeam;
        String awayTeam;
        Matcher teamsMatcher = teamsPattern.matcher(data);
        if (teamsMatcher.find()) {
            homeTeam = teamsMatcher.group(1).toLowerCase();
            awayTeam = teamsMatcher.group(2).toLowerCase();
        } else {
            return;
        }

        Prediction prediction = predictionService.getByUserTelegramIdAndTeams(chatId, homeTeam.toUpperCase(), awayTeam.toUpperCase());
        messageService.sendPredictKeyBoard(
                chatId,
                "Выбери прогноз на матч " + homeTeam.toUpperCase() + "-" + awayTeam.toUpperCase(),
                homeTeam,
                awayTeam,
                prediction
        );
    }
}
