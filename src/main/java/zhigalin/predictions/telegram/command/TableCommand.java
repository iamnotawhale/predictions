package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.converter.football.StandingMapper;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.util.List;

public class TableCommand implements Command {
    private final SendBotMessageService sendBotMessageService;
    private final StandingService standingService;
    private final StandingMapper standingMapper;

    public TableCommand(SendBotMessageService sendBotMessageService, StandingService standingService, StandingMapper standingMapper) {
        this.sendBotMessageService = sendBotMessageService;
        this.standingService = standingService;
        this.standingMapper = standingMapper;
    }

    @Override
    public void execute(Update update) {
        List<Standing> list = standingService.getAll().stream().map(standingMapper::toEntity).toList();
        int i = 1;
        StringBuilder builder = new StringBuilder();
        builder.append("`").append("  ").append("КЛУБ ").append("И  ").append("В  ").append("Н  ").append("П  ")
                .append("ЗМ ").append("ПМ ").append("О  ").append("`").append("\n");
        for (Standing standing : list) {
            builder.append("`").append(i++).append(".");
            if (i < 11) {
                builder.append(" ");
            }
            builder.append(standing.getTeam().getCode()).append(" ")
                    .append(standing.getGames()).append(" ");
            if (standing.getGames() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getWon()).append(" ");
            if (standing.getWon() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getDraw()).append(" ");
            if (standing.getDraw() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getLost()).append(" ");
            if (standing.getLost() < 10) {
                builder.append(" ");
            }
            builder.append(standing.getGoalsScored()).append(" ");
            if (standing.getGoalsScored() < 10) {
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
