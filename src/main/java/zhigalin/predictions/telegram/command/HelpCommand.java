package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import static zhigalin.predictions.telegram.command.CommandName.HELP;
import static zhigalin.predictions.telegram.command.CommandName.NEWS;
import static zhigalin.predictions.telegram.command.CommandName.START;
import static zhigalin.predictions.telegram.command.CommandName.STOP;
import static zhigalin.predictions.telegram.command.CommandName.TABLE;
import static zhigalin.predictions.telegram.command.CommandName.TODAY;
import static zhigalin.predictions.telegram.command.CommandName.TOUR;
import static zhigalin.predictions.telegram.command.CommandName.UPCOMING;

@RequiredArgsConstructor
public class HelpCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    public static final String HELP_MESSAGE = String.format("✨Доступные команды✨\n\n" +
                                                            "/%s - начать работу со мной\n" +
                                                            "/%s - приостановить работу со мной\n" +
                                                            "/%s - получить помощь в работе со мной\n" +
                                                            "/%s - получить информацию по сегодняшним матчам\n" +
                                                            "/%s - посмотреть актуальную турнирную таблицу\n" +
                                                            "/%s - рузультаты туров\n" +
                                                            "/%s - новости АПЛ\n" +
                                                            "/%s - матчи в ближайшие 7 дней\n",
            START.getName(), STOP.getName(), HELP.getName(), TODAY.getName(),
            TABLE.getName(), TOUR.getName(), NEWS.getName(), UPCOMING.getName());

    @Override
    public void execute(Update update) {
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), HELP_MESSAGE);
    }
}
