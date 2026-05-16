package zhigalin.predictions.miniapp;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramWebAppAuthService {

    private final String botToken;

    public TelegramWebAppAuthService(@Value("${bot.token}") String botToken) {
        this.botToken = botToken;
    }

    public boolean isValid(String initData) {
        return parseUserId(initData) != null;
    }

    public String parseUserId(String initData) {
        if (initData == null || initData.isBlank()) {
            return null;
        }
        Map<String, String> params = parseQuery(initData);
        String hash = params.remove("hash");
        if (hash == null || hash.isBlank()) {
            return null;
        }
        String dataCheckString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        if (!hash.equals(computeHash(dataCheckString))) {
            return null;
        }
        String userJson = params.get("user");
        if (userJson == null) {
            return null;
        }
        int idStart = userJson.indexOf("\"id\":");
        if (idStart < 0) {
            return null;
        }
        int valueStart = idStart + 5;
        int valueEnd = userJson.indexOf(',', valueStart);
        if (valueEnd < 0) {
            valueEnd = userJson.indexOf('}', valueStart);
        }
        if (valueEnd < 0) {
            return null;
        }
        return userJson.substring(valueStart, valueEnd).trim();
    }

    private String computeHash(String dataCheckString) {
        try {
            Mac hmacToken = Mac.getInstance("HmacSHA256");
            hmacToken.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secretKey = hmacToken.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            Mac hmacData = Mac.getInstance("HmacSHA256");
            hmacData.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] hashBytes = hmacData.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            return "";
        }
    }

    private static Map<String, String> parseQuery(String initData) {
        Map<String, String> result = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
