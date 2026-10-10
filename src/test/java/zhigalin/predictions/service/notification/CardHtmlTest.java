package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zhigalin.predictions.service.notification.CardRenderModels.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CardHtmlTest {

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void escapeHandlesHtmlSpecialsAndNull() {
        assertEquals("", CardHtml.escape(null));
        assertEquals("a &amp; b &lt;i&gt;", CardHtml.escape("a & b <i>"));
        assertEquals("say &quot;hi&quot;", CardHtml.escapeAttr("say \"hi\""));
    }

    @Test
    void shellWrapsBodyWithAccentAndLayoutClass() {
        String html = CardHtml.shell("eng.fa", "<p>x</p>", "card-narrow");
        assertTrue(html.contains("<div class=\"card card-narrow\" data-accent=\"eng.fa\"><p>x</p></div>"));
        assertTrue(html.contains("<style>"));
        assertTrue(html.contains("--accent"), "base.css should be inlined");
        assertTrue(CardHtml.shell("epl", "b").contains("<div class=\"card\" data-accent=\"epl\">"));
    }

    @Test
    void shellEscapesAccent() {
        assertTrue(CardHtml.shell("a\"b", "x").contains("data-accent=\"a&quot;b\""));
    }

    @Test
    void todayCardRendersOneRowPerMatchAndStubsForMissingLogos() {
        TodayCard card = new TodayCard(List.of(
                new TodaySection("WEEK 5", List.of(
                        new TodayRow("MCI", "ARS", "", "data:image/png;base64,AAAA", "17:30"),
                        new TodayRow("LIV", "CHE", "", "", "20:00")))));
        String html = CardHtml.buildTodayHtml(card);
        assertEquals(2, count(html, "class=\"match-row\""));
        assertTrue(html.contains("WEEK 5"));
        assertTrue(html.contains(">17:30<"));
        assertEquals(3, count(html, "class=\"stub\""));
        assertEquals(1, count(html, "<img "));
    }

    @Test
    void resultCardShowsAiLineOnlyWhenPresentAndFlagsWeekBonus() {
        ResultCard base = new ResultCard("epl", "EPL · FULL TIME", "MCI", "MUN", "", "", "2:1", null,
                List.of(new ResultLine("nik", "2:1", 3), new ResultLine("max", "1:1", 1, true)));
        String without = CardHtml.buildResultHtml(base);
        assertFalse(without.contains("class=\"ai-line\""));
        assertEquals(1, count(without, "<span class=\"badge-bonus\">"));
        assertTrue(without.contains("result-line week-bonus"));

        ResultCard withAi = new ResultCard("epl", "x", "MCI", "MUN", "", "", "2:1", "1:1", List.of());
        String html = CardHtml.buildResultHtml(withAi);
        assertTrue(html.contains("AI 1:1"));
        assertFalse(html.contains("class=\"results\""), "no results panel for empty list");
    }

    @Test
    void weeklyCardTintsOnlyTopThree() {
        WeeklyCard card = new WeeklyCard(7, List.of(
                new WeeklyRow(1, "NIK", 12), new WeeklyRow(2, "MAX", 10),
                new WeeklyRow(3, "ALE", 8), new WeeklyRow(4, "IVA", 5)));
        String html = CardHtml.buildWeeklyHtml(card);
        assertTrue(html.contains("weekly-row p1"));
        assertTrue(html.contains("weekly-row p3"));
        assertFalse(html.contains("p4"));
        assertTrue(html.contains("Результаты 7 тура"));
    }

    @Test
    void reminderCardHandlesEmptyFormAndH2h() {
        ReminderCard card = new ReminderCard("epl", "PREDICT", "TOT", "AST", "", "", "14:30",
                "2.10", "3.50", "3.40", List.of(), List.of(), List.of(), true);
        String html = CardHtml.buildReminderHtml(card);
        assertTrue(html.contains("нет данных"));
        assertEquals(2, count(html, "hint-empty\">—<"));
        assertTrue(html.contains("<span class=\"badge-bonus reminder-bonus\">"));
        assertEquals(3, count(html, "class=\"odd-cell\""));
        assertTrue(html.contains("2.10"));
    }

    @Test
    void reminderFormChipsKeepSideOrdering() {
        FormChip chip = new FormChip("data:image/png;base64,AA", "2:1");
        ReminderCard card = new ReminderCard("epl", "P", "A", "B", "", "", "1", "1", "1", "1",
                List.of(chip), List.of(chip), List.of(), false);
        String html = CardHtml.buildReminderHtml(card);
        int homeImg = html.indexOf("form-col\"><div class=\"form-chip\"><img");
        int awayScore = html.indexOf("form-col form-col-away\"><div class=\"form-chip\"><span class=\"chip-score\">");
        assertTrue(homeImg >= 0, "home side: logo before score");
        assertTrue(awayScore >= 0, "away side: score before logo");
        assertFalse(html.contains("<span class=\"badge-bonus reminder-bonus\">"));
    }

    @Test
    void textIsEscapedInCards() {
        YourPredictCard card = new YourPredictCard("<A>", "B&B", "", "", "WEEK <1>", "10:00", "1:0");
        String html = CardHtml.buildYourPredictHtml(card);
        assertTrue(html.contains("&lt;A&gt;"));
        assertTrue(html.contains("B&amp;B"));
        assertFalse(html.contains("<A>"));
    }

    @Test
    void chartCardEmbedsDataUri() {
        String html = CardHtml.buildChartHtml("data:image/png;base64,QUJD");
        assertTrue(html.contains("<img src=\"data:image/png;base64,QUJD\""));
    }
}
