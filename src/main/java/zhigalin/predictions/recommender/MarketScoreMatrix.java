package zhigalin.predictions.recommender;

/**
 * Builds a score-probability matrix from bookmaker odds.
 * <p>
 * Back-test on 3 EPL seasons (docs/recommender-backtest.md): de-vigged 1X2 probabilities are almost perfectly
 * calibrated, and an independent-Poisson matrix fitted to them describes scorelines about as well as anything
 * simpler or fancier. Steps: (1) fit home/away goal rates to the market's 1X2 probabilities with a weak pull
 * towards an expected total, (2) build the Poisson matrix, (3) rescale the home-win / draw / away-win regions
 * so the matrix reproduces the market's 1X2 exactly (iterative proportional fitting).
 */
final class MarketScoreMatrix {

    /** Goals counted per side while fitting (mass beyond 10 is negligible). */
    private static final int FIT_GOALS = 11;
    private static final double LAMBDA_MIN = 0.15;
    private static final double LAMBDA_MAX = 5.0;
    private static final double LAMBDA_STEP = 0.025;
    /** Strength of the pull towards the expected total goals (1X2 alone does not pin the total down). */
    private static final double TOTAL_PULL = 2.0;

    private static final double[] LAMBDAS;
    private static final double[][] P_HOME;
    private static final double[][] P_DRAW;

    static {
        int n = (int) Math.round((LAMBDA_MAX - LAMBDA_MIN) / LAMBDA_STEP);
        LAMBDAS = new double[n];
        double[][] pmf = new double[n][];
        double[][] cdfBelow = new double[n][FIT_GOALS];
        for (int i = 0; i < n; i++) {
            LAMBDAS[i] = LAMBDA_MIN + i * LAMBDA_STEP;
            pmf[i] = poissonRow(LAMBDAS[i], FIT_GOALS);
            double run = 0;
            for (int k = 0; k < FIT_GOALS; k++) {
                cdfBelow[i][k] = run;
                run += pmf[i][k];
            }
        }
        P_HOME = new double[n][n];
        P_DRAW = new double[n][n];
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                double home = 0;
                double draw = 0;
                for (int k = 0; k < FIT_GOALS; k++) {
                    home += pmf[a][k] * cdfBelow[b][k];
                    draw += pmf[a][k] * pmf[b][k];
                }
                P_HOME[a][b] = home;
                P_DRAW[a][b] = draw;
            }
        }
    }

    private MarketScoreMatrix() {
    }

    record Lambdas(double home, double away) {
    }

    /** Goal rates whose Poisson 1X2 is closest to {@code market}, nudged towards {@code totalTarget} goals. */
    static Lambdas fit(PoissonScoreModel.MarketOutcome market, double totalTarget) {
        double bestErr = Double.MAX_VALUE;
        int bestA = 0;
        int bestB = 0;
        for (int a = 0; a < LAMBDAS.length; a++) {
            for (int b = 0; b < LAMBDAS.length; b++) {
                double home = P_HOME[a][b];
                double draw = P_DRAW[a][b];
                double away = 1.0 - home - draw;
                double total = LAMBDAS[a] + LAMBDAS[b];
                double err = sq(home - market.homeWin()) + sq(draw - market.draw()) + sq(away - market.awayWin())
                        + TOTAL_PULL * sq((total - totalTarget) / 10.0);
                if (err < bestErr) {
                    bestErr = err;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        return new Lambdas(LAMBDAS[bestA], LAMBDAS[bestB]);
    }

    /** Normalized independent-Poisson matrix [home goals][away goals] for 0..maxGoals. */
    static double[][] poisson(double lambdaHome, double lambdaAway, int maxGoals) {
        double[] h = poissonRow(lambdaHome, maxGoals + 1);
        double[] a = poissonRow(lambdaAway, maxGoals + 1);
        double[][] m = new double[maxGoals + 1][maxGoals + 1];
        double sum = 0;
        for (int i = 0; i <= maxGoals; i++) {
            for (int j = 0; j <= maxGoals; j++) {
                m[i][j] = h[i] * a[j];
                sum += m[i][j];
            }
        }
        for (int i = 0; i <= maxGoals; i++) {
            for (int j = 0; j <= maxGoals; j++) {
                m[i][j] /= sum;
            }
        }
        return m;
    }

    /**
     * Rescales home-win / draw / away-win cells so their totals equal the market's probabilities
     * (the shape of the scorelines inside each outcome is kept). Returns a normalized matrix.
     */
    static double[][] calibrateTo1x2(double[][] matrix, PoissonScoreModel.MarketOutcome market) {
        int n = matrix.length;
        double[][] m = new double[n][];
        for (int i = 0; i < n; i++) {
            m[i] = matrix[i].clone();
        }
        double[] target = {market.homeWin(), market.draw(), market.awayWin()};
        for (int iter = 0; iter < 30; iter++) {
            double[] mass = new double[3];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    mass[region(i, j)] += m[i][j];
                }
            }
            double[] scale = new double[3];
            for (int r = 0; r < 3; r++) {
                scale[r] = mass[r] > 0 ? target[r] / mass[r] : 1.0;
            }
            double sum = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    m[i][j] *= scale[region(i, j)];
                    sum += m[i][j];
                }
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    m[i][j] /= sum;
                }
            }
        }
        return m;
    }

    /** Matrix for the fitted rates, calibrated to the market 1X2. */
    static double[][] calibrated(double lambdaHome, double lambdaAway,
                                 PoissonScoreModel.MarketOutcome market, int maxGoals) {
        return calibrateTo1x2(poisson(lambdaHome, lambdaAway, maxGoals), market);
    }

    /**
     * Mean total goals such that P(total &gt; line) equals {@code overProbability}
     * (a typical over price is about 0.57; football-data.co.uk over-2.5 averaged 0.566).
     */
    static double totalMeanForOver(double line, double overProbability) {
        int minGoalsForOver = (int) Math.floor(line) + 1;
        double lo = 0.3;
        double hi = 8.0;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            double below = 0;
            double term = Math.exp(-mid);
            for (int k = 0; k < minGoalsForOver; k++) {
                below += term;
                term *= mid / (k + 1);
            }
            if (1.0 - below < overProbability) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    private static int region(int home, int away) {
        return home > away ? 0 : (home == away ? 1 : 2);
    }

    private static double[] poissonRow(double lambda, int size) {
        double[] row = new double[size];
        double term = Math.exp(-lambda);
        double sum = 0;
        for (int k = 0; k < size; k++) {
            row[k] = term;
            sum += term;
            term *= lambda / (k + 1);
        }
        for (int k = 0; k < size; k++) {
            row[k] /= sum;
        }
        return row;
    }

    private static double sq(double x) {
        return x * x;
    }
}
