package zhigalin.predictions.telegram.command;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;
import zhigalin.predictions.util.DaoUtil;

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
                Team homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId());
                Team awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId());
                builder.append("`").append(homeTeam.getCode()).append(" ");
                if (Objects.equals(match.getStatus(), "ft")) {
                    builder.append(match.getHomeTeamScore())
                            .append(" - ")
                            .append(match.getAwayTeamScore())
                            .append(" ")
                            .append(awayTeam.getCode());
                } else {
                    builder.append("- ")
                            .append(awayTeam.getCode()).append(" ")
                            .append("\uD83D\uDDD3 ")
                            .append(match.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")));
                }
                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessageWithMatchesKeyboard(tourMatches, predictableMatches, chatId, builder.toString());
        } else {
            sendBotMessageService.sendMessage(chatId, "Такого тура нет. Попробуй 1-38 туры");
        }
    }
}
