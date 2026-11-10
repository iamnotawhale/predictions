package zhigalin.predictions.service.notification;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reminder / your-predict live widget. Team identity = 3-letter codes (no crest
 * images — zlib budget). Stats: APL place, form, H2H, horizontal pitch lineups; AI tip on grid.
 * Height follows content ({@code sendWidget} without fixed {@code height}); capped by Telegram 50–600.
 */
public final class ReminderWidgetHtml {

    public static final int MAX_HTML_BYTES = 32_768;
    public static final int MAX_ZLIB_BYTES = 4096;
    public static final int MAX_CHAT_DATA_BYTES = 1024;
    public static final int SCORE_MAX = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HTML = loadHtml();

    private static String loadHtml() {
        try (var in = ReminderWidgetHtml.class.getResourceAsStream("/widget/reminder.html")) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource /widget/reminder.html");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to read /widget/reminder.html", e);
        }
    }

    private ReminderWidgetHtml() {}

    public static String html() {
        return HTML;
    }

    /** Team codes come from chat_data; crest images are not embedded. */
    public static String htmlForTeams(String homeCode, String awayCode, int homeTeamId, int awayTeamId) {
        return HTML;
    }

    public static boolean fitsZlib(String html) {
        byte[] raw = utf8(html);
        if (raw.length > MAX_HTML_BYTES) {
            return false;
        }
        return zlib9(raw).length <= MAX_ZLIB_BYTES;
    }

    public static String chatDataJson(
            String homeCode,
            String awayCode,
            String kickoff,
            long minutesLeft,
            boolean weekBonus,
            int matchId,
            String oddsSlash,
            String predictedScore,
            String tip,
            String h2h,
            String formHome,
            String formAway,
            Integer tableHome,
            Integer tableAway,
            String lineupHome,
            String lineupAway
    ) {
        return chatDataJson(
                homeCode, awayCode, kickoff, minutesLeft, weekBonus, matchId, oddsSlash,
                predictedScore, tip, h2h, formHome, formAway, tableHome, tableAway,
                lineupHome, lineupAway, null, null);
    }

    public static String chatDataJson(
            String homeCode,
            String awayCode,
            String kickoff,
            long minutesLeft,
            boolean weekBonus,
            int matchId,
            String oddsSlash,
            String predictedScore,
            String tip,
            String h2h,
            String formHome,
            String formAway,
            Integer tableHome,
            Integer tableAway,
            String lineupHome,
            String lineupAway,
            String homeFormation,
            String awayFormation
    ) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("h", homeCode != null ? homeCode : "?");
        node.put("a", awayCode != null ? awayCode : "?");
        node.put("k", kickoff != null ? kickoff : "--:--");
        node.put("m", minutesLeft);
        node.put("b", weekBonus ? 1 : 0);
        node.put("mid", matchId);
        if (oddsSlash != null && !oddsSlash.isBlank()) {
            node.put("o", oddsSlash);
        }
        if (predictedScore != null && !predictedScore.isBlank()) {
            node.put("p", predictedScore);
        }
        if (tip != null && !tip.isBlank()) {
            node.put("tip", tip);
        }
        if (h2h != null && !h2h.isBlank()) {
            node.put("h2h", h2h);
        }
        if (formHome != null && !formHome.isBlank()) {
            node.put("fh", formHome);
        }
        if (formAway != null && !formAway.isBlank()) {
            node.put("fa", formAway);
        }
        if (tableHome != null) {
            node.put("th", tableHome);
        }
        if (tableAway != null) {
            node.put("ta", tableAway);
        }
        if (lineupHome != null && !lineupHome.isBlank()) {
            node.put("lh", lineupHome);
        }
        if (lineupAway != null && !lineupAway.isBlank()) {
            node.put("la", lineupAway);
        }
        if (homeFormation != null && !homeFormation.isBlank()) {
            node.put("hf", homeFormation);
        }
        if (awayFormation != null && !awayFormation.isBlank()) {
            node.put("af", awayFormation);
        }
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] zlib9(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            out.write(buf, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }
}
