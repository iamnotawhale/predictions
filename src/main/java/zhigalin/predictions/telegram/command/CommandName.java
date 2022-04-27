package zhigalin.predictions.telegram.command;

public enum CommandName {

    START("/start"),
    STOP("/stop"),
    HELP("/help"),
    NO("nocommand"),
    TABLE("/table"),
    TOUR("/tours"),
    TOURNUM("/tour"),
    TODAY("/today"),

    TEAM("/team");


    private final String commandName;

    CommandName(String commandName) {
        this.commandName = commandName;
    }

    public String getCommandName() {
        return commandName;
    }
}
