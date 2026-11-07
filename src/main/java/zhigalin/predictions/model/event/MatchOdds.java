package zhigalin.predictions.model.event;

public record MatchOdds(
        double home,
        double draw,
        double away,
        Double overUnder,
        Double homeTotal,
        Double awayTotal
) {
    public MatchOdds(double home, double draw, double away) {
        this(home, draw, away, null, null, null);
    }
}
