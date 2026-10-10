package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static zhigalin.predictions.service.notification.CardRenderModels.TodayCard;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class HtmlImageRendererModelTest {

    private static MatchRecord epl(int id, Integer week, LocalDateTime at) {
        return new MatchRecord(null, null, week, at, id, null, null, null, null, null);
    }

    private static MatchRecord cup(int id, String competition, LocalDateTime at) {
        return new MatchRecord(null, null, null, at, id, competition, "AAA", "BBB", null, null);
    }

    @Test
    void cupTitlesAndBadges() {
        assertEquals("CUP", HtmlImageRenderer.cupSectionTitle(null));
        assertEquals("FA CUP", HtmlImageRenderer.cupSectionTitle("eng.fa"));
        assertEquals("CARABAO", HtmlImageRenderer.cupSectionTitle("eng.league_cup"));
        assertEquals("SOME.CUP", HtmlImageRenderer.cupSectionTitle("some.cup"));
        assertEquals("UCL · FULL TIME", HtmlImageRenderer.competitionBadge("uefa.champions"));
    }

    @Test
    void accentDefaultsToEpl() {
        assertEquals("epl", HtmlImageRenderer.competitionAccent(null));
        assertEquals("epl", HtmlImageRenderer.competitionAccent(" "));
        assertEquals("uefa.europa", HtmlImageRenderer.competitionAccent("uefa.europa"));
    }

    @Test
    void todayCardGroupsEplByWeekThenCupsByKickoff() {
        LocalDateTime d = LocalDateTime.of(2026, 9, 17, 12, 0);
        TodayCard card = HtmlImageRenderer.toTodayCard(List.of(
                cup(1, "uefa.europa", d.plusHours(10)),
                epl(5, 4, d.plusHours(3)),
                cup(2, "eng.league_cup", d.plusHours(9)),
                epl(4, 4, d.plusHours(3)),
                epl(3, 5, d.plusHours(1))));
        List<String> titles = card.sections().stream().map(s -> s.title()).toList();
        assertEquals(List.of("WEEK 5", "WEEK 4", "CARABAO", "UEL"), titles);
        // sections follow earliest kickoff, so week 5 (13:00) precedes week 4 (15:00)
        assertEquals(1, card.sections().get(0).rows().size());
        assertEquals(2, card.sections().get(1).rows().size());
        assertEquals("21:00", card.sections().get(2).rows().get(0).kickoff());
    }

    @Test
    void missingKickoffRendersPlaceholderAndCupCodesAreUsed() {
        TodayCard card = HtmlImageRenderer.toTodayCard(List.of(cup(1, "eng.fa", null)));
        assertEquals("--:--", card.sections().get(0).rows().get(0).kickoff());
        assertEquals("AAA", card.sections().get(0).rows().get(0).homeCode());
        assertEquals("BBB", card.sections().get(0).rows().get(0).awayCode());
    }
}
