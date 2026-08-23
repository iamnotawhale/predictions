package zhigalin.predictions.util;

public final class TeamCodeMapper {

    private TeamCodeMapper() {}

    public static String toInternalCode(String espnCode) {
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
