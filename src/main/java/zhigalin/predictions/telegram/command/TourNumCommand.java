package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class TourNumCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        String chatId;
        long tourId;

        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId().toString();
            tourId = Long.parseLong(update.getCallbackQuery().getData().split("(?<numbers>\\D+)")[1]);
        } else {
            if (update.getMessage().getText().equals("/tour")) {
                sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Нужно указать тур");
                return;
            }
            chatId = update.getMessage().getChatId().toString();
            tourId = Long.parseLong(update.getMessage().getText().split("(?<numbers>\\D+)")[1]);
        }
        StringBuilder builder = new StringBuilder();

        List<MatchDto> tourMatches = matchService.findAllByWeekId(tourId);
        if (!tourMatches.isEmpty()) {
            builder.append("`").append(tourId).append(" ТУР").append("`").append("\n");
            for (MatchDto dto : tourMatches) {
                builder.append("`").append(dto.getHomeTeam().getCode()).append(" ");
                if (Objects.equals(dto.getStatus(), "ft")) {
                    builder.append(dto.getHomeTeamScore())
                            .append(" - ")
                            .append(dto.getAwayTeamScore())
                            .append(" ")
                            .append(dto.getAwayTeam().getCode());
                } else {
                    builder.append("- ")
                            .append(dto.getAwayTeam().getCode()).append(" ")
                            .append("\uD83D\uDDD3 ")
                            .append(dto.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")));
                }
                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessage(chatId, builder.toString());
        } else {
            sendBotMessageService.sendMessage(chatId, "Такого тура нет. Попробуй 1-38 туры");
        }
    }
}
