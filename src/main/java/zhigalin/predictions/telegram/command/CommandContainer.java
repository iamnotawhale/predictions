package zhigalin.predictions.telegram.command;

import java.util.HashMap;
import java.util.Map;

import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.predict.PredictionService;
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
import static zhigalin.predictions.telegram.command.CommandName.UPCOMING;

public class CommandContainer {
    private final Map<String, Command> commandMap;
    private final Command unknownCommand;
    private final Command teamCommand;
    private final Command headToHeadCommand;
    private final Command updateCommand;
    private final Command predictCommand;
    private final Command predictKeyboardCommand;
    private final Command myPredictsCommand;
    private final Command toursCommand;
    private final Command tourNumCommand;
    private final Command cancelMessageCommand;
    private final Command menuCommand;

    public CommandContainer(SendBotMessageService sendBotMessageService, MatchService matchService,
                            TeamService teamService, HeadToHeadService headToHeadService, DataInitService dataInitService,
                            PredictionService predictionService, PanicSender panicSender) {
        commandMap = new HashMap<>();
        commandMap.put(TODAY.getName(), new TodayMatchesCommand(sendBotMessageService, matchService));
        commandMap.put(START.getName(), new StartCommand(sendBotMessageService));
        commandMap.put(STOP.getName(), new StopCommand(sendBotMessageService));
        commandMap.put(HELP.getName(), new HelpCommand(sendBotMessageService));
        commandMap.put(NO.getName(), new NoCommand(sendBotMessageService));
        commandMap.put(TABLE.getName(), new TableCommand(sendBotMessageService, matchService));
        commandMap.put(NEWS.getName(), new NewsCommand(sendBotMessageService, dataInitService));
        commandMap.put(UPCOMING.getName(), new UpcomingCommand(sendBotMessageService, matchService));
        commandMap.put(REFRESH.getName(), new RefreshCommand(sendBotMessageService, predictionService));
        commandMap.put(TOTAL.getName(), new TotalCommand(sendBotMessageService, predictionService));
        unknownCommand = new UnknownCommand(sendBotMessageService);
        teamCommand = new TeamCommand(sendBotMessageService, teamService, matchService, headToHeadService);
        headToHeadCommand = new HeadToHeadCommand(sendBotMessageService, headToHeadService);
        updateCommand = new UpdateCommand(sendBotMessageService, matchService, predictionService);
        predictCommand = new PredictCommand(sendBotMessageService, predictionService, matchService, panicSender);
        predictKeyboardCommand = new PredictKeyboardCommand(sendBotMessageService, predictionService);
        myPredictsCommand = new MyPredictsCommand(sendBotMessageService, predictionService);
        toursCommand = new TourCommand(sendBotMessageService, predictionService);
        tourNumCommand = new TourNumCommand(sendBotMessageService, matchService);
        cancelMessageCommand = new CancelMessageCommand(sendBotMessageService);
        menuCommand = new MenuCommand(sendBotMessageService);
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

    public Command retrievePredictKeyBoardCommand() {
        return predictKeyboardCommand;
    }

    public Command retrieveMyPredictsCommand() {
        return myPredictsCommand;
    }

    public Command retrieveToursCommand() {
        return toursCommand;
    }

    public Command retrieveTourNumCommand() {
        return tourNumCommand;
    }

    public Command retrieveCancelMessageCommand() {
        return cancelMessageCommand;
    }

    public Command retrieveMenuCommand() {
        return menuCommand;
    }
}
