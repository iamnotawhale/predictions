package zhigalin.predictions.recommender;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class FootyStatsTeamNameMapper {

    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("arsenal fc", "ARS"),
            Map.entry("aston villa fc", "AST"),
            Map.entry("afc bournemouth", "BOU"),
            Map.entry("brentford fc", "BRE"),
            Map.entry("brighton & hove albion fc", "BRI"),
            Map.entry("chelsea fc", "CHE"),
            Map.entry("coventry city fc", "COV"),
            Map.entry("crystal palace fc", "CRY"),
            Map.entry("everton fc", "EVE"),
            Map.entry("fulham fc", "FUL"),
            Map.entry("hull city afc", "HUL"),
            Map.entry("ipswich town fc", "IPS"),
            Map.entry("leeds united fc", "LEE"),
            Map.entry("liverpool fc", "LIV"),
            Map.entry("manchester city fc", "MAC"),
            Map.entry("manchester united fc", "MUN"),
            Map.entry("newcastle united fc", "NEW"),
            Map.entry("nottingham forest fc", "NOT"),
            Map.entry("sunderland afc", "SUN"),
            Map.entry("tottenham hotspur fc", "TOT"),
            Map.entry("west ham united fc", "WES"),
            Map.entry("wolverhampton wanderers fc", "WOL"),
            Map.entry("burnley fc", "BUR"),
            Map.entry("sheffield united fc", "SHU"),
            Map.entry("luton town fc", "LUT")
    );

    private FootyStatsTeamNameMapper() {
    }

    public static Optional<String> toTeamCode(String footyStatsName) {
        if (footyStatsName == null || footyStatsName.isBlank()) {
            return Optional.empty();
        }
        String normalized = footyStatsName.trim().toLowerCase(Locale.ROOT);
        String exact = EXACT.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        for (Map.Entry<String, String> entry : EXACT.entrySet()) {
            if (normalized.startsWith(entry.getKey()) || entry.getKey().startsWith(normalized)) {
                return Optional.of(entry.getValue());
            }
            String club = entry.getKey().replace(" fc", "");
            if (normalized.equals(club) || normalized.startsWith(club + " ")) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
