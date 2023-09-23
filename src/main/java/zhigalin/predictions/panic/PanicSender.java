package zhigalin.predictions.panic;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class PanicSender {
    @Value("${chatId}")
    private String chatId;
    @Value("${bot.urlMessage}")
    private String url;

    public void sendPanic(Exception e) {
        StringBuilder builder = new StringBuilder();
        builder.append("Predictions exception: ").append(e).append(" // ").append(e.getMessage());
        try {
            HttpResponse<String> response = Unirest.get(url)
                    .queryString("chat_id", chatId)
                    .queryString("text", builder.toString())
                    .queryString("parse_mode", "Markdown")
                    .asString();
            if (response.getStatus() == 200) {
                log.info(response.getBody());
                log.info("Message has been send");
            } else {
                log.warn("Don't send exception notification" + response.getBody());
            }
        } catch (UnirestException ex) {
            log.error("Sending message error: " + ex.getMessage());
        }
    }
}
