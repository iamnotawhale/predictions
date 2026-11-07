package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.recommender.model.ExplanationBreakdown;
import zhigalin.predictions.recommender.model.ExplanationRow;
import zhigalin.predictions.recommender.model.ScoreDistribution;

class ExplanationBreakdownAnalysisTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyJsonWithoutAnalysisStillLoadsRowsAndMatrix() throws Exception {
        ScoreDistribution dist = ScoreDistribution.fromNormalizedMatrix(new double[][]{
                {0.1, 0.05, 0.02, 0.01, 0.0, 0.0},
                {0.08, 0.12, 0.06, 0.02, 0.01, 0.0},
                {0.04, 0.07, 0.09, 0.04, 0.01, 0.0},
                {0.02, 0.03, 0.04, 0.03, 0.01, 0.0},
                {0.01, 0.01, 0.02, 0.01, 0.01, 0.0},
                {0.0, 0.01, 0.01, 0.01, 0.0, 0.0}
        });
        ExplanationBreakdown original = new ExplanationBreakdown(
                List.of(new ExplanationRow("xG", "1.4", "1.2")),
                List.of("note"),
                dist,
                null
        );
        String json = mapper.writeValueAsString(original);
        String withoutAnalysis = json.replaceAll(",\"analysis\":null", "");
        ExplanationBreakdown parsed = mapper.readValue(withoutAnalysis, ExplanationBreakdown.class);
        assertEquals(1, parsed.rows().size());
        assertEquals("xG", parsed.rows().get(0).metric());
        assertNotNull(parsed.distribution());
        assertTrue(parsed.distribution().hasMatrix());
        assertNull(parsed.analysis());
    }

    @Test
    void withDistributionPreservesAnalysis() {
        ExplanationBreakdown base = new ExplanationBreakdown(
                List.of(),
                List.of("n"),
                null,
                "проза"
        );
        ExplanationBreakdown withDist = base.withDistribution(ScoreDistribution.empty());
        assertEquals("проза", withDist.analysis());
    }
}
