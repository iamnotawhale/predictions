package zhigalin.predictions.service.notification;

import java.io.File;
import java.util.Map;

import kong.unirest.HttpResponse;
import kong.unirest.MultipartBody;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelegramClient {
    private static final Logger log = LoggerFactory.getLogger("server");

    @Value("${bot.urlMessage}")
    private String urlMessage;

    @Value("${bot.urlPhoto}")
    private String urlPhoto;

    public boolean sendMessage(String chatId, String text, String replyMarkupJson) {
        try {
            var req = Unirest.post(urlMessage)
                    .headers(Map.of("accept", "application/json", "content-type", "application/json"))
                    .queryString("chat_id", chatId)
                    .queryString("text", text);
            if (replyMarkupJson != null) {
                req.queryString("reply_markup", replyMarkupJson);
            }
            HttpResponse<String> resp = req.asString();
            return checkOk("sendMessage", resp);
        } catch (UnirestException e) {
            log.error("sendMessage error: {}", e.getMessage());
            return false;
        }
    }

    public void sendPhoto(String chatId, String caption, String filePath, String replyMarkupJson) {
        try {
            File file = new File(filePath);
            MultipartBody body = Unirest.post(urlPhoto)
                    .header("accept", "application/json")
                    .queryString("chat_id", chatId)
                    .queryString("caption", caption)
                    .field("photo", file);

            if (replyMarkupJson != null) {
                body = body.queryString("reply_markup", replyMarkupJson);
            }
            HttpResponse<String> resp = body.asString();
            checkOk("sendPhoto", resp);
        } catch (Exception e) {
            log.error("sendPhoto error: {}", e.getMessage());
        }
    }

    private boolean checkOk(String op, HttpResponse<String> resp) {
        if (resp.getStatus() == 200) {
            log.info("{}: ok", op);
            return true;
        } else {
            log.warn("{}: status={} body={}", op, resp.getStatus(), resp.getBody());
            return false;
        }
    }
}
