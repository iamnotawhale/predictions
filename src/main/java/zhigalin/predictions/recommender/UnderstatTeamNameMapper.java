package zhigalin.predictions.recommender;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class UnderstatTeamNameMapper {

    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("arsenal", "ARS"),
            Map.entry("aston villa", "AST"),
            Map.entry("bournemouth", "BOU"),
            Map.entry("brentford", "BRE"),
            Map.entry("brighton", "BRI"),
            Map.entry("burnley", "BUR"),
            Map.entry("chelsea", "CHE"),
            Map.entry("coventry", "COV"),
            Map.entry("coventry city", "COV"),
            Map.entry("crystal palace", "CRY"),
            Map.entry("everton", "EVE"),
            Map.entry("fulham", "FUL"),
            Map.entry("hull city", "HUL"),
            Map.entry("ipswich town", "IPS"),
            Map.entry("leeds", "LEE"),
            Map.entry("leeds united", "LEE"),
            Map.entry("liverpool", "LIV"),
            Map.entry("manchester city", "MCI"),
            Map.entry("manchester united", "MUN"),
            Map.entry("newcastle united", "NEW"),
            Map.entry("nottingham forest", "NOT"),
            Map.entry("sunderland", "SUN"),
            Map.entry("tottenham", "TOT"),
            Map.entry("west ham", "WES"),
            Map.entry("wolverhampton wanderers", "WOL"),
            Map.entry("wolves", "WOL")
    );

    private UnderstatTeamNameMapper() {
    }

    public static Optional<String> toTeamCode(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawName
                .replace('\u00a0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return Optional.ofNullable(EXACT.get(normalized));
    }
}
