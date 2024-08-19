package zhigalin.predictions.telegram.command;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;
import zhigalin.predictions.util.DaoUtil;

@RequiredArgsConstructor
public class TableCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        List<Standing> list = matchService.getStandings();
        int i = 1;
        StringBuilder builder = new StringBuilder();
        builder.append("`").append("  ").append("КЛУБ ").append("И  ").append("В  ").append("Н  ").append("П  ")
                .append("ЗМ ").append("ПМ ").append("О  ").append("`").append("\n");
        for (Standing standing : list) {
            builder.append("`").append(i++).append(" ");
            if (i < 11) {
                builder.append(" ");
            }
            Team team = DaoUtil.TEAMS.get(standing.getTeamId());
            builder.append(team.getCode()).append(" ")
                    .append(standing.getGames()).append(" ");
            if (standing.getGames() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getWon()).append(" ");
            if (standing.getWon() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getDrawn()).append(" ");
            if (standing.getDrawn() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getLost()).append(" ");
            if (standing.getLost() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getGoalsFor()).append(" ");
            if (standing.getGoalsFor() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getGoalsAgainst()).append(" ");
            if (standing.getGoalsAgainst() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getPoints()).append(" ");
            if (standing.getPoints() < 10) {
                builder.append(" ");
            }
            builder.append("`").append("\n");
        }

        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
