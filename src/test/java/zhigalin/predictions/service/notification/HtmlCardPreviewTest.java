package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.util.DaoUtil;

/**
 * Writes HTML-card previews to target/preview-cards/ for visual review.
 * Does not push or deploy.
 */
class HtmlCardPreviewTest {

    private HtmlImageRenderer renderer;

    @BeforeEach
    void setUp() {
        seedTeams();
        OddsService odds = mock(OddsService.class);
        when(odds.getOdd(anyInt())).thenReturn(new OddsService.Odd(2.10, 3.50, 3.40, 2.5, null, null));
        MatchService matches = mock(MatchService.class);
        when(matches.findLast5MatchesByTeamId(anyInt())).thenAnswer(inv -> {
            int id = inv.getArgument(0);
            if (id == 47) {
                return List.of(
                        finished(47, 42, 2, 2),
                        finished(40, 47, 1, 1),
                        finished(47, 50, 1, 1),
                        finished(47, 33, 3, 0),
                        finished(66, 47, 1, 2)
                );
            }
            if (id == 66) {
                return List.of(
                        finished(66, 45, 2, 2),
                        finished(42, 66, 1, 2),
                        finished(66, 51, 4, 3),
                        finished(66, 35, 3, 2),
                        finished(33, 66, 0, 1)
                );
            }
            return List.of();
        });
        HeadToHeadService h2h = mock(HeadToHeadService.class);
        when(h2h.findAllByTwoTeamsCode(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    String a = inv.getArgument(0);
                    String b = inv.getArgument(1);
                    if (("TOT".equals(a) && "AST".equals(b)) || ("AST".equals(a) && "TOT".equals(b))) {
                        return List.of(
                                h2hRow(47, 66, 2, 0),
                                h2hRow(66, 47, 2, 2),
                                h2hRow(47, 66, 0, 0),
                                h2hRow(66, 47, 1, 0),
                                h2hRow(47, 66, 3, 1),
                                h2hRow(66, 47, 0, 2),
                                h2hRow(47, 66, 1, 1)
                        );
                    }
                    return List.of();
                });
        renderer = new HtmlImageRenderer(mock(PanicSender.class), matches, h2h, odds);
    }

    @AfterEach
    void tearDown() {
        renderer.shutdown();
    }

    @Test
    void generatePreviewCards() throws Exception {
        Path outDir = Path.of("target/preview-cards");
        Files.createDirectories(outDir);

        String today = renderer.createTodayMatchesImage(List.of(
                cup(50, null, "eng.league_cup", "MCI", "NOR",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/382.png",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/381.png",
                        LocalDateTime.of(2026, 9, 17, 21, 30), 401914260),
                cup(52, null, "uefa.europa", "CRY", "LECH",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/384.png",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/2990.png",
                        LocalDateTime.of(2026, 9, 17, 22, 0), 401915569),
                cup(null, 35, "uefa.europa", "RSO", "BOU",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/89.png",
                        "https://a.espncdn.com/i/teamlogos/soccer/500/349.png",
                        LocalDateTime.of(2026, 9, 17, 22, 0), 401915583)
        ));
        copy(today, outDir.resolve("today-2026-09-17.png"));

        String reminder = renderer.createReminderImage(1557416, 47, 66, "14:30");
        copy(reminder, outDir.resolve("reminder-epl.png"));
        copy(reminder, outDir.resolve("reminder-tot-ast-today.png"));

        List<Result> results = List.of(
                new Result("nik", "2:1", 3),
                new Result("max", "1:1", 1),
                new Result("alex", "2:0", 0),
                new Result("ivan", "1:2", 0)
        );

        copy(renderer.createEplResultImage(1, 50, 33, "2:1", results, "1:1"),
                outDir.resolve("result-epl.png"));
        copy(renderer.createResultImage("uefa.champions", "UCL · FULL TIME",
                        50, null, "MCI", "RMA",
                        null, "https://a.espncdn.com/i/teamlogos/soccer/500/86.png",
                        "3:1", null, results),
                outDir.resolve("result-ucl.png"));
        copy(renderer.createResultImage("uefa.europa", "UEL · FULL TIME",
                        52, null, "CRY", "LECH",
                        null, "https://a.espncdn.com/i/teamlogos/soccer/500/2990.png",
                        "2:0", null, results),
                outDir.resolve("result-uel.png"));
        copy(renderer.createResultImage("eng.league_cup", "CARABAO · FULL TIME",
                        50, null, "MCI", "NOR",
                        null, "https://a.espncdn.com/i/teamlogos/soccer/500/381.png",
                        "1:0", null, results),
                outDir.resolve("result-carabao.png"));

        java.util.LinkedHashMap<String, Integer> weekly = new java.util.LinkedHashMap<>();
        weekly.put("nikita", 12);
        weekly.put("maxim", 10);
        weekly.put("alex", 8);
        weekly.put("ivan", 5);
        copy(renderer.createWeeklyImage(5, weekly), outDir.resolve("weekly-results.png"));

        Match sample = Match.builder()
                .publicId(1)
                .homeTeamId(50)
                .awayTeamId(33)
                .weekId(5)
                .localDateTime(LocalDateTime.of(2026, 9, 20, 17, 30))
                .build();
        copy(renderer.createYourPredictImage(sample, "2:1"), outDir.resolve("your-predict.png"));

        assertTrue(Files.size(outDir.resolve("reminder-tot-ast-today.png")) > 20_000);
        System.out.println("Previews in " + outDir.toAbsolutePath());
    }

    private static void copy(String path, Path dest) throws Exception {
        assertNotNull(path, "render failed for " + dest.getFileName());
        Files.copy(Path.of(path), dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private static MatchRecord cup(
            Integer homeId, Integer awayId, String competition,
            String homeCode, String awayCode,
            String homeLogo, String awayLogo,
            LocalDateTime kickoff, int publicId
    ) {
        return new MatchRecord(
                homeId, awayId, null, kickoff, publicId,
                competition, homeCode, awayCode, homeLogo, awayLogo
        );
    }

    private static void seedTeams() {
        put(50, "MCI", "Manchester City");
        put(52, "CRY", "Crystal Palace");
        put(35, "BOU", "AFC Bournemouth");
        put(33, "MUN", "Manchester United");
        put(47, "TOT", "Tottenham");
        put(66, "AST", "Aston Villa");
        put(42, "ARS", "Arsenal");
        put(40, "LIV", "Liverpool");
        put(45, "EVE", "Everton");
        put(51, "BRI", "Brighton");
    }

    private static void put(int id, String code, String name) {
        DaoUtil.TEAMS.put(id, Team.builder().publicId(id).code(code).name(name).build());
    }

    private static Match finished(int home, int away, int hs, int as) {
        return Match.builder()
                .homeTeamId(home)
                .awayTeamId(away)
                .homeTeamScore(hs)
                .awayTeamScore(as)
                .status("ft")
                .build();
    }

    private static HeadToHead h2hRow(int home, int away, int hs, int as) {
        return HeadToHead.builder()
                .homeTeamId(home)
                .awayTeamId(away)
                .homeTeamScore(hs)
                .awayTeamScore(as)
                .build();
    }
}
