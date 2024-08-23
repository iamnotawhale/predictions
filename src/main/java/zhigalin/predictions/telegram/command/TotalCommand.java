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

    @Override
    public void execute(Update update) {
        StringBuilder builder = new StringBuilder();
        Map<String, Integer> usersPoints = predictionService.getAllPointsByUsers();
        builder.append("`").append("Текущие очки: ").append("\n");
        for (Map.Entry<String, Integer> userPoint : usersPoints.entrySet()) {
            builder.append(userPoint.getKey().toUpperCase(), 0, 3).append(" ")
                    .append(userPoint.getValue()).append(" pts").append("\n");
        }
        builder.append("`");
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
