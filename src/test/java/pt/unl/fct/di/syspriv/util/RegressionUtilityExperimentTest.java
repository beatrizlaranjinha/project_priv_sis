package pt.unl.fct.di.syspriv.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegressionUtilityExperimentTest {

    @Test
    void comparesFourDataProtectionsAndReportsComposition() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            assertDoesNotThrow(() -> new RegressionUtilityExperiment().runDefaultExperiment());
        } finally {
            System.setOut(originalOut);
        }

        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Unprotected health data"));
        assertTrue(report.contains("K-anonymous (k=5)"));
        assertTrue(report.contains("K-anonymous + t-closeness"));
        assertTrue(report.contains("Differential Privacy (epsilon="));
        assertTrue(report.contains("Parallel: disjoint record-ID halves"));
        assertTrue(report.contains("Sequential: same full data queried twice"));
        assertTrue(report.contains("this does not claim patient-level privacy"));
        assertTrue(report.contains("Epsilon deducted from this experiment's in-memory budget: 1.9"));
    }
}
