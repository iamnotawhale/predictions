package zhigalin.predictions.telegram.command;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TotalCommand implements Command {
    private final SendBotMessageService sendBotMessageService;
    private final PredictionService predictionService;
    private static final String REGEX = "[^A-Za-z0-9]";

    @Override
    public void execute(Update update) {
        String text = update.getMessage().getText();
        String[] totals = text.split(REGEX);
        StringBuilder builder = new StringBuilder();
        Map<String, Integer> usersPoints;
        if (totals.length > 3) {
            String weekId = totals[2];
            usersPoints = predictionService.getWeeklyUsersPoints(Integer.parseInt(weekId));
            builder.append("`").append("Очки за ").append(weekId).append(" тур").append("\n");
        } else {
            usersPoints = predictionService.getAllPointsByUsers();
            builder.append("`").append("Всего набранных очков").append("\n");
        }

        for (Map.Entry<String, Integer> userPoint : usersPoints.entrySet()) {
            builder.append(userPoint.getKey().toUpperCase(), 0, 3).append(" ")
                    .append(userPoint.getValue()).append(" pts").append("\n");
        }
        builder.append("`");
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
