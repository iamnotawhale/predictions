package zhigalin.predictions.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.v2.Competitor;
import zhigalin.predictions.model.v2.Competitor.LineScore;
import zhigalin.predictions.model.v2.Competition;
import zhigalin.predictions.model.v2.Event;

class EspnLinescoresTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fromEventReadsFirstPeriod() {
        Competitor home = new Competitor();
        home.setHomeAway("home");
        LineScore h1 = new LineScore();
        h1.setDisplayValue("2");
        LineScore h2 = new LineScore();
        h2.setDisplayValue("1");
        home.setLinescores(List.of(h1, h2));

        Competitor away = new Competitor();
        away.setHomeAway("away");
        LineScore a1 = new LineScore();
        a1.setDisplayValue("0");
        LineScore a2 = new LineScore();
        a2.setDisplayValue("0");
        away.setLinescores(List.of(a1, a2));

        Competition competition = new Competition();
        competition.setCompetitors(List.of(home, away));
        Event event = new Event();
        event.setCompetitions(List.of(competition));

        EspnLinescores.HtScore ht = EspnLinescores.fromEvent(event);
        assertNotNull(ht);
        assertEquals(2, ht.home());
        assertEquals(0, ht.away());
    }

    @Test
    void fromSummaryReadsHeaderLinescoresIncludingZero() throws Exception {
        String json = """
                {
                  "header": {
                    "competitions": [{
                      "competitors": [
                        {"homeAway":"home","score":"4","linescores":[{"displayValue":"0"},{"displayValue":"4"}]},
                        {"homeAway":"away","score":"0","linescores":[{"displayValue":"0"},{"displayValue":"0"}]}
                      ]
                    }]
                  }
                }
                """;
        EspnLinescores.HtScore ht = EspnLinescores.fromSummary(mapper.readTree(json));
        assertNotNull(ht);
        assertEquals(0, ht.home());
        assertEquals(0, ht.away());
    }

    @Test
    void fromEventReturnsNullWithoutLinescores() {
        Competitor home = new Competitor();
        home.setHomeAway("home");
        Competitor away = new Competitor();
        away.setHomeAway("away");
        Competition competition = new Competition();
        competition.setCompetitors(List.of(home, away));
        Event event = new Event();
        event.setCompetitions(List.of(competition));
        assertNull(EspnLinescores.fromEvent(event));
    }
}
