package zhigalin.predictions.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.service.api.ApiClient.LatestGoalInfo;

class ApiClientGoalCommentaryTest {

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(new ObjectMapper());
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
}
