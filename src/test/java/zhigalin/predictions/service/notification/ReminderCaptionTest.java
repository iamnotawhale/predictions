package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Player;

class ReminderCaptionTest {

    private static Lineup lineup(int number, String name) {
        Player p = new Player();
        p.setNumber(number);
        p.setName(name);
        Lineup l = new Lineup();
        l.setPlayer(p);
        return l;
    }

    @Test
    void reminderWindowIsTwoMinutesWide() {
        assertTrue(ReminderCaption.isReminderWindow(60, 60));
        assertTrue(ReminderCaption.isReminderWindow(59, 60));
        assertFalse(ReminderCaption.isReminderWindow(58, 60));
        assertFalse(ReminderCaption.isReminderWindow(61, 60));
    }

    @Test
    void formattedNameShowsNumberAndLastName() {
        assertEquals("9.  Haaland", ReminderCaption.formattedName(lineup(9, "Erling Braut Haaland")));
        assertEquals("10. Foden", ReminderCaption.formattedName(lineup(10, "Phil Foden")));
        assertEquals("1.  Ederson", ReminderCaption.formattedName(lineup(1, "Ederson")));
    }

    @Test
    void trimCaptionAddsEllipsisOnlyWhenTooLong() {
        assertNull(ReminderCaption.trimCaption(null, 5));
        assertEquals("abcde", ReminderCaption.trimCaption("abcde", 5));
        assertEquals("abcd…", ReminderCaption.trimCaption("abcdef", 5));
    }

    @Test
    void padRightPadsWithSpaces() {
        assertEquals("ab   ", ReminderCaption.padRight("ab", 5));
        assertEquals("abcdef", ReminderCaption.padRight("abcdef", 3));
    }
}
