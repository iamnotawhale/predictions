package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class UpcomingCommand implements Command {
    private final SendBotMessageService sendBotMessageService;
    private final MatchService matchService;
    private final MatchMapper matchMapper;

    public UpcomingCommand(SendBotMessageService sendBotMessageService, MatchService matchService, MatchMapper matchMapper) {
        this.sendBotMessageService = sendBotMessageService;
        this.matchService = matchService;
        this.matchMapper = matchMapper;
    }

    @Override
    public void execute(Update update) {
        Long tour = null;
        List<Match> upcomingMatches = matchService.getAllByUpcomingDays(7).stream().map(matchMapper::toEntity).toList();
        StringBuilder builder = new StringBuilder();
        if (!upcomingMatches.isEmpty()) {
            for (Match match : upcomingMatches) {
                builder.append("`");
                if (!match.getWeek().getId().equals(tour)) {
                    builder.append(match.getWeek().getId()).append(" тур").append("\n");
                    tour = match.getWeek().getId();
                }
                builder.append(match.getHomeTeam().getCode()).append(" ")
                        .append("- ").append(match.getAwayTeam().getCode())
                        .append(" ⏱ ").append(match.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")))
                        .append("`").append("\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        } else {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Матчей в ближайшие 7 дней нет");
        }
    }
}
