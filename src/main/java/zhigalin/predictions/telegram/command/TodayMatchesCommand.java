package zhigalin.predictions.telegram.command;

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
public class TodayMatchesCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        int tour = 0;
        String chatId = update.getMessage().getChatId().toString();
        List<Match> matches = matchService.findAllByTodayDate();
        List<Integer> predictableMatches = matchService.predictableTodayMatchesByUserTelegramIdAndWeekId(chatId);
        StringBuilder builder = new StringBuilder();
        if (!matches.isEmpty()) {
            for (Match match : matches) {
                builder.append("`");
                if (match.getWeekId() != tour) {
                    builder.append(match.getWeekId()).append(" тур").append("\n");
                    tour = match.getWeekId();
                }
                Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
                Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
                builder.append(homeTeam.getCode()).append(" ");
                if (!Objects.equals(match.getStatus(), "ns") && !Objects.equals(match.getStatus(), "pst")) {
                    builder.append(match.getHomeTeamScore()).append(" - ")
                            .append(match.getAwayTeamScore()).append(" ")
                            .append(awayTeam.getCode()).append(" ")
                            .append(match.getStatus()).append(" ");
                } else if (Objects.equals(match.getStatus(), "pst")) {
                    builder.append("- ").append(awayTeam.getCode())
                            .append(" ⏰ ").append(match.getStatus());
                } else {
                    builder.append("- ").append(awayTeam.getCode())
                            .append(" ⏱ ").append(match.getLocalDateTime().toLocalTime());
                }
                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessageWithTodayMatchesKeyboard(matches, predictableMatches, chatId, builder.toString());
        } else {
            sendBotMessageService.sendMessage(chatId, "Сегодня матчей нет");
        }

    }
}
