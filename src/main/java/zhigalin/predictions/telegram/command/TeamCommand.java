package zhigalin.predictions.telegram.command;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

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
                    match.getHomeTeamId() == team.getPublicId() ||
                    match.getAwayTeamId() == team.getPublicId())) {
                Match match = matchService.findAllByTodayDate().stream().filter(m ->
                        m.getHomeTeamId() == team.getPublicId() ||
                        m.getAwayTeamId() == team.getPublicId()).findFirst().get();
                int anotherTeamId = team.getPublicId() != match.getHomeTeamId() ? match.getHomeTeamId() : match.getAwayTeamId();
                Team anotherTeam = DaoUtil.TEAMS.get(anotherTeamId);
                builder.append("\n\n").append(anotherTeam.getCode()).append("\n");
                getLastFiveMatchesInfoByTeam(anotherTeam, builder);
                builder.append("\n\n").append("HEAD TO HEAD:").append("\n");
                List<HeadToHead> list = headToHeadService.findAllByTwoTeamsCode(team.getCode(), anotherTeam.getCode());
                for (HeadToHead h2h : list) {
                    Team homeTeam = DaoUtil.TEAMS.get(h2h.getHomeTeamId());
                    Team awayTeam = DaoUtil.TEAMS.get(h2h.getAwayTeamId());
                    builder.append("`").append(h2h.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM.yy"))).append(" ")
                            .append(h2h.getLeagueName()).append("\n")
                            .append(homeTeam.getCode()).append(" ")
                            .append(h2h.getHomeTeamScore()).append(" - ")
                            .append(h2h.getAwayTeamScore()).append(" ")
                            .append(awayTeam.getCode()).append(" ")
                            .append("`").append("\n");
                }
            }
        }
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }

    private void getLastFiveMatchesInfoByTeam(Team team, StringBuilder builder) {
        List<Match> lastFiveMatches = matchService.findLast5MatchesByTeamId(team.getPublicId());
        List<String> result = matchService.getLast5MatchesResultByTeamId(team.getPublicId());
        int i = 0;
        for (Match match : lastFiveMatches) {
            Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
            Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
            builder.append("`").append(homeTeam.getCode()).append(" ")
                    .append(match.getHomeTeamScore()).append(" - ")
                    .append(match.getAwayTeamScore()).append(" ")
                    .append(awayTeam.getCode());
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
