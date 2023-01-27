package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.dto.news.NewsDto;
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
        List<NewsDto> list = newsService.findLastNews().stream().limit(10).toList();
        StringBuilder builder = new StringBuilder();
        for (NewsDto dto : list) {
            builder.append("*").append(dto.getLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm"))).append("* ")
                    .append("[").append(dto.getTitle()).append("]").append("(").append(dto.getLink()).append(") ")
                    .append("\n\n");
        }
        sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
    }
}
