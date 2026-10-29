package zhigalin.predictions.service.predict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileTeamPredictionsResponse;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.recommender.FootyStatsStatsDao;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.service.api.TeamLogoCacheService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.util.DaoUtil;

@ExtendWith(MockitoExtension.class)
class UserPredictionStatsTeamTest {

    @Mock
    PredictionService predictionService;
    @Mock
    MatchService matchService;
    @Mock
    CupMatchDao cupMatchDao;
    @Mock
    UserWeekBonusMatchService bonusService;
    @Mock
    TeamLogoCacheService logos;
    @Mock
    FootyStatsStatsDao statsDao;

    private final Team ars = Team.builder().publicId(1).code("ARS").name("Arsenal").build();
    private final Team che = Team.builder().publicId(2).code("CHE").name("Chelsea").build();
    private final Team liv = Team.builder().publicId(3).code("LIV").name("Liverpool").build();

    @BeforeEach
    void teams() {
        DaoUtil.TEAMS.put(1, ars);
        DaoUtil.TEAMS.put(2, che);
        DaoUtil.TEAMS.put(3, liv);
    }

    @AfterEach
    void cleanup() {
        DaoUtil.TEAMS.remove(1);
        DaoUtil.TEAMS.remove(2);
        DaoUtil.TEAMS.remove(3);
    }

    private static MatchPrediction mp(int id, int home, int away, Integer rh, Integer ra, String status, int ph, int pa) {
        Match m = Match.builder().publicId(id).weekId(7).homeTeamId(home).awayTeamId(away)
                .homeTeamScore(rh).awayTeamScore(ra).status(status)
                .localDateTime(LocalDateTime.of(2026, 10, id, 17, 30)).build();
        Prediction p = Prediction.builder().matchPublicId(id).userId(5).homeTeamScore(ph).awayTeamScore(pa).build();
        return new MatchPrediction(m, p);
    }

    private static Match match(int id, int home, int away, Integer rh, Integer ra, String status) {
        return Match.builder().publicId(id).weekId(7).homeTeamId(home).awayTeamId(away)
                .homeTeamScore(rh).awayTeamScore(ra).status(status)
                .localDateTime(LocalDateTime.of(2026, 10, id, 17, 30)).build();
    }

    @Test
    void collectsAllTeamMatchesWithAiAndOthers() {
        User user = User.builder().id(5).login("nik").build();
        when(predictionService.findAllByUserId(5)).thenReturn(List.of(
                mp(3, 1, 2, null, null, "ns", 2, 0),
                mp(2, 2, 1, 1, 1, "ft", 1, 1),
                mp(1, 1, 3, 2, 0, "ft", 2, 1),
                mp(4, 2, 3, 0, 0, "ft", 1, 0)
        ));
        when(matchService.findAllByTeamPublicId(1)).thenReturn(List.of(
                match(3, 1, 2, null, null, "ns"),
                match(2, 2, 1, 1, 1, "ft"),
                match(1, 1, 3, 2, 0, "ft")
        ));
        when(bonusService.isWeekBonusMatch(anyInt(), anyInt(), anyInt())).thenReturn(false);
        when(statsDao.findKickoffScoresByMatchIds(anyCollection())).thenReturn(Map.of(1, new int[]{1, 0}));
        when(predictionService.othersPointsByMatchIds(anyCollection(), eq(5)))
                .thenReturn(Map.of(1, new int[]{3, 2}, 2, new int[]{-2, 2}));

        UserPredictionStatsService service =
                new UserPredictionStatsService(predictionService, matchService, cupMatchDao, bonusService, logos, statsDao);
        ProfileTeamPredictionsResponse r = service.teamPredictions(user, ars);

        assertEquals(3, r.items().size());
        assertEquals(2, r.matches());
        assertEquals(2.5, r.avgPoints(), 1e-9);
        assertEquals(1, r.aiMatches());
        assertEquals(1.0, r.aiAvgPoints(), 1e-9);
        assertEquals(0.3, r.othersAvgPoints(), 1e-9);

        var upcoming = r.items().get(0);
        assertFalse(upcoming.finished());
        assertNull(upcoming.points());
        assertNull(upcoming.homeScore());

        var draw = r.items().get(1);
        assertEquals(4, draw.points());
        assertEquals(-1.0, draw.othersAvgPoints(), 1e-9);

        var win = r.items().get(2);
        assertEquals(1, win.points());
        assertEquals(1, win.aiPoints());
        assertEquals(1.5, win.othersAvgPoints(), 1e-9);
        assertEquals(2, win.othersCount());
    }

