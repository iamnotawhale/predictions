package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StatsTeamNameMapperTest {

    @Test
    void mapsManchesterCityToMciNotMac() {
        assertEquals(Optional.of("MCI"), StatsTeamNameMapper.toTeamCode("Manchester City FC"));
        assertEquals(Optional.of("MCI"), StatsTeamNameMapper.toTeamCode("Manchester City"));
    }

    @Test
    void mapsCoventry() {
        assertEquals(Optional.of("COV"), StatsTeamNameMapper.toTeamCode("Coventry City FC"));
    }
}
