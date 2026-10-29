package zhigalin.predictions.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EspnTeamLogosTest {

    @Test
    void mapsInternalCodesToEspnCdn() {
        assertEquals(359, EspnTeamLogos.espnTeamId("ARS"));
        assertEquals(362, EspnTeamLogos.espnTeamId("AST"));
        assertEquals(382, EspnTeamLogos.espnTeamId("MCI"));
        assertEquals(360, EspnTeamLogos.espnTeamId("MUN"));
        assertEquals(
                "https://a.espncdn.com/i/teamlogos/soccer/500/364.png",
                EspnTeamLogos.logoUrl("LIV")
        );
        assertTrue(EspnTeamLogos.allEspnIds().size() >= 20);
        assertNotNull(EspnTeamLogos.logoUrl("tot"));
    }

    @Test
    void resolvesEspnTeamIdNotAbbreviation() {
        assertEquals("BRI", EspnTeamLogos.codeForEspnTeamId("331"));
        assertEquals("MUN", EspnTeamLogos.codeForEspnTeamId("360"));
        assertNull(EspnTeamLogos.codeForEspnTeamId("308"));
        assertNull(EspnTeamLogos.codeForEspnTeamId("635"));
        assertNull(EspnTeamLogos.codeForEspnTeamId("351"));
        assertNull(EspnTeamLogos.codeForEspnTeamId(null));
        assertNull(EspnTeamLogos.codeForEspnTeamId("abc"));
    }
}
