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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.DaoUtil;

class TeamLogoCacheServiceTest {

    private static final byte[] PNG = {1, 2, 3, 4};
    private static final byte[] NONE = new byte[0];
    private static final int TEAM_ID = 888_001;
    private static final String TEAM_LOGO = "https://cdn.example/team.png";

    @TempDir
    Path dir;

    private final AtomicLong now = new AtomicLong(1_000_000L);

    @BeforeEach
    void putTeam() {
        DaoUtil.TEAMS.put(TEAM_ID, Team.builder()
                .id(TEAM_ID)
                .code("ZZZ")
                .name("Test")
                .logo(TEAM_LOGO)
                .build());
    }

    @AfterEach
    void clearTeam() {
        DaoUtil.TEAMS.remove(TEAM_ID);
    }

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
        assertArrayEquals(PNG, s.bytesForTeam(TEAM_ID));
        assertArrayEquals(PNG, s.bytesForTeam(TEAM_ID));
        assertEquals(1, calls.get());
    }

    @Test
    void logoSurvivesRestartThroughDisk() {
        service(url -> PNG).bytesForTeam(TEAM_ID);
        AtomicInteger calls = new AtomicInteger();
        TeamLogoCacheService afterRestart = service(url -> {
            calls.incrementAndGet();
            return NONE;
        });
        assertArrayEquals(PNG, afterRestart.bytesForTeam(TEAM_ID));
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
        assertNull(s.bytesForTeam(TEAM_ID));
        int afterFirst = calls.get();
        assertTrue(afterFirst >= 1);

        up[0] = true;
        now.addAndGet(TeamLogoCacheService.NEGATIVE_TTL_MS - 1);
        assertNull(s.bytesForTeam(TEAM_ID), "still inside the negative window");
        assertEquals(afterFirst, calls.get());

        now.addAndGet(1);
        assertArrayEquals(PNG, s.bytesForTeam(TEAM_ID), "retried once the window passed");
    }

    @Test
    void unknownTeamWithAllSourcesDownYieldsNull() {
        TeamLogoCacheService s = service(url -> PNG);
        assertNull(s.bytesForTeam(999_999));
    }

    @Test
    void fetchDoesNotRequestApiSportsCdn() {
        AtomicInteger afHits = new AtomicInteger();
        TeamLogoCacheService s = service(url -> {
            if (url.contains("media.api-sports.io")) {
                afHits.incrementAndGet();
            }
            return url.equals(TEAM_LOGO) ? PNG : NONE;
        });
        assertArrayEquals(PNG, s.bytesForTeam(TEAM_ID));
        assertEquals(0, afHits.get());
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
