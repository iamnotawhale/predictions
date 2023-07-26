package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.util.EnumSet;

@RequiredArgsConstructor
public class PredictCommand implements Command {
    private final SendBotMessageService messageService;
    private final PredictionService predictionService;
    private final UserService userService;
    private final MatchService matchService;

    private static final String REGEX = "[^A-Za-z0-9]";

    @Override
    public void execute(Update update) {
        String chatId = update.getMessage().getChatId().toString();
        Prediction dto = getPredict(update, chatId);
        if (dto != null) {
            predictionService.save(dto);
            messageService.sendMessage(chatId, "Ваш прогноз на матч сохранен");
        } else {
            messageService.sendMessage(chatId, "У вас нет прав делать прогноз" +
                    " из этого чата");
        }
    }

    private Prediction getPredict(Update update, String chatId) {
        String[] matchToUpdate = update.getMessage().getText().split(REGEX);
        String homeTeam = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getName().toLowerCase().contains(matchToUpdate[2].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (homeTeam == null) {
            return null;
        }
        String awayTeam = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getName().toLowerCase().contains(matchToUpdate[4].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (awayTeam == null) {
            return null;
        }
        int homePredict = Integer.parseInt(matchToUpdate[3]);
        int awayPredict = Integer.parseInt(matchToUpdate[5]);

        User user = userService.findByTelegramId(chatId);
        if (user == null) {
            return null;
        }

        Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
        if (match == null) {
            return null;
        }

        return Prediction.builder()
                .match(match)
                .user(user)
                .homeTeamScore(homePredict)
                .awayTeamScore(awayPredict)
                .build();
    }
}
