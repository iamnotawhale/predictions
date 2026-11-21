package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnderstatTeamNameMapperTest {

    @Test
    void mapsExactTitlesOnly() {
        assertEquals("MCI", UnderstatTeamNameMapper.toTeamCode("Manchester City").orElseThrow());
        assertEquals("NOT", UnderstatTeamNameMapper.toTeamCode("Nottingham Forest").orElseThrow());
        assertEquals("WOL", UnderstatTeamNameMapper.toTeamCode("Wolverhampton Wanderers").orElseThrow());
        assertTrue(UnderstatTeamNameMapper.toTeamCode("Man City").isEmpty());
        assertTrue(UnderstatTeamNameMapper.toTeamCode("Brighton and Hove Albion").isEmpty());
    }
}
