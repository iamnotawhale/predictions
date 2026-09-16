package zhigalin.predictions.util;

public final class TeamCodeMapper {

    private TeamCodeMapper() {}

    /**
     * Map ESPN scoreboard abbreviations into our internal EPL codes.
     * ESPN uses {@code MUN} for Bayern and {@code MAN} for Manchester United —
     * only call this on raw ESPN abbreviations, not on already-stored internal codes.
     */
    public static String fromEspnAbbreviation(String espnCode) {
        if (espnCode == null) {
            return null;
        }
        return switch (espnCode) {
            case "AVL" -> "AST";
            case "BHA" -> "BRI";
            case "WHU" -> "WES";
            case "MNC" -> "MCI";
            case "NFO" -> "NOT";
            case "MAN" -> "MUN"; // Manchester United
            case "MUN" -> "BAY"; // Bayern Munich
            default -> espnCode;
        };
    }

    /** Alias map for known ESPN→internal EPL codes (safe on internal codes except do not remap MUN). */
    public static String toInternalCode(String espnCode) {
        if (espnCode == null) {
            return null;
        }
        return switch (espnCode) {
            case "AVL" -> "AST";
            case "BHA" -> "BRI";
            case "WHU" -> "WES";
            case "MNC" -> "MCI";
            case "NFO" -> "NOT";
            case "MAN" -> "MUN";
            default -> espnCode;
        };
    }
}
