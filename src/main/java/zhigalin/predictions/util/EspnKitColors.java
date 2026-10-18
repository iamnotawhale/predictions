package zhigalin.predictions.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;

public final class EspnKitColors {

    private static final Pattern HEX = Pattern.compile("[0-9A-Fa-f]{6}");

    public record Kit(String color, String alternateColor) {
    }

    private EspnKitColors() {
    }

    public static Kit load(JsonNode summary, String side) {
        if (summary == null) {
            return null;
        }
        for (JsonNode roster : summary.path("rosters")) {
            if (!side.equalsIgnoreCase(roster.path("homeAway").asText(""))) {
                continue;
            }
            JsonNode uniform = roster.path("uniform");
            String color = hex(uniform.path("color").asText(""));
            if (color == null) {
                return null;
            }
            return new Kit(color, hex(uniform.path("alternateColor").asText("")));
        }
        return null;
    }

    private static String hex(String raw) {
        String value = raw == null ? "" : raw.trim();
        return HEX.matcher(value).matches() ? "#" + value.toLowerCase() : null;
    }
}
