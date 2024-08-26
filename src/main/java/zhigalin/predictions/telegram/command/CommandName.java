package zhigalin.predictions.telegram.command;

public enum CommandName {

    START("start"),
    STOP("stop"),
    HELP("help"),
    NO("nocommand"),
    TABLE("table"),
    TOUR("tours"),
    TOUR_NUM("tour"),
    TODAY("today"),
    TEAM("team"),
    UPCOMING("upcoming"),
    NEWS("news"),
    UPDATE("update"),
    PRED("pred"),
    REFRESH("refresh"),
    TOTAL("total"),
    PREDICTS("predicts"),;

    private final String name;

    CommandName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
