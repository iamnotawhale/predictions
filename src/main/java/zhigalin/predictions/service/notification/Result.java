package zhigalin.predictions.service.notification;

public record Result(String login, String predict, int point, boolean weekBonus) {
    public Result(String login, String predict, int point) {
        this(login, predict, point, false);
    }
}
