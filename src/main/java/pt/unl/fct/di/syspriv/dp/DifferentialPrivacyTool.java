package pt.unl.fct.di.syspriv.dp;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleSupplier;

import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.util.ConfigLoader;

public class DifferentialPrivacyTool {
    private static final String DEFAULT_EPSILON_PROPERTY = "dp.epsilon";
    private static final String GLUCOSE_LOWER_PROPERTY = "dp.glucose.lower";
    private static final String GLUCOSE_UPPER_PROPERTY = "dp.glucose.upper";
    private static final String AGE_LOWER_PROPERTY = "dp.age.lower";
    private static final String AGE_UPPER_PROPERTY = "dp.age.upper";

    private record AgeGlucose(int age, int glucose) {}

    private final Hibernate hibernate;
    private final List<Long> recordIds;
    private final String idFilter;
    private final DoubleSupplier uniformRandom;
    private final double defaultEpsilon;
    private final int glucoseLower;
    private final int glucoseUpper;
    private final int ageLower;
    private final int ageUpper;
    private double privacyBudget;
    private boolean dataSplit;

    public DifferentialPrivacyTool() {
        this(ConfigLoader.getDoubleProperty("dp.privacy.budget", 2.0),
                loadRecordIds(), new SecureRandom()::nextDouble);
    }

    DifferentialPrivacyTool(double privacyBudget) {
        this(privacyBudget, loadRecordIds(), new SecureRandom()::nextDouble);
    }

    public DifferentialPrivacyTool(double privacyBudget, List<Long> recordIds) {
        this(privacyBudget, recordIds, new SecureRandom()::nextDouble);
    }

    DifferentialPrivacyTool(double privacyBudget, List<Long> recordIds, DoubleSupplier uniformRandom) {
        if (!Double.isFinite(privacyBudget) || privacyBudget < 0) {
            throw new IllegalArgumentException("Privacy budget must be finite and non-negative.");
        }
        this.privacyBudget = privacyBudget;
        this.hibernate = Hibernate.getInstance();
        List<Long> validatedRecordIds = List.copyOf(Objects.requireNonNull(recordIds, "recordIds"));
        if (validatedRecordIds.stream().anyMatch(id -> id < 0)) {
            throw new IllegalArgumentException("Record IDs must be non-negative.");
        }
        this.recordIds = validatedRecordIds.stream().sorted().toList();
        this.idFilter = this.recordIds.isEmpty()
                ? "1 = 0"
                : "id IN (" + String.join(",", this.recordIds.stream().map(String::valueOf).toList()) + ")";
        this.uniformRandom = Objects.requireNonNull(uniformRandom, "uniformRandom");

        this.defaultEpsilon = configuredDouble(DEFAULT_EPSILON_PROPERTY, 0.5);
        this.glucoseLower = ConfigLoader.getIntProperty(GLUCOSE_LOWER_PROPERTY, 0);
        this.glucoseUpper = ConfigLoader.getIntProperty(GLUCOSE_UPPER_PROPERTY, 500);
        this.ageLower = ConfigLoader.getIntProperty(AGE_LOWER_PROPERTY, 0);
        this.ageUpper = ConfigLoader.getIntProperty(AGE_UPPER_PROPERTY, 120);
        validateBounds(glucoseLower, glucoseUpper, GLUCOSE_LOWER_PROPERTY, GLUCOSE_UPPER_PROPERTY);
        validateBounds(ageLower, ageUpper, AGE_LOWER_PROPERTY, AGE_UPPER_PROPERTY);
        validateEpsilon(defaultEpsilon);
    }

