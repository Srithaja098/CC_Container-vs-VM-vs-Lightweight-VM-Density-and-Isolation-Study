package isolationstudy;

import java.util.Random;

/**
 * Request arrival times for latency experiments.
 *
 * <p>{@link #poisson} draws exponential gaps from the caller's {@link Random}. Pass
 * {@code scenario.getRandom()} (seed 42) or a {@code new Random(seed + replicate)} so that runs are
 * reproducible but replicates can differ.
 */
public final class Arrivals {

    private Arrivals() { }

    /**
     * Poisson process: {@code count} arrival times in seconds, strictly increasing, all after
     * {@code startS}, with mean gap {@code 1 / ratePerS}.
     */
    public static double[] poisson(Random rnd, double ratePerS, int count, double startS) {
        if (ratePerS <= 0.0) {
            throw new IllegalArgumentException("rate must be > 0, got " + ratePerS);
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, got " + count);
        }
        double[] times = new double[count];
        double t = startS;
        for (int i = 0; i < count; i++) {
            t += -Math.log(1.0 - rnd.nextDouble()) / ratePerS;   // 1 - u is in (0, 1], so log is finite
            times[i] = t;
        }
        return times;
    }
}
