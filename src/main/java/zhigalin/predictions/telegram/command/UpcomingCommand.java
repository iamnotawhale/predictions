package zhigalin.predictions.telegram.command;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;
import zhigalin.predictions.util.DaoUtil;

@RequiredArgsConstructor
public class UpcomingCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        int tour = 0;
        List<Match> upcomingMatches = matchService.findAllByUpcomingDays(7);
        StringBuilder builder = new StringBuilder();
        if (!upcomingMatches.isEmpty()) {
            for (Match match : upcomingMatches) {
                builder.append("`");
                if (match.getWeekId() != tour) {
                    builder.append(match.getWeekId()).append(" тур").append("\n");
                    tour = match.getWeekId();
                }
                Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
                Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
                builder.append(homeTeam.getCode()).append(" ")
                        .append("- ").append(awayTeam.getCode());
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
