package zhigalin.predictions.telegram.command;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.telegram.service.SendBotMessageService;
import zhigalin.predictions.util.DaoUtil;

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
                Team homeTeam = DaoUtil.TEAMS.get(h2h.getHomeTeamId());
                Team awayTeam = DaoUtil.TEAMS.get(h2h.getAwayTeamId());
                builder.append("`").append(h2h.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM.yy"))).append(" ")
                        .append(h2h.getLeagueName()).append("\n")
                        .append(homeTeam.getCode()).append(" ")
                        .append(h2h.getHomeTeamScore()).append(" - ")
                        .append(h2h.getAwayTeamScore()).append(" ")
                        .append(awayTeam.getCode()).append(" ")
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
