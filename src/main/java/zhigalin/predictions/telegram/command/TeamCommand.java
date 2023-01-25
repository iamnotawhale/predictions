package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.util.EnumSet;
import java.util.List;

@RequiredArgsConstructor
public class TeamCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final TeamService teamService;

    private final MatchService matchService;

    private final MatchMapper matchMapper;

    private final TeamMapper teamMapper;

    @Override
    public void execute(Update update) {
        Team team = getTeamByCommand(update);
        StringBuilder builder = new StringBuilder();
        if (team == null) {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Такой команды нет. Повтори запрос");
        } else {
            List<Match> lastFiveMatches = matchService.findLast5MatchesByTeamId(team.getId()).stream().map(matchMapper::toEntity).toList();
            List<String> result = matchService.getLast5MatchesResultByTeamId(team.getId());
            int i = 0;
            for (Match match : lastFiveMatches) {
                builder.append("`").append(match.getHomeTeam().getCode()).append(" ")
                        .append(match.getHomeTeamScore()).append(" - ")
                        .append(match.getAwayTeamScore()).append(" ")
                        .append(match.getAwayTeam().getCode());
                String str = result.get(i++);
                if (str.equals("W")) {
                    builder.append(" \uD83D\uDFE2");
                } else if (str.equals("L")) {
                    builder.append(" \uD83D\uDD34");
                } else {
                    builder.append(" \uD83D\uDFE1");
                }
                builder.append("`").append("\n");
            }
        }
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }

    public Team getTeamByCommand(Update update) {
        String teamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getTeamName().toLowerCase().contains(update.getMessage().getText().split("\\W|\\d")[1].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (teamCode == null) {
            return null;
        }
        return teamMapper.toEntity(teamService.findByCode(teamCode));
    }
}
