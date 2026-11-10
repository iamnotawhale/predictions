package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReminderWidgetHtmlTest {

    private static final String FH_FULL =
            "W1-0@HUL@H@07.11,W0-1@LIV@A@01.11,D1-1@MCI@H@05.10,W0-3@BHA@A@21.09,W1-0@NFO@H@14.09";
    private static final String FA_FULL =
            "W1-0@ARS@H@12.10,L0-1@TOT@A@05.10,W1-3@EVE@H@28.09,D2-2@WHU@A@21.09,L2-1@AVL@H@14.09";
    private static final String H2H_FULL = "L2:1@LIV@ARS@EPL@24/25|D0:0@ARS@CHE@FA@22/23|W1:3@CHE@ARS@UCL@21/22|D1:1@ARS@CHE@EPL@21/22|L0:2@CHE@ARS@LC@21/22|W2:0@ARS@CHE@EPL@20/21|L1:2@CHE@ARS@EPL@20/21|D2:2@ARS@CHE@EPL@19/20|W3:1@CHE@ARS@FA@18/19|L0:1@ARS@CHE@EPL@18/19";
    private static final String LH =
            "1.Raya@1|4.Saliba@5|2.White@2|6.Gabriel@6|12.Timber@3|41.Rice@4|8.Odegaard@10|7.Saka@7|11.Martinelli@11|19.Trossard@8|29.Havertz@9";
    private static final String LA =
            "1.Sanchez@1|24.James@2|6.Silva@5|26.Colwill@6|3.Cucurella@3|25.Caicedo@8|8.Fernandez@4|20.Palmer@10|11.Madueke@7|15.Jackson@9|7.Neto@11";

    @Test
    void htmlIsIndependentOfMinutesAndUsesChatData() {
        String html = ReminderWidgetHtml.html();
        assertEquals(html, ReminderWidgetHtml.html());
        assertEquals(html, ReminderWidgetHtml.htmlForTeams("ARS", "CHE", 33, 40));
        assertTrue(html.contains("TelegramWidget.chatData"));
        assertTrue(html.contains("chatDataChanged"));
        assertTrue(html.contains("TelegramWidget.request"));
        assertTrue(html.contains("Сделать прогноз"));
        assertTrue(html.contains("Изменить прогноз"));
        assertTrue(html.contains("Твой прогноз"));
        assertFalse(html.contains("height:512px"));
        assertTrue(html.contains("min-height:380px"));
        assertTrue(html.contains("AI "));
        assertTrue(html.contains("last 5"));
        assertTrue(html.contains("h2h"));
        assertTrue(html.contains("нет встреч"));
        assertTrue(html.contains("lineups"));
        assertTrue(html.contains("нет составов"));
        assertTrue(html.contains("class=\"pitch"));
        assertTrue(html.contains("fdet"));
        assertTrue(html.contains("h2hd"));
        assertFalse(html.contains("id=\"tbl1\""));
        assertFalse(html.contains("data:image"));
        assertFalse(html.contains("\"m\":60"));
        String script = html.substring(html.indexOf("<script>"), html.indexOf("</script>"));
        assertFalse(script.contains("</span"), "closing tags inside script break Telegram widget HTML");
        assertFalse(script.contains("</div"), "closing tags inside script break Telegram widget HTML");
        assertFalse(script.contains("</b>"), "closing tags inside script break Telegram widget HTML");
    }

    @Test
    void htmlFitsTelegramLimits() {
        String html = ReminderWidgetHtml.html();
        byte[] raw = ReminderWidgetHtml.utf8(html);
        assertTrue(raw.length <= ReminderWidgetHtml.MAX_HTML_BYTES, "raw=" + raw.length);
        byte[] zlib = ReminderWidgetHtml.zlib9(raw);
        assertTrue(zlib.length <= ReminderWidgetHtml.MAX_ZLIB_BYTES, "zlib=" + zlib.length);
    }

    @Test
    void chatDataFitsAndCarriesFields() {
        String json = ReminderWidgetHtml.chatDataJson(
                "ARS", "CHE", "18:30", 40, true, 1557474, "1.83/3.7/3.95", "2:1",
                "2:1", H2H_FULL, FH_FULL, FA_FULL, 3, 8, LH, LA, "4-3-3", "4-2-3-1");
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= ReminderWidgetHtml.MAX_CHAT_DATA_BYTES,
                "chat_data=" + json.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(json.contains("\"mid\":1557474"));
        assertTrue(json.contains("\"tip\":\"2:1\""));
        assertTrue(json.contains("\"h2h\":\"" + H2H_FULL + "\""));
        assertTrue(json.contains("\"fh\":\"" + FH_FULL + "\""));
        assertTrue(json.contains("\"th\":3"));
        assertTrue(json.contains("\"p\":\"2:1\""));
        assertTrue(json.contains("\"lh\":\"" + LH + "\""));
        assertTrue(json.contains("\"la\":\"" + LA + "\""));
        assertTrue(json.contains("\"hf\":\"4-3-3\""));
        assertTrue(json.contains("\"af\":\"4-2-3-1\""));
    }
}
