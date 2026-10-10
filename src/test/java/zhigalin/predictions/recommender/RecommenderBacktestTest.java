package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsLeagueSnapshot;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;

/**
 * Back-tests the real {@link PoissonScoreModel} on historical EPL results with bookmaker odds.
 * Skipped unless BACKTEST_DIR points at a folder of football-data.co.uk E0*.csv files
 * (columns FTHG, FTAG, PSCH, PSCD, PSCA, PC&gt;2.5).
 * <p>
 * Team stats are set to league averages ("no information"), so this measures the market-driven
 * core: calibration of the outcome probabilities and the points earned by the recommended score.
 */
class RecommenderBacktestTest {

    private static final FootyStatsLeagueSnapshot LEAGUE =
            new FootyStatsLeagueSnapshot(10, 1.65, 1.37, 1.37, 1.65, Instant.now());

    private static FootyStatsTeamSnapshot neutralTeam(String code) {
        return new FootyStatsTeamSnapshot(code, 1.5, 1.65, 1.37, 1.5, 1.37, 1.65,
                null, null, null, null, null, FootyStatsExtendedMetrics.empty(), Instant.now());
    }

    @Test
    void marketCorePointsAndCalibration() throws IOException {
        String dir = System.getenv("BACKTEST_DIR");
        assumeTrue(dir != null && !dir.isBlank(), "set BACKTEST_DIR to run the historical back-test");
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of(dir), "E0*.csv")) {
            ds.forEach(files::add);
        }
        files.sort(Path::compareTo);
        assumeTrue(!files.isEmpty(), "no E0*.csv files in " + dir);

        int n = 0;
        double ptsPick = 0;
        double ptsModal = 0;
        double ll = 0;
        double[] meanP = new double[3];
        double[] actual = new double[3];
        int[] distinctCount = new int[36];
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            List<String> header = Arrays.asList(lines.get(0).split(",", -1));
            int iH = header.indexOf("FTHG");
            int iA = header.indexOf("FTAG");
            int iO1 = header.indexOf("PSCH");
            int iO2 = header.indexOf("PSCD");
            int iO3 = header.indexOf("PSCA");
            int iOver = header.indexOf("PC>2.5");
            for (String line : lines.subList(1, lines.size())) {
                String[] c = line.split(",", -1);
                if (c.length <= iO3 || c[iH].isBlank() || c[iO1].isBlank() || c[iO3].isBlank()) {
                    continue;
                }
                int fh = Integer.parseInt(c[iH]);
                int fa = Integer.parseInt(c[iA]);
                Double line25 = c.length > iOver && !c[iOver].isBlank() ? 2.5 : null;
                PoissonScoreModel.Result r = PoissonScoreModel.recommend(
                        Double.parseDouble(c[iO1]), Double.parseDouble(c[iO2]), Double.parseDouble(c[iO3]),
                        neutralTeam("H"), neutralTeam("A"), LEAGUE, null, line25, null, null);
                n++;
                ptsPick += PredictionService.computePoints(ScoringMode.EPL, fh, fa, r.recommendedHome(), r.recommendedAway());
                ptsModal += PredictionService.computePoints(ScoringMode.EPL, fh, fa, r.mostLikelyHome(), r.mostLikelyAway());
                var d = r.distribution();
                double[] p = {d.homeWin(), d.draw(), d.awayWin()};
                int out = fh > fa ? 0 : (fh == fa ? 1 : 2);
                ll += -Math.log(p[out]);
                for (int k = 0; k < 3; k++) {
                    meanP[k] += p[k];
                }
                actual[out]++;
                distinctCount[Math.min(5, r.recommendedHome()) * 6 + Math.min(5, r.recommendedAway())]++;
            }
        }
        System.out.printf(Locale.US, "BACKTEST n=%d  EV-pick %.3f pts/match  modal %.3f pts/match  1X2 logloss %.4f%n",
                n, ptsPick / n, ptsModal / n, ll / n);
        System.out.printf(Locale.US, "BACKTEST mean P(h/d/a) %.3f/%.3f/%.3f vs actual %.3f/%.3f/%.3f%n",
                meanP[0] / n, meanP[1] / n, meanP[2] / n, actual[0] / n, actual[1] / n, actual[2] / n);
        StringBuilder dist = new StringBuilder();
        for (int i = 0; i < distinctCount.length; i++) {
            if (distinctCount[i] > n * 0.02) {
                dist.append(String.format(Locale.US, " %d:%d=%.0f%%", i / 6, i % 6, 100.0 * distinctCount[i] / n));
            }
        }
        System.out.println("BACKTEST picks:" + dist);
        assertTrue(n > 300);
        assertTrue(ptsPick / n > 0.40, "recommended score should earn clearly more than a coin-flip tipster");
        assertTrue(ptsPick >= ptsModal, "expected-points pick should not lose to the modal score");
    }
}
