package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.FotMobClient.FotMobFixture;
import zhigalin.predictions.recommender.FotMobClient.XgPair;

class FotMobClientTest {

    private final FotMobClient client = new FotMobClient(new ObjectMapper());

    @Test
    void parseFixturesKeepsFinishedMappedTeams() {
        String json = """
                {
                  "fixtures": {
                    "allMatches": [
                      {
                        "id": "1",
                        "home": {"name": "Arsenal"},
                        "away": {"name": "Chelsea"},
                        "status": {
                          "utcTime": "2026-08-15T14:00:00Z",
                          "finished": true
                        }
                      },
                      {
                        "id": "2",
                        "home": {"name": "Arsenal"},
                        "away": {"name": "Liverpool"},
                        "status": {
                          "utcTime": "2026-08-22T14:00:00Z",
                          "finished": false
                        }
                      }
                    ]
                  }
                }
                """;
        List<FotMobFixture> fixtures = client.parseFixtures(json);
        assertEquals(1, fixtures.size());
        assertEquals("1", fixtures.getFirst().fotmobId());
        assertEquals("ARS", fixtures.getFirst().homeCode());
        assertEquals("CHE", fixtures.getFirst().awayCode());
    }

    @Test
    void parseMatchXgReadsExpectedGoals() {
        String json = """
                {
                  "content": {
                    "stats": {
                      "Periods": {
                        "All": {
                          "stats": [
                            {
                              "title": "Top stats",
                              "stats": [
                                {
                                  "title": "Expected goals (xG)",
                                  "key": "expected_goals",
                                  "stats": ["1.88", "0.20"],
                                  "rawStats": [{"value": 1.88}, {"value": 0.2}],
                                  "type": "text"
                                }
                              ]
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """;
        Optional<XgPair> xg = client.parseMatchXg(json);
        assertTrue(xg.isPresent());
        assertEquals(1.88, xg.get().homeXg());
        assertEquals(0.2, xg.get().awayXg());
    }
}
