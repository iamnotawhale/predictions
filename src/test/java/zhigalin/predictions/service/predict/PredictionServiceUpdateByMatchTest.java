package zhigalin.predictions.service.predict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.user.UserService;

@ExtendWith(MockitoExtension.class)
class PredictionServiceUpdateByMatchTest {

    @Mock
    private PredictionDao predictionDao;
    @Mock
    private MatchService matchService;
    @Mock
    private UserService userService;

    @InjectMocks
    private PredictionService predictionService;

    @Test
    void updateByMatch_batchesComputedPointsForAllUsers() {
        Match match = Match.builder()
                .publicId(100)
                .homeTeamScore(2)
                .awayTeamScore(1)
                .status("ft")
                .build();
        User u1 = User.builder().id(1).login("aaa").build();
        User u2 = User.builder().id(2).login("bbb").build();
        User u3 = User.builder().id(3).login("ccc").build();
        User u4 = User.builder().id(4).login("ddd").build();

        when(userService.findAll()).thenReturn(List.of(u1, u2, u3, u4));
        when(predictionDao.findAllByMatchIds(List.of(100))).thenReturn(List.of(
                Prediction.builder().matchPublicId(100).userId(1).homeTeamScore(2).awayTeamScore(1).build(),
                Prediction.builder().matchPublicId(100).userId(2).homeTeamScore(3).awayTeamScore(2).build(),
                Prediction.builder().matchPublicId(100).userId(3).homeTeamScore(0).awayTeamScore(1).build(),
                Prediction.builder().matchPublicId(100).userId(4).homeTeamScore(null).awayTeamScore(null).build()
        ));

        predictionService.updateByMatch(match);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PredictionDao.PointsUpdate>> captor = ArgumentCaptor.forClass(List.class);
        verify(predictionDao).updatePointsBatch(captor.capture());
        verify(predictionDao, never()).updatePoints(anyInt(), anyInt(), anyInt());

        List<PredictionDao.PointsUpdate> updates = captor.getValue();
        assertEquals(4, updates.size());
        assertEquals(4, pointsFor(updates, 1));
        assertEquals(2, pointsFor(updates, 2));
        assertEquals(-1, pointsFor(updates, 3));
        assertEquals(-1, pointsFor(updates, 4));
    }

    @Test
    void updateByMatch_fillsMissingPredictsWhenFewerThanFour() {
        Match match = Match.builder()
                .publicId(200)
                .homeTeamScore(1)
                .awayTeamScore(0)
                .status("ft")
                .build();
        User u1 = User.builder().id(1).login("aaa").build();
        User u2 = User.builder().id(2).login("bbb").build();
        User u3 = User.builder().id(3).login("ccc").build();
        User u4 = User.builder().id(4).login("ddd").build();

        when(userService.findAll()).thenReturn(List.of(u1, u2, u3, u4));
        when(predictionDao.findAllByMatchIds(List.of(200))).thenReturn(
                List.of(Prediction.builder().matchPublicId(200).userId(1).homeTeamScore(1).awayTeamScore(0).build()),
                List.of(
                        Prediction.builder().matchPublicId(200).userId(1).homeTeamScore(1).awayTeamScore(0).build(),
                        Prediction.builder().matchPublicId(200).userId(2).homeTeamScore(null).awayTeamScore(null).points(-1).build(),
                        Prediction.builder().matchPublicId(200).userId(3).homeTeamScore(null).awayTeamScore(null).points(-1).build(),
                        Prediction.builder().matchPublicId(200).userId(4).homeTeamScore(null).awayTeamScore(null).points(-1).build()
                )
        );

        predictionService.updateByMatch(match);

        verify(predictionDao).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getUserId() == 2 && p.getPoints() != null && p.getPoints() == -1));
        verify(predictionDao).updatePointsBatch(anyList());
    }

    private static int pointsFor(List<PredictionDao.PointsUpdate> updates, int userId) {
        return updates.stream()
                .filter(u -> u.userId() == userId)
                .map(PredictionDao.PointsUpdate::points)
                .findFirst()
                .orElseThrow();
    }
}
