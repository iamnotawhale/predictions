package zhigalin.predictions.service.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamLogoCacheServiceTest {

    private static final byte[] PNG = {1, 2, 3, 4};
    private static final byte[] NONE = new byte[0];

    @TempDir
    Path dir;

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private TeamLogoCacheService service(Function<String, byte[]> fetcher) {
        return new TeamLogoCacheService(dir.toString(), fetcher, now::get);
    }

    @Test
    void teamLogoIsFetchedOnceThenServedFromMemory() {
        AtomicInteger calls = new AtomicInteger();
        TeamLogoCacheService s = service(url -> {
            calls.incrementAndGet();
            return PNG;
        });
        assertArrayEquals(PNG, s.bytesForTeam(50));
        assertArrayEquals(PNG, s.bytesForTeam(50));
        assertEquals(1, calls.get());
    }

    @Test
    void logoSurvivesRestartThroughDisk() {
        service(url -> PNG).bytesForTeam(50);
        AtomicInteger calls = new AtomicInteger();
        TeamLogoCacheService afterRestart = service(url -> {
            calls.incrementAndGet();
            return NONE;
        });
        assertArrayEquals(PNG, afterRestart.bytesForTeam(50));
        assertEquals(0, calls.get(), "disk copy must make the CDN unnecessary");
    }

    @Test
    void failedFetchIsRetriedOnlyAfterNegativeTtl() {
        AtomicInteger calls = new AtomicInteger();
        boolean[] up = {false};
        TeamLogoCacheService s = service(url -> {
            calls.incrementAndGet();
            return up[0] ? PNG : NONE;
        });
        assertNull(s.bytesForTeam(7));
        int afterFirst = calls.get();
        assertTrue(afterFirst >= 1);

        up[0] = true;
        now.addAndGet(TeamLogoCacheService.NEGATIVE_TTL_MS - 1);
        assertNull(s.bytesForTeam(7), "still inside the negative window");
        assertEquals(afterFirst, calls.get());

        now.addAndGet(1);
        assertArrayEquals(PNG, s.bytesForTeam(7), "retried once the window passed");
    }

    @Test
    void unknownTeamWithAllSourcesDownYieldsNull() {
        TeamLogoCacheService s = service(url -> url.contains("api-sports") ? NONE : PNG);
        // DaoUtil.TEAMS has no such team, so only API-Football is tried, it fails, no bundled file → null
        assertNull(s.bytesForTeam(999_999));
    }

    @Test
    void remoteLogoWarmedByUrlIsServedByHash() {
        TeamLogoCacheService s = service(url -> url.equals("https://cdn.example/a.png") ? PNG : NONE);
        String path = s.pathForRemote("https://cdn.example/a.png");
        assertTrue(path.startsWith("/api/miniapp/logos/r/") && path.endsWith(".png"));
        assertEquals(1, s.warmRemote(List.of("https://cdn.example/a.png", "", "https://cdn.example/missing.png")));
        String hash = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
        assertArrayEquals(PNG, s.bytesForRemoteHash(hash));
    }

    @Test
    void remoteHashUnknownAfterRestartButDiskCopyStillServes() {
        TeamLogoCacheService first = service(url -> PNG);
        String path = first.pathForRemote("https://cdn.example/a.png");
        String hash = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
        first.warmRemote(List.of("https://cdn.example/a.png"));

        TeamLogoCacheService restarted = service(url -> NONE);
        assertNotNull(restarted.bytesForRemoteHash(hash));
        assertNull(restarted.bytesForRemoteHash("deadbeefdeadbeef"), "unknown hash, nothing on disk");
    }

    @Test
    void nonHttpRemoteUrlsAreLeftAlone() {
        TeamLogoCacheService s = service(url -> PNG);
        assertEquals("", s.pathForRemote(null));
        assertEquals("", s.pathForRemote(" "));
        assertEquals("/img/x.png", s.pathForRemote("/img/x.png"));
        assertEquals("/api/miniapp/logos/r/abc.png", s.pathForRemote("/api/miniapp/logos/r/abc.png"));
        assertEquals(0, s.warmRemote(List.of("/img/x.png")));
    }
}
