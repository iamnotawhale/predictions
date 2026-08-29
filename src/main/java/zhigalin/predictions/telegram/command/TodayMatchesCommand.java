package zhigalin.predictions.telegram.command;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.MatchMessageFormatter;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TodayMatchesCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final MatchService matchService;
    private final String botChatId;

    @Override
    public void execute(Update update) {
        int tour = 0;
        String chatId = update.getMessage().getChatId().toString();
        List<Match> matches = matchService.findAllByTodayDate();
        List<Integer> predictableMatches = matchService.predictableTodayMatchesByUserTelegramIdAndWeekId(chatId);
        StringBuilder builder = new StringBuilder();
        if (!matches.isEmpty()) {
            for (Match match : matches) {
                int mark = builder.length();
                builder.append("`");
                if (match.getWeekId() != tour) {
                    builder.append(match.getWeekId()).append(" тур").append("\n");
                    tour = match.getWeekId();
                }
                if (!MatchMessageFormatter.appendMatchBody(builder, match, MatchMessageFormatter.Style.TODAY)) {
                    builder.setLength(mark);
                    continue;
                }
                builder.append("`").append("\n");
            }
            if (builder.length() == 0) {
                sendBotMessageService.sendMessage(chatId, "Сегодня матчей нет");
                return;
            }
            if (botChatId.equals(chatId)) {
                sendBotMessageService.sendMessage(chatId, builder.toString());
            } else {
                sendBotMessageService.sendMessageWithMatchesKeyboard(matches, predictableMatches, chatId, builder.toString());
            }
        } else {
            sendBotMessageService.sendMessage(chatId, "Сегодня матчей нет");
        }
    }
}
