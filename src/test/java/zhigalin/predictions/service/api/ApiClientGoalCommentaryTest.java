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
    void parseGoalIncludesScoreAfter() {
        LatestGoalInfo goal = api.parseGoalCommentary(
                "Goal!  Manchester City 2, Liverpool 1. Erling Haaland (Manchester City) left footed shot.");
        assertNotNull(goal);
        assertEquals("Erling Haaland", goal.scorer());
        assertEquals(2, goal.homeScore());
        assertEquals(1, goal.awayScore());
    }

    @Test
    void parseOwnGoalIncludesScoreAfter() {
        LatestGoalInfo goal = api.parseGoalCommentary(
                "Own Goal by Ashley Young, Everton. Everton 1, Brighton and Hove Albion 1.");
        assertNotNull(goal);
        assertEquals("Ashley Young (авт.)", goal.scorer());
        assertEquals(1, goal.homeScore());
        assertEquals(1, goal.awayScore());
    }

    @Test
    void extractGoalScorersFormatsScorePerGoal() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "commentary": [
                    {
                      "sequence": 1,
                      "text": "Goal!  Manchester City 0, Liverpool 1. Mohamed Salah (Liverpool) left footed shot.",
                      "time": { "value": 23, "displayValue": "23'" }
                    },
                    {
                      "sequence": 2,
                      "text": "Goal!  Manchester City 1, Liverpool 1. Phil Foden (Manchester City) right footed shot.",
                      "time": { "value": 61, "displayValue": "61'" }
                    },
                    {
                      "sequence": 3,
                      "text": "Goal!  Manchester City 2, Liverpool 1. Erling Haaland (Manchester City) header.",
                      "time": { "value": 74, "displayValue": "74'" }
                    }
                  ]
                }
                """);
        List<GoalScorer> scorers = api.extractGoalScorers(root, 3);
        assertEquals(3, scorers.size());
        assertEquals("0:1 Mohamed Salah 23'", scorers.get(0).formatForCaption());
        assertEquals("1:1 Phil Foden 61'", scorers.get(1).formatForCaption());
        assertEquals("2:1 Erling Haaland 74'", scorers.get(2).formatForCaption());
    }

    @Test
    void parseOwnGoalScorer() {
        LatestGoalInfo goal = api.parseGoalCommentary(
                "Own Goal by Ashley Young, Everton. Everton 1, Brighton and Hove Albion 1.");
        assertNotNull(goal);
        assertEquals("Ashley Young (авт.)", goal.scorer());
        assertNull(goal.assist());
    }

    @Test
    void extractGoalScorersIncludesOwnGoals() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "commentary": [
                    {
                      "sequence": 1,
                      "text": "Goal!  Brentford 1, West Ham United 0. Neal Maupay (Brentford) header.",
                      "time": { "value": 12, "displayValue": "12'" }
                    },
                    {
                      "sequence": 2,
                      "text": "Own Goal by Konstantinos Mavropanos, West Ham United. Brentford 2, West Ham United 0.",
                      "time": { "value": 55, "displayValue": "55'" }
                    }
                  ]
                }
                """);
        List<GoalScorer> scorers = api.extractGoalScorers(root, 2);
        assertEquals(2, scorers.size());
        assertEquals("Neal Maupay", scorers.get(0).scorer());
        assertEquals("1:0 Neal Maupay 12'", scorers.get(0).formatForCaption());
        assertEquals("Konstantinos Mavropanos (авт.)", scorers.get(1).scorer());
        assertEquals("2:0 Konstantinos Mavropanos (авт.) 55'", scorers.get(1).formatForCaption());
        assertEquals("55'", scorers.get(1).minute());
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
