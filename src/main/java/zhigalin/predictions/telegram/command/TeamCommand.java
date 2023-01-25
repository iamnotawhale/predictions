package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.football.TeamDto;
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

    @Override
    public void execute(Update update) {
        TeamDto teamDto = getTeamByCommand(update);
        StringBuilder builder = new StringBuilder();
        if (teamDto == null) {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Такой команды нет. Повтори запрос");
        } else {
            List<MatchDto> lastFiveMatches = matchService.findLast5MatchesByTeamId(teamDto.getId());
            List<String> result = matchService.getLast5MatchesResultByTeamId(teamDto.getId());
            int i = 0;
            for (MatchDto dto : lastFiveMatches) {
                builder.append("`").append(dto.getHomeTeam().getCode()).append(" ")
                        .append(dto.getHomeTeamScore()).append(" - ")
                        .append(dto.getAwayTeamScore()).append(" ")
                        .append(dto.getAwayTeam().getCode());
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

    public TeamDto getTeamByCommand(Update update) {
        String teamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getTeamName().toLowerCase().contains(update.getMessage().getText().split("\\W|\\d")[1].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (teamCode == null) {
            return null;
        }
        return teamService.findByCode(teamCode);
    }
}
