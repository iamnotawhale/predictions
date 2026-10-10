package zhigalin.predictions.miniapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.FormationPlayerItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchEventItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchStatItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamFormationItem;

class EspnSummaryParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    private static final String ROSTERS = """
        {"rosters":[
          {"homeAway":"away","formation":"4-4-2","roster":[]},
          {"homeAway":"home","formation":"4-3-3","uniform":{"color":"ff0000"},"roster":[
            {"starter":false,"jersey":"20","formationPlace":0,
             "athlete":{"id":"9","displayName":"Bench Guy"},
             "position":{"abbreviation":"M","displayName":"Midfielder"}},
            {"starter":true,"jersey":"9","formationPlace":10,"subbedOut":true,
             "subbedOutFor":{"athlete":{"id":"9","displayName":"Bench Guy"}},
             "athlete":{"id":"1","displayName":"Erling Haaland","jerseyImages":[
                {"href":"https://x/j.png","rel":["full"]},
                {"href":"https://x/j-dark.png","rel":["dark"]}]},
             "position":{"abbreviation":"F","displayName":"Forward"},
             "stats":[{"name":"totalGoals","displayValue":"2"},
                      {"name":"goalAssists","displayValue":"x"},
                      {"name":"yellowCards","displayValue":""}]},
            {"starter":true,"jersey":"1","formationPlace":1,
             "athlete":{"id":"2","displayName":"Ederson Moraes"},
             "position":{"abbreviation":"G","displayName":"Goalkeeper"}}
          ]}]}
        """;

    @Test
    void formationPicksRequestedSideAndSortsStartersByPlace() throws Exception {
        TeamFormationItem f = EspnSummaryParser.loadTeamFormation(json(ROSTERS), "home", "MCI");
        assertEquals("4-3-3", f.formation());
        assertEquals("#ff0000", f.kitColor());
        assertEquals("MCI", f.teamCode());
        assertEquals(List.of("Ederson Moraes", "Erling Haaland"),
                f.starters().stream().map(FormationPlayerItem::name).toList());
        assertEquals(1, f.bench().size());
    }

    @Test
    void formationPlayerDetails() throws Exception {
        TeamFormationItem f = EspnSummaryParser.loadTeamFormation(json(ROSTERS), "home", "MCI");
        FormationPlayerItem haaland = f.starters().get(1);
        assertEquals("Haaland", haaland.lastName());
        assertEquals("Haaland", haaland.shortName());
        assertEquals(2, haaland.goals());
        assertNull(haaland.assists());
        assertNull(haaland.yellowCards());
        assertEquals("9", haaland.subPartnerId());
        assertEquals("Bench Guy", haaland.subPartnerName());
        assertEquals("https://x/j-dark.png?width=128&height=128", haaland.jerseyImage());
    }

    @Test
    void formationIsNullForMissingSideOrRoot() throws Exception {
        assertNull(EspnSummaryParser.loadTeamFormation(null, "home", "MCI"));
        assertNull(EspnSummaryParser.loadTeamFormation(json("{}"), "home", "MCI"));
        assertNull(EspnSummaryParser.loadTeamFormation(json(ROSTERS), "neutral", "MCI"));
        assertNull(EspnSummaryParser.loadTeamFormation(
                json("{\"rosters\":[{\"homeAway\":\"home\",\"roster\":[]}]}"), "home", "MCI"));
    }

    @Test
    void pitchColorFallsBackWhenKitMissing() throws Exception {
        TeamFormationItem f = EspnSummaryParser.loadTeamFormation(json(ROSTERS), "home", "MCI");
        assertEquals("#ff0000", EspnSummaryParser.pitchColorFromFormation(f, "#fff"));
        assertEquals("#fff", EspnSummaryParser.pitchColorFromFormation(null, "#fff"));
    }

    @Test
    void lineupFromFormationKeepsStartersOnly() throws Exception {
        TeamFormationItem f = EspnSummaryParser.loadTeamFormation(json(ROSTERS), "home", "MCI");
        assertEquals(2, EspnSummaryParser.toLineupItemsFromFormation(f).size());
        assertTrue(EspnSummaryParser.toLineupItemsFromFormation(null).isEmpty());
    }

    @Test
    void liveEventsAreNewestFirstAndFilterNoise() throws Exception {
        JsonNode root = json("""
            {"commentary":[
              {"text":"Lineups are announced and players are warming up.","time":{"displayValue":""}},
              {"text":"Kick off","time":{"displayValue":"","value":0},"play":{"type":{"type":"kickoff"}}},
              {"text":"First","sequence":1,"time":{"displayValue":"5'","value":300},
               "play":{"period":{"number":1},"type":{"type":"shot"}}},
              {"text":"Goal!","sequence":2,"time":{"displayValue":"30'","value":1800},
               "play":{"period":{"number":1},"type":{"type":"goal"},"team":{"displayName":"City"},
                       "participants":[{"athlete":{"displayName":"Erling Haaland"}}],
                       "fieldPositionX":0.9}},
              {"text":"","time":{"displayValue":"31'"}}
            ]}
            """);
        List<MatchEventItem> events = EspnSummaryParser.loadLiveEvents(root);
        assertEquals(2, events.size());
        assertEquals("Goal!", events.get(0).text());
        assertEquals("goal", events.get(0).type());
        assertEquals("Erling Haaland", events.get(0).playerName());
        assertEquals("City", events.get(0).teamName());
        assertEquals(0.9, events.get(0).fieldX());
        assertEquals("First", events.get(1).text());
    }

    @Test
    void liveEventsCappedAt22() throws Exception {
        StringBuilder sb = new StringBuilder("{\"commentary\":[");
        for (int i = 0; i < 30; i++) {
            sb.append(i > 0 ? "," : "")
              .append("{\"text\":\"e").append(i).append("\",\"sequence\":").append(i)
              .append(",\"time\":{\"displayValue\":\"").append(i).append("'\",\"value\":").append(i * 60).append("}}");
        }
        sb.append("]}");
        List<MatchEventItem> events = EspnSummaryParser.loadLiveEvents(json(sb.toString()));
        assertEquals(22, events.size());
        assertEquals("e29", events.get(0).text());
    }

    @Test
    void liveEventsEmptyForMissingCommentary() throws Exception {
        assertTrue(EspnSummaryParser.loadLiveEvents(null).isEmpty());
        assertTrue(EspnSummaryParser.loadLiveEvents(json("{}")).isEmpty());
    }

    @Test
    void liveStatsUsesFallbackKeysAndPercentSuffix() throws Exception {
        JsonNode root = json("""
            {"boxscore":{"teams":[
              {"statistics":[{"name":"possessionPct","displayValue":"60"},
                             {"name":"shotsTotal","displayValue":"12"},
                             {"name":"shotsOnTarget","displayValue":"5"}]},
              {"statistics":[{"name":"possession","displayValue":"40%"},
                             {"name":"totalShots","value":8}]}
            ]}}
            """);
        List<MatchStatItem> stats = EspnSummaryParser.loadLiveStats(root);
        assertEquals(8, stats.size());
        assertEquals("60%", stats.get(0).homeValue());
        assertEquals("40%", stats.get(0).awayValue());
        assertEquals("12", stats.get(1).homeValue());
        assertEquals("8", stats.get(1).awayValue());
        assertEquals("5", stats.get(2).homeValue());
        assertEquals("—", stats.get(2).awayValue());
    }

    @Test
    void liveStatsEmptyWithoutTwoTeams() throws Exception {
        assertTrue(EspnSummaryParser.loadLiveStats(null).isEmpty());
        assertTrue(EspnSummaryParser.loadLiveStats(json("{\"boxscore\":{\"teams\":[{}]}}")).isEmpty());
        assertTrue(EspnSummaryParser.loadLiveStats(
                json("{\"boxscore\":{\"teams\":[{\"statistics\":[]},{\"statistics\":[]}]}}")).isEmpty());
    }
}
