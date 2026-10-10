package zhigalin.predictions.service.notification;

import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Player;

/** Text helpers for reminder captions (monospace lineups, Telegram caption limit). */
final class ReminderCaption {
    private ReminderCaption() {
    }

    /** True in the 2-minute slot ending exactly {@code reminderMinutes} before kickoff. */
    static boolean isReminderWindow(long minutesLeft, int reminderMinutes) {
        return minutesLeft <= reminderMinutes && minutesLeft > reminderMinutes - 2;
    }

    /** "9.  Haaland": shirt number padded to 4 chars + last name. */
    static String formattedName(Lineup l) {
        Player player = l.getPlayer();
        String number = String.valueOf(player.getNumber());
        String name = player.getName();
        String shown = name.contains(" ") ? name.substring(name.lastIndexOf(' ') + 1) : name;
        return padRight(number + ". ", 4) + shown;
    }

    static String trimCaption(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 1) + "…";
    }

    static String padRight(String text, int length) {
        return String.format("%-" + length + "s", text);
    }
}
