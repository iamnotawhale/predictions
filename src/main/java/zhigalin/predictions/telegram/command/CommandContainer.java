package zhigalin.predictions.telegram.command;

import com.google.common.collect.ImmutableMap;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.predict.PointsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import static zhigalin.predictions.telegram.command.CommandName.*;

public class CommandContainer {
    private final ImmutableMap<String, Command> commandMap;
    private final Command unknownCommand;
    private final Command teamCommand;
    private final Command headToHeadCommand;
    private final Command updateCommand;
    private final Command predictCommand;

    public CommandContainer(SendBotMessageService sendBotMessageService, MatchService matchService,
                            StandingService standingService, TeamService teamService,
                            HeadToHeadService headToHeadService, DataInitService dataInitService,
                            PredictionService predictionService, UserService userService,
                            PointsService pointsService) {
        commandMap = ImmutableMap.<String, Command>builder()
                .put(TODAY.getName(), new TodayMatchesCommand(sendBotMessageService, matchService))
                .put(START.getName(), new StartCommand(sendBotMessageService))
                .put(STOP.getName(), new StopCommand(sendBotMessageService))
                .put(HELP.getName(), new HelpCommand(sendBotMessageService))
                .put(NO.getName(), new NoCommand(sendBotMessageService))
                .put(TABLE.getName(), new TableCommand(sendBotMessageService, standingService))
                .put(TOUR.getName(), new TourCommand(sendBotMessageService))
                .put(TOUR_NUM.getName(), new TourNumCommand(sendBotMessageService, matchService))
                .put(NEWS.getName(), new NewsCommand(sendBotMessageService, dataInitService))
                .put(UPCOMING.getName(), new UpcomingCommand(sendBotMessageService, matchService))
                .put(REFRESH.getName(), new RefreshCommand(sendBotMessageService, matchService))
                .put(TOTAL.getName(), new TotalCommand(sendBotMessageService, pointsService))
                .build();
        unknownCommand = new UnknownCommand(sendBotMessageService);
        teamCommand = new TeamCommand(sendBotMessageService, teamService, matchService, headToHeadService);
        headToHeadCommand = new HeadToHeadCommand(sendBotMessageService, headToHeadService);
        updateCommand = new UpdateCommand(sendBotMessageService, matchService);
        predictCommand = new PredictCommand(sendBotMessageService, predictionService,
                userService, matchService);
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
