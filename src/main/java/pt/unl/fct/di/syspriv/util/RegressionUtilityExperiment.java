package pt.unl.fct.di.syspriv.util;

import pt.unl.fct.di.syspriv.dp.DifferentialPrivacyTool;
import pt.unl.fct.di.syspriv.entities.AnonymousHealthData;
import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.kanonymity.KAnonymityTool;
import pt.unl.fct.di.syspriv.storage.Hibernate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegressionUtilityExperiment {
    private static final int DEFAULT_K = 5;
    private static final float DEFAULT_T = 0.2f;
    private static final int AGE_BUCKET_WIDTH = 10;
    private static final int GLUCOSE_BUCKET_WIDTH = 50;
    private static final int MAX_DP_SAMPLE_SIZE = 50_000;
    private static final Pattern POSTAL_CODE = Pattern.compile("\\b(\\d{5})\\b");
    private static final List<Double> DEFAULT_EPSILONS = List.of(0.1, 0.5, 1.0);

    private record HealthPoint(long id, int age, int birthYear, int diagnosisYear,
                               int glucose, String address) {}

    private record RegressionSummary(String name, int sampleSize,
                                     LinearRegression.RegressionResult metrics) {}

    private record CompositionSummary(double parallelEffectiveEpsilon,
                                     double sequentialEffectiveEpsilon,
                                     float parallelEstimate,
                                     float firstSequentialEstimate,
                                     float secondSequentialEstimate) {}

    private final Hibernate hibernate;
    private final LinearRegression regression;

    public RegressionUtilityExperiment() {
        this.hibernate = Hibernate.getInstance();
        this.regression = new LinearRegression();
    }

    public void runDefaultExperiment() {
        double configuredBudget = ConfigLoader.getDoubleProperty("dp.privacy.budget", 2.0);
        double compositionEpsilon = DEFAULT_EPSILONS.get(0);
        double totalEpsilonSpend = DEFAULT_EPSILONS.stream().mapToDouble(Double::doubleValue).sum()
                + compositionEpsilon + 2 * compositionEpsilon;
        if (configuredBudget < totalEpsilonSpend) {
            throw new IllegalStateException("The selected regression and composition experiments would spend "
                    + totalEpsilonSpend + " epsilon, exceeding the configured budget of "
                    + configuredBudget + ".");
        }

        List<HealthPoint> data = loadHealthPoints();
        if (data.size() < DEFAULT_K) {
            throw new IllegalStateException("At least " + DEFAULT_K
                    + " eligible health records are required for the anonymization experiments.");
        }

        RegressionSummary raw = summarize("Unprotected health data", data.stream()
                .map(point -> new double[]{point.age(), point.glucose()}).toList());
        KAnonymityTool<AnonymousHealthData> kTool = new KAnonymityTool<>(DEFAULT_K, DEFAULT_T);
        RegressionSummary kAnonymous = summarize("K-anonymous (k=" + DEFAULT_K + ")",
                generalizedPoints(kTool.runMondrain(anonymizationInput(data))));
        RegressionSummary tClose = summarize("K-anonymous + t-closeness (k=" + DEFAULT_K
                        + ", t=" + DEFAULT_T + ")",
                generalizedPoints(kTool.runTCloseMondrain(anonymizationInput(data))));

        System.out.println("\n=== Age/glucose regression utility comparison ===");
        System.out.println("Age is calculated at diagnosis; anonymous ages use generalized-year midpoints.");
        printSummary(raw, raw);
        printSummary(kAnonymous, raw);
        printSummary(tClose, raw);

        List<Long> recordIds = data.stream().map(HealthPoint::id).toList();
        DifferentialPrivacyTool dpTool = new DifferentialPrivacyTool(configuredBudget, recordIds);
        double budgetBeforeExperiments = dpTool.getPrivacyBudget();
        double dpEpsilonSpend = 0;
        System.out.println("\n=== Differential Privacy regression (10-year x 50-unit public bins) ===");
        System.out.println("Epsilon values: 0.1 (more privacy/noisier), 0.5 (middle), "
                + "1.0 (less privacy/less noise). Each run releases one noisy histogram.");
        for (double epsilon : DEFAULT_EPSILONS) {
            DPResult histogram = dpTool.histogramAgeAndGlucose(
                    epsilon, AGE_BUCKET_WIDTH, GLUCOSE_BUCKET_WIDTH);
            if (histogram.getResultCode() != DPResult.ResultCode.SUCCESS) {
                throw new IllegalStateException("DP regression query failed for epsilon " + epsilon
                        + "; available budget was " + histogram.getPrivacyBudget() + ".");
            }
            RegressionSummary dpSummary = summarize("Differential Privacy (epsilon=" + epsilon + ")",
                    pointsFromNoisyHistogram(histogram.getHistogramResultValue()));
            printSummary(dpSummary, raw);
            dpEpsilonSpend += epsilon;
        }

        CompositionSummary composition = runCompositionExperiment(dpTool, compositionEpsilon);
        System.out.println("\n=== Composition experiment (count glucose < 120) ===");
        System.out.printf(Locale.ROOT,
                "Parallel: disjoint record-ID halves, epsilon per half=%.1f, effective epsilon=max=%.1f, combined estimate=%.3f%n",
                compositionEpsilon, composition.parallelEffectiveEpsilon(), composition.parallelEstimate());
        System.out.printf(Locale.ROOT,
                "Sequential: same full data queried twice, epsilon per query=%.1f, effective epsilon=sum=%.1f, estimates=%.3f and %.3f%n",
                compositionEpsilon, composition.sequentialEffectiveEpsilon(),
                composition.firstSequentialEstimate(), composition.secondSequentialEstimate());
        double measuredEpsilonSpend = budgetBeforeExperiments - dpTool.getPrivacyBudget();
        System.out.printf(Locale.ROOT,
                "Epsilon deducted from this experiment's in-memory budget: %.1f; expected from parameters: %.1f; remaining: %.12f (initial: %.1f).%n",
                measuredEpsilonSpend, dpEpsilonSpend + composition.parallelEffectiveEpsilon()
                        + composition.sequentialEffectiveEpsilon(),
                dpTool.getPrivacyBudget(), configuredBudget);
        System.out.println("The split is by record ID and is independent of the health values. "
                + "Parallel composition and the query sensitivities are record-level; "
                + "this does not claim patient-level privacy.");
        System.out.println("The parallel result is charged once at max(epsilon); the sequential results add.");
        System.out.println("The DP budget is in memory and resets when a new tool/application run starts.");
    }

    private List<HealthPoint> loadHealthPoints() {
        String query = """
                SELECT h.id || '|' ||
                       (
                           CAST(strftime('%Y', h.diagnosisDate) AS INTEGER)
                           - CAST(strftime('%Y', p.birthdate) AS INTEGER)
                           - CASE WHEN strftime('%m-%d', h.diagnosisDate) < strftime('%m-%d', p.birthdate)
                                  THEN 1 ELSE 0 END
                       ) || '|' ||
                       CAST(strftime('%Y', p.birthdate) AS INTEGER) || '|' ||
                       CAST(strftime('%Y', h.diagnosisDate) AS INTEGER) || '|' ||
                       h.glucose || '|' || COALESCE(p.address, '')
                FROM health_data h
                JOIN patients p ON p.id = h.patientId
                WHERE h.diagnosisDate IS NOT NULL
                  AND p.birthdate IS NOT NULL
                """;
        List<HealthPoint> points = new ArrayList<>();
        for (String row : hibernate.sql(query, String.class)) {
            String[] fields = row.split("\\|", 6);
            if (fields.length < 6) {
                throw new IllegalStateException("Could not parse a health-data row for regression.");
            }
            int age = Integer.parseInt(fields[1]);
            if (age >= 0 && age <= 120) {
                points.add(new HealthPoint(Long.parseLong(fields[0]), age,
                        Integer.parseInt(fields[2]), Integer.parseInt(fields[3]),
                        Integer.parseInt(fields[4]), fields[5]));
            }
        }
        return points;
    }

    private List<AnonymousHealthData> anonymizationInput(List<HealthPoint> points) {
        List<AnonymousHealthData> result = new ArrayList<>(points.size());
        for (HealthPoint point : points) {
            Matcher postalMatcher = POSTAL_CODE.matcher(point.address());
            int postalCode = postalMatcher.find() ? Integer.parseInt(postalMatcher.group(1)) : -1;
            Date diagnosisDate = Date.from(LocalDate.of(point.diagnosisYear(), 1, 1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant());
            result.add(new AnonymousHealthData(point.id(), point.birthYear(), postalCode,
                    point.glucose(), diagnosisDate));
        }
        return result;
    }

    private List<double[]> generalizedPoints(List<AnonymousHealthData> records) {
        List<double[]> points = new ArrayList<>(records.size());
        for (AnonymousHealthData record : records) {
            Map<String, Object> quasiIdentifiers = record.getQuasiIdentifiers();
            int birthYear = midpoint(quasiIdentifiers.get("birthYear"));
            int diagnosisYear = midpoint(quasiIdentifiers.get("diagnosisYear"));
            int age = diagnosisYear - birthYear;
            if (age >= 0 && age <= 120) {
                points.add(new double[]{age, record.getGlucose()});
            }
        }
        return points;
    }

    private static int midpoint(Object generalizedValue) {
        if (generalizedValue instanceof Number number) {
            return number.intValue();
        }
        String[] limits = generalizedValue.toString().split("-", 2);
        int lower = Integer.parseInt(limits[0]);
        return limits.length == 1 ? lower
                : lower + (Integer.parseInt(limits[1]) - lower) / 2;
    }

    private List<double[]> pointsFromNoisyHistogram(Map<String, Float> histogram) {
        List<Double> ages = new ArrayList<>();
        List<Double> glucoseValues = new ArrayList<>();
        for (Map.Entry<String, Float> bin : histogram.entrySet()) {
            String[] values = bin.getKey().replace("Age: ", "").split(", Glucose: ", 2);
            double age = Double.parseDouble(values[0]);
            double glucose = Double.parseDouble(values[1]);
            long count = Math.max(0, Math.round(bin.getValue()));
            if (ages.size() + count > MAX_DP_SAMPLE_SIZE) {
                throw new IllegalStateException("Noisy histogram is too large to expand for regression. "
                        + "Use a larger epsilon.");
            }
            for (long i = 0; i < count; i++) {
                ages.add(age);
                glucoseValues.add(glucose);
            }
        }
        if (ages.size() < 2) {
            throw new IllegalStateException("The noisy histogram produced fewer than two usable points.");
        }
        List<double[]> points = new ArrayList<>(ages.size());
        for (int i = 0; i < ages.size(); i++) {
            points.add(new double[]{ages.get(i), glucoseValues.get(i)});
        }
        return points;
    }

    private RegressionSummary summarize(String name, List<double[]> points) {
        if (points.size() < 2) {
            throw new IllegalStateException(name + " produced fewer than two regression points.");
        }
        double[] ages = new double[points.size()];
        double[] glucoseValues = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            ages[i] = points.get(i)[0];
            glucoseValues[i] = points.get(i)[1];
        }
        LinearRegression.RegressionResult result = regression.run(ages, glucoseValues);
        if (!Double.isFinite(result.getSlope()) || !Double.isFinite(result.getIntercept())
                || !Double.isFinite(result.getRSquare())
                || !Double.isFinite(result.getMeanSquareError())) {
            throw new IllegalStateException(name + " did not produce finite regression metrics.");
        }
        return new RegressionSummary(name, points.size(), result);
    }

    private void printSummary(RegressionSummary result, RegressionSummary baseline) {
        LinearRegression.RegressionResult metrics = result.metrics();
        System.out.printf("%s%n  n=%d, slope=%.6f, intercept=%.6f, R^2=%.6f, MSE=%.6f%n",
                result.name(), result.sampleSize(), metrics.getSlope(), metrics.getIntercept(),
                metrics.getRSquare(), metrics.getMeanSquareError());
        System.out.printf("  |slope - raw slope|=%.6f, |intercept - raw intercept|=%.6f%n",
                Math.abs(metrics.getSlope() - baseline.metrics().getSlope()),
                Math.abs(metrics.getIntercept() - baseline.metrics().getIntercept()));
        System.out.printf("  |R^2 - raw R^2|=%.6f, MSE difference=%.6f%n",
                Math.abs(metrics.getRSquare() - baseline.metrics().getRSquare()),
                metrics.getMeanSquareError() - baseline.metrics().getMeanSquareError());
    }

    private CompositionSummary runCompositionExperiment(DifferentialPrivacyTool sequential, double epsilon) {
        DPResult firstSequential = sequential.countBelowGlucose(epsilon, 120);
        DPResult secondSequential = sequential.countBelowGlucose(epsilon, 120);
        ensureSuccess(firstSequential, "first sequential query");
        ensureSuccess(secondSequential, "second sequential query");

        int recordCount = sequential.getDatasetSize();
        if (recordCount < 2) {
            throw new IllegalStateException("At least two records are required for parallel composition.");
        }
        int recordCutoff = recordCount / 2;
        DifferentialPrivacyTool[] subsets = sequential.split(recordCutoff, epsilon);
        DPResult firstParallel = subsets[0].countBelowGlucose(epsilon, 120);
        DPResult secondParallel = subsets[1].countBelowGlucose(epsilon, 120);
        ensureSuccess(firstParallel, "first parallel record subset");
        ensureSuccess(secondParallel, "second parallel record subset");

        return new CompositionSummary(
                Math.max(epsilon, epsilon),
                epsilon + epsilon,
                firstParallel.getResultValue() + secondParallel.getResultValue(),
                firstSequential.getResultValue(),
                secondSequential.getResultValue());
    }

    private static void ensureSuccess(DPResult result, String queryName) {
        if (result.getResultCode() != DPResult.ResultCode.SUCCESS) {
            throw new IllegalStateException("The " + queryName + " failed due to insufficient DP budget.");
        }
    }

}
