package isolationstudy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small, dependency-free statistics helpers for latency results (mean, nearest-rank percentiles).
 * Static and deterministic, so experiments and {@link ModelChecks} share one definition.
 *
 * <p>Percentile definition: nearest-rank. For n sorted values, the p-th percentile is the value at
 * rank {@code ceil(p / 100 * n)} (1-based). So P99 of 100 values is the 99th smallest.
 */
public final class LatencyStats {

    private LatencyStats() { }

    /** Arithmetic mean; NaN for an empty list. */
    public static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (Double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    /** Nearest-rank percentile, {@code 0 < p <= 100}; NaN for an empty list. */
    public static double percentile(List<Double> values, double p) {
        if (p <= 0.0 || p > 100.0) {
            throw new IllegalArgumentException("percentile must be in (0, 100], got " + p);
        }
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new ArrayList<Double>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        rank = Math.max(1, Math.min(sorted.size(), rank));
        return sorted.get(rank - 1);
    }

    /** Largest value; NaN for an empty list. */
    public static double max(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        return Collections.max(values);
    }
}
