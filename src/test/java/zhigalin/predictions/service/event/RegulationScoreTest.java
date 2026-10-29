package zhigalin.predictions.service.event;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.v2.Competitor;
import zhigalin.predictions.model.v2.Detail;

class RegulationScoreTest {

    private static Competitor competitor(String id) {
        Competitor c = new Competitor();
        c.setId(id);
        return c;
    }

    private static Detail goal(String teamId, String minute) {
        return event(teamId, minute, true, false, false);
    }

    private static Detail event(String teamId, String minute, boolean scoring, boolean shootout, boolean ownGoal) {
        Detail d = new Detail();
        Detail.Clock clock = new Detail.Clock();
        clock.setDisplayValue(minute);
        d.setClock(clock);
        Detail.TeamRef team = new Detail.TeamRef();
        team.setId(teamId);
        d.setTeam(team);
        d.setScoringPlay(scoring);
        d.setShootout(shootout);
        d.setOwnGoal(ownGoal);
        return d;
    }

    private final Competitor home = competitor("1");
    private final Competitor away = competitor("2");

    @Test
    void extraTimeGoalsAreRemoved() {
        List<Detail> details = List.of(goal("1", "12'"), goal("2", "88'"), goal("1", "104'"));
        assertArrayEquals(new int[] {1, 1}, RegulationScore.fromDetails(details, home, away, 2, 1));
    }

    @Test
    void stoppageTimeOfSecondHalfCountsAsRegulation() {
        List<Detail> details = List.of(goal("2", "90'+4'"), goal("2", "120'+1'"));
        assertArrayEquals(new int[] {0, 1}, RegulationScore.fromDetails(details, home, away, 0, 2));
    }

    @Test
    void shootoutKicksAndNonScoringEventsAreIgnored() {
        List<Detail> details = List.of(
                goal("1", "30'"),
                goal("2", "60'"),
                event("1", "70'", false, false, false),
                event("1", "120'", true, true, false)
        );
        assertArrayEquals(new int[] {1, 1}, RegulationScore.fromDetails(details, home, away, 1, 1));
    }

    @Test
    void incompleteGoalListGivesUp() {
        assertNull(RegulationScore.fromDetails(List.of(goal("1", "10'")), home, away, 2, 0));
        assertNull(RegulationScore.fromDetails(null, home, away, 1, 0));
    }

    @Test
    void ownGoalInExtraTimeGivesUp() {
        List<Detail> details = List.of(goal("1", "10'"), event("2", "100'", true, false, true));
        assertNull(RegulationScore.fromDetails(details, home, away, 1, 1));
    }

    @Test
    void minuteParsing() {
        Detail.Clock c = new Detail.Clock();
        c.setDisplayValue("90'+3'");
        assertEquals(90, RegulationScore.minuteOf(c));
        c.setDisplayValue("105'");
        assertEquals(105, RegulationScore.minuteOf(c));
        c.setDisplayValue("-");
        assertNull(RegulationScore.minuteOf(c));
    }
}
