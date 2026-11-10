package zhigalin.predictions.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Player;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.notification.ReminderWidgetFactory;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

@ExtendWith(MockitoExtension.class)
class ReminderWidgetQueryHandlerTest {

    @Mock ApiClient api;
    @Mock MatchService matchService;
    @Mock PredictionService predictionService;
    @Mock UserService userService;
    @Mock ReminderWidgetFactory reminderWidgetFactory;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clearCache() {
        ReminderWidgetFactory.clearLineupsCache();
    }

    @Test
    void nestedMessageCoordinatesSaveAndEditWithP() throws Exception {
        ReminderWidgetQueryHandler handler = new ReminderWidgetQueryHandler(
                api, matchService, predictionService, userService, reminderWidgetFactory);

        User user = User.builder().id(7).telegramId("42").login("NIK").build();
        Match match = Match.builder()
                .id(1557474)
                .homeTeamId(1)
                .awayTeamId(2)
                .weekId(11)
                .status("ns")
                .startsAt(LocalDateTime.now().plusMinutes(40))
                .build();
        when(userService.findByTelegramId("42")).thenReturn(user);
        when(matchService.findById(1557474)).thenReturn(match);
        when(predictionService.findByMatchIdAndUserId(1557474, 7)).thenReturn(
                Prediction.builder().userId(7).matchId(1557474).homeScore(2).awayScore(1).build());
        when(api.getEspnLineups(isNull(), anyInt(), anyInt())).thenReturn(ApiClient.EspnLineups.empty());
        when(api.getLineups(1557474)).thenReturn(Map.of());
        when(reminderWidgetFactory.build(
                eq(match), eq(7), anyLong(), anyString(), eq("2:1"), any(), isNull(), isNull()))
                .thenReturn(new ReminderWidgetFactory.Payload(
                        "<html/>",
                        "{\"h\":\"ARS\",\"a\":\"CHE\",\"mid\":1557474,\"p\":\"2:1\"}"));
        when(api.editMessageWidgetData(eq("42"), eq(99), anyString())).thenReturn(true);

        var query = mapper.readTree("""
                {
                  "id": "wq1",
                  "from": {"id": 42, "is_bot": false, "first_name": "N"},
                  "data": "s:1557474:2:1",
                  "message": {
                    "message_id": 99,
                    "chat": {"id": 42, "type": "private"}
                  }
                }
                """);
        handler.handle(query);

        verify(api).answerWidgetQuery("wq1", "ok");
        ArgumentCaptor<String> chatData = ArgumentCaptor.forClass(String.class);
        verify(api).editMessageWidgetData(eq("42"), eq(99), chatData.capture());
        assertTrue(chatData.getValue().contains("\"p\":\"2:1\""));
        assertTrue(chatData.getValue().contains("\"mid\":1557474"));
    }

