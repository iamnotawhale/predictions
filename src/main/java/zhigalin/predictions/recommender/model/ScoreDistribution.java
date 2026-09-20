package zhigalin.predictions.recommender.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Normalized 0–5 score probability grid plus derived markets (calculator-style).
 */
public record ScoreDistribution(
        List<List<Double>> matrix,
        double homeWin,
        double draw,
        double awayWin,
        double btts,
        double over15,
        double over25,
        double over35,
        List<TopScore> topScores
) {
    public ScoreDistribution {
        if (matrix == null) {
            matrix = List.of();
        } else {
            List<List<Double>> copy = new ArrayList<>(matrix.size());
            for (List<Double> row : matrix) {
                copy.add(row == null ? List.of() : List.copyOf(row));
            }
            matrix = List.copyOf(copy);
        }
        topScores = topScores != null ? List.copyOf(topScores) : List.of();
    }

    public record TopScore(int home, int away, double probability) {
    }

    public static ScoreDistribution fromNormalizedMatrix(double[][] matrix) {
        if (matrix == null || matrix.length == 0) {
            return empty();
        }
        double homeWin = 0;
        double draw = 0;
        double awayWin = 0;
        double btts = 0;
        double over15 = 0;
        double over25 = 0;
        double over35 = 0;
        List<List<Double>> rows = new ArrayList<>(matrix.length);
        List<TopScore> tops = new ArrayList<>();
        for (int h = 0; h < matrix.length; h++) {
            List<Double> row = new ArrayList<>(matrix[h].length);
            for (int a = 0; a < matrix[h].length; a++) {
                double p = matrix[h][a];
                row.add(p);
                if (h > a) {
                    homeWin += p;
                } else if (h == a) {
                    draw += p;
                } else {
                    awayWin += p;
                }
                if (h > 0 && a > 0) {
                    btts += p;
                }
                int goals = h + a;
                if (goals > 1) {
                    over15 += p;
                }
                if (goals > 2) {
                    over25 += p;
                }
                if (goals > 3) {
                    over35 += p;
                }
                tops.add(new TopScore(h, a, p));
            }
            rows.add(List.copyOf(row));
        }
        tops.sort(Comparator.comparingDouble(TopScore::probability).reversed());
        if (tops.size() > 6) {
            tops = tops.subList(0, 6);
        }
        return new ScoreDistribution(
                rows,
                homeWin,
                draw,
                awayWin,
                btts,
                over15,
                over25,
                over35,
                tops
        );
    }

    public static ScoreDistribution empty() {
        return new ScoreDistribution(List.of(), 0, 0, 0, 0, 0, 0, 0, List.of());
    }

    public boolean hasMatrix() {
        return matrix != null && !matrix.isEmpty();
    }
}
