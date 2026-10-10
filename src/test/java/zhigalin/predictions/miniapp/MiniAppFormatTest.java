package zhigalin.predictions.miniapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MiniAppFormatTest {

    @Test
    void round1RoundsHalfUpAndDropsNonFinite() {
        assertEquals(1.3, MiniAppFormat.round1(1.26));
        assertNull(MiniAppFormat.round1(null));
        assertNull(MiniAppFormat.round1(Double.NaN));
        assertNull(MiniAppFormat.round1(Double.POSITIVE_INFINITY));
    }

    @Test
    void roundPctScalesFractionsButKeepsPercentages() {
        assertEquals(45.0, MiniAppFormat.roundPct(0.45));
        assertEquals(45.5, MiniAppFormat.roundPct(45.5));
        assertEquals(150.0, MiniAppFormat.roundPct(1.5));
        assertNull(MiniAppFormat.roundPct(null));
        assertNull(MiniAppFormat.roundPct(Double.NaN));
    }

    @Test
    void percentHandlesZeroTotal() {
        assertEquals(0, MiniAppFormat.percent(3, 0));
        assertEquals(33, MiniAppFormat.percent(1, 3));
        assertEquals(67, MiniAppFormat.percent(2, 3));
    }

    @Test
    void firstNonNullPrefersFirst() {
        assertEquals(1.0, MiniAppFormat.firstNonNull(1.0, 2.0));
        assertEquals(2.0, MiniAppFormat.firstNonNull(null, 2.0));
        assertNull(MiniAppFormat.firstNonNull(null, null));
    }

    @Test
    void competitionLabelMapsKnownCodes() {
        assertEquals("FA Cup", MiniAppFormat.competitionLabel("eng.fa"));
        assertEquals("Carabao Cup", MiniAppFormat.competitionLabel("eng.league_cup"));
        assertEquals("UCL", MiniAppFormat.competitionLabel("uefa.champions"));
        assertEquals("UEL", MiniAppFormat.competitionLabel("uefa.europa"));
        assertEquals("UECL", MiniAppFormat.competitionLabel("uefa.europa.conf"));
        assertEquals("xyz", MiniAppFormat.competitionLabel("xyz"));
        assertEquals("Cup", MiniAppFormat.competitionLabel(null));
    }
}
