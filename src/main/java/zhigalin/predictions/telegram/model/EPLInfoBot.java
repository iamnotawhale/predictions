package zhigalin.predictions.telegram.model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.telegram.command.CommandContainer;
import zhigalin.predictions.telegram.command.TeamName;
import zhigalin.predictions.telegram.service.impl.SendBotMessageServiceImpl;

import java.util.EnumSet;

import static zhigalin.predictions.telegram.command.CommandName.NO;

@Component
public class EPLInfoBot extends TelegramLongPollingBot {

    private static final String COMMAND_PREFIX = "/";

    private static final String REGEX = "\\W|\\d";
    @Value("${bot.username}")
    private String name;
    @Value("${bot.token}")
    private String token;

    private final CommandContainer commandContainer;

    @Autowired
    public EPLInfoBot(MatchService matchService, StandingService standingService, TeamService teamService,
                      HeadToHeadService headToHeadService, NewsService newsService) {
        commandContainer = new CommandContainer(new SendBotMessageServiceImpl(this), matchService, standingService,
                teamService, headToHeadService, newsService);
    }

    @Override
    public String getBotUsername() {
        return name;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText().trim();
            if (message.startsWith(COMMAND_PREFIX)) {
                String[] array = message.split(REGEX);
                String commandIdentifier = array[1].toLowerCase();
                if (array.length == 3 &&
                        EnumSet.allOf(TeamName.class).stream().anyMatch(name -> name.getTeamName().toLowerCase().contains(array[1])) &&
                        EnumSet.allOf(TeamName.class).stream().anyMatch(name -> name.getTeamName().toLowerCase().contains(array[2]))) {
                    commandContainer.retrieveHeadToHeadCommand().execute(update);
                } else {
                    if (EnumSet.allOf(TeamName.class).stream()
                            .anyMatch(name -> name.getTeamName().toLowerCase().contains(commandIdentifier))) {
                        commandContainer.retrieveTeamCommand().execute(update);
                    } else {
                        commandContainer.retrieveCommand(commandIdentifier).execute(update);
                    }
                }
            } else {
                commandContainer.retrieveCommand(NO.getCommandName()).execute(update);
            }
        } else if (update.hasCallbackQuery()) {
            String message = update.getCallbackQuery().getData();
            if (message.startsWith(COMMAND_PREFIX)) {
                String[] array = message.split(REGEX);
                String commandIdentifier = array[1].toLowerCase();
                if (EnumSet.allOf(TeamName.class).stream()
                        .anyMatch(name -> name.getTeamName().toLowerCase().contains(commandIdentifier))) {
                    commandContainer.retrieveTeamCommand().execute(update);
                } else {
                    commandContainer.retrieveCommand(commandIdentifier).execute(update);
                }
            } else {
                commandContainer.retrieveCommand(NO.getCommandName()).execute(update);
            }
        }

    }
}
