package zhigalin.predictions.panic;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PanicSender {
    private static final long DEDUP_TTL_MS = 10 * 60 * 1000L;

    @Value("${chatId}")
    private String chatId;
    @Value("${bot.urlMessage}")
    private String url;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final ConcurrentHashMap<String, Long> recentPanics = new ConcurrentHashMap<>();

    public void sendPanic(String message, Exception e) {
        String cause = rootCauseMessage(e);
        String dedupKey = message + "|" + cause;
        long now = System.currentTimeMillis();
        prune(now);
        Long previous = recentPanics.putIfAbsent(dedupKey, now);
        if (previous != null && now - previous < DEDUP_TTL_MS) {
            serverLogger.warn("Panic suppressed (dedup {}m): {} — {}", DEDUP_TTL_MS / 60000, message, cause);
            return;
        }
        recentPanics.put(dedupKey, now);

        StringBuilder builder = new StringBuilder();
        builder.append("Predictions exception: ")
                .append(message).append("\n");
        if (e != null) {
            builder.append(e.toString());
            if (e.getMessage() != null && !e.toString().contains(e.getMessage())) {
                builder.append("\n").append(e.getMessage());
            }
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root != e) {
                builder.append("\ncause: ").append(root);
            }
        }
        try {
            HttpResponse<String> response = Unirest.get(url)
                    .queryString("chat_id", chatId)
                    .queryString("text", builder.toString())
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

    private void prune(long now) {
        Iterator<Map.Entry<String, Long>> it = recentPanics.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > DEDUP_TTL_MS) {
                it.remove();
            }
        }
    }

    private static String rootCauseMessage(Exception e) {
        if (e == null) {
            return "null";
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return msg == null || msg.isBlank() ? root.getClass().getSimpleName() : msg;
    }
}
