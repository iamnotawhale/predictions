package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.util.DaoUtil;

/**
 * Renders a local preview of the 17.09.2026 today-matches card (cups only that day).
 * Output: target/preview-today-2026-09-17.png
 */
class TodayMatchesImagePreviewTest {

    @Test
    void renderCupsFor17Sep2026() throws Exception {
        seedTeams();
        ImageRenderer renderer = new ImageRenderer(
                null, null, new ObjectMapper(), mock(PanicSender.class), null);
        renderer.initTeamColors();

        List<MatchRecord> list = List.of(
                new MatchRecord(
                        50, null, null,
                        LocalDateTime.of(2026, 9, 17, 21, 30),
                        401914260,
                        "eng.league_cup",
                        "MCI", "NOR",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/382.png",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/381.png"
                ),
                new MatchRecord(
                        52, null, null,
                        LocalDateTime.of(2026, 9, 17, 22, 0),
                        401915569,
                        "uefa.europa",
                        "CRY", "LECH",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/384.png",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/2990.png"
                ),
                new MatchRecord(
                        null, 35, null,
                        LocalDateTime.of(2026, 9, 17, 22, 0),
                        401915583,
                        "uefa.europa",
                        "RSO", "BOU",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/89.png",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/349.png"
                )
        );

        String path = renderer.createTodayMatchesImage(list);
        assertNotNull(path);
        Path out = Path.of("target/preview-today-2026-09-17.png");
        Files.createDirectories(out.getParent());
        Files.copy(Path.of(path), out, StandardCopyOption.REPLACE_EXISTING);
        assertTrue(Files.size(out) > 10_000, "preview too small");
        System.out.println("Preview written to " + out.toAbsolutePath());
    }

    private static void seedTeams() {
        putTeam(50, "MCI", "Manchester City");
        putTeam(52, "CRY", "Crystal Palace");
        putTeam(35, "BOU", "AFC Bournemouth");
    }

    private static void putTeam(int id, String code, String name) {
        DaoUtil.TEAMS.put(id, Team.builder().publicId(id).code(code).name(name).build());
    }
}
