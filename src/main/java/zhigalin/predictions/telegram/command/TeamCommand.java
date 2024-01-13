package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

@RequiredArgsConstructor
public class TeamCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final TeamService teamService;

    private final MatchService matchService;
    private final HeadToHeadService headToHeadService;

    private static final String REGEX = "[^A-Za-z]";

    @Override
    public void execute(Update update) {
        Team team = getTeamByCommand(update);
        StringBuilder builder = new StringBuilder();
        if (team == null) {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Такой команды нет. Повтори запрос");
        } else {
            getLastFiveMatchesInfoByTeam(team, builder);
            if (matchService.findAllByTodayDate().stream().anyMatch(match ->
                    match.getHomeTeam().getId().equals(team.getId()) ||
                    match.getAwayTeam().getId().equals(team.getId()))) {
                Match match = matchService.findAllByTodayDate().stream().filter(m ->
                        m.getHomeTeam().getId().equals(team.getId()) ||
                                m.getAwayTeam().getId().equals(team.getId())).findFirst().get();
                Long anotherTeamId = !team.getId().equals(match.getHomeTeam().getId()) ? match.getHomeTeam().getId() : match.getAwayTeam().getId();
                Team anotherTeam = teamService.findById(anotherTeamId);
                builder.append("\n\n").append(anotherTeam.getCode()).append("\n");
                getLastFiveMatchesInfoByTeam(anotherTeam, builder);
                builder.append("\n\n").append("HEAD TO HEAD:").append("\n");
                List<HeadToHead> list = headToHeadService.findAllByTwoTeamsCode(team.getCode(), anotherTeam.getCode());
                for (HeadToHead h2h : list) {
                    builder.append("`").append(h2h.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM.yy"))).append(" ")
                            .append(h2h.getLeagueName()).append("\n")
                            .append(h2h.getHomeTeam().getCode()).append(" ")
                            .append(h2h.getHomeTeamScore()).append(" - ")
                            .append(h2h.getAwayTeamScore()).append(" ")
                            .append(h2h.getAwayTeam().getCode()).append(" ")
                            .append("`").append("\n");
                }
            }
        }
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }

    private void getLastFiveMatchesInfoByTeam(Team team, StringBuilder builder) {
        List<Match> lastFiveMatches = matchService.findLast5MatchesByTeamId(team.getId());
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

    public Team getTeamByCommand(Update update) {
        String teamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getName().toLowerCase()
                        .contains(update.getMessage().getText().split(REGEX)[1].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (teamCode == null) {
            return null;
        }
        return teamService.findByCode(teamCode);
    }
}
