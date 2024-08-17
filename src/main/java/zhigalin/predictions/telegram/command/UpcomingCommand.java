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
public class UpcomingCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        Long tour = null;
        List<Match> upcomingMatches = matchService.findAllByUpcomingDays(7);
        StringBuilder builder = new StringBuilder();
        if (!upcomingMatches.isEmpty()) {
            for (Match match : upcomingMatches) {
                builder.append("`");
                if (!match.getWeek().getId().equals(tour)) {
                    builder.append(match.getWeek().getId()).append(" тур").append("\n");
                    tour = match.getWeek().getId();
                }
                builder.append(match.getHomeTeam().getCode()).append(" ")
                        .append("- ").append(match.getAwayTeam().getCode());
                if (Objects.equals(match.getStatus(), "pst")) {
                    builder.append(" ⏰ ").append(match.getStatus());
                } else {
                    builder.append(" ⏱ ").append(match.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")));
                }

                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        } else {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Матчей в ближайшие 7 дней нет");
        }
    }
}