    @Test
    void emptyEspnUsesCachedLineupsForSameMessage() throws Exception {
        Player player = new Player();
        player.setName("Saka");
        player.setPos("F");
        player.setNumber(7);
        player.setFormationPlace(7);
        Lineup row = new Lineup();
        row.setPlayer(player);
        ReminderWidgetFactory.rememberLineups(
                "42", 99, Map.of(1, List.of(row)), "4-3-3", "4-2-3-1");

        ReminderWidgetQueryHandler handler = new ReminderWidgetQueryHandler(
                api, matchService, predictionService, userService, reminderWidgetFactory);

        User user = User.builder().id(7).telegramId("42").login("NIK").build();
        Match match = Match.builder()
                .id(1557474)
                .homeTeamId(1)
                .awayTeamId(2)
                .weekId(11)
                .status("ns")
                .startsAt(LocalDateTime.now().plusMinutes(40))
                .build();
        when(userService.findByTelegramId("42")).thenReturn(user);
        when(matchService.findById(1557474)).thenReturn(match);
        when(predictionService.findByMatchIdAndUserId(1557474, 7)).thenReturn(
                Prediction.builder().userId(7).matchId(1557474).homeScore(1).awayScore(0).build());
        when(api.getEspnLineups(isNull(), anyInt(), anyInt())).thenReturn(ApiClient.EspnLineups.empty());
        when(api.getLineups(1557474)).thenReturn(Map.of());
        when(reminderWidgetFactory.build(
                eq(match), eq(7), anyLong(), anyString(), eq("1:0"), any(), eq("4-3-3"), eq("4-2-3-1")))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Map<Integer, List<Lineup>> passed = inv.getArgument(5);
                    assertEquals("Saka", passed.get(1).get(0).getPlayer().getName());
                    return new ReminderWidgetFactory.Payload(
                            "<html/>",
                            "{\"h\":\"ARS\",\"a\":\"CHE\",\"mid\":1557474,\"p\":\"1:0\",\"lh\":\"7.Saka@7\"}");
                });
        when(api.editMessageWidgetData(eq("42"), eq(99), anyString())).thenReturn(true);

        var query = mapper.readTree("""
                {
                  "id": "wq2",
                  "from": {"id": 42, "is_bot": false, "first_name": "N"},
                  "data": "s:1557474:1:0",
                  "message": {
                    "message_id": 99,
                    "chat": {"id": 42, "type": "private"}
                  }
                }
                """);
        handler.handle(query);

        verify(reminderWidgetFactory).build(
                eq(match), eq(7), anyLong(), anyString(), eq("1:0"), any(), eq("4-3-3"), eq("4-2-3-1"));
    }

    @Test
    void emptyTeamListsStillUseCachedLineups() throws Exception {
        Player player = new Player();
        player.setName("Saka");
        player.setPos("F");
        player.setNumber(7);
        player.setFormationPlace(7);
        Lineup row = new Lineup();
        row.setPlayer(player);
        ReminderWidgetFactory.rememberLineups(
                "42", 99, Map.of(1, List.of(row)), "4-3-3", "4-2-3-1");

        ReminderWidgetQueryHandler handler = new ReminderWidgetQueryHandler(
                api, matchService, predictionService, userService, reminderWidgetFactory);

        User user = User.builder().id(7).telegramId("42").login("NIK").build();
        Match match = Match.builder()
                .id(1557474)
                .homeTeamId(1)
                .awayTeamId(2)
                .weekId(11)
                .status("ns")
                .startsAt(LocalDateTime.now().plusMinutes(40))
                .build();
        when(userService.findByTelegramId("42")).thenReturn(user);
        when(matchService.findById(1557474)).thenReturn(match);
        when(predictionService.findByMatchIdAndUserId(1557474, 7)).thenReturn(
                Prediction.builder().userId(7).matchId(1557474).homeScore(1).awayScore(0).build());
        when(api.getEspnLineups(isNull(), anyInt(), anyInt())).thenReturn(ApiClient.EspnLineups.empty());
        when(api.getLineups(1557474)).thenReturn(Map.of(1, List.of(), 2, List.of()));
        when(reminderWidgetFactory.build(
                eq(match), eq(7), anyLong(), anyString(), eq("1:0"), any(), eq("4-3-3"), eq("4-2-3-1")))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Map<Integer, List<Lineup>> passed = inv.getArgument(5);
                    assertEquals("Saka", passed.get(1).get(0).getPlayer().getName());
                    return new ReminderWidgetFactory.Payload(
                            "<html/>",
                            "{\"h\":\"ARS\",\"a\":\"CHE\",\"mid\":1557474,\"p\":\"1:0\",\"lh\":\"7.Saka@7\"}");
                });
        when(api.editMessageWidgetData(eq("42"), eq(99), anyString())).thenReturn(true);

        var query = mapper.readTree("""
                {
                  "id": "wq4",
                  "from": {"id": 42, "is_bot": false, "first_name": "N"},
                  "data": "s:1557474:1:0",
                  "message": {
                    "message_id": 99,
                    "chat": {"id": 42, "type": "private"}
                  }
                }
                """);
        handler.handle(query);

        verify(reminderWidgetFactory).build(
                eq(match), eq(7), anyLong(), anyString(), eq("1:0"), any(), eq("4-3-3"), eq("4-2-3-1"));
    }

    @Test
    void emptyEspnIgnoresCacheForOtherMessage() throws Exception {
        Player player = new Player();
        player.setName("Saka");
        player.setPos("F");
        player.setNumber(7);
        player.setFormationPlace(7);
        Lineup row = new Lineup();
        row.setPlayer(player);
        ReminderWidgetFactory.rememberLineups(
                "42", 88, Map.of(1, List.of(row)), "4-3-3", "4-2-3-1");

        ReminderWidgetQueryHandler handler = new ReminderWidgetQueryHandler(
                api, matchService, predictionService, userService, reminderWidgetFactory);

        User user = User.builder().id(7).telegramId("42").login("NIK").build();
        Match match = Match.builder()
                .id(1557474)
                .homeTeamId(1)
                .awayTeamId(2)
                .weekId(11)
                .status("ns")
                .startsAt(LocalDateTime.now().plusMinutes(40))
                .build();
        when(userService.findByTelegramId("42")).thenReturn(user);
        when(matchService.findById(1557474)).thenReturn(match);
        when(predictionService.findByMatchIdAndUserId(1557474, 7)).thenReturn(
                Prediction.builder().userId(7).matchId(1557474).homeScore(1).awayScore(0).build());
        when(api.getEspnLineups(isNull(), anyInt(), anyInt())).thenReturn(ApiClient.EspnLineups.empty());
        when(api.getLineups(1557474)).thenReturn(Map.of());
        when(reminderWidgetFactory.build(
                eq(match), eq(7), anyLong(), anyString(), eq("1:0"), any(), isNull(), isNull()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Map<Integer, List<Lineup>> passed = inv.getArgument(5);
                    assertTrue(passed == null || passed.isEmpty());
                    return new ReminderWidgetFactory.Payload(
                            "<html/>",
                            "{\"h\":\"ARS\",\"a\":\"CHE\",\"mid\":1557474,\"p\":\"1:0\"}");
                });
        when(api.editMessageWidgetData(eq("42"), eq(99), anyString())).thenReturn(true);

        var query = mapper.readTree("""
                {
                  "id": "wq3",
                  "from": {"id": 42, "is_bot": false, "first_name": "N"},
                  "data": "s:1557474:1:0",
                  "message": {
                    "message_id": 99,
                    "chat": {"id": 42, "type": "private"}
                  }
                }
                """);
        handler.handle(query);

        verify(reminderWidgetFactory).build(
                eq(match), eq(7), anyLong(), anyString(), eq("1:0"), any(), isNull(), isNull());
    }
}
