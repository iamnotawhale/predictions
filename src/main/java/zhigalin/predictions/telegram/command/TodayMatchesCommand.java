package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class TodayMatchesCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        Long tour = null;
        List<Match> list = matchService.findAllByTodayDate();
        StringBuilder builder = new StringBuilder();
        if (!list.isEmpty()) {
            for (Match match : list) {
                builder.append("`");
                if (!match.getWeek().getId().equals(tour)) {
                    builder.append(match.getWeek().getId()).append(" тур").append("\n");
                    tour = match.getWeek().getId();
                }
                builder.append(match.getHomeTeam().getCode()).append(" ");
                if (!Objects.equals(match.getStatus(), "ns") && !Objects.equals(match.getStatus(), "pst")) {
                    builder.append(match.getHomeTeamScore()).append(" - ")
                            .append(match.getAwayTeamScore()).append(" ")
                            .append(match.getAwayTeam().getCode()).append(" ")
                            .append(match.getStatus()).append(" ");
                } else if (Objects.equals(match.getStatus(), "pst")) {
                    builder.append("- ").append(match.getAwayTeam().getCode())
                            .append(" ⏰ ").append(match.getStatus());
                } else {
                    builder.append("- ").append(match.getAwayTeam().getCode())
                            .append(" ⏱ ").append(match.getLocalDateTime().toLocalTime());
                }
                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        } else {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Сегодня матчей нет");
        }

    }
}
