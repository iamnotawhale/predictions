package zhigalin.predictions.telegram.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class PredictKeyboardCommand implements Command {
    private final SendBotMessageService messageService;
    private final Pattern teamsPattern = Pattern.compile("^.([a-zA-Z]{3}).([a-zA-Z]{3})$");

    @Override
    public void execute(Update update) {
        String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
        String data = update.getCallbackQuery().getData();
        String homeTeam;
        String awayTeam;
        Matcher teamsMatcher = teamsPattern.matcher(data);
        if (teamsMatcher.find()) {
            homeTeam = teamsMatcher.group(1).toLowerCase();
            awayTeam = teamsMatcher.group(2).toLowerCase();
        } else {
            return;
        }
        messageService.sendPredictKeyBoard(chatId, homeTeam, awayTeam);
    }
}
