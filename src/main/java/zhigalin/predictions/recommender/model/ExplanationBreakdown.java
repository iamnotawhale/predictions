package zhigalin.predictions.recommender.model;

import java.util.ArrayList;
import java.util.List;

public record ExplanationBreakdown(
        List<ExplanationRow> rows,
        List<String> notes,
        ScoreDistribution distribution,
        String analysis,
        ModelScore modelScore
) {
    public record ModelScore(
            int home,
            int away,
            double probability,
            double homeWin,
            double draw,
            double awayWin
    ) {
    }

    public ExplanationBreakdown {
        rows = rows != null ? List.copyOf(rows) : List.of();
        notes = notes != null ? List.copyOf(notes) : List.of();
    }

    public ExplanationBreakdown(List<ExplanationRow> rows, List<String> notes) {
        this(rows, notes, null, null, null);
    }

    public ExplanationBreakdown(List<ExplanationRow> rows, List<String> notes, ScoreDistribution distribution) {
        this(rows, notes, distribution, null, null);
    }

    public ExplanationBreakdown(
            List<ExplanationRow> rows,
            List<String> notes,
            ScoreDistribution distribution,
            String analysis
    ) {
        this(rows, notes, distribution, analysis, null);
    }

    public static ExplanationBreakdown empty() {
        return new ExplanationBreakdown(List.of(), List.of(), null, null, null);
    }

    public ExplanationBreakdown withDistribution(ScoreDistribution distribution) {
        return new ExplanationBreakdown(rows, notes, distribution, analysis, modelScore);
    }

    public ExplanationBreakdown withAnalysis(String analysis) {
        return new ExplanationBreakdown(rows, notes, distribution, analysis, modelScore);
    }

    public ExplanationBreakdown withModelScore(ModelScore modelScore) {
        return new ExplanationBreakdown(rows, notes, distribution, analysis, modelScore);
    }

    public List<String> asLines() {
        List<String> lines = new ArrayList<>();
        for (ExplanationRow row : rows) {
            if (row == null || row.metric() == null) {
                continue;
            }
            String home = row.home() != null ? row.home() : "—";
            String away = row.away() != null ? row.away() : "—";
            lines.add(row.metric() + ": " + home + " / " + away);
        }
        lines.addAll(notes);
        return lines;
    }
}
