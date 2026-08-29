package zhigalin.predictions.telegram.command;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.MatchMessageFormatter;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TourNumCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final MatchService matchService;

    private static final String REGEX = "(?<numbers>\\D+)";

    @Override
    public void execute(Update update) {
        String chatId;
        int tourId;

        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId().toString();
            tourId = Integer.parseInt(update.getCallbackQuery().getData().split(REGEX)[1]);
        } else {
            if (update.getMessage().getText().equals("/tour")) {
                sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Нужно указать тур");
                return;
            }
            chatId = update.getMessage().getChatId().toString();
            tourId = Integer.parseInt(update.getMessage().getText().split(REGEX)[1]);
        }
        StringBuilder builder = new StringBuilder();

        List<Match> tourMatches = matchService.findAllByWeekId(tourId);
        List<Integer> predictableMatches = matchService.predictableMatchesByUserTelegramIdAndWeekId(chatId, tourId);
        if (!tourMatches.isEmpty()) {
            builder.append("`").append(tourId).append(" ТУР").append("`").append("\n");
            for (Match match : tourMatches) {
                int mark = builder.length();
                builder.append("`");
                if (!MatchMessageFormatter.appendMatchBody(builder, match, MatchMessageFormatter.Style.TOUR)) {
                    builder.setLength(mark);
                    continue;
                }
                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessageWithMatchesKeyboard(tourMatches, predictableMatches, chatId, builder.toString());
        } else {
            sendBotMessageService.sendMessage(chatId, "Такого тура нет. Попробуй 1-38 туры");
        }
    }
}
