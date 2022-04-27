package zhigalin.predictions.telegram.command;

public enum TeamName {

    ARS("/arsenal"),
    VIL("/astonvilla"),
    BFC("/brentfordBFC"),
    BHA("/brightonBHA"),
    BUR("/burnley"),
    CFC("/chelseaCFC"),
    PAL("/crystalpalace"),
    EVE("/everton"),
    LU("/leedsunitedLU"),
    LEI("/leicester"),
    LFC("/liverpoolLFC"),
    MNC("/mcMNC"),
    MNU("/muMNU"),
    NEW("/newcastle"),
    NOR("/norwich"),
    SOT("/southamptonSOT"),
    TOT("/tottenham"),
    WAT("/watford"),
    WHU("/westhamWHU"),
    WOL("/wolverhampton");

    private final String teamName;

    TeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamName() {
        return teamName;
    }
}
