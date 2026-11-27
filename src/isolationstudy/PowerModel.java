package isolationstudy;

/**
 * Linear host power model: {@code P(u) = P_idle + (P_max - P_idle) * u}, with u the host CPU
 * utilisation in [0, 1]. This is the model used in most CloudSim energy studies (CloudSim 4.0 ships
 * the same formula as {@code PowerModelLinear}); it is re-implemented here because the study's
 * hosts track their own utilisation and integrate it exactly (see {@link IsolatedHost}).
 *
 * <p>Energy of a powered host over a run of {@code durationS} seconds:
 * <pre>E = P_idle * durationS + (P_max - P_idle) * integral(u dt)</pre>
 * Both terms are exact because utilisation is piecewise constant between simulation events.
 *
 * <p>Assumptions: hosts with no instance are switched off (0 W); a powered host draws at least
 * P_idle for the whole run, including while instances are still starting; CPU stalled on memory
 * or I/O contention still counts as busy; reserved but idle pinned cores count as idle.
 * Immutable.
 */
public final class PowerModel {

    /** Placeholder idle power of the 16-core host, W. Replace with a cited SPECpower figure. */
    public static final double DEFAULT_IDLE_W = 100.0;
    /** Placeholder peak power of the 16-core host, W. Replace with a cited SPECpower figure. */
    public static final double DEFAULT_MAX_W = 300.0;

    private final double idleW;
    private final double maxW;

    public PowerModel(double idleW, double maxW) {
        if (idleW < 0.0) {
            throw new IllegalArgumentException("idle power must be >= 0, got " + idleW);
        }
        if (maxW < idleW) {
            throw new IllegalArgumentException("max power (" + maxW + ") must be >= idle power (" + idleW + ")");
        }
        this.idleW = idleW;
        this.maxW = maxW;
    }

    public static PowerModel fromConfig(SimConfig cfg) {
        return new PowerModel(cfg.getHostIdlePowerW(), cfg.getHostMaxPowerW());
    }

    public double getIdleW() { return idleW; }
    public double getMaxW() { return maxW; }

    /** Power draw at utilisation {@code u} (clamped to [0, 1]), watts. */
    public double powerW(double u) {
        double c = Math.max(0.0, Math.min(1.0, u));
        return idleW + (maxW - idleW) * c;
    }

    /**
     * Energy of one powered host, joules.
     *
     * @param durationS          seconds the host was powered
     * @param utilisationSeconds integral of utilisation over time, in seconds (u = 1 for 10 s gives 10)
     */
    public double energyJ(double durationS, double utilisationSeconds) {
        return idleW * durationS + (maxW - idleW) * utilisationSeconds;
    }

    @Override
    public String toString() {
        return "PowerModel[idle=" + idleW + "W, max=" + maxW + "W]";
    }
}
