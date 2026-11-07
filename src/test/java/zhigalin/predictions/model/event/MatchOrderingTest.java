package zhigalin.predictions.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchOrderingTest {

    @Test
    void sortsByKickoffThenPublicId() {
        Match later = Match.builder()
                .id(1)
                .startsAt(LocalDateTime.of(2026, 8, 29, 18, 0))
                .build();
        Match earlierSameTimeHigherId = Match.builder()
                .id(20)
                .startsAt(LocalDateTime.of(2026, 8, 29, 16, 0))
                .build();
        Match earlierSameTimeLowerId = Match.builder()
                .id(10)
                .startsAt(LocalDateTime.of(2026, 8, 29, 16, 0))
                .build();
        Match nullTime = Match.builder()
                .id(5)
                .startsAt(null)
                .build();

        List<Match> sorted = List.of(later, earlierSameTimeHigherId, nullTime, earlierSameTimeLowerId).stream()
                .sorted(Match.BY_KICKOFF_THEN_ID)
                .toList();

        assertEquals(List.of(10, 20, 1, 5), sorted.stream().map(Match::getId).toList());
    }
}
