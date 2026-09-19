package zhigalin.predictions.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zhigalin.predictions.service.api.InjuryService.KIND_DOUBT;
import static zhigalin.predictions.service.api.InjuryService.KIND_INJURY;
import static zhigalin.predictions.service.api.InjuryService.KIND_OTHER;
import static zhigalin.predictions.service.api.InjuryService.KIND_SUSPENSION;

import org.junit.jupiter.api.Test;

class InjuryServiceTest {

    @Test
    void filtersTransfersAndLoans() {
        assertTrue(InjuryService.isTransferOrDeparture("Has joined Al Hilal permanently"));
        assertTrue(InjuryService.isTransferOrDeparture(
                "Has joined Borussia Dortmund on loan for the rest of the season"));
        assertFalse(InjuryService.isTransferOrDeparture("Knee injury - Unknown return date"));
        assertFalse(InjuryService.isTransferOrDeparture("not included in squad."));
    }

    @Test
    void statusUKeepsOnlyAbsenceLikeNews() {
        assertTrue(InjuryService.isTransferStatus("u", "Has joined Al Hilal permanently"));
        assertFalse(InjuryService.isTransferStatus("u", "not included in squad."));
        assertFalse(InjuryService.isTransferStatus("i", "Has joined Al Hilal permanently"));
    }

    @Test
    void mapsKinds() {
        assertEquals(KIND_INJURY, InjuryService.kindFromFplStatus("i"));
        assertEquals(KIND_SUSPENSION, InjuryService.kindFromFplStatus("s"));
        assertEquals(KIND_DOUBT, InjuryService.kindFromFplStatus("d"));
        assertEquals(KIND_OTHER, InjuryService.kindFromFplStatus("u"));
        assertEquals(KIND_SUSPENSION, InjuryService.kindFromEspn("Suspended", ""));
        assertEquals(KIND_INJURY, InjuryService.kindFromEspn("Out", "Knee injury"));
        assertEquals("Salah", InjuryService.shortName("Mohamed Salah"));
    }
}
