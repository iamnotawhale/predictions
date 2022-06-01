package zhigalin.predictions.telegram.command;

public enum TeamName {

    ARS("/arsenal"),
    AST("/astonvilla"),
    BRE("/brentford"),
    BRI("/brightonbha"),
    BUR("/burnley"),
    CHE("/chelseacfc"),
    CRY("/crystalpalace"),
    EVE("/everton"),
    LEE("/leedsunited"),
    LEI("/leicester"),
    LIV("/liverpoollfc"),
    MAC("/mcmncmac"),
    MUN("/munmnu"),
    NEW("/newcastle"),
    NOR("/norwich"),
    SOU("/southamptonsot"),
    TOT("/tottenham"),
    WAT("/watford"),
    WES("/westhamwhu"),
    WOL("/wolverhampton");

    private final String teamName;

    TeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamName() {
        return teamName;
    }
}
