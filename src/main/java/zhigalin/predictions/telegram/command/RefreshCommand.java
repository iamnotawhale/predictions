package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class RefreshCommand implements Command {
    private final SendBotMessageService messageService;
    private final PredictionService predictionService;

    @Override
    public void execute(Update update) {
        if (update.getMessage().getChatId() == 739299) {
            predictionService.updateUnpredictable();
            String message = "update unpredictable";
            messageService.sendMessage(update.getMessage().getChatId().toString(), message);
        }
    }
}
