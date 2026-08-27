package zhigalin.predictions.telegram.model;

import java.io.IOException;
import java.text.ParseException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.rometools.rome.io.FeedException;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.notification.ImageRenderer;
import zhigalin.predictions.service.notification.NotificationService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.command.CommandContainer;
import zhigalin.predictions.telegram.command.TeamName;
import zhigalin.predictions.telegram.service.SendBotMessageService;
import zhigalin.predictions.util.DaoUtil;

import static zhigalin.predictions.telegram.command.CommandName.NO;

@Component
public class EPLInfoBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger("server");

    @Getter
    private final Map<Long, Update> usersStates = new HashMap<>();
    @Getter
    private final Map<Long, Integer> messageToDelete = new HashMap<>();

    private final String name;
    private final PanicSender panicSender;
    private final CommandContainer commandContainer;
    private final SendBotMessageService sendBotMessageService;
    private volatile boolean registered;

    private static final String COMMAND_PREFIX = "/";
    private static final String REGEX = "[^A-Za-z]";
    private static final Pattern TEAM_PATTERN = Pattern.compile("^.([a-zA-Z]{3})$");
    private static final Pattern H2H_PATTERN = Pattern.compile("^.([a-zA-Z]{3}).([a-zA-Z]{3})$");
    private static final Pattern PATTERN = Pattern.compile("^.([a-zA-Z]{3}).([a-zA-Z]{3})$");
    private static final Pattern PREDICT_PATTERN = Pattern.compile("^.([a-zA-Z]{3}).([a-zA-Z]{3}).$");

    public EPLInfoBot(@Value("${bot.token}") String token, @Value("${bot.username}") String name,
                      @Value("${bot.chatId}") String botChatId,
                      @Value("${chatId:0}") String adminChatId,
                      @Value("${bot.webAppUrl:}") String webAppUrl,
                      MatchService matchService, TeamService teamService,
                      HeadToHeadService headToHeadService, DataInitService dataInitService,
                      PredictionService predictionService, PanicSender panicSender, NotificationService notificationService,
                      ImageRenderer imageRenderer) {
        super(token);
        this.name = name;
        this.sendBotMessageService = new SendBotMessageService(this, imageRenderer, webAppUrl);
        this.panicSender = panicSender;
        long adminId = 0L;
        try {
            adminId = Long.parseLong(adminChatId.trim());
        } catch (Exception ignored) {
            // admin commands disabled until ADMIN_CHAT_ID / chatId is set
        }
        this.commandContainer = new CommandContainer(sendBotMessageService, matchService, teamService,
                headToHeadService, dataInitService, predictionService, panicSender, botChatId, notificationService, adminId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerWhenReady() {
        if (registered) {
            return;
        }
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
            registered = true;
            log.info("Telegram bot @{} polling started", name);
        } catch (TelegramApiException e) {
            panicSender.sendPanic("Telegram bots API init error", e);
            log.error("Telegram bot registration failed", e);
        }
    }

    @Override
    public String getBotUsername() {
        return name;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update == null) {
            return;
        }
        logIncoming(update);
        try {
            if (update.hasCallbackQuery()) {
                CallbackQuery callbackQuery = update.getCallbackQuery();
                Long chatId = callbackQuery.getMessage().getChatId();
                Integer messageId = callbackQuery.getMessage().getMessageId();
                String message = callbackQuery.getData();
                handleCallbackQuery(update, chatId, messageId, message);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                Long chatId = update.getMessage().getChatId();
                Integer messageId = update.getMessage().getMessageId();
                String message = update.getMessage().getText().trim();
                handleTextMessage(update, chatId, messageId, message);
            } else if (update.hasMessage()) {
                sendBotMessageService.sendMessage(
                        update.getMessage().getChatId().toString(),
                        "Я понимаю только текстовые сообщения. Используйте /help"
                );
            }
        } catch (Exception e) {
            log.error("Error handling Telegram update: {}", updateSummary(update), e);
        }
    }

    private static void logIncoming(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            log.info("Telegram message: chatId={}, text={}", update.getMessage().getChatId(), update.getMessage().getText());
        } else if (update.hasCallbackQuery()) {
            log.info("Telegram callback: chatId={}, data={}",
                    update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getData());
        }
    }

    private static String updateSummary(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            return "chatId=" + update.getMessage().getChatId() + ", text=" + update.getMessage().getText();
        }
        if (update.hasCallbackQuery()) {
            return "callback=" + update.getCallbackQuery().getData();
        }
        return String.valueOf(update.getUpdateId());
    }

    private void handleTextMessage(Update update, Long chatId, Integer messageId, String message) throws FeedException, IOException, ParseException {
        messageToDelete.clear();
        if (message.contains("menu")) {
            commandContainer.retrieveMenuCommand().execute(update);
        } else if (message.contains("update")) {
            String[] array = message.split("[^A-Za-z0-9]");
            if (isValidTeamCommand(array)) {
                commandContainer.retrieveUpdateCommand().execute(update);
            }
        } else if (message.contains("predicts")) {
            commandContainer.retrieveMyPredictsCommand().execute(update);
        } else if (message.contains("pred")) {
            String[] array = message.split("[^A-Za-z0-9]");
            if (isValidTeamCommand(array)) {
                commandContainer.retrievePredictCommand().execute(update);
            }
        } else if (message.contains("tours")) {
            commandContainer.retrieveToursCommand().execute(update);
        } else if (message.contains("tour")) {
            usersStates.put(chatId, update);
            commandContainer.retrieveTourNumCommand().execute(update);
        } else if (message.contains("total")) {
            commandContainer.retrieveTotalCommand().execute(update);
        } else if (message.contains("gen")) {
            commandContainer.retrieveGenerateCommand().execute(update);
        } else if (message.contains("todaypub")) {
            commandContainer.retrieveTodayNotifyCommand().execute(update);
        } else if (message.startsWith(COMMAND_PREFIX)) {
            Matcher teamMatcher = TEAM_PATTERN.matcher(message);
            Matcher h2hMatcher = H2H_PATTERN.matcher(message);
            if (isValidTeamName(teamMatcher)) {
                commandContainer.retrieveTeamCommand().execute(update);
            } else if (isValidTeamCommand(h2hMatcher)) {
                commandContainer.retrieveHeadToHeadCommand().execute(update);
            } else {
                String[] array = message.split(REGEX);
                String commandIdentifier = array[1].toLowerCase();
                commandContainer.retrieveCommand(commandIdentifier).execute(update);
            }
        } else {
            commandContainer.retrieveCommand(NO.getName()).execute(update);
        }
    }

    private void handleCallbackQuery(Update update, Long chatId, Integer messageId, String message) throws FeedException, IOException, ParseException {
        putMessageToDelete(chatId, messageId);
        if (message.contains("menu")) {
            commandContainer.retrieveMenuCommand().executeCallback(update.getCallbackQuery());
        } else if (message.contains("cancel")) {
            commandContainer.retrieveCancelMessageCommand().executeCallback(update.getCallbackQuery());
            Update previous = usersStates.get(chatId);
            onUpdateReceived(previous);
        } else if (message.contains("delete")) {
            commandContainer.retrievePredictCommand().executeCallback(update.getCallbackQuery());
            Update previous = usersStates.get(chatId);
            onUpdateReceived(previous);
        } else if (message.contains("predictTour")) {
            usersStates.put(chatId, update);
            commandContainer.retrieveMyPredictsCommand().executeCallback(update.getCallbackQuery());
        } else if (message.contains("predicts")) {
            commandContainer.retrieveMyPredictsCommand().executeCallback(update.getCallbackQuery());
        } else if (message.contains("notpred")) {
            String[] array = message.split("[^A-Za-z0-9]");
            if (isValidTeamCommand(array)) {
                commandContainer.retrieveNotificationPredictCommand().executeCallback(update.getCallbackQuery());
            }
        } else if (message.contains("pred")) {
            String[] array = message.split("[^A-Za-z0-9]");
            if (isValidTeamCommand(array)) {
                commandContainer.retrievePredictCommand().executeCallback(update.getCallbackQuery());
                Update previous = usersStates.get(chatId);
                onUpdateReceived(previous);
            }
        } else if (message.contains("tours")) {
            commandContainer.retrieveToursCommand().executeCallback(update.getCallbackQuery());
        } else if (message.contains("tour")) {
            usersStates.put(chatId, update);
            commandContainer.retrieveTourNumCommand().execute(update);
        } else if (message.startsWith(COMMAND_PREFIX)) {
            Matcher matcher = PATTERN.matcher(message);
            Matcher matcher_predict = PREDICT_PATTERN.matcher(message);
            if (isValidTeamCommand(matcher)) {
                commandContainer.retrievePredictKeyBoardCommand().executeCallback(update.getCallbackQuery());
            } else if (isValidTeamCommand(matcher_predict)) {
                usersStates.put(chatId, update);
                commandContainer.retrieveNotificationPredictKeyBoardCommand().executeCallback(update.getCallbackQuery());
            } else if (isValidTeamName(matcher)) {
                commandContainer.retrieveTeamCommand().execute(update);
            } else {
                String[] array = message.split(REGEX);
                String commandIdentifier = array[1].toLowerCase();
                commandContainer.retrieveCommand(commandIdentifier).execute(update);
            }
        }
    }

    private boolean isValidTeamCommand(String[] array) {
        return array.length == 6 &&
               isKnownTeamCode(array[2]) &&
               isKnownTeamCode(array[4]);
    }

    private boolean isValidTeamCommand(Matcher matcher) {
        return matcher.find() &&
               matcher.groupCount() == 2 &&
               Stream.of(matcher.group(1), matcher.group(2)).allMatch(this::isKnownTeamCode);
    }

    private boolean isValidTeamName(Matcher matcher) {
        return matcher.find() &&
               matcher.groupCount() == 1 &&
               EnumSet.allOf(TeamName.class).stream()
                       .anyMatch(t -> t.getName().toLowerCase().contains(matcher.group(1).toLowerCase()));
    }

    private boolean isKnownTeamCode(String teamCode) {
        if (teamCode == null) {
            return false;
        }
        String normalized = teamCode.toUpperCase();
        return DaoUtil.TEAMS.values().stream()
                .anyMatch(team -> normalized.equals(team.getCode()));
    }

    private void putMessageToDelete(Long chatId, Integer messageId) {
        StackTraceElement[] stackTrace = Thread.currentThread()
                .getStackTrace();
        if (!stackTrace[2].getMethodName().equals(stackTrace[4].getMethodName())) {
            messageToDelete.put(chatId, messageId);
        } else {
            messageToDelete.clear();
        }
    }
}