package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.model.news.News;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
public class NewsCommand implements Command {
    private final SendBotMessageService sendBotMessageService;
    private final NewsService newsService;

    @Override
    public void execute(Update update) {
        List<News> list = newsService.findLastNews().stream().limit(10).toList();
        StringBuilder builder = new StringBuilder();
        for (News news : list) {
            builder.append("*").append(news.getLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm"))).append("* ")
                    .append("[").append(news.getTitle()).append("]").append("(").append(news.getLink()).append(") ")
                    .append("\n\n");
        }
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
