package zhigalin.predictions.telegram.command;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TourNumCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final MatchService matchService;

    private static final String REGEX = "(?<numbers>\\D+)";

    @Override
    public void execute(Update update) {
        String chatId;
        int tourId;

        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId().toString();
            tourId = Integer.parseInt(update.getCallbackQuery().getData().split(REGEX)[1]);
        } else {
            if (update.getMessage().getText().equals("/tour")) {
                sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Нужно указать тур");
                return;
            }
            chatId = update.getMessage().getChatId().toString();
            tourId = Integer.parseInt(update.getMessage().getText().split(REGEX)[1]);
        }
        StringBuilder builder = new StringBuilder();

        List<Match> tourMatches = matchService.findAllByWeekId(tourId);
        if (!tourMatches.isEmpty()) {
            builder.append("`").append(tourId).append(" ТУР").append("`").append("\n");
            for (Match match : tourMatches) {
                builder.append("`").append(match.getHomeTeam().getCode()).append(" ");
                if (Objects.equals(match.getStatus(), "ft")) {
                    builder.append(match.getHomeTeamScore())
                            .append(" - ")
                            .append(match.getAwayTeamScore())
                            .append(" ")
                            .append(match.getAwayTeam().getCode());
                } else {
                    builder.append("- ")
                            .append(match.getAwayTeam().getCode()).append(" ")
                            .append("\uD83D\uDDD3 ")
                            .append(match.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")));
                }
                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessage(chatId, builder.toString());
        } else {
            sendBotMessageService.sendMessage(chatId, "Такого тура нет. Попробуй 1-38 туры");
        }
    }
}
