package zhigalin.predictions.telegram.command;

public enum TeamName {

    ARS("/arsenal"),
    AST("/astonvilla"),
    BOU("/bournemouth"),
    BRE("/brentford"),
    BRI("/brightonbha"),
    BUR("/burnley"),
    CHE("/chelseacfc"),
    CRY("/crystalpalace"),
    EVE("/everton"),
    LEE("/leedsunited"),
    LEI("/leicester"),
    LIV("/liverpoollfc"),
    LUT("/luton"),
    MAC("/mcmncmac"),
    MUN("/munmnu"),
    NEW("/newcastle"),
    NOR("/norwich"),
    NOT("/nottingham"),
    SHE("/sheffield"),
    SOU("/southamptonsot"),
    TOT("/tottenham"),
    WAT("/watford"),
    WES("/westhamwhu"),
    WOL("/wolverhampton"),
    FUL("/fulham"),
    IPS("/ipswich"),
    SUN("/sunderlend");

    private final String name;

    TeamName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
