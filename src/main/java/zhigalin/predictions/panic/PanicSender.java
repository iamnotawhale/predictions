package zhigalin.predictions.panic;

import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PanicSender {
    @Value("${chatId}")
    private String chatId;
    @Value("${bot.urlMessage}")
    private String url;
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    public void sendPanic(String message, Exception e) {
        StringBuilder builder = new StringBuilder();
        builder.append("Predictions exception: ")
                .append(message).append("\n");
        if (e != null) {
            builder.append(e).append("\n")
                    .append(e.getMessage());
        }
        try {
            HttpResponse<String> response = Unirest.get(url)
                    .queryString("chat_id", chatId)
                    .queryString("text", builder.toString())
                    .queryString("parse_mode", "Markdown")
                    .asString();
            if (response.getStatus() == 200) {
                serverLogger.info("Message has been send");
            } else {
                serverLogger.warn("Don't send exception notification{}", response.getBody());
            }
        } catch (UnirestException ex) {
            serverLogger.error("Sending message error: {}", ex.getMessage());
        }
    }
}
