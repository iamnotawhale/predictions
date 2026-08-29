package zhigalin.predictions.telegram.command;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.MatchMessageFormatter;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class UpcomingCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        int tour = 0;
        List<Match> upcomingMatches = matchService.findAllByUpcomingDays(7);
        StringBuilder builder = new StringBuilder();
        if (!upcomingMatches.isEmpty()) {
            for (Match match : upcomingMatches) {
                int mark = builder.length();
                builder.append("`");
                if (match.getWeekId() != tour) {
                    builder.append(match.getWeekId()).append(" тур").append("\n");
                    tour = match.getWeekId();
                }
                if (!MatchMessageFormatter.appendMatchBody(builder, match, MatchMessageFormatter.Style.UPCOMING)) {
                    builder.setLength(mark);
                    continue;
                }
                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        } else {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Матчей в ближайшие 7 дней нет");
        }
    }
}
