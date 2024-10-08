package zhigalin.predictions.telegram.command;

import java.util.EnumSet;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class UpdateCommand implements Command {
    private final SendBotMessageService messageService;
    private final MatchService matchService;
    private final PredictionService predictionService;
    private static final String REGEX = "[^A-Za-z0-9]";

    @Override
    public void execute(Update update) {
        if (update.getMessage().getChatId() == 739299) {
            Match match = getMatchDto(update);
            if (match != null) {
                predictionService.updateByMatch(match);
                matchService.update(match);
                String message = "Матч обновлен";
                messageService.sendMessage(update.getMessage().getChatId().toString(), message);
            } else {
                messageService.sendMessage(update.getMessage().getChatId().toString(),
                        "Неизвестный матч, ошибка в названии команды");
            }
        }
    }

    private Match getMatchDto(Update update) {
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
        int homeScore = Integer.parseInt(matchToUpdate[3]);
        int awayScore = Integer.parseInt(matchToUpdate[5]);
        String result = homeScore > awayScore ? "H" : homeScore < awayScore ? "A" : "D";
        Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
        match.setHomeTeamScore(homeScore);
        match.setAwayTeamScore(awayScore);
        match.setStatus("ft");
        match.setResult(result);
        return match;
    }
}
