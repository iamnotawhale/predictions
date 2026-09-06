package zhigalin.predictions.model.event;

public record MatchOdds(
        double home,
        double draw,
        double away,
        Double overUnder,
        Double homeTeamTotal,
        Double awayTeamTotal
) {
    public MatchOdds(double home, double draw, double away) {
        this(home, draw, away, null, null, null);
    }
}
