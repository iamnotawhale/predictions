package zhigalin.predictions.telegram.command;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

public class NotificationPredictCommand implements Command {

    private final SendBotMessageService messageService;
    private final PredictionService predictionService;
    private final MatchService matchService;
    private final PanicSender panicSender;

    private static final String REGEX = "[^A-Za-z0-9]";

    public NotificationPredictCommand(SendBotMessageService messageService, PredictionService predictionService, MatchService matchService, PanicSender panicSender) {
        this.messageService = messageService;
        this.predictionService = predictionService;
        this.matchService = matchService;
        this.panicSender = panicSender;
    }

    @Override
    public void executeCallback(CallbackQuery callback) {
        String chatId = callback.getMessage().getChatId().toString();
        String text = callback.getData();
        String message;
        Match match = null;
        int homePredict;
        int awayPredict;
        try {
            String[] matchToUpdate = text.split(REGEX);

            String homeTeam = EnumSet.allOf(TeamName.class).stream()
                    .filter(t -> t.getName().toLowerCase().contains(matchToUpdate[2].toLowerCase()))
                    .map(Enum::name).findFirst().orElse(null);
            if (homeTeam == null) {
                message = "Неизвестная домашняя команда";
                messageService.sendMessage(chatId, message);
                return;
            }
            String awayTeam = EnumSet.allOf(TeamName.class).stream()
                    .filter(t -> t.getName().toLowerCase().contains(matchToUpdate[4].toLowerCase()))
                    .map(Enum::name).findFirst().orElse(null);
            if (awayTeam == null) {
                message = "Неизвестная гостевая команда";
                messageService.sendMessage(chatId, message);
                return;
            }

            match = matchService.findByTeamCodes(homeTeam, awayTeam);
            if (LocalDateTime.now().isAfter(match.getLocalDateTime().plusMinutes(5))) {
                message = "Время для прогноза истекло. Матч уже начался";
                messageService.sendMessage(chatId, message);
                return;
            } else {

                homePredict = Integer.parseInt(matchToUpdate[3]);
                awayPredict = Integer.parseInt(matchToUpdate[5]);

                String action;
                if (predictionService.isExist(chatId, match.getPublicId())) {
                    action = "обновлен";
                } else {
                    action = "сохранен";
                }
                predictionService.save(chatId, homeTeam, awayTeam, homePredict, awayPredict);
                message = String.format("Прогноз %s %d %s %d %s", homeTeam, homePredict, awayTeam, awayPredict, action);
            }
        } catch (Exception e) {
            message = "Ошибка в обработке прогноза: ";
            panicSender.sendPanic(message + "text " + text + " chatId " + chatId, e);
            messageService.sendMessage(chatId, message);
            return;
        }

        messageService.sendMessageNotificationPicture(chatId, message, Objects.requireNonNull(match), homePredict, awayPredict);
    }
}
