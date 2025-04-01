package zhigalin.predictions.telegram.command;

import java.io.IOException;
import java.text.ParseException;
import java.util.EnumSet;

import com.rometools.rome.io.FeedException;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.NotificationService;
import zhigalin.predictions.service.event.MatchService;

public class GenerateCommand implements Command {

    private final MatchService matchService;
    private final NotificationService notificationService;
    private final PanicSender panicSender;

    private static final String REGEX = "[^A-Za-z]";

    public GenerateCommand(MatchService matchService, NotificationService notificationService, PanicSender panicSender) {
        this.matchService = matchService;
        this.notificationService = notificationService;
        this.panicSender = panicSender;
    }

    @Override
    public void execute(Update update) throws FeedException, IOException, ParseException {
        Match match = getGenerateResult(update);

        if (match != null) {
            notificationService.fullTimeMatchNotification(match);
        } else {
            panicSender.sendPanic("Can't generate match result notification", null);
        }
    }

    private Match getGenerateResult(Update update) {
        String[] message = update.getMessage().getText().split(REGEX);
        String firstTeamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getName().toLowerCase()
                        .contains(message[2].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (firstTeamCode == null) {
            return null;
        }
        String secondTeamCode = EnumSet.allOf(TeamName.class).stream()
                .filter(t -> t.getName().toLowerCase()
                        .contains(message[3].toLowerCase()))
                .map(Enum::name).findFirst().orElse(null);
        if (secondTeamCode == null) {
            return null;
        }

        return matchService.findByTeamCodes(firstTeamCode, secondTeamCode);
    }
}
