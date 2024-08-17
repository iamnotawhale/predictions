package zhigalin.predictions.telegram.command;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.predict.PointsService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TotalCommand implements Command {
    private final SendBotMessageService sendBotMessageService;
    private final PointsService pointsService;

    @Override
    public void execute(Update update) {
        StringBuilder builder = new StringBuilder();
        Map<User, Integer> usersPoints = pointsService.getAll();
        builder.append("`").append("Текущие очки: ").append("\n");
        for (Map.Entry<User, Integer> userPoint : usersPoints.entrySet()) {
            builder.append(userPoint.getKey().getLogin().toUpperCase(), 0, 3).append(" ")
                    .append(userPoint.getValue()).append(" pts").append("\n");
        }
        builder.append("`");
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
