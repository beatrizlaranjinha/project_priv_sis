package pt.unl.fct.di.syspriv.kanonymity;

import pt.unl.fct.di.syspriv.anonymity.Anonymizable;
import java.util.*;

/** Mondrian partitioning with optional t-closeness constraints for numerical sensitive values. */
public class KAnonymityTool<T extends Anonymizable> {
    private final int k;
    private final float t;

    public KAnonymityTool(int k, float t) {
        if (k < 1 || t < 0 || t > 1) {
            throw new IllegalArgumentException("Require k >= 1 and 0 <= t <= 1");
        }
        this.k = k;
        this.t = t;
    }

    public boolean isKAnonymous(List<T> records) {
        if (records == null || records.isEmpty()) return false;
        Map<Map<String, Object>, Integer> counts = new HashMap<>();
        for (T record : records) {
            Map<String, Object> key = Map.copyOf(record.getQuasiIdentifiers());
            counts.merge(key, 1, Integer::sum);
        }
        return counts.values().stream().allMatch(count -> count >= k);
    }

    public Map<Map<String, Object>, Set<Object>> sensitiveValuesPerGroup(List<T> records) {
        Map<Map<String, Object>, Set<Object>> groups = new HashMap<>();
        for (T record : records) {
            Map<String, Object> key = Map.copyOf(record.getQuasiIdentifiers());
            groups.computeIfAbsent(key, ignored -> new HashSet<>()).add(record.getSensitiveValue());
        }
        return groups;
    }

    /** All equivalence classes in P must have EMD <= t against original distribution Q. */
    public boolean isTClose(List<T> P, List<T> Q) {
        if (P == null || Q == null || P.isEmpty() || Q.isEmpty()) return false;
        Map<Map<String, Object>, List<T>> groups = new HashMap<>();
        for (T record : P) {
            Map<String, Object> key = Map.copyOf(record.getQuasiIdentifiers());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
        }
        return groups.values().stream().allMatch(group -> earthMoversDistance(group, Q) <= t + 1e-9);
    }

    public List<T> runMondrain(List<T> records) {
        return run(records, false);
    }

    public List<T> runTCloseMondrain(List<T> records) {
        return run(records, true);
    }

    private List<T> run(List<T> records, boolean withTCloseness) {
        if (records == null || records.size() < k) {
            throw new IllegalArgumentException("At least k records are needed");
        }
        List<T> result = new ArrayList<>(records);
        // Snapshot raw values before starting to generalize equivalence classes.
        Map<T, Map<String, Object>> originalQI = new IdentityHashMap<>();
        for (T record : result) {
            Map<String, Object> qi = new LinkedHashMap<>(record.getQuasiIdentifiers());
            for (Object value : qi.values()) {
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("Mondrian requires numerical quasi-identifiers");
                }
            }
            originalQI.put(record, qi);
        }
        Map<String, Double> globalSpans = spans(result, originalQI);
        partition(result, result, originalQI, globalSpans, withTCloseness);
        return result;
    }

    private void partition(List<T> partition, List<T> population,
                           Map<T, Map<String, Object>> originalQI,
                           Map<String, Double> globalSpans, boolean withTCloseness) {
        List<String> dimensions = new ArrayList<>(globalSpans.keySet());
        dimensions.sort((a, b) -> Double.compare(
                normalizedSpan(partition, b, originalQI, globalSpans),
                normalizedSpan(partition, a, originalQI, globalSpans)));
        for (String dimension : dimensions) {
            List<T> sorted = new ArrayList<>(partition);
            sorted.sort(Comparator.comparingDouble(record -> number(originalQI.get(record).get(dimension))));
            int middle = sorted.size() / 2;
            // Try cut positions closest to the median first. Split only between distinct QI values.
            for (int distance = 0; distance < sorted.size(); distance++) {
                int[] cutPositions = {middle - distance, middle + distance};
                for (int cut : cutPositions) {
                    if (cut < k || sorted.size() - cut < k) continue;
                    if (number(originalQI.get(sorted.get(cut - 1)).get(dimension))
                            == number(originalQI.get(sorted.get(cut)).get(dimension))) continue;
                    List<T> left = new ArrayList<>(sorted.subList(0, cut));
                    List<T> right = new ArrayList<>(sorted.subList(cut, sorted.size()));
                    if (withTCloseness && (earthMoversDistance(left, population) > t + 1e-9
                            || earthMoversDistance(right, population) > t + 1e-9)) continue;
                    partition(left, population, originalQI, globalSpans, withTCloseness);
                    partition(right, population, originalQI, globalSpans, withTCloseness);
                    return;
                }
            }
        }
        generalize(partition, originalQI);
    }

    private void generalize(List<T> partition, Map<T, Map<String, Object>> originalQI) {
        for (String key : originalQI.get(partition.get(0)).keySet()) {
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (T record : partition) {
                double value = number(originalQI.get(record).get(key));
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            String generalized = format(min) + (min == max ? "" : "-" + format(max));
            for (T record : partition) {
                record.getQuasiIdentifiers().put(key, generalized);
            }
        }
    }

    private Map<String, Double> spans(List<T> records, Map<T, Map<String, Object>> originalQI) {
        Map<String, Double> spans = new LinkedHashMap<>();
        for (String key : originalQI.get(records.get(0)).keySet()) {
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (T record : records) {
                double value = number(originalQI.get(record).get(key));
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            spans.put(key, max - min);
        }
        return spans;
    }

    private double normalizedSpan(List<T> records, String key, Map<T, Map<String, Object>> originalQI,
                                  Map<String, Double> globalSpans) {
        double span = globalSpans.get(key);
        if (span == 0) return 0;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (T record : records) {
            double value = number(originalQI.get(record).get(key));
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return (max - min) / span;
    }

    private double earthMoversDistance(List<T> subset, List<T> population) {
        TreeSet<Double> values = new TreeSet<>();
        for (T record : population) values.add(number(record.getSensitiveValue()));
        for (T record : subset) values.add(number(record.getSensitiveValue()));
        if (values.size() < 2) return 0;
        Map<Double, Integer> subsetCounts = new HashMap<>(), totalCounts = new HashMap<>();
        for (T record : subset) subsetCounts.merge(number(record.getSensitiveValue()), 1, Integer::sum);
        for (T record : population) totalCounts.merge(number(record.getSensitiveValue()), 1, Integer::sum);
        double delta = 0, cumulative = 0;
        int index = 0;
        for (double value : values) {
            cumulative += (double) subsetCounts.getOrDefault(value, 0) / subset.size()
                    - (double) totalCounts.getOrDefault(value, 0) / population.size();
            if (++index < values.size()) delta += Math.abs(cumulative);
        }
        return delta / (values.size() - 1);
    }

    private static double number(Object value) { return ((Number) value).doubleValue(); }
    private static String format(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value);
    }
}
