package zhigalin.predictions.service.notification;

import java.io.InputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.util.EspnTeamLogos;
import zhigalin.predictions.util.TeamCodeMapper;

/** Resolves team crests to inline data URIs so headless Chrome never waits on a CDN. */
final class CardLogos {
    private static final Logger log = LoggerFactory.getLogger("server");
    private static final int LOGO_CONNECT_TIMEOUT_MS = 2_000;
    private static final int LOGO_SOCKET_TIMEOUT_MS = 3_000;

    /** Remote logo URL → data URI (or "" after failed fetch). */
    private static final ConcurrentHashMap<String, String> LOGO_DATA_URI_CACHE = new ConcurrentHashMap<>();

    private CardLogos() {
    }

    static String resolveLogoSrc(Integer teamId, String remoteUrl) {
        // EPL: API-Football media CDN (ESPN CDN hangs mid-download from Odyssey).
        if (teamId != null) {
            String af = embedRemoteLogo("https://media.api-sports.io/football/teams/" + teamId + ".png");
            if (!af.isBlank()) {
                return af;
            }
            Team team = DaoUtil.TEAMS.get(teamId);
            if (team != null) {
                String espn = EspnTeamLogos.logoUrl(TeamCodeMapper.toInternalCode(team.getCode()));
                if (espn != null) {
                    String embedded = embedRemoteLogo(espn);
                    if (!embedded.isBlank()) {
                        return embedded;
                    }
                }
            }
            String data = classpathImageDataUri("static/img/teams/" + teamId + ".webp");
            if (data == null) {
                data = classpathImageDataUri("static/img/teams/" + teamId + ".png");
            }
            if (data != null) {
                return data;
            }
        }
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            return embedRemoteLogo(remoteUrl);
        }
        return "";
    }

    /**
     * Fetch remote logo into a data URI so Playwright never blocks on ESPN CDN (often stalls on Odyssey).
     * Failures are cached as empty to avoid repeated timeouts.
     */
    static String embedRemoteLogo(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        if (url.startsWith("data:")) {
            return url;
        }
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return url;
        }
        String cached = LOGO_DATA_URI_CACHE.get(url);
        if (cached != null) {
            return cached;
        }
        String embedded = fetchLogoDataUri(url);
        LOGO_DATA_URI_CACHE.put(url, embedded);
        return embedded;
    }

    private static String fetchLogoDataUri(String url) {
        try {
            HttpResponse<byte[]> resp = Unirest.get(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .connectTimeout(LOGO_CONNECT_TIMEOUT_MS)
                    .socketTimeout(LOGO_SOCKET_TIMEOUT_MS)
                    .asBytes();
            if (resp.getStatus() != 200 || resp.getBody() == null || resp.getBody().length == 0) {
                log.warn("Logo fetch failed status={} url={}", resp.getStatus(), url);
                return "";
            }
            String mime = contentTypeToMime(resp.getHeaders().getFirst("Content-Type"), url);
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(resp.getBody());
        } catch (Exception e) {
            log.warn("Logo fetch error url={}: {}", url, e.getMessage());
            return "";
        }
    }

    static String contentTypeToMime(String contentType, String url) {
        if (contentType != null && !contentType.isBlank()) {
            String mime = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (mime.startsWith("image/")) {
                return mime;
            }
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        if (lower.contains(".svg")) {
            return "image/svg+xml";
        }
        return "image/png";
    }

    private static String classpathImageDataUri(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            if (!res.exists()) {
                return null;
            }
            try (InputStream in = res.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                String mime = path.endsWith(".webp") ? "image/webp" : "image/png";
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
