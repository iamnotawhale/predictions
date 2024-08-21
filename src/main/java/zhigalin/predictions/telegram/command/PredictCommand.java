package zhigalin.predictions.telegram.command;

import java.time.LocalDateTime;
import java.util.EnumSet;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class PredictCommand implements Command {
    private final SendBotMessageService messageService;
    private final PredictionService predictionService;
    private final UserService userService;
    private final MatchService matchService;

    private static final String REGEX = "[^A-Za-z0-9]";

    @Override
    public void execute(Update update) {
        Message message = update.getMessage();
        String chatId = message.getChatId().toString();
        messageService.sendMessage(chatId, getMessage(message.getText(), chatId));
    }

    @Override
    public void executeCallback(CallbackQuery callback) {
        String chatId = callback.getMessage().getChatId().toString();
        Integer messageId = callback.getMessage().getMessageId();
        messageService.sendMessageDeletingKeyboard(messageId, chatId, getMessage(callback.getData(), chatId));
    }

    private String getMessage(String text, String chatId) {
        try {
            String[] matchToUpdate = text.split(REGEX);
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
            if (match.getLocalDateTime().isBefore(LocalDateTime.now().minusMinutes(5L))) {
                return "Время для прогноза истекло. Матч уже начался";
            } else {
                Prediction predict = Prediction.builder()
                        .matchPublicId(match.getPublicId())
                        .userId(user.getId())
                        .homeTeamScore(homePredict)
                        .awayTeamScore(awayPredict)
                        .build();

                String action;
                if (predictionService.findByMatchIdAndUserId(predict.getMatchPublicId(), predict.getUserId()) != null) {
                    action = "обновлен";
                } else {
                    action = "сохранен";
                }
                predictionService.save(predict);

                return "Прогноз на матч " + homeTeam + " - " + awayTeam + " " + action;
            }
        } catch (Exception e) {
            return "Ошибка в сохранении прогноза";
        }

    }
}
