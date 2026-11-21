package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FotMobTeamNameMapperTest {

    @Test
    void mapsExactTitlesOnly() {
        assertEquals("BOU", FotMobTeamNameMapper.toTeamCode("AFC Bournemouth").orElseThrow());
        assertEquals("BRI", FotMobTeamNameMapper.toTeamCode("Brighton & Hove Albion").orElseThrow());
        assertEquals("TOT", FotMobTeamNameMapper.toTeamCode("Tottenham Hotspur").orElseThrow());
        assertEquals("WES", FotMobTeamNameMapper.toTeamCode("West Ham United").orElseThrow());
        assertTrue(FotMobTeamNameMapper.toTeamCode("Man City").isEmpty());
    }
}
