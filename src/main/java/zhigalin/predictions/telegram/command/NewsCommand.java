package zhigalin.predictions.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.converter.news.NewsMapper;
import zhigalin.predictions.model.news.News;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class NewsCommand implements Command{
    private final SendBotMessageService sendBotMessageService;
    private final NewsService newsService;
    private final NewsMapper newsMapper;

    public NewsCommand(SendBotMessageService sendBotMessageService, NewsService newsService, NewsMapper newsMapper) {
        this.sendBotMessageService = sendBotMessageService;
        this.newsService = newsService;
        this.newsMapper = newsMapper;
    }


    @Override
    public void execute(Update update) {
        List<News> list = newsService.getAllLast().stream().limit(10).map(newsMapper::toEntity).toList();
        StringBuilder builder = new StringBuilder();
        for (News news : list) {
            builder.append("*").append(news.getDateTime().format(DateTimeFormatter.ofPattern("HH:mm"))).append("* ")
                    .append("[").append(news.getTitle()).append("]").append("(").append(news.getLink()).append(") ")
                    .append("\n\n");
        }
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
