package zhigalin.predictions.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;

class WidgetUpdateDeserializeTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void messageUpdateKeepsChatIdAndText() throws Exception {
        String json = """
                {
                  "update_id": 10,
                  "message": {
                    "message_id": 1,
                    "date": 1,
                    "chat": {"id": 42, "type": "private"},
                    "from": {"id": 42, "is_bot": false, "first_name": "N"},
                    "text": "/today"
                  }
                }
                """;
        Update update = mapper.readValue(json, Update.class);
        assertTrue(update.hasMessage());
        assertEquals(42L, update.getMessage().getChatId());
        assertEquals("/today", update.getMessage().getText());
    }

    @Test
    void callbackUpdateKeepsChatIdAndData() throws Exception {
        String json = """
                {
                  "update_id": 11,
                  "callback_query": {
                    "id": "cb1",
                    "from": {"id": 42, "is_bot": false, "first_name": "N"},
                    "chat_instance": "1",
                    "data": "/notpred ARS 2 CHE 1",
                    "message": {
                      "message_id": 9,
                      "date": 1,
                      "chat": {"id": 42, "type": "private"},
                      "text": "x"
                    }
                  }
                }
                """;
        Update update = mapper.readValue(json, Update.class);
        assertTrue(update.hasCallbackQuery());
        assertNotNull(update.getCallbackQuery().getMessage());
        assertEquals(42L, update.getCallbackQuery().getMessage().getChatId());
        assertEquals("/notpred ARS 2 CHE 1", update.getCallbackQuery().getData());
    }
}
