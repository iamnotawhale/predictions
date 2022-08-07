package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.converter.event.HeadToHeadMapper;
import zhigalin.predictions.converter.football.TeamMapper;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

public class HeadToHeadCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final TeamService teamService;

    private final TeamMapper teamMapper;

    private final HeadToHeadService headToHeadService;

    private final HeadToHeadMapper headToHeadMapper;

    public HeadToHeadCommand(SendBotMessageService sendBotMessageService, TeamService teamService,
                             TeamMapper teamMapper, HeadToHeadService headToHeadService,
                             HeadToHeadMapper headToHeadMapper) {
        this.sendBotMessageService = sendBotMessageService;
        this.teamService = teamService;
        this.teamMapper = teamMapper;
        this.headToHeadService = headToHeadService;
        this.headToHeadMapper = headToHeadMapper;
    }

    @Override
    public void execute(Update update) {
        List<HeadToHead> list = getHeadToHead(update);
        StringBuilder builder = new StringBuilder();

        if (list == null) {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Эти команды еще не играли друг против друга");
        } else {
            for (HeadToHead h2h : list) {
                builder.append("`").append(h2h.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM.YY"))).append(" ")
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
        return headToHeadService.getAllByTwoTeamsCode(firstTeamCode, secondTeamCode).stream().map(headToHeadMapper::toEntity).toList();
    }
}
