package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Player;

class ReminderWidgetFactoryTest {

    @Test
    void formTokenUsesPlayedScorelineAndTeamResultLetter() {
        assertEquals('W', ReminderWidgetFactory.resultLetter(2, 1));
        assertEquals('L', ReminderWidgetFactory.resultLetter(0, 2));
        assertEquals('D', ReminderWidgetFactory.resultLetter(1, 1));
        assertEquals(
                "W1-0@HUL@H@07.11",
                ReminderWidgetFactory.formToken('W', 1, 0, "HUL", true, LocalDateTime.of(2024, 11, 7, 15, 0)));
        assertEquals(
                "W0-1@LIV@A@01.11",
                ReminderWidgetFactory.formToken('W', 0, 1, "LIV", false, LocalDateTime.of(2024, 11, 1, 17, 0)));
        assertEquals("L0-2@???@A", ReminderWidgetFactory.formToken('L', 0, 2, null, false, null));
        assertFalse(ReminderWidgetFactory.formToken('D', 1, 1, "ARS", true, null).endsWith("@"));
    }

    @Test
    void h2hTokenUsesSeasonNotCalendarDate() {
        assertEquals(
                "L2:1@LIV@ARS@EPL@24/25",
                ReminderWidgetFactory.h2hToken(
                        'L', 2, 1, "LIV", "ARS", "EPL", LocalDateTime.of(2024, 11, 1, 20, 0)));
        assertEquals("W1:3@ARS@CHE@FA", ReminderWidgetFactory.h2hToken('W', 1, 3, "ARS", "CHE", "FA", null));
        assertEquals("D0:0@ARS@CHE", ReminderWidgetFactory.h2hToken('D', 0, 0, "ARS", "CHE", null, null));
        assertEquals(
                "D0:0@ARS@CHE@24/25",
                ReminderWidgetFactory.h2hToken(
                        'D', 0, 0, "ARS", "CHE", null, LocalDateTime.of(2024, 11, 1, 20, 0)));
    }

    @Test
    void seasonLabelUsesAugustBoundary() {
        assertEquals("22/23", ReminderWidgetFactory.seasonLabel(LocalDateTime.of(2023, 3, 2, 15, 0)));
        assertEquals("23/24", ReminderWidgetFactory.seasonLabel(LocalDateTime.of(2023, 9, 3, 15, 0)));
        assertEquals("23/24", ReminderWidgetFactory.seasonLabel(LocalDateTime.of(2024, 7, 31, 20, 0)));
        assertEquals("24/25", ReminderWidgetFactory.seasonLabel(LocalDateTime.of(2024, 8, 1, 0, 0)));
        assertNull(ReminderWidgetFactory.seasonLabel(null));
    }

    @Test
    void competitionCodeMapsLeagueName() {
        assertEquals("EPL", ReminderWidgetFactory.competitionCode("PL 2024"));
        assertEquals("EPL", ReminderWidgetFactory.competitionCode("Premier League"));
        assertEquals("FA", ReminderWidgetFactory.competitionCode("FA 2023"));
        assertEquals("LC", ReminderWidgetFactory.competitionCode("LC 2022"));
        assertEquals("LC", ReminderWidgetFactory.competitionCode("League Cup"));
        assertEquals("LC", ReminderWidgetFactory.competitionCode("EFL Cup"));
        assertEquals("CH", ReminderWidgetFactory.competitionCode("CH 2024"));
        assertEquals("CH", ReminderWidgetFactory.competitionCode("Championship"));
        assertEquals("UCL", ReminderWidgetFactory.competitionCode("UCL 2021"));
        assertEquals("UCL", ReminderWidgetFactory.competitionCode("UEFA Champions League"));
        assertEquals("UEL", ReminderWidgetFactory.competitionCode("UEFA Europa League"));
        assertEquals("UECL", ReminderWidgetFactory.competitionCode("UEFA Europa Conference League"));
        assertNull(ReminderWidgetFactory.competitionCode(null));
        assertNull(ReminderWidgetFactory.competitionCode("  "));
    }

    @Test
    void formatLineupTakesNumberSurnameAndPlace() {
        assertEquals(
                "1.Raya|1.Saliba|1.Rice|1.Saka",
                ReminderWidgetFactory.formatLineup(List.of(
                        lineup(player("Bukayo Saka", "F")),
                        lineup(player("David Raya", "G")),
                        lineup(player("Declan Rice", "M")),
                        lineup(player("William Saliba", "D")))));
        Player withPlace = player("Erling Haaland", "F");
        withPlace.setNumber(9);
        withPlace.setFormationPlace(9);
        assertEquals(
                "9.Haaland@9",
                ReminderWidgetFactory.formatLineup(List.of(lineup(withPlace))));
        assertNull(ReminderWidgetFactory.formatLineup(null));
        assertNull(ReminderWidgetFactory.formatLineup(List.of()));
    }

    private static Player player(String name, String pos) {
        Player p = new Player();
        p.setName(name);
        p.setPos(pos);
        p.setNumber(1);
        return p;
    }

    private static Lineup lineup(Player player) {
        Lineup l = new Lineup();
        l.setPlayer(player);
        return l;
    }
}
