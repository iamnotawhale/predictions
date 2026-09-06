package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.PoissonScoreModel.TotalScope;

class PoissonScoreModelTotalsExplanationTest {

    @Test
    void goalsNeededForOverOnHalfLines() {
        assertEquals(3, PoissonScoreModel.goalsNeededForOver(2.5));
        assertEquals(2, PoissonScoreModel.goalsNeededForOver(1.5));
        assertEquals(4, PoissonScoreModel.goalsNeededForOver(3.5));
        assertEquals("0–2", PoissonScoreModel.underGoalsRange(2.5));
        assertEquals("0–1", PoissonScoreModel.underGoalsRange(1.5));
    }

    @Test
    void probabilityOverLineSumsMatchTotals() {
        double[][] matrix = new double[3][3];
        matrix[0][0] = 0.10; // 0
        matrix[1][0] = 0.20; // 1
        matrix[1][1] = 0.30; // 2
        matrix[2][1] = 0.40; // 3 → over 2.5
        assertEquals(0.40, PoissonScoreModel.probabilityOverLine(matrix, 2.5, TotalScope.MATCH), 1e-9);
        assertEquals(0.40, PoissonScoreModel.probabilityOverLine(matrix, 1.5, TotalScope.HOME), 1e-9);
        assertEquals(0.70, PoissonScoreModel.probabilityOverLine(matrix, 0.5, TotalScope.AWAY), 1e-9);
    }

    @Test
    void totalsLinesAreReadableWithOverUnderPercents() {
        List<String> lines = new ArrayList<>();
        PoissonScoreModel.appendTotalsLines(
                lines,
                "MUN",
                "MCI",
                3.5,
                1.5,
                1.5,
                0.40,
                0.35,
                0.67
        );
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("Тотал матча — линия 3.5"));
        assertTrue(lines.get(0).contains("больше (4+) ~40%"));
        assertTrue(lines.get(0).contains("меньше (0–3) ~60%"));
        assertTrue(lines.get(1).contains("MUN больше 1.5 (2+) ~35%"));
        assertTrue(lines.get(1).contains("MCI больше 1.5 (2+) ~67%"));
    }
}
