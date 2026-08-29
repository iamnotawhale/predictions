package zhigalin.predictions.recommender;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps SoccerSTATS.com team labels to internal Premier League codes.
 */
public final class SoccerStatsTeamNameMapper {

    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("arsenal", "ARS"),
            Map.entry("aston villa", "AST"),
            Map.entry("bournemouth", "BOU"),
            Map.entry("brentford", "BRE"),
            Map.entry("brighton", "BRI"),
            Map.entry("chelsea", "CHE"),
            Map.entry("coventry city", "COV"),
            Map.entry("crystal palace", "CRY"),
            Map.entry("crystal pala.", "CRY"),
            Map.entry("everton", "EVE"),
            Map.entry("fulham", "FUL"),
            Map.entry("hull city", "HUL"),
            Map.entry("ipswich town", "IPS"),
            Map.entry("leeds utd", "LEE"),
            Map.entry("leeds united", "LEE"),
            Map.entry("liverpool", "LIV"),
            Map.entry("manchester city", "MAC"),
            Map.entry("manchester c.", "MAC"),
            Map.entry("manchester utd", "MUN"),
            Map.entry("manchester u.", "MUN"),
            Map.entry("manchester united", "MUN"),
            Map.entry("newcastle utd", "NEW"),
            Map.entry("newcastle united", "NEW"),
            Map.entry("nottm forest", "NOT"),
            Map.entry("nottingham forest", "NOT"),
            Map.entry("sunderland", "SUN"),
            Map.entry("tottenham", "TOT"),
            Map.entry("west ham", "WES"),
            Map.entry("wolverhampton", "WOL"),
            Map.entry("wolves", "WOL"),
            Map.entry("burnley", "BUR"),
            Map.entry("sheffield utd", "SHU"),
            Map.entry("luton town", "LUT")
    );

    private SoccerStatsTeamNameMapper() {
    }

    public static Optional<String> toTeamCode(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawName
                .replace('\u00a0', ' ')
                .replace("&nbsp;", " ")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        String exact = EXACT.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        for (Map.Entry<String, String> entry : EXACT.entrySet()) {
            if (normalized.startsWith(entry.getKey()) || entry.getKey().startsWith(normalized)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
