package zhigalin.predictions.telegram.command;

import java.time.LocalDateTime;
import java.util.EnumSet;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class PredictCommand implements Command {
    private final SendBotMessageService messageService;
    private final PredictionService predictionService;
    private final MatchService matchService;
    private final PanicSender panicSender;

    private static final String REGEX = "[^A-Za-z0-9]";

    @Override
    public void execute(Update update) {
        Message message = update.getMessage();
        String chatId = message.getChatId().toString();
        messageService.sendMessageDeletingKeyboard(chatId, getMessage(message.getText(), chatId));
    }

    @Override
    public void executeCallback(CallbackQuery callback) {
        String chatId = callback.getMessage().getChatId().toString();
        messageService.sendMessageDeletingKeyboard(chatId, getMessage(callback.getData(), chatId));
    }

    private String getMessage(String text, String chatId) {
        try {
            String[] matchToUpdate = text.split(REGEX);

            if (matchToUpdate[1].equals("delete")) {
                String homeTeam = EnumSet.allOf(TeamName.class).stream()
                        .filter(t -> t.getName().toLowerCase().contains(matchToUpdate[2].toLowerCase()))
                        .map(Enum::name).findFirst().orElse(null);
                if (homeTeam == null) {
                    return "Неизвестная домашняя команда";
                }
                String awayTeam = EnumSet.allOf(TeamName.class).stream()
                        .filter(t -> t.getName().toLowerCase().contains(matchToUpdate[3].toLowerCase()))
                        .map(Enum::name).findFirst().orElse(null);
                if (awayTeam == null) {
                    return "Неизвестная гостевая команда";
                }

                predictionService.deleteByUserTelegramIdAndTeams(chatId, homeTeam.toUpperCase(), awayTeam.toUpperCase());
                return "Прогноз " + homeTeam + "-" + awayTeam + " удален";
            }

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

            Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
            if (LocalDateTime.now().isAfter(match.getLocalDateTime().plusMinutes(5))) {
                return "Время для прогноза истекло. Матч уже начался";
            } else {

                String action;
                if (predictionService.isExist(chatId, match.getPublicId())) {
                    action = "обновлен";
                } else {
                    action = "сохранен";
                }
                predictionService.save(chatId, homeTeam, awayTeam, homePredict, awayPredict);

                return String.format("Прогноз %s %d %s %d %s", homeTeam, homePredict, awayTeam, awayPredict, action);
            }
        } catch (Exception e) {
            String message = "Ошибка в обработке прогноза: ";
            panicSender.sendPanic(message + "text " + text + " chatId " + chatId, e);
            return message;
        }

    }
}
