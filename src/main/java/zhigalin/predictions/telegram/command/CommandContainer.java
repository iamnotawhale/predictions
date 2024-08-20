package zhigalin.predictions.telegram.command;

import java.util.HashMap;
import java.util.Map;

import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import static zhigalin.predictions.telegram.command.CommandName.HELP;
import static zhigalin.predictions.telegram.command.CommandName.NEWS;
import static zhigalin.predictions.telegram.command.CommandName.NO;
import static zhigalin.predictions.telegram.command.CommandName.REFRESH;
import static zhigalin.predictions.telegram.command.CommandName.START;
import static zhigalin.predictions.telegram.command.CommandName.STOP;
import static zhigalin.predictions.telegram.command.CommandName.TABLE;
import static zhigalin.predictions.telegram.command.CommandName.TODAY;
import static zhigalin.predictions.telegram.command.CommandName.TOTAL;
import static zhigalin.predictions.telegram.command.CommandName.TOUR;
import static zhigalin.predictions.telegram.command.CommandName.TOUR_NUM;
import static zhigalin.predictions.telegram.command.CommandName.UPCOMING;

public class CommandContainer {
    private final Map<String, Command> commandMap;
    private final Command unknownCommand;
    private final Command teamCommand;
    private final Command headToHeadCommand;
    private final Command updateCommand;
    private final Command predictCommand;

    public CommandContainer(SendBotMessageService sendBotMessageService, MatchService matchService,
                            TeamService teamService, HeadToHeadService headToHeadService, DataInitService dataInitService,
                            PredictionService predictionService, UserService userService) {
        commandMap = new HashMap<>();
        commandMap.put(TODAY.getName(), new TodayMatchesCommand(sendBotMessageService, matchService));
        commandMap.put(START.getName(), new StartCommand(sendBotMessageService));
        commandMap.put(STOP.getName(), new StopCommand(sendBotMessageService));
        commandMap.put(HELP.getName(), new HelpCommand(sendBotMessageService));
        commandMap.put(NO.getName(), new NoCommand(sendBotMessageService));
        commandMap.put(TABLE.getName(), new TableCommand(sendBotMessageService, matchService));
        commandMap.put(TOUR.getName(), new TourCommand(sendBotMessageService));
        commandMap.put(TOUR_NUM.getName(), new TourNumCommand(sendBotMessageService, matchService));
        commandMap.put(NEWS.getName(), new NewsCommand(sendBotMessageService, dataInitService));
        commandMap.put(UPCOMING.getName(), new UpcomingCommand(sendBotMessageService, matchService));
        commandMap.put(REFRESH.getName(), new RefreshCommand(sendBotMessageService, predictionService));
        commandMap.put(TOTAL.getName(), new TotalCommand(sendBotMessageService, predictionService));
        unknownCommand = new UnknownCommand(sendBotMessageService);
        teamCommand = new TeamCommand(sendBotMessageService, teamService, matchService, headToHeadService);
        headToHeadCommand = new HeadToHeadCommand(sendBotMessageService, headToHeadService);
        updateCommand = new UpdateCommand(sendBotMessageService, matchService, predictionService);
        predictCommand = new PredictCommand(sendBotMessageService, predictionService, userService, matchService);
    }

    public Command retrieveCommand(String commandIdentifier) {
        return commandMap.getOrDefault(commandIdentifier, unknownCommand);
    }

    public Command retrieveTeamCommand() {
        return teamCommand;
    }

    public Command retrieveHeadToHeadCommand() {
        return headToHeadCommand;
    }

    public Command retrieveUpdateCommand() {
        return updateCommand;
    }

    public Command retrievePredictCommand() {
        return predictCommand;
    }
}
