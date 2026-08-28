package zhigalin.predictions.config;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeploymentInfoService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final String domain;
    private volatile CachedLookup cache;

    public DeploymentInfoService(@Value("${bot.webAppUrl:}") String webAppUrl) {
        this.domain = hostFromUrl(webAppUrl);
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

    private static String lookup(String domain) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(domain);
            if (addresses.length == 0) {
                return domain + " → ?";
            }
            return domain + " → " + addresses[0].getHostAddress();
        } catch (Exception e) {
            return domain + " → ?";
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
