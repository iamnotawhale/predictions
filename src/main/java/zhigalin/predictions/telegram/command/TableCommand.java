package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.dto.football.StandingDto;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.util.List;

@RequiredArgsConstructor
public class TableCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final StandingService standingService;

    @Override
    public void execute(Update update) {
        List<StandingDto> list = standingService.findAll();
        int i = 1;
        StringBuilder builder = new StringBuilder();
        builder.append("`").append("  ").append("КЛУБ ").append("И  ").append("В  ").append("Н  ").append("П  ")
                .append("ЗМ ").append("ПМ ").append("О  ").append("`").append("\n");
        for (StandingDto standingDto : list) {
            builder.append("`").append(i++).append(" ");
            if (i < 11) {
                builder.append(" ");
            }
            builder.append(standingDto.getTeam().getCode()).append(" ")
                    .append(standingDto.getGames()).append(" ");
            if (standingDto.getGames() < 10) {
                builder.append(" ");
            }
            builder.append(standingDto.getWon()).append(" ");
            if (standingDto.getWon() < 10) {
                builder.append(" ");
            }
            builder.append(standingDto.getDraw()).append(" ");
            if (standingDto.getDraw() < 10) {
                builder.append(" ");
            }
            builder.append(standingDto.getLost()).append(" ");
            if (standingDto.getLost() < 10) {
                builder.append(" ");
            }
            builder.append(standingDto.getGoalsScored()).append(" ");
            if (standingDto.getGoalsScored() < 10) {
                builder.append(" ");
            }
            builder.append(standingDto.getGoalsAgainst()).append(" ");
            if (standingDto.getGoalsAgainst() < 10) {
                builder.append(" ");
            }
            builder.append(standingDto.getPoints()).append(" ");
            if (standingDto.getPoints() < 10) {
                builder.append(" ");
            }
            builder.append("`").append("\n");
        }

        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
