package zhigalin.predictions.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.service.api.ApiClient.GoalScorer;
import zhigalin.predictions.service.api.ApiClient.LatestGoalInfo;

class ApiClientGoalCommentaryTest {

    private ApiClient api;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        api = new ApiClient(mapper);
    }

    @Test
    void stripsFollowingNarrativeFromAssist() {
        LatestGoalInfo goal = api.parseGoalCommentary(
                "Goal!  Bournemouth 1, Everton 0. Alex Scott (Bournemouth) left footed shot from the centre of the box. "
                        + "Assisted by Evanilson following a fast break.");
        assertNotNull(goal);
        assertEquals("Alex Scott", goal.scorer());
        assertEquals("Evanilson", goal.assist());
    }

    @Test
    void stripsWithNarrativeFromAssist() {
        LatestGoalInfo goal = api.parseGoalCommentary(
                "Goal!  Arsenal 1, Chelsea 0. Bukayo Saka (Arsenal) left footed shot from the centre of the box. "
                        + "Assisted by Martin Odegaard with a through ball.");
        assertNotNull(goal);
        assertEquals("Bukayo Saka", goal.scorer());
        assertEquals("Martin Odegaard", goal.assist());
    }

    @Test
    void plainAssistKeepsNameOnly() {
        LatestGoalInfo goal = api.parseGoalCommentary(
                "Goal!  Arsenal 1, Chelsea 0. Bukayo Saka (Arsenal) left footed shot from the centre of the box. "
                        + "Assisted by Declan Rice.");
        assertNotNull(goal);
        assertEquals("Declan Rice", goal.assist());
    }

    @Test
    void noAssistWhenAbsent() {
        LatestGoalInfo goal = api.parseGoalCommentary(
                "Goal!  Arsenal 1, Chelsea 0. Bukayo Saka (Arsenal) converts the penalty.");
        assertNotNull(goal);
        assertEquals("Bukayo Saka", goal.scorer());
        assertNull(goal.assist());
    }

    @Test
    void extractGoalScorersKeepsEarliestWhenVarTrimsTotal() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "commentary": [
                    {
                      "sequence": 1,
                      "text": "Goal!  Arsenal 1, Chelsea 0. Bukayo Saka (Arsenal) left footed shot. Assisted by Declan Rice.",
                      "time": { "value": 23, "displayValue": "23'" }
                    },
                    {
                      "sequence": 2,
                      "text": "Goal!  Arsenal 2, Chelsea 0. Kai Havertz (Arsenal) header.",
                      "time": { "value": 67, "displayValue": "67'" }
                    },
                    {
                      "sequence": 3,
                      "text": "Goal!  Arsenal 3, Chelsea 0. Gabriel Jesus (Arsenal) right footed shot.",
                      "time": { "value": 80, "displayValue": "80'" }
                    }
                  ]
                }
                """);
        List<GoalScorer> scorers = api.extractGoalScorers(root, 2);
        assertEquals(2, scorers.size());
        assertEquals("Bukayo Saka", scorers.get(0).scorer());
        assertEquals("23'", scorers.get(0).minute());
        assertEquals("Kai Havertz", scorers.get(1).scorer());
        assertEquals("67'", scorers.get(1).minute());
    }

    @Test
    void extractGoalScorersEmptyWhenNoGoals() throws Exception {
        JsonNode root = mapper.readTree("""
                { "commentary": [ { "text": "Foul by Rice.", "time": { "value": 10 } } ] }
                """);
        assertEquals(List.of(), api.extractGoalScorers(root, 1));
    }
}
