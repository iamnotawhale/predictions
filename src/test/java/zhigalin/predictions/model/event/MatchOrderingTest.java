package zhigalin.predictions.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchOrderingTest {

    @Test
    void sortsByKickoffThenPublicId() {
        Match later = Match.builder()
                .publicId(1)
                .localDateTime(LocalDateTime.of(2026, 8, 29, 18, 0))
                .build();
        Match earlierSameTimeHigherId = Match.builder()
                .publicId(20)
                .localDateTime(LocalDateTime.of(2026, 8, 29, 16, 0))
                .build();
        Match earlierSameTimeLowerId = Match.builder()
                .publicId(10)
                .localDateTime(LocalDateTime.of(2026, 8, 29, 16, 0))
                .build();
        Match nullTime = Match.builder()
                .publicId(5)
                .localDateTime(null)
                .build();

        List<Match> sorted = List.of(later, earlierSameTimeHigherId, nullTime, earlierSameTimeLowerId).stream()
                .sorted(Match.BY_KICKOFF_THEN_PUBLIC_ID)
                .toList();

        assertEquals(List.of(10, 20, 1, 5), sorted.stream().map(Match::getPublicId).toList());
    }
}
