package isolationstudy;

/**
 * *** STUB - OWNED BY PERSON 3. Replace this file with Person 3's real SimConfig. ***
 *
 * <p>Person 3 owns SimConfig (contract table, due day 2). This minimal stand-in has the same
 * class name, plain getters and no logic, so Person 1's code compiles today. Every getter below
 * is called somewhere in Person 1's code; Person 3's version must offer the same names (or tell
 * Person 1 which names to change). The values are the defaults from the brief:
 * 1 host, 16 cores x 2500 MIPS, 64 GB RAM, reference start-up and CPU overheads.
 *
 * <p>Calibration note: per-instance memory overheads (512 / 256 / 768 MB) and the 2-vCPU,
 * 256 MB application size were chosen so the model reproduces the reference density of
 * 8 / 128 / 64 instances per host. They are modelling choices, not measurements.
 */
public class SimConfig {

    // ---- host (identical for all three levels) ----
    private final int hostCount = 1;
    private final int hostCores = 16;
    private final double hostMipsPerCore = 2500.0;
    private final int hostRamMb = 64 * 1024;
    private final long hostBw = 10000000L;
    private final long hostStorageMb = 1000000L;

    // ---- one instance (same size for all three levels) ----
    private final int instanceVcpus = 2;
    private final double instanceMipsPerVcpu = 2500.0;
    private final int instanceAppRamMb = 256;
    private final long instanceBw = 1000L;
    private final long instanceImageSizeMb = 1000L;

    // ---- per-level isolation parameters (reference values) ----
    private final double startupFullVmS = 30.0;
    private final double startupContainerS = 2.0;
    private final double startupLightweightVmS = 3.8;

    private final double cpuOverheadFullVmPct = 5.0;
    private final double cpuOverheadContainerPct = 2.0;
    private final double cpuOverheadLightweightVmPct = 8.0;

    private final int memOverheadFullVmMb = 512;
    private final int memOverheadContainerMb = 256;
    private final int memOverheadLightweightVmMb = 768;

    private final long seed = 42L;

    /** Defaults. Person 3's real class should offer the same static factory (or a no-arg constructor). */
    public static SimConfig defaults() {
        return new SimConfig();
    }

    public long getSeed() { return seed; }

    public int getHostCount() { return hostCount; }
    public int getHostCores() { return hostCores; }
    public double getHostMipsPerCore() { return hostMipsPerCore; }
    public int getHostRamMb() { return hostRamMb; }
    public long getHostBw() { return hostBw; }
    public long getHostStorageMb() { return hostStorageMb; }

    public int getInstanceVcpus() { return instanceVcpus; }
    public double getInstanceMipsPerVcpu() { return instanceMipsPerVcpu; }
    public int getInstanceAppRamMb() { return instanceAppRamMb; }
    public long getInstanceBw() { return instanceBw; }
    public long getInstanceImageSizeMb() { return instanceImageSizeMb; }

    public double getStartupDelayFullVmS() { return startupFullVmS; }
    public double getStartupDelayContainerS() { return startupContainerS; }
    public double getStartupDelayLightweightVmS() { return startupLightweightVmS; }

    public double getCpuOverheadFullVmPct() { return cpuOverheadFullVmPct; }
    public double getCpuOverheadContainerPct() { return cpuOverheadContainerPct; }
    public double getCpuOverheadLightweightVmPct() { return cpuOverheadLightweightVmPct; }

    public int getMemOverheadFullVmMb() { return memOverheadFullVmMb; }
    public int getMemOverheadContainerMb() { return memOverheadContainerMb; }
    public int getMemOverheadLightweightVmMb() { return memOverheadLightweightVmMb; }
}
