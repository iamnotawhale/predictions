package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class FootyStatsTeamNameMapperTest {

    @Test
    void mapsManchesterCityToMciNotMac() {
        assertEquals(Optional.of("MCI"), FootyStatsTeamNameMapper.toTeamCode("Manchester City FC"));
        assertEquals(Optional.of("MCI"), FootyStatsTeamNameMapper.toTeamCode("Manchester City"));
    }

    @Test
    void mapsCoventry() {
        assertEquals(Optional.of("COV"), FootyStatsTeamNameMapper.toTeamCode("Coventry City FC"));
    }
}
