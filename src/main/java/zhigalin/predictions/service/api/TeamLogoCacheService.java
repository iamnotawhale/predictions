package zhigalin.predictions.service.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.util.EspnTeamLogos;
import zhigalin.predictions.util.TeamCodeMapper;

/**
 * Same-origin team logos for the miniapp: fetch once (API-Football media, then ESPN),
 * keep in memory + disk, serve with long browser cache. Avoids flaky ESPN CDN in Telegram WebView.
 * <p>
 * Resolution order for an EPL team: memory → disk → CDN → bundled {@code static/img/teams/{id}.png}.
 * A failed fetch is remembered only for {@link #NEGATIVE_TTL_MS} so the next warm-up retries it.
 */
@Service
public class TeamLogoCacheService {
    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String AF_CDN = "https://media.api-sports.io/football/teams/%d.png";
    private static final int CONNECT_MS = 2_500;
    private static final int SOCKET_MS = 4_000;
    private static final byte[] EMPTY = new byte[0];
    /** How long a failed fetch is not retried. */
    static final long NEGATIVE_TTL_MS = 10 * 60_000L;

    private final Path cacheDir;
    private final Function<String, byte[]> fetcher;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, byte[]> memory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failedAtMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> remoteByHash = new ConcurrentHashMap<>();

    @Autowired
    public TeamLogoCacheService(
            @Value("${predictions.logos.cache-dir:}") String cacheDirProp
    ) {
        this(cacheDirProp, null, System::currentTimeMillis);
    }

    /** Test seam: custom fetcher (null = real HTTP) and clock. */
    TeamLogoCacheService(String cacheDirProp, Function<String, byte[]> fetcher, LongSupplier clock) {
        this.fetcher = fetcher != null ? fetcher : this::httpGet;
        this.clock = clock;
        String dir = cacheDirProp != null && !cacheDirProp.isBlank()
                ? cacheDirProp
                : System.getProperty("java.io.tmpdir") + "/predictions-team-logos";
        this.cacheDir = Path.of(dir);
        try {
            Files.createDirectories(this.cacheDir);
        } catch (IOException e) {
            log.warn("Team logo cache dir unavailable: {}", e.getMessage());
        }
    }

    public String pathForTeam(int teamPublicId) {
        return "/api/miniapp/logos/" + teamPublicId + ".png";
    }

    /** Proxy arbitrary remote logo (cups) through our origin. */
    public String pathForRemote(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return "";
        }
        if (remoteUrl.startsWith("/api/miniapp/logos/")) {
            return remoteUrl;
        }
        if (!(remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://"))) {
            return remoteUrl;
        }
        String hash = sha16(remoteUrl);
        remoteByHash.put(hash, remoteUrl);
        return "/api/miniapp/logos/r/" + hash + ".png";
    }

    public byte[] bytesForTeam(int teamPublicId) {
        String key = "t-" + teamPublicId;
        return lookup(key, () -> fetchTeam(teamPublicId));
    }