    @Test
    void includesCupMatchesWithoutEplOpponentAndUnpredictedMatches() {
        User user = User.builder().id(5).login("nik").build();
        when(predictionService.findAllByUserId(5)).thenReturn(List.of());
        when(predictionService.findAllCupByUserId(5)).thenReturn(List.of(
                Prediction.builder().cupMatchId(900).userId(5).homeTeamScore(2).awayTeamScore(1).build()
        ));
        when(matchService.findAllByTeamPublicId(1)).thenReturn(List.of());
        when(cupMatchDao.findAllByTeamPublicId(1)).thenReturn(List.of(
                CupMatch.builder().publicId(900).homeTeamId(1).homeName("Arsenal")
                        .awayName("Lower Club").homeTeamScore(2).awayTeamScore(1).status("ft")
                        .localDateTime(LocalDateTime.of(2026, 10, 20, 18, 0)).build(),
                CupMatch.builder().publicId(901).homeTeamId(1).awayTeamId(2).homeName("Arsenal")
                        .awayName("Chelsea").status("ns")
                        .localDateTime(LocalDateTime.of(2026, 10, 21, 18, 0)).build()
        ));
        when(statsDao.findKickoffScoresByMatchIds(anyCollection())).thenReturn(Map.of());
        when(predictionService.othersPointsByCupMatchIds(anyCollection(), eq(5))).thenReturn(Map.of());

        UserPredictionStatsService service =
                new UserPredictionStatsService(predictionService, matchService, cupMatchDao, bonusService, logos, statsDao);
        ProfileTeamPredictionsResponse r = service.teamPredictions(user, ars);

        assertEquals(2, r.items().size());
        assertEquals(1, r.matches());
        assertEquals(2.0, r.avgPoints(), 1e-9);

        var unpredicted = r.items().get(0);
        assertEquals("CHE", unpredicted.awayCode());
        assertTrue(unpredicted.cup());
        assertNull(unpredicted.predictHome());
        assertNull(unpredicted.points());

        var cup = r.items().get(1);
        assertEquals("Lower Club", cup.awayCode());
        assertTrue(cup.cup());
        assertFalse(cup.weekBonus());
        assertEquals(2, cup.points());
    }

    @Test
    void clubListAverageMatchesTeamModalAverageWithCupAndEplPicks() {
        User user = User.builder().id(5).login("nik").build();
        when(predictionService.findAllByUserId(5)).thenReturn(List.of(mp(1, 1, 3, 2, 0, "ft", 2, 1)));
        when(predictionService.findAllCupByUserId(5)).thenReturn(List.of(
                Prediction.builder().cupMatchId(900).userId(5).homeTeamScore(2).awayTeamScore(1).build()
        ));
        when(cupMatchDao.findAllByPublicIds(anyCollection())).thenReturn(List.of(
                CupMatch.builder().publicId(900).homeTeamId(1).homeName("Arsenal").awayName("Lower Club")
                        .homeTeamScore(2).awayTeamScore(1).status("ft")
                        .localDateTime(LocalDateTime.of(2026, 10, 20, 18, 0)).build()
        ));
        when(matchService.findAllByTeamPublicId(1)).thenReturn(List.of(match(1, 1, 3, 2, 0, "ft")));
        when(cupMatchDao.findAllByTeamPublicId(1)).thenReturn(List.of(
                CupMatch.builder().publicId(900).homeTeamId(1).homeName("Arsenal").awayName("Lower Club")
                        .homeTeamScore(2).awayTeamScore(1).status("ft")
                        .localDateTime(LocalDateTime.of(2026, 10, 20, 18, 0)).build()
        ));

        UserPredictionStatsService service =
                new UserPredictionStatsService(predictionService, matchService, cupMatchDao, bonusService, logos, statsDao);
        double modal = service.teamPredictions(user, ars).avgPoints();
        double club = service.build(user).teams().stream()
                .filter(t -> "ARS".equals(t.teamCode()))
                .findFirst().orElseThrow().avgPoints();

        assertEquals(2, service.teamPredictions(user, ars).matches());
        assertEquals(club, modal, 1e-9);
    }
}
