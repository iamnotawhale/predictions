package zhigalin.predictions.telegram.model;

import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.telegram.command.CommandContainer;
import zhigalin.predictions.telegram.command.TeamName;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import static zhigalin.predictions.telegram.command.CommandName.NO;

@Component
public class EPLInfoBot extends TelegramLongPollingBot {

    private final String name;
    private final CommandContainer commandContainer;

    private static final String COMMAND_PREFIX = "/";
    private static final String REGEX = "[^A-Za-z]";
    private static final Pattern PATTERN = Pattern.compile("^.([a-zA-Z]{3}).([a-zA-Z]{3})$");

    public EPLInfoBot(@Value("${bot.token}") String token, @Value("${bot.username}") String name,
                      MatchService matchService, TeamService teamService, HeadToHeadService headToHeadService,
                      DataInitService dataInitService, PredictionService predictionService, UserService userService,
                      PanicSender panicSender
    ) {
        super(token);
        this.name = name;
        this.commandContainer = new CommandContainer(new SendBotMessageService(this), matchService, teamService,
                headToHeadService, dataInitService, predictionService, userService, panicSender);

        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiException e) {
            String message = "Telegram bots API init error";
            panicSender.sendPanic(message, e);
        }
    }

    @Override
    public String getBotUsername() {
        return name;
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText().trim();
            if (message.contains("update")) {
                String[] array = message.split("[^A-Za-z0-9]");
                if (array.length == 6 &&
                    EnumSet.allOf(TeamName.class).stream().anyMatch(n -> n.getName().toLowerCase().contains(array[2])) &&
                    EnumSet.allOf(TeamName.class).stream().anyMatch(n -> n.getName().toLowerCase().contains(array[4]))) {
                    commandContainer.retrieveUpdateCommand().execute(update);
                }
            }
            if (message.contains("predicts")) {
                commandContainer.retrieveMyPredictsCommand().execute(update);
            }
            if (message.contains("pred")) {
                String[] array = message.split("[^A-Za-z0-9]");
                if (array.length == 6 &&
                    EnumSet.allOf(TeamName.class).stream().anyMatch(n -> n.getName().toLowerCase().contains(array[2])) &&
                    EnumSet.allOf(TeamName.class).stream().anyMatch(n -> n.getName().toLowerCase().contains(array[4]))) {
                    commandContainer.retrievePredictCommand().execute(update);
                }
            } else if (message.startsWith(COMMAND_PREFIX)) {
                String[] array = message.split(REGEX);
                String commandIdentifier = array[1].toLowerCase();
                if (array.length == 3 &&
                    EnumSet.allOf(TeamName.class).stream().anyMatch(n -> n.getName().toLowerCase().contains(array[1])) &&
                    EnumSet.allOf(TeamName.class).stream().anyMatch(n -> n.getName().toLowerCase().contains(array[2]))) {
                    commandContainer.retrieveHeadToHeadCommand().execute(update);
                } else {
                    if (EnumSet.allOf(TeamName.class).stream()
                            .anyMatch(n -> n.getName().toLowerCase().contains(commandIdentifier))) {
                        commandContainer.retrieveTeamCommand().execute(update);
                    } else {
                        commandContainer.retrieveCommand(commandIdentifier).execute(update);
                    }
                }
            } else {
                commandContainer.retrieveCommand(NO.getName()).execute(update);
            }
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            String message = callbackQuery.getData();
            if (message.contains("predictTour")) {
                commandContainer.retrieveMyPredictsCommand().executeCallback(update.getCallbackQuery());
            } else if (message.contains("pred")) {
                String[] array = message.split("[^A-Za-z0-9]");
                String homeTeam = array[2].toLowerCase();
                String awayTeam = array[4].toLowerCase();
                if (
                        array.length == 6 &&
                        EnumSet.allOf(TeamName.class).stream().anyMatch(n -> n.getName().toLowerCase().contains(homeTeam)) &&
                        EnumSet.allOf(TeamName.class).stream().anyMatch(n -> n.getName().toLowerCase().contains(awayTeam))
                ) {
                    commandContainer.retrievePredictCommand().executeCallback(callbackQuery);
                }
            } else if (message.startsWith(COMMAND_PREFIX)) {
                Matcher matcher = PATTERN.matcher(message);
                if (
                        matcher.find() &&
                        matcher.groupCount() == 2 &&
                        Stream.of(matcher.group(1), matcher.group(2)).allMatch(team -> EnumSet.allOf(TeamName.class).stream()
                                .anyMatch(t -> t.getName().toLowerCase().contains(team.toLowerCase())))
                ) {
                    commandContainer.retrievePredictKeyBoardCommand().execute(update);
                } else if (
                        matcher.find() &&
                        matcher.groupCount() == 1 &&
                        EnumSet.allOf(TeamName.class).stream()
                                .anyMatch(t -> t.getName().toLowerCase().contains(matcher.group(1).toLowerCase()))
                ) {
                    commandContainer.retrieveTeamCommand().execute(update);
                } else {
                    String[] array = message.split(REGEX);
                    String commandIdentifier = array[1].toLowerCase();
                    commandContainer.retrieveCommand(commandIdentifier).execute(update);
                }
            }
        } else {
            commandContainer.retrieveCommand(NO.getName()).execute(update);
        }
    }
}
