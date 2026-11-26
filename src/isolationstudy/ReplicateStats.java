package isolationstudy;

/** Mean and two-sided 95% confidence interval helper for five replicate observations. */
public final class ReplicateStats {
    private static final double T_95_DF4 = 2.776;

    private ReplicateStats() { }

    public static double mean(double[] values) {
        check(values);
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    /** Half-width of the 95% Student-t interval (t=2.776 for df=4). */
    public static double ci95HalfWidth(double[] values) {
        check(values);
        double average = mean(values);
        double squared = 0.0;
        for (double value : values) {
            double delta = value - average;
            squared += delta * delta;
        }
        double sampleSd = Math.sqrt(squared / (values.length - 1));
        return T_95_DF4 * sampleSd / Math.sqrt(values.length);
    }

    private static void check(double[] values) {
        if (values == null || values.length != 5) {
            throw new IllegalArgumentException("exactly five replicate values are required");
        }
        for (double value : values) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("replicate values must be finite");
            }
        }
    }
}
