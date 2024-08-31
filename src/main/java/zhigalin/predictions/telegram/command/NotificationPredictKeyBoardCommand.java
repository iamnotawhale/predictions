package zhigalin.predictions.telegram.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import zhigalin.predictions.telegram.service.SendBotMessageService;

public class NotificationPredictKeyBoardCommand implements Command {

    private final SendBotMessageService messageService;
    private final Pattern teamsPattern = Pattern.compile("^.([a-zA-Z]{3}).([a-zA-Z]{3}).$");

    public NotificationPredictKeyBoardCommand(SendBotMessageService messageService) {
        this.messageService = messageService;
    }

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

        messageService.sendNotificationPredictKeyBoard(
                chatId,
                "Выбери прогноз на матч " + homeTeam.toUpperCase() + "-" + awayTeam.toUpperCase(),
                homeTeam,
                awayTeam
        );
    }
}
