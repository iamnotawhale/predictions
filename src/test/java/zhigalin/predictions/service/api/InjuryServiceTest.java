package zhigalin.predictions.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InjuryServiceTest {

    @Test
    void filtersTransfersAndLoans() {
        assertTrue(InjuryService.isTransferOrDeparture("Has joined Al Hilal permanently"));
        assertTrue(InjuryService.isTransferOrDeparture(
                "Has joined Borussia Dortmund on loan for the rest of the season"));
        assertTrue(InjuryService.isTransferOrDeparture("has departed the club as a free agent."));
        assertTrue(InjuryService.isTransferOrDeparture("has returned to Getafe CF"));
        assertFalse(InjuryService.isTransferOrDeparture("Knee injury - Unknown return date"));
        assertFalse(InjuryService.isTransferOrDeparture("not included in squad."));
    }

    @Test
    void statusUKeepsOnlyAbsenceLikeNews() {
        assertTrue(InjuryService.isTransferStatus("u", "Has joined Al Hilal permanently"));
        assertTrue(InjuryService.isTransferStatus("u", ""));
        assertFalse(InjuryService.isTransferStatus("u", "not included in squad."));
        assertFalse(InjuryService.isTransferStatus("i", "Has joined Al Hilal permanently"));
    }

    @Test
    void translatesInjuryReasonsToRussian() {
        assertEquals(
                "Травма колена — дата возвращения неизвестна",
                InjuryService.translateReason("Knee injury - Unknown return date"));
        assertEquals(
                "Травма икры — шанс сыграть 75%",
                InjuryService.translateReason("Calf injury - 75% chance of playing"));
        assertEquals(
                "Травма задней поверхности бедра — ориентир 10 окт",
                InjuryService.translateReason("Hamstring injury - Expected back 10 Oct"));
        assertEquals(
                "Травма (не уточнено) — ориентир 12 окт",
                InjuryService.translateReason("Unspecified injury - Expected back 12 Oct"));
        assertEquals(
                "Сотрясение — ориентир 10 окт",
                InjuryService.translateReason("Concussion - Expected back 10 Oct"));
        assertEquals(
                "Дисквалификация до 19 окт",
                InjuryService.translateReason("Suspended until 19 Oct"));
        assertEquals("Не в заявке", InjuryService.translateReason("not included in squad."));
    }
}
