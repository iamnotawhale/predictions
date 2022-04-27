package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.util.List;

public class TodayMatchesCommand implements Command{

    private final SendBotMessageService sendBotMessageService;

    private final MatchService matchService;

    private final MatchMapper matchMapper;

    public TodayMatchesCommand(SendBotMessageService sendBotMessageService, MatchService matchService, MatchMapper matchMapper) {
        this.sendBotMessageService = sendBotMessageService;
        this.matchService = matchService;
        this.matchMapper = matchMapper;
    }

    @Override
    public void execute(Update update) {
        Long tour = null;
        List<Match> list = matchService.getAllByTodayDate().stream().map(matchMapper::toEntity).toList();
        StringBuilder builder = new StringBuilder();
        if (!list.isEmpty()) {
            for (Match match : list) {
                if (!match.getWeek().getId().equals(tour)) {
                    builder.append(match.getWeek().getId()).append(" тур").append("\n");
                    tour = match.getWeek().getId();
                }
                if (!match.getStatus().equals("-")) {
                    builder.append(match.getHomeTeam().getTeamName()).append(" ")
                            .append(match.getHomeTeamScore()).append(" ")
                            .append(match.getStatus()).append(" ").append(match.getAwayTeamScore())
                            .append(" ").append(match.getAwayTeam().getTeamName());
                } else {
                    builder.append(match.getHomeTeam().getTeamName()).append(" ")
                            .append(match.getMatchTime()).append(" ")
                            .append(match.getAwayTeam().getTeamName());
                }
                builder.append("\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        } else {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Сегодня матчей нет");
        }

    }
}
