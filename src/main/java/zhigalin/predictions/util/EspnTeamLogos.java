package zhigalin.predictions.util;

import java.util.Map;

/**
 * ESPN soccer team logo CDN for current EPL clubs.
 * URL pattern: {@code https://a.espncdn.com/i/teamlogos/soccer/500/{espnTeamId}.png}
 */
public final class EspnTeamLogos {

    private static final String CDN = "https://a.espncdn.com/i/teamlogos/soccer/500/%d.png";

    /** Internal EPL code → ESPN team id (eng.1). */
    private static final Map<String, Integer> ESPN_ID_BY_CODE = Map.ofEntries(
            Map.entry("ARS", 359),
            Map.entry("AST", 362),
            Map.entry("BOU", 349),
            Map.entry("BRE", 337),
            Map.entry("BRI", 331),
            Map.entry("CHE", 363),
            Map.entry("COV", 388),
            Map.entry("CRY", 384),
            Map.entry("EVE", 368),
            Map.entry("FUL", 370),
            Map.entry("HUL", 306),
            Map.entry("IPS", 373),
            Map.entry("LEE", 357),
            Map.entry("LIV", 364),
            Map.entry("MCI", 382),
            Map.entry("MUN", 360),
            Map.entry("NEW", 361),
            Map.entry("NOT", 393),
            Map.entry("SUN", 366),
            Map.entry("TOT", 367),
            Map.entry("WES", 371)
    );

    private EspnTeamLogos() {}

    public static Integer espnTeamId(String internalCode) {
        if (internalCode == null || internalCode.isBlank()) {
            return null;
        }
        return ESPN_ID_BY_CODE.get(internalCode.trim().toUpperCase());
    }

    public static String logoUrl(String internalCode) {
        Integer id = espnTeamId(internalCode);
        return id == null ? null : CDN.formatted(id);
    }

    public static Map<String, Integer> allEspnIds() {
        return ESPN_ID_BY_CODE;
    }
}
