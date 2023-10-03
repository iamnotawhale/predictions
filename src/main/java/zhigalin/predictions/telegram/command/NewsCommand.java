package zhigalin.predictions.telegram.command;

import com.rometools.rome.io.FeedException;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.news.News;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.io.IOException;
import java.text.ParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
public class NewsCommand implements Command {
    private final SendBotMessageService sendBotMessageService;
    private final DataInitService dataInitService;

    @Override
    public void execute(Update update) throws FeedException, IOException, ParseException {
        List<News> list = dataInitService.newsInit().stream().limit(10).toList();
        StringBuilder builder = new StringBuilder();
        for (News news : list) {
            builder.append("*").append(news.getLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm"))).append("* ")
                    .append("[").append(news.getTitle()).append("]").append("(").append(news.getLink()).append(") ")
                    .append("\n\n");
        }
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
