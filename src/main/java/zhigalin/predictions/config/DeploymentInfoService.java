package zhigalin.predictions.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeploymentInfoService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String domain;
    private final HttpClient httpClient;
    private volatile CachedLookup cache;

    public DeploymentInfoService(@Value("${bot.webAppUrl:}") String webAppUrl) {
        this.domain = hostFromUrl(webAppUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String dnsHintForAdmin() {
        if (domain.isBlank()) {
            return null;
        }
        CachedLookup cached = cache;
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.hint();
        }
        String hint = lookup(domain);
        cache = new CachedLookup(hint, Instant.now().plus(CACHE_TTL));
        return hint;
    }

    private String lookup(String domain) {
        String ip = resolveViaPublicDns(domain);
        if (ip == null || ip.isBlank()) {
            ip = "?";
        }
        return domain + " → " + ip;
    }

    private String resolveViaPublicDns(String domain) {
        try {
            URI uri = URI.create("https://dns.google/resolve?name=" + domain + "&type=A");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return null;
            }
            JsonNode answers = MAPPER.readTree(response.body()).path("Answer");
            if (!answers.isArray() || answers.isEmpty()) {
                return null;
            }
            for (JsonNode answer : answers) {
                if (answer.path("type").asInt() == 1) {
                    String data = answer.path("data").asText("").trim();
                    if (!data.isBlank()) {
                        return data;
                    }
                }
            }
            return answers.get(0).path("data").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String hostFromUrl(String webAppUrl) {
        if (webAppUrl == null || webAppUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(webAppUrl.trim());
            return Optional.ofNullable(uri.getHost()).orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    private record CachedLookup(String hint, Instant expiresAt) {
    }
}
