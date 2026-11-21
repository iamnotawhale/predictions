package zhigalin.predictions.service.notification;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    static final long NEGATIVE_TTL_MS = 10 * 60_000L;

    private static final ConcurrentHashMap<String, CacheEntry> LOGO_DATA_URI_CACHE = new ConcurrentHashMap<>();

    private CardLogos() {
    }

    static String resolveLogoSrc(Integer teamId, String remoteUrl) {
        return resolveLogoSrc(teamId, remoteUrl, null);
    }

    static String resolveLogoSrc(Integer teamId, String remoteUrl, String teamCodeHint) {
        String code = normalizeCode(teamCodeHint);
        if (code == null && teamId != null) {
            Team team = DaoUtil.TEAMS.get(teamId);
            if (team != null) {
                code = normalizeCode(team.getCode());
            }
        }
        if (teamId != null) {
            String data = classpathImageDataUri("static/img/teams/" + teamId + ".webp");
            if (data == null) {
                data = classpathImageDataUri("static/img/teams/" + teamId + ".png");
            }
            if (data == null && code != null) {
                data = classpathImageDataUri("static/img/teams/by-code/" + code + ".png");
            }
            if (data != null) {
                return data;
            }
            if (code != null) {
                String espn = EspnTeamLogos.logoUrl(code);
                if (espn != null) {
                    String embedded = embedRemoteLogo(espn);
                    if (!embedded.isBlank()) {
                        return embedded;
                    }
                }
            }
        } else if (code != null) {
            String data = classpathImageDataUri("static/img/teams/by-code/" + code + ".png");
            if (data != null) {
                return data;
            }
            String espn = EspnTeamLogos.logoUrl(code);
            if (espn != null) {
                String embedded = embedRemoteLogo(espn);
                if (!embedded.isBlank()) {
                    return embedded;
                }
            }
        }
        if (remoteUrl != null && !remoteUrl.isBlank() && !remoteUrl.contains("media.api-sports.io")) {
            String embedded = embedRemoteLogo(remoteUrl);
            if (!embedded.isBlank()) {
                return embedded;
            }
        }
        if (code != null) {
            log.warn("Logo missing teamId={} code={} — using monogram fallback", teamId, code);
            return monogramDataUri(code);
        }
        if (teamId != null) {
            log.warn("Logo missing teamId={} (no team code) — empty", teamId);
        }
        return "";
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return TeamCodeMapper.toInternalCode(raw.trim());
    }

    /**
     * Fetch remote logo into a data URI so Playwright never blocks on ESPN CDN (often stalls on Odyssey).
     * Failures are remembered for {@link #NEGATIVE_TTL_MS} then retried.
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
        long now = System.currentTimeMillis();
        CacheEntry cached = LOGO_DATA_URI_CACHE.get(url);
        if (cached != null) {
            if (!cached.value().isEmpty()) {
                return cached.value();
            }
            if (now - cached.storedAtMs() < NEGATIVE_TTL_MS) {
                return "";
            }
        }
        String embedded = fetchLogoDataUri(url);
        LOGO_DATA_URI_CACHE.put(url, new CacheEntry(embedded, now));
        return embedded;
    }

    static void clearCacheForTests() {
        LOGO_DATA_URI_CACHE.clear();
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
                log.warn("Logo fetch failed status={} bytes={} url={}",
                        resp.getStatus(),
                        resp.getBody() == null ? 0 : resp.getBody().length,
                        url);
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

    static String monogramDataUri(String teamCode) {
        String code = teamCode == null ? "?" : teamCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() > 4) {
            code = code.substring(0, 4);
        }
        String safe = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">\
                <rect width="128" height="128" rx="28" fill="#1a2332"/>\
                <text x="64" y="72" text-anchor="middle" font-family="DejaVu Sans,Arial,sans-serif"\
                font-size="42" font-weight="700" fill="#f4f1ea">%s</text></svg>
                """.formatted(safe).replace("\n", "");
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static String classpathImageDataUri(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            if (!res.exists()) {
                return null;
            }
            try (InputStream in = res.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length == 0) {
                    return null;
                }
                String mime = path.endsWith(".webp") ? "image/webp" : "image/png";
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private record CacheEntry(String value, long storedAtMs) {
    }
}
