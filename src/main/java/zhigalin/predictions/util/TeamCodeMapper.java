package zhigalin.predictions.util;

public final class TeamCodeMapper {

    private TeamCodeMapper() {}

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
            case "MAN" -> "MUN"; // Manchester United
            case "MUN" -> "BAY"; // Bayern Munich (do not collide with MUN)
            default -> espnCode;
        };
    }
}
