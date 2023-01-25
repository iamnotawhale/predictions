package zhigalin.predictions.telegram.command;

import com.google.common.collect.ImmutableMap;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import static zhigalin.predictions.telegram.command.CommandName.*;

public class CommandContainer {

    private final ImmutableMap<String, Command> commandMap;
    private final Command unknownCommand;
    private final Command teamCommand;
    private final Command headToHeadCommand;

    public CommandContainer(SendBotMessageService sendBotMessageService, MatchService matchService,
                            StandingService standingService, TeamService teamService,
                            HeadToHeadService headToHeadService, NewsService newsService) {
        commandMap = ImmutableMap.<String, Command>builder()
                .put(TODAY.getCommandName(), new TodayMatchesCommand(sendBotMessageService, matchService))
                .put(START.getCommandName(), new StartCommand(sendBotMessageService))
                .put(STOP.getCommandName(), new StopCommand(sendBotMessageService))
                .put(HELP.getCommandName(), new HelpCommand(sendBotMessageService))
                .put(NO.getCommandName(), new NoCommand(sendBotMessageService))
                .put(TABLE.getCommandName(), new TableCommand(sendBotMessageService, standingService))
                .put(TOUR.getCommandName(), new TourCommand(sendBotMessageService))
                .put(TOUR_NUM.getCommandName(), new TourNumCommand(sendBotMessageService, matchService))
                .put(NEWS.getCommandName(), new NewsCommand(sendBotMessageService, newsService))
                .put(UPCOMING.getCommandName(), new UpcomingCommand(sendBotMessageService, matchService))
                .build();

        unknownCommand = new UnknownCommand(sendBotMessageService);

        teamCommand = new TeamCommand(sendBotMessageService, teamService, matchService);

        headToHeadCommand = new HeadToHeadCommand(sendBotMessageService, headToHeadService);
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
}
