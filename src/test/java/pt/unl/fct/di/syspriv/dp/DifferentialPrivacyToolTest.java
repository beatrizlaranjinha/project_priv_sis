package pt.unl.fct.di.syspriv.dp;

import org.junit.jupiter.api.Test;
import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.storage.Hibernate;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifferentialPrivacyToolTest {

    @Test
    void countAddsLaplaceNoiseAndConsumesBudget() {
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0f, List.of(), () -> 0.75);

        DPResult result = tool.countBelowGlucose(0.5f, 120);

        assertEquals(DPResult.ResultType.COUNT, result.getResultType());
        assertEquals(DPResult.ResultCode.SUCCESS, result.getResultCode());
        assertEquals(1.0f, result.getQuerySensitivity());
        assertEquals((float) (-Math.log(0.5) * 2), result.getResultValue(), 0.0001f);
        assertEquals(0.5, result.getPrivacyBudget(), 1.0e-12);
        assertEquals(0.5, tool.getPrivacyBudget(), 1.0e-12);
    }

    @Test
    void insufficientBudgetFailsWithoutChangingBudget() {
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(0.25f, List.of(), () -> 0.5);

        DPResult result = tool.countBelowGlucose(0.5f, 120);

        assertEquals(DPResult.ResultCode.FAIL, result.getResultCode());
        assertEquals("-1", result.getResultString());
        assertEquals(0.25, result.getPrivacyBudget(), 1.0e-12);
        assertEquals(0.25, tool.getPrivacyBudget(), 1.0e-12);
    }

    @Test
    void sumAndAverageReportClippingAndSensitivity() {
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0f, List.of(), () -> 0.5);

        DPResult sum = tool.sumGlucoseValues(0.25f);
        DPResult average = tool.averageGlucoseValues(0.25f);

        assertEquals(DPResult.ResultType.SUM, sum.getResultType());
        assertEquals(500.0f, sum.getQuerySensitivity());
        assertEquals(0.0f, sum.getClippingLower());
        assertEquals(500.0f, sum.getClippingUpper());
        assertEquals(DPResult.ResultType.AVERAGE, average.getResultType());
        assertEquals(500.0f, average.getQuerySensitivity());
        assertEquals(1.0f, average.getCountSensitivity());
        assertEquals(0.0f, average.getResultValue());
        assertEquals(0.5, tool.getPrivacyBudget(), 1.0e-12);
    }

    @Test
    void averageAddsIndependentNoiseToSumAndCount() {
        double[] uniforms = {0.75, 0.9};
        AtomicInteger next = new AtomicInteger();
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(
                1.0f, List.of(), () -> uniforms[next.getAndIncrement()]);

        DPResult average = tool.averageGlucoseValues(0.5f);

        double noisedSum = -Math.log(0.5) * (500.0 / 0.25);
        double noisedCount = -Math.log(0.2) * (1.0 / 0.25);
        assertEquals((float) (noisedSum / noisedCount), average.getResultValue(), 0.001f);
        assertEquals(0.5, average.getPrivacyBudget(), 1.0e-12);
        assertEquals(1.0f, average.getCountSensitivity());
    }

    @Test
    void histogramUsesPublicBinsAndNoisedCounts() {
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0f, List.of(), () -> 0.5);

        DPResult result = tool.histogramAgeAndGlucose(0.5f);

        assertEquals(DPResult.ResultType.HISTOGRAM, result.getResultType());
        assertEquals(DPResult.ResultCode.SUCCESS, result.getResultCode());
        assertEquals(1.0f, result.getQuerySensitivity());
        assertEquals(0.5, result.getPrivacyBudget(), 1.0e-12);
        assertNotNull(result.getHistogramResultValue());
        assertEquals(121 * 501, result.getHistogramResultValue().size());
        assertEquals(0.0f, result.getHistogramResultValue().get("Age: 28, Glucose: 107"));
    }

    @Test
    void histogramSupportsCoarsePublicBucketsForRegression() {
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0f, List.of(), () -> 0.5);

        DPResult result = tool.histogramAgeAndGlucose(0.5f, 10, 50);

        assertEquals(13 * 11, result.getHistogramResultValue().size());
        assertEquals(0.0f, result.getHistogramResultValue().get("Age: 4, Glucose: 24"));
        assertEquals(0.5, result.getPrivacyBudget(), 1.0e-12);
    }

    @Test
    void splitPartitionsByRecordAndAssignsFullParallelBudgetToEachSubset() {
        List<Long> recordIds = Hibernate.getInstance().sql(
                "SELECT id FROM health_data ORDER BY id", Long.class);
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(2.0, recordIds, () -> 0.5);
        assertTrue(recordIds.size() > 1, "Test data must include at least two records.");

        DifferentialPrivacyTool[] parts = tool.split(recordIds.size() / 2, 2.0);

        assertEquals(0.0, tool.getPrivacyBudget(), 1.0e-12);
        assertEquals(2.0, parts[0].getPrivacyBudget(), 1.0e-12);
        assertEquals(2.0, parts[1].getPrivacyBudget(), 1.0e-12);
        assertEquals(recordIds.size(), parts[0].getDatasetSize() + parts[1].getDatasetSize());
    }

    @Test
    void splitValidatesBudgetAndPreventsParentAndRepeatedQueries() {
        List<Long> recordIds = Hibernate.getInstance().sql(
                "SELECT id FROM health_data ORDER BY id", Long.class);
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0, recordIds, () -> 0.5);
        int recordCount = recordIds.size();

        assertThrows(IndexOutOfBoundsException.class, () -> tool.split(0, 0.5));
        assertThrows(IndexOutOfBoundsException.class,
                () -> tool.split(recordCount, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> tool.split(recordCount / 2, 1.1));
        assertEquals(1.0, tool.getPrivacyBudget(), 1.0e-12);

        DifferentialPrivacyTool[] parts = tool.split(recordCount / 2, 0.5);
        assertEquals(0.5, tool.getPrivacyBudget(), 1.0e-12);
        assertThrows(IllegalStateException.class, () -> tool.split(recordCount / 2, 0.1));
        assertEquals(DPResult.ResultCode.FAIL, tool.countBelowGlucose(0.1, 120).getResultCode());
        assertEquals(DPResult.ResultCode.SUCCESS,
                parts[0].countBelowGlucose(0.5, 120).getResultCode());
        assertEquals(DPResult.ResultCode.SUCCESS,
                parts[1].countBelowGlucose(0.5, 120).getResultCode());
        assertEquals(0.0, parts[0].getPrivacyBudget(), 1.0e-12);
        assertEquals(0.0, parts[1].getPrivacyBudget(), 1.0e-12);
    }

    @Test
    void invalidBudgetAndEpsilonValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DifferentialPrivacyTool(-1.0, List.of(), () -> 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new DifferentialPrivacyTool(Double.NaN, List.of(), () -> 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new DifferentialPrivacyTool(Double.POSITIVE_INFINITY, List.of(), () -> 0.5));

        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0, List.of(), () -> 0.5);
        assertThrows(IllegalArgumentException.class, () -> tool.countBelowGlucose(0.0, 120));
        assertThrows(IllegalArgumentException.class, () -> tool.sumGlucoseValues(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> tool.averageGlucoseValues(Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> tool.histogramAgeAndGlucose(Double.NaN));
    }

    @Test
    void insufficientBudgetFailsForSumAverageAndHistogram() {
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(0.25, List.of(), () -> 0.5);

        assertEquals(DPResult.ResultCode.FAIL, tool.sumGlucoseValues(0.5).getResultCode());
        assertEquals(DPResult.ResultCode.FAIL, tool.averageGlucoseValues(0.5).getResultCode());
        assertEquals(DPResult.ResultCode.FAIL, tool.histogramAgeAndGlucose(0.5).getResultCode());
        assertEquals(0.25, tool.getPrivacyBudget(), 1.0e-12);
    }

    @Test
    void repeatedQueriesExhaustBudgetWithoutGoingNegative() {
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0, List.of(), () -> 0.5);

        for (int i = 0; i < 10; i++) {
            assertEquals(DPResult.ResultCode.SUCCESS,
                    tool.countBelowGlucose(0.1, 120).getResultCode());
        }
        assertEquals(0.0, tool.getPrivacyBudget(), 1.0e-12);
        assertEquals(DPResult.ResultCode.FAIL,
                tool.countBelowGlucose(0.1, 120).getResultCode());
        assertTrue(tool.getPrivacyBudget() >= 0);
    }

    @Test
    void clippingAndAdditionSensitivityRespectConfiguredBounds() {
        assertEquals(500, DifferentialPrivacyTool.clip(900, 0, 500));
        assertEquals(0, DifferentialPrivacyTool.clip(-20, 0, 500));
        assertEquals(300, DifferentialPrivacyTool.clip(300, 0, 500));
        assertEquals(500.0, DifferentialPrivacyTool.sumSensitivity(0, 500));
        assertEquals(500.0, DifferentialPrivacyTool.sumSensitivity(-500, 500));
        assertEquals(700.0, DifferentialPrivacyTool.sumSensitivity(-200, 700));
    }

    @Test
    void laplaceNoiseIsFiniteAtTheUniformEndpoint() {
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0, List.of(), () -> 0.0);

        assertTrue(Double.isFinite(tool.laplaceNoise(1.0)));
    }

    @Test
    void laplaceNoiseHasExpectedMeanAndVariance() {
        Random random = new Random(20261029L);
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0, List.of(), random::nextDouble);
        int samples = 20_000;
        double scale = 2.0;
        double sum = 0;
        double sumSquares = 0;

        for (int i = 0; i < samples; i++) {
            double noise = tool.laplaceNoise(scale);
            sum += noise;
            sumSquares += noise * noise;
        }

        double mean = sum / samples;
        double variance = sumSquares / samples - mean * mean;
        assertEquals(0.0, mean, 0.08);
        assertEquals(2 * scale * scale, variance, 0.35);
    }

    @Test
    void histogramStringIncludesGroupedCount() {
        DPResult result = new DPResult();
        result.setResultCode(DPResult.ResultCode.SUCCESS);
        result.setHistogramResultValue(Map.of("Age: 28, Glucose: 107", 3.0f));

        assertEquals("Age: 28, Glucose: 107, Count: 3.0", result.getHistogramResultString());
    }

    @Test
    void loadsAndQueriesTheConfiguredHealthDataDatabase() {
        DifferentialPrivacyTool autoLoadedTool = new DifferentialPrivacyTool(1.0f);
        assertEquals(300, autoLoadedTool.getDatasetSize(),
                "The configured DP database should contain the 300 CSV health records.");

        List<Long> databaseIds = Hibernate.getInstance().sql(
                "SELECT id FROM health_data ORDER BY id", Long.class);
        assertEquals(300, databaseIds.size());
        assertEquals(3001L, databaseIds.get(0));
        assertEquals(3300L, databaseIds.get(databaseIds.size() - 1));

        int expectedCount = Hibernate.getInstance().sql(
                "SELECT COUNT(*) FROM health_data WHERE glucose < 120", Integer.class).get(0);
        DifferentialPrivacyTool deterministicTool =
                new DifferentialPrivacyTool(2.0f, databaseIds, () -> 0.5);
        DPResult result = deterministicTool.countBelowGlucose(0.5f, 120);

        assertEquals(DPResult.ResultCode.SUCCESS, result.getResultCode());
        assertEquals(expectedCount, result.getResultValue(), 0.0001f,
                "Uniform 0.5 produces zero Laplace noise, so the count matches the database.");
        assertEquals(1.5, result.getPrivacyBudget(), 1.0e-12);

        List<Integer> glucoseValues = Hibernate.getInstance().sql(
                "SELECT glucose FROM health_data", Integer.class);
        double expectedClippedSum = glucoseValues.stream()
                .mapToDouble(value -> Math.max(0, Math.min(500, value)))
                .sum();
        double expectedClippedAverage = expectedClippedSum / glucoseValues.size();

        DPResult sum = deterministicTool.sumGlucoseValues(0.5f);
        DPResult average = deterministicTool.averageGlucoseValues(0.5f);

        assertEquals(expectedClippedSum, sum.getResultValue(), 0.0001f);
        assertEquals(expectedClippedAverage, average.getResultValue(), 0.0001f,
                "With zero noise, the DP average is the clipped sum divided by the noisy count.");
        assertEquals(1.0f, average.getCountSensitivity());
        assertEquals(0.5, average.getPrivacyBudget(), 1.0e-12);

        int eligibleHistogramRows = Hibernate.getInstance().sql("""
                SELECT COUNT(*)
                FROM health_data h
                JOIN patients p ON p.id = h.patientId
                WHERE h.diagnosisDate IS NOT NULL
                  AND p.birthdate IS NOT NULL
                  AND (
                    CAST(strftime('%Y', h.diagnosisDate) AS INTEGER)
                    - CAST(strftime('%Y', p.birthdate) AS INTEGER)
                    - CASE WHEN strftime('%m-%d', h.diagnosisDate) < strftime('%m-%d', p.birthdate)
                           THEN 1 ELSE 0 END
                  ) BETWEEN 0 AND 120
                """, Integer.class).get(0);
        DifferentialPrivacyTool histogramTool =
                new DifferentialPrivacyTool(1.0f, databaseIds, () -> 0.5);
        DPResult histogram = histogramTool.histogramAgeAndGlucose(0.5f);

        assertEquals(DPResult.ResultCode.SUCCESS, histogram.getResultCode());
        assertEquals(121 * 501, histogram.getHistogramResultValue().size());
        assertEquals(eligibleHistogramRows,
                histogram.getHistogramResultValue().values().stream().mapToDouble(Float::doubleValue).sum(),
                0.001);
        assertEquals(0.5f, histogram.getPrivacyBudget());
    }
}
