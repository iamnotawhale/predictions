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

import java.time.LocalDateTime;
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
        String message = getMessage(update, chatId);
        messageService.sendMessage(chatId, message);
    }

    private String getMessage(Update update, String chatId) {
        try {
            String[] matchToUpdate = update.getMessage().getText().split(REGEX);
            String homeTeam = EnumSet.allOf(TeamName.class).stream()
                    .filter(t -> t.getName().toLowerCase().contains(matchToUpdate[2].toLowerCase()))
                    .map(Enum::name).findFirst().orElse(null);
            if (homeTeam == null) {
                return "Неизвестная домашняя команда";
            }
            String awayTeam = EnumSet.allOf(TeamName.class).stream()
                    .filter(t -> t.getName().toLowerCase().contains(matchToUpdate[4].toLowerCase()))
                    .map(Enum::name).findFirst().orElse(null);
            if (awayTeam == null) {
                return "Неизвестная гостевая команда";
            }
            int homePredict = Integer.parseInt(matchToUpdate[3]);
            int awayPredict = Integer.parseInt(matchToUpdate[5]);

            User user = userService.findByTelegramId(chatId);
            if (user == null) {
                return "Пользователь не найден";
            }

            Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
            if (match.getLocalDateTime().isBefore(LocalDateTime.now().plusMinutes(5L))) {
                return "Время для прогноза истекло. Матч уже начался";
            } else {
                Prediction predict = Prediction.builder()
                        .match(match)
                        .user(user)
                        .homeTeamScore(homePredict)
                        .awayTeamScore(awayPredict)
                        .build();

                String action;
                if (predictionService.findByMatchIdAndUserId(predict.getMatch().getId(), predict.getUser().getId()) != null) {
                    action = "обновлен";
                } else {
                    action = "сохранен";
                }
                predictionService.save(predict);

                Long tour = match.getWeek().getId();

                return "Ваш прогноз на матч " + tour + " тура " + action;
            }
        } catch (Exception e) {
            return "Ошибка в сохранении прогноза";
        }

    }
}
