package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

@RequiredArgsConstructor
public class HeadToHeadCommand implements Command {
    private final SendBotMessageService sendBotMessageService;
    private final HeadToHeadService headToHeadService;

    private static final String REGEX = "[^A-Za-z]";

    @Override
    public void execute(Update update) {
        List<HeadToHead> list = getHeadToHead(update);
        StringBuilder builder = new StringBuilder();

        if (list == null) {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Эти команды еще не играли друг против друга");
        } else {
            for (HeadToHead h2h : list) {
                builder.append("`").append(h2h.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM.yy"))).append(" ")
                        .append(h2h.getLeagueName()).append("\n")
                        .append(h2h.getHomeTeam().getCode()).append(" ")
                        .append(h2h.getHomeTeamScore()).append(" - ")
                        .append(h2h.getAwayTeamScore()).append(" ")
                        .append(h2h.getAwayTeam().getCode()).append(" ")
                        .append("`").append("\n\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        }
    }

    public List<HeadToHead> getHeadToHead(Update update) {
        String firstTeamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getName().toLowerCase()
                        .contains(update.getMessage().getText().split(REGEX)[1].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (firstTeamCode == null) {
            return Collections.emptyList();
        }
        String secondTeamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getName().toLowerCase()
                        .contains(update.getMessage().getText().split(REGEX)[2].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (secondTeamCode == null) {
            return Collections.emptyList();
        }
        return headToHeadService.findAllByTwoTeamsCode(firstTeamCode, secondTeamCode);
    }
}
