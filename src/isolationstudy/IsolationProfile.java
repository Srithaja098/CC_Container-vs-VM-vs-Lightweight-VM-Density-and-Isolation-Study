package isolationstudy;

/**
 * The four numbers that define an isolation level (contract: Person 1 owns this class).
 *
 * <ul>
 *   <li>start-up delay (s): time from launch request until the first cloudlet may run</li>
 *   <li>per-instance memory overhead (MB): added on top of the application's own memory</li>
 *   <li>CPU overhead (%): share of the CPU an instance is given that is lost to virtualisation</li>
 *   <li>cap policy: pinned / uncapped / capped (see {@link CapPolicy})</li>
 * </ul>
 *
 * Immutable. Values come from {@link SimConfig}; the level-to-policy mapping is fixed by the
 * study design (full VM = pinned, container = uncapped, lightweight VM = capped).
 */
public final class IsolationProfile {

    private final IsolationLevel level;
    private final double startupDelayS;
    private final int memoryOverheadMb;
    private final double cpuOverheadPct;
    private final CapPolicy capPolicy;

    private IsolationProfile(IsolationLevel level, double startupDelayS, int memoryOverheadMb,
                             double cpuOverheadPct, CapPolicy capPolicy) {
        if (startupDelayS < 0.0) {
            throw new IllegalArgumentException("start-up delay must be >= 0, got " + startupDelayS);
        }
        if (memoryOverheadMb < 0) {
            throw new IllegalArgumentException("memory overhead must be >= 0, got " + memoryOverheadMb);
        }
        if (cpuOverheadPct < 0.0 || cpuOverheadPct >= 100.0) {
            throw new IllegalArgumentException("CPU overhead must be in [0, 100), got " + cpuOverheadPct);
        }
        this.level = level;
        this.startupDelayS = startupDelayS;
        this.memoryOverheadMb = memoryOverheadMb;
        this.cpuOverheadPct = cpuOverheadPct;
        this.capPolicy = capPolicy;
    }

    /** Builds the profile of one level from the configuration. */
    public static IsolationProfile of(IsolationLevel level, SimConfig cfg) {
        switch (level) {
            case FULL_VM:
                return new IsolationProfile(level, cfg.getStartupDelayFullVmS(),
                        cfg.getMemOverheadFullVmMb(), cfg.getCpuOverheadFullVmPct(), CapPolicy.PINNED);
            case CONTAINER:
                return new IsolationProfile(level, cfg.getStartupDelayContainerS(),
                        cfg.getMemOverheadContainerMb(), cfg.getCpuOverheadContainerPct(), CapPolicy.UNCAPPED);
            case LIGHTWEIGHT_VM:
                return new IsolationProfile(level, cfg.getStartupDelayLightweightVmS(),
                        cfg.getMemOverheadLightweightVmMb(), cfg.getCpuOverheadLightweightVmPct(), CapPolicy.CAPPED);
            default:
                throw new IllegalArgumentException("unknown level " + level);
        }
    }

    /** Contract form {@code IsolationProfile.of(level)}: uses the default configuration. */
    public static IsolationProfile of(IsolationLevel level) {
        return of(level, SimConfig.defaults());
    }

    public IsolationLevel getLevel() { return level; }
    public double getStartupDelayS() { return startupDelayS; }
    public int getMemoryOverheadMb() { return memoryOverheadMb; }
    public double getCpuOverheadPct() { return cpuOverheadPct; }
    public CapPolicy getCapPolicy() { return capPolicy; }

    /** CPU overhead as a fraction in [0, 1). */
    public double cpuOverheadFraction() {
        return cpuOverheadPct / 100.0;
    }

    /** MIPS the guest actually gets out of {@code givenMips} after the virtualisation tax. */
    public double effectiveMips(double givenMips) {
        return givenMips * (1.0 - cpuOverheadFraction());
    }

    @Override
    public String toString() {
        return level + "[startup=" + startupDelayS + "s, memOverhead=" + memoryOverheadMb
                + "MB, cpuOverhead=" + cpuOverheadPct + "%, cap=" + capPolicy + "]";
    }
}
