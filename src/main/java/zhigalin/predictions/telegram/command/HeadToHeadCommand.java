package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.dto.event.HeadToHeadDto;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

@RequiredArgsConstructor
public class HeadToHeadCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final HeadToHeadService headToHeadService;

    @Override
    public void execute(Update update) {
        List<HeadToHeadDto> list = getHeadToHead(update);
        StringBuilder builder = new StringBuilder();

        if (list == null) {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Эти команды еще не играли друг против друга");
        } else {
            for (HeadToHeadDto dto : list) {
                builder.append("`").append(dto.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM.yy"))).append(" ")
                        .append(dto.getLeagueName()).append("\n")
                        .append(dto.getHomeTeam().getCode()).append(" ")
                        .append(dto.getHomeTeamScore()).append(" - ")
                        .append(dto.getAwayTeamScore()).append(" ")
                        .append(dto.getAwayTeam().getCode()).append(" ")
                        .append("`").append("\n\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        }
    }

    public List<HeadToHeadDto> getHeadToHead(Update update) {
        String firstTeamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getTeamName().toLowerCase().contains(update.getMessage().getText().split("\\W|\\d")[1].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (firstTeamCode == null) {
            return null;
        }
        String secondTeamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getTeamName().toLowerCase().contains(update.getMessage().getText().split("\\W|\\d")[2].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (secondTeamCode == null) {
            return null;
        }
        return headToHeadService.findAllByTwoTeamsCode(firstTeamCode, secondTeamCode);
    }
}