    public byte[] bytesForRemoteHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        String key = "r-" + hash;
        return lookup(key, () -> {
            String url = remoteByHash.get(hash);
            return url == null ? null : fetcher.apply(url);
        });
    }

    /** Memory → disk → fetch; a failure is cached only until the negative TTL expires. */
    private byte[] lookup(String key, java.util.function.Supplier<byte[]> fetch) {
        byte[] cached = memory.get(key);
        if (cached != null && cached != EMPTY) {
            return cached;
        }
        if (cached == EMPTY && !negativeExpired(key)) {
            return null;
        }
        byte[] fromDisk = readDisk(key);
        if (fromDisk != null && fromDisk.length > 0) {
            memory.put(key, fromDisk);
            failedAtMs.remove(key);
            return fromDisk;
        }
        byte[] fetched = fetch.get();
        if (fetched == null) {
            return null; // unknown remote hash: nothing to cache
        }
        store(key, fetched);
        return fetched.length == 0 ? null : fetched;
    }

    private boolean negativeExpired(String key) {
        Long at = failedAtMs.get(key);
        return at == null || clock.getAsLong() - at >= NEGATIVE_TTL_MS;
    }

    /** Pre-download logos for cup matches (remote URLs) so they are on disk before anyone opens the app. */
    public int warmRemote(Collection<String> remoteUrls) {
        int ok = 0;
        for (String url : remoteUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String path = pathForRemote(url);
            if (!path.startsWith("/api/miniapp/logos/r/")) {
                continue;
            }
            String hash = path.substring(path.lastIndexOf('/') + 1, path.length() - ".png".length());
            try {
                if (bytesForRemoteHash(hash) != null) {
                    ok++;
                }
            } catch (Exception e) {
                log.warn("Remote logo warm failed url={}: {}", url, e.getMessage());
            }
        }
        return ok;
    }

    /** Warm common EPL logos after DaoUtil is ready. */
    public void warmEplTeams() {
        int ok = 0;
        for (Team team : DaoUtil.TEAMS.values()) {
            if (team == null) {
                continue;
            }
            try {
                if (bytesForTeam(team.getPublicId()) != null) {
                    ok++;
                }
            } catch (Exception e) {
                log.warn("Logo warm failed team={}: {}", team.getPublicId(), e.getMessage());
            }
        }
        log.info("Team logo cache warm: {}/{} teams", ok, DaoUtil.TEAMS.size());
    }

    private byte[] fetchTeam(int teamPublicId) {
        byte[] af = fetcher.apply(AF_CDN.formatted(teamPublicId));
        if (af.length > 0) {
            return af;
        }
        Team team = DaoUtil.TEAMS.get(teamPublicId);
        if (team != null) {
            String espn = EspnTeamLogos.logoUrl(TeamCodeMapper.toInternalCode(team.getCode()));
            if (espn != null) {
                byte[] fromEspn = fetcher.apply(espn);
                if (fromEspn.length > 0) {
                    return fromEspn;
                }
            }
            if (team.getLogo() != null && team.getLogo().startsWith("http")) {
                byte[] fromTeam = fetcher.apply(team.getLogo());
                if (fromTeam.length > 0) {
                    return fromTeam;
                }
            }
        }
        return bundled(teamPublicId);
    }

    /** Logo shipped inside the jar ({@code static/img/teams/{id}.png}); last resort when every CDN is down. */
    private static byte[] bundled(int teamPublicId) {
        ClassPathResource res = new ClassPathResource("static/img/teams/" + teamPublicId + ".png");
        if (!res.exists()) {
            return EMPTY;
        }
        try (var in = res.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            return EMPTY;
        }
    }

    private byte[] httpGet(String url) {
        try {
            HttpResponse<byte[]> resp = Unirest.get(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .connectTimeout(CONNECT_MS)
                    .socketTimeout(SOCKET_MS)
                    .asBytes();
            if (resp.getStatus() != 200 || resp.getBody() == null || resp.getBody().length == 0) {
                return EMPTY;
            }
            return resp.getBody();
        } catch (Exception e) {
            log.warn("Logo fetch failed url={}: {}", url, e.getMessage());
            return EMPTY;
        }
    }

    private void store(String key, byte[] bytes) {
        byte[] value = bytes == null || bytes.length == 0 ? EMPTY : bytes;
        memory.put(key, value);
        if (value == EMPTY) {
            failedAtMs.put(key, clock.getAsLong());
            return;
        }
        failedAtMs.remove(key);
        try {
            Files.write(cacheDir.resolve(safeFile(key)), value);
        } catch (Exception e) {
            log.warn("Logo disk write failed key={}: {}", key, e.getMessage());
        }
    }

    private byte[] readDisk(String key) {
        try {
            Path p = cacheDir.resolve(safeFile(key));
            if (Files.isRegularFile(p)) {
                return Files.readAllBytes(p);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String safeFile(String key) {
        return key.replaceAll("[^a-zA-Z0-9._-]", "_") + ".bin";
    }

    private static String sha16(String input) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public Map<String, Integer> stats() {
        return Map.of("memory", memory.size(), "remotes", remoteByHash.size());
    }
}
