package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class RefreshCommand implements Command {
    private final SendBotMessageService messageService;
    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        if (update.getMessage().getChatId() == 739299) {
            matchService.updateUnpredictableMatches();
            String message = "update unpredictable";
            messageService.sendMessage(update.getMessage().getChatId().toString(), message);
        }
    }
}