    private static double configuredDouble(String property, double defaultValue) {
        double value = ConfigLoader.getDoubleProperty(property, defaultValue);
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalStateException("Invalid configuration value for " + property + ".");
        }
        return value;
    }

    private static List<Long> loadRecordIds() {
        return Hibernate.getInstance().sql("SELECT id FROM health_data ORDER BY id", Long.class);
    }

    private static void validateBounds(int lower, int upper, String lowerProperty, String upperProperty) {
        if (lower > upper) {
            throw new IllegalStateException(lowerProperty + " must not exceed " + upperProperty + ".");
        }
    }

    private static void validateEpsilon(double epsilon) {
        if (!Double.isFinite(epsilon) || epsilon <= 0) {
            throw new IllegalArgumentException("Epsilon must be finite and greater than zero."); 
        }
    }

    public synchronized DifferentialPrivacyTool[] split(int recordCutoff, double parallelBudget) {
        if (dataSplit) {
            throw new IllegalStateException("This dataset has already been split.");
        }
        validateEpsilon(parallelBudget);
        if (parallelBudget > privacyBudget) {
            throw new IllegalArgumentException("Parallel budget exceeds the available privacy budget.");
        }
        if (recordCutoff < 1 || recordCutoff >= recordIds.size()) {
            throw new IndexOutOfBoundsException("Split index must leave records in both subsets.");
        }

        privacyBudget -= parallelBudget;
        dataSplit = true;
        return new DifferentialPrivacyTool[]{
                new DifferentialPrivacyTool(parallelBudget, recordIds.subList(0, recordCutoff), uniformRandom),
                new DifferentialPrivacyTool(parallelBudget, recordIds.subList(recordCutoff, recordIds.size()),
                        uniformRandom)
        };
    }

    public DPResult countBelowGlucose(int upperGlucose) {
        return countBelowGlucose(defaultEpsilon, upperGlucose);
    }

    public synchronized DPResult countBelowGlucose(double privacyCost, int upperGlucose) {
        double sensitivity = 1.0;
        DPResult result = prepareResult(DPResult.ResultType.COUNT, privacyCost, sensitivity, null, null);
        if (result.getResultCode() == DPResult.ResultCode.FAIL) {
            return result;
        }

        String query = "SELECT COUNT(*) FROM health_data WHERE glucose < " + upperGlucose
                + " AND " + idFilter;
        int count = hibernate.sql(query, Integer.class).get(0);
        completeScalarResult(result, count, sensitivity, privacyCost);
        return result;
    }

    public DPResult sumGlucoseValues() {
        return sumGlucoseValues(defaultEpsilon);
    }

    public synchronized DPResult sumGlucoseValues(double privacyCost) {
        double sensitivity = sumSensitivity();
        DPResult result = prepareResult(DPResult.ResultType.SUM, privacyCost, sensitivity,
                (float) glucoseLower, (float) glucoseUpper);
        if (result.getResultCode() == DPResult.ResultCode.FAIL) {
            return result;
        }

        double sum = 0;
        for (int glucose : loadGlucoseValues()) {
            sum += clip(glucose, glucoseLower, glucoseUpper);
        }
        completeScalarResult(result, sum, sensitivity, privacyCost);
        return result;
    }

    public DPResult averageGlucoseValues() {
        return averageGlucoseValues(defaultEpsilon);
    }

    public synchronized DPResult averageGlucoseValues(double privacyCost) {
        validateEpsilon(privacyCost);
        double sumSensitivity = sumSensitivity();
        double countSensitivity = 1.0;
        DPResult result = prepareResult(DPResult.ResultType.AVERAGE, privacyCost, sumSensitivity,
                (float) glucoseLower, (float) glucoseUpper);
        if (result.getResultCode() == DPResult.ResultCode.FAIL) {
            return result;
        }

        double epsilonPerQuery = privacyCost / 2.0;
        double clippedSum = loadGlucoseValues().stream()
                .mapToDouble(value -> clip(value, glucoseLower, glucoseUpper))
                .sum();
        int count = hibernate.sql(
                "SELECT COUNT(*) FROM health_data WHERE " + idFilter, Integer.class).get(0);
        double noisedSum = clippedSum + laplaceNoise(sumSensitivity / epsilonPerQuery);
        double noisedCount = count + laplaceNoise(countSensitivity / epsilonPerQuery);
        double safeDenominator = Math.max(1.0, noisedCount);

        result.setResultValue((float) (noisedSum / safeDenominator));
        result.setCountSensitivity((float) countSensitivity);
        result.setPrivacyBudget(getPrivacyBudget());
        result.setResultCode(DPResult.ResultCode.SUCCESS);
        return result;
    }

    public DPResult histogramAgeAndGlucose() {
        return histogramAgeAndGlucose(defaultEpsilon);
    }

    public synchronized DPResult histogramAgeAndGlucose(double privacyCost) {
        return histogramAgeAndGlucose(privacyCost, 1, 1);
    }

    public synchronized DPResult histogramAgeAndGlucose(double privacyCost, int ageBucketWidth,
                                                         int glucoseBucketWidth) {
        if (ageBucketWidth < 1 || glucoseBucketWidth < 1) {
            throw new IllegalArgumentException("Histogram bucket widths must be positive.");
        }
        DPResult result = prepareResult(DPResult.ResultType.HISTOGRAM, privacyCost, 1.0,
                (float) glucoseLower, (float) glucoseUpper);
        if (result.getResultCode() == DPResult.ResultCode.FAIL) {
            return result;
        }

        Map<String, Integer> observedCounts = new LinkedHashMap<>();
        for (AgeGlucose value : loadAgeGlucoseValues()) {
            if (value.age() < ageLower || value.age() > ageUpper) {
                continue;
            }
            int glucose = clip(value.glucose(), glucoseLower, glucoseUpper);
            String key = histogramBucketKey(value.age(), glucose, ageBucketWidth, glucoseBucketWidth);
            observedCounts.merge(key, 1, Integer::sum);
        }

        double scale = 1.0 / privacyCost;
        Map<String, Float> noisedCounts = new LinkedHashMap<>();
        for (int ageStart = ageLower; ageStart <= ageUpper; ageStart += ageBucketWidth) {
            int ageEnd = Math.min(ageUpper, ageStart + ageBucketWidth - 1);
            int ageMidpoint = ageStart + (ageEnd - ageStart) / 2;
            for (int glucoseStart = glucoseLower; glucoseStart <= glucoseUpper;
                 glucoseStart += glucoseBucketWidth) {
                int glucoseEnd = Math.min(glucoseUpper, glucoseStart + glucoseBucketWidth - 1);
                int glucoseMidpoint = glucoseStart + (glucoseEnd - glucoseStart) / 2;
                String key = histogramKey(ageMidpoint, glucoseMidpoint);
                double noisyCount = observedCounts.getOrDefault(key, 0) + laplaceNoise(scale);
                noisedCounts.put(key, (float) noisyCount);
            }
        }

        result.setHistogramResultValue(noisedCounts);
        result.setPrivacyBudget(getPrivacyBudget());
        result.setResultCode(DPResult.ResultCode.SUCCESS);
        return result;
    }

    private DPResult prepareResult(DPResult.ResultType type, double privacyCost, double sensitivity,
                                   Float clippingLower, Float clippingUpper) {
        validateEpsilon(privacyCost);
        DPResult result = new DPResult();
        result.setResultType(type);
        result.setQuerySensitivity((float) sensitivity);
        result.setClippingLower(clippingLower);
        result.setClippingUpper(clippingUpper);
        result.setPrivacyBudget(getPrivacyBudget());
        if (dataSplit || privacyBudget < privacyCost) {
            result.setResultCode(DPResult.ResultCode.FAIL);
            return result;
        }
        privacyBudget = Math.max(0.0, privacyBudget - privacyCost);
        result.setPrivacyBudget(getPrivacyBudget());
        return result;
    }

    private void completeScalarResult(DPResult result, double rawValue, double sensitivity,
                                      double privacyCost) {
        result.setResultValue((float) (rawValue + laplaceNoise(sensitivity / privacyCost)));
        result.setPrivacyBudget(getPrivacyBudget());
        result.setResultCode(DPResult.ResultCode.SUCCESS);
    }

    double laplaceNoise(double scale) {
        if (scale == 0) {
            return 0;
        }
        double uniform = uniformRandom.getAsDouble();
        if (!Double.isFinite(uniform) || uniform < 0 || uniform >= 1) {
            throw new IllegalStateException("Random source must return a value in [0, 1).");
        }
        double endpointMargin = Math.ulp(1.0);
        uniform = Math.max(endpointMargin, Math.min(1.0 - endpointMargin, uniform));
        double centered = uniform - 0.5;
        return -scale * Math.signum(centered) * Math.log1p(-2 * Math.abs(centered));
    }

    private List<Integer> loadGlucoseValues() {
        return hibernate.sql("SELECT glucose FROM health_data WHERE " + idFilter, Integer.class);
    }

    private List<AgeGlucose> loadAgeGlucoseValues() {
        if (recordIds.isEmpty()) {
            return List.of();
        }
        String histogramIdFilter = idFilter.replace("id IN", "h.id IN");
        String query = """
                SELECT (
                    CAST(strftime('%%Y', h.diagnosisDate) AS INTEGER)
                    - CAST(strftime('%%Y', p.birthdate) AS INTEGER)
                    - CASE WHEN strftime('%%m-%%d', h.diagnosisDate) < strftime('%%m-%%d', p.birthdate)
                           THEN 1 ELSE 0 END
                ) || '|' || h.glucose
                FROM health_data h
                JOIN patients p ON p.id = h.patientId
                WHERE h.diagnosisDate IS NOT NULL
                  AND p.birthdate IS NOT NULL
                  AND %s
                """.formatted(histogramIdFilter);
        return hibernate.sql(query, String.class).stream()
                .map(value -> {
                    String[] parts = value.split("\\|", 2);
                    return new AgeGlucose(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                })
                .toList();
    }

    static int clip(int value, int lower, int upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private double sumSensitivity() {
        return sumSensitivity(glucoseLower, glucoseUpper);
    }

    static double sumSensitivity(int lower, int upper) {
        return Math.max(Math.abs((double) lower), Math.abs((double) upper));
    }

    private static String histogramKey(int age, int glucose) {
        return "Age: " + age + ", Glucose: " + glucose;
    }

    private String histogramBucketKey(int age, int glucose, int ageBucketWidth, int glucoseBucketWidth) {
        int ageStart = ageLower + (age - ageLower) / ageBucketWidth * ageBucketWidth;
        int ageEnd = Math.min(ageUpper, ageStart + ageBucketWidth - 1);
        int glucoseStart = glucoseLower + (glucose - glucoseLower) / glucoseBucketWidth * glucoseBucketWidth;
        int glucoseEnd = Math.min(glucoseUpper, glucoseStart + glucoseBucketWidth - 1);
        return histogramKey(ageStart + (ageEnd - ageStart) / 2,
                glucoseStart + (glucoseEnd - glucoseStart) / 2);
    }

    public synchronized double getPrivacyBudget() {
        return privacyBudget;
    }

    public int getDatasetSize() {
        return recordIds.size();
    }

}
