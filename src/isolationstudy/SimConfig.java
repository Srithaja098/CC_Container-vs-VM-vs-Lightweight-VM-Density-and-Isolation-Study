package isolationstudy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Properties;

/**
 * Configuration layer for the CloudSim 4.0 Isolation Study.
 * Owned by Person 3.
 *
 * <p>Loads parameters from {@code config/experiment.properties} or uses calibrated
 * reference defaults if the file cannot be accessed. Immutable, thread-safe, and
 * provides non-destructive copy-with methods for sensitivity sweeps.</p>
 *
 * <p>Hardware calibration:
 * 1 host, 16 physical cores @ 2500.0 MIPS, 64 GB (65536 MB) RAM.
 * Application size: 2 vCPUs, 256 MB RAM.
 * Overheads reproduce reference density of 8 (Full VM) / 128 (Container) / 64 (Lightweight VM).
 */
public class SimConfig implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_PROPERTIES_PATH = "config/experiment.properties";

    // ---- host (identical across all three levels) ----
    private final int hostCount;
    private final int hostCores;
    private final double hostMipsPerCore;
    private final int hostRamMb;
    private final long hostBw;
    private final long hostStorageMb;

    // ---- base instance (identical application size across all three levels) ----
    private final int instanceVcpus;
    private final double instanceMipsPerVcpu;
    private final int instanceAppRamMb;
    private final long instanceBw;
    private final long instanceImageSizeMb;

    // ---- per-level isolation start-up delays (seconds) ----
    private final double startupFullVmS;
    private final double startupContainerS;
    private final double startupLightweightVmS;

    // ---- per-level CPU virtualization overheads (%) ----
    private final double cpuOverheadFullVmPct;
    private final double cpuOverheadContainerPct;
    private final double cpuOverheadLightweightVmPct;

    // ---- per-level memory overheads (MB) ----
    private final int memOverheadFullVmMb;
    private final int memOverheadContainerMb;
    private final int memOverheadLightweightVmMb;

    // ---- random seed ----
    private final long seed;

    // ---- fleet sizes for startup sweep (Exp 2) ----
    private final int[] fleetSizes;

    // ---- workload & noisy-neighbour parameters (Exp 3) ----
    private final int victimCount;
    private final long victimLengthMi;
    private final long neighbourLengthMi;
    private final double neighbourIntensity;

    // ---- uncore contention parameters for mixed tenancy (Exp 4) ----
    private final double memContentionFactor;
    private final double ioContentionFactor;

    /**
     * Internal full constructor.
     */
    public SimConfig(int hostCount, int hostCores, double hostMipsPerCore, int hostRamMb,
                     long hostBw, long hostStorageMb, int instanceVcpus, double instanceMipsPerVcpu,
                     int instanceAppRamMb, long instanceBw, long instanceImageSizeMb,
                     double startupFullVmS, double startupContainerS, double startupLightweightVmS,
                     double cpuOverheadFullVmPct, double cpuOverheadContainerPct, double cpuOverheadLightweightVmPct,
                     int memOverheadFullVmMb, int memOverheadContainerMb, int memOverheadLightweightVmMb,
                     long seed, int[] fleetSizes, int victimCount, long victimLengthMi,
                     long neighbourLengthMi, double neighbourIntensity,
                     double memContentionFactor, double ioContentionFactor) {
        this.hostCount = hostCount;
        this.hostCores = hostCores;
        this.hostMipsPerCore = hostMipsPerCore;
        this.hostRamMb = hostRamMb;
        this.hostBw = hostBw;
        this.hostStorageMb = hostStorageMb;
        this.instanceVcpus = instanceVcpus;
        this.instanceMipsPerVcpu = instanceMipsPerVcpu;
        this.instanceAppRamMb = instanceAppRamMb;
        this.instanceBw = instanceBw;
        this.instanceImageSizeMb = instanceImageSizeMb;
        this.startupFullVmS = startupFullVmS;
        this.startupContainerS = startupContainerS;
        this.startupLightweightVmS = startupLightweightVmS;
        this.cpuOverheadFullVmPct = cpuOverheadFullVmPct;
        this.cpuOverheadContainerPct = cpuOverheadContainerPct;
        this.cpuOverheadLightweightVmPct = cpuOverheadLightweightVmPct;
        this.memOverheadFullVmMb = memOverheadFullVmMb;
        this.memOverheadContainerMb = memOverheadContainerMb;
        this.memOverheadLightweightVmMb = memOverheadLightweightVmMb;
        this.seed = seed;
        this.fleetSizes = fleetSizes != null ? fleetSizes.clone() : new int[]{1, 8, 16, 32, 64};
        this.victimCount = victimCount;
        this.victimLengthMi = victimLengthMi;
        this.neighbourLengthMi = neighbourLengthMi;
        this.neighbourIntensity = neighbourIntensity;
        this.memContentionFactor = memContentionFactor;
        this.ioContentionFactor = ioContentionFactor;
    }

    /**
     * Default constructor: constructs config with calibrated reference defaults.
     */
    public SimConfig() {
        this(1, 16, 2500.0, 64 * 1024, 10000000L, 1000000L,
             2, 2500.0, 256, 1000L, 1000L,
             30.0, 2.0, 3.8,
             5.0, 2.0, 8.0,
             512, 256, 768,
             42L, new int[]{1, 8, 16, 32, 64},
             4, 100000L, 1000000L, 8.0,
             0.15, 0.20);
    }

    /**
     * Static factory returning default configuration.
     * Guaranteed to match Person 1's contract table.
     */
    public static SimConfig defaults() {
        return new SimConfig();
    }

    /**
     * Loads configuration from default properties file {@code config/experiment.properties}.
     * If the file is not found, falls back to calibrated reference defaults.
     */
    public static SimConfig load() {
        return load(DEFAULT_PROPERTIES_PATH);
    }

    /**
     * Loads configuration from a given file path.
     */
    public static SimConfig load(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            // Check if available as classpath resource
            InputStream is = SimConfig.class.getClassLoader().getResourceAsStream(filePath);
            if (is != null) {
                try {
                    Properties props = new Properties();
                    props.load(is);
                    return fromProperties(props);
                } catch (IOException ignored) {
                } finally {
                    try { is.close(); } catch (IOException ignored) {}
                }
            }
            return defaults();
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            return fromProperties(props);
        } catch (IOException e) {
            System.err.println("Warning: could not read properties from " + filePath + ", using defaults: " + e.getMessage());
            return defaults();
        }
    }

    /**
     * Builds a {@link SimConfig} from loaded {@link Properties}.
     */
    public static SimConfig fromProperties(Properties p) {
        SimConfig d = defaults();
        int hostCount = getInt(p, "host.count", "host_count", d.hostCount);
        int hostCores = getInt(p, "host.cores", "host_cores", d.hostCores);
        double hostMipsPerCore = getDouble(p, "host.mips_per_core", "host_mips_per_core", d.hostMipsPerCore);
        int hostRamMb = getInt(p, "host.ram_mb", "host_ram_mb", d.hostRamMb);
        long hostBw = getLong(p, "host.bw", "host_bw", d.hostBw);
        long hostStorageMb = getLong(p, "host.storage_mb", "host_storage_mb", d.hostStorageMb);

        int instanceVcpus = getInt(p, "instance.vcpus", "instance_vcpus", d.instanceVcpus);
        double instanceMipsPerVcpu = getDouble(p, "instance.mips_per_vcpu", "instance_mips_per_vcpu", d.instanceMipsPerVcpu);
        int instanceAppRamMb = getInt(p, "instance.app_ram_mb", "instance_app_ram_mb", d.instanceAppRamMb);
        long instanceBw = getLong(p, "instance.bw", "instance_bw", d.instanceBw);
        long instanceImageSizeMb = getLong(p, "instance.image_size_mb", "instance_image_size_mb", d.instanceImageSizeMb);

        double startupFullVmS = getDouble(p, "startup.delay.full_vm_s", "startup_delay_full_vm_s", d.startupFullVmS);
        double startupContainerS = getDouble(p, "startup.delay.container_s", "startup_delay_container_s", d.startupContainerS);
        double startupLightweightVmS = getDouble(p, "startup.delay.lightweight_vm_s", "startup_delay_lightweight_vm_s", d.startupLightweightVmS);

        double cpuOverheadFullVmPct = getDouble(p, "cpu.overhead.full_vm_pct", "cpu_overhead_full_vm_pct", d.cpuOverheadFullVmPct);
        double cpuOverheadContainerPct = getDouble(p, "cpu.overhead.container_pct", "cpu_overhead_container_pct", d.cpuOverheadContainerPct);
        double cpuOverheadLightweightVmPct = getDouble(p, "cpu.overhead.lightweight_vm_pct", "cpu_overhead_lightweight_vm_pct", d.cpuOverheadLightweightVmPct);

        int memOverheadFullVmMb = getInt(p, "mem.overhead.full_vm_mb", "mem_overhead_full_vm_mb", d.memOverheadFullVmMb);
        int memOverheadContainerMb = getInt(p, "mem.overhead.container_mb", "mem_overhead_container_mb", d.memOverheadContainerMb);
        int memOverheadLightweightVmMb = getInt(p, "mem.overhead.lightweight_vm_mb", "mem_overhead_lightweight_vm_mb", d.memOverheadLightweightVmMb);

        long seed = getLong(p, "seed", "random_seed", d.seed);
        int[] fleetSizes = getIntArray(p, "fleet.sizes", "fleet_sizes", d.fleetSizes);

        int victimCount = getInt(p, "workload.victim_count", "victim_count", d.victimCount);
        long victimLengthMi = getLong(p, "workload.victim_length_mi", "victim_length_mi", d.victimLengthMi);
        long neighbourLengthMi = getLong(p, "workload.neighbour_length_mi", "neighbour_length_mi", d.neighbourLengthMi);
        double neighbourIntensity = getDouble(p, "workload.neighbour_intensity", "neighbour_intensity", d.neighbourIntensity);

        double memContentionFactor = getDouble(p, "mixed_tenancy.mem_contention_factor", "mem_contention_factor", d.memContentionFactor);
        double ioContentionFactor = getDouble(p, "mixed_tenancy.io_contention_factor", "io_contention_factor", d.ioContentionFactor);

        return new SimConfig(hostCount, hostCores, hostMipsPerCore, hostRamMb, hostBw, hostStorageMb,
                instanceVcpus, instanceMipsPerVcpu, instanceAppRamMb, instanceBw, instanceImageSizeMb,
                startupFullVmS, startupContainerS, startupLightweightVmS,
                cpuOverheadFullVmPct, cpuOverheadContainerPct, cpuOverheadLightweightVmPct,
                memOverheadFullVmMb, memOverheadContainerMb, memOverheadLightweightVmMb,
                seed, fleetSizes, victimCount, victimLengthMi, neighbourLengthMi, neighbourIntensity,
                memContentionFactor, ioContentionFactor);
    }

    // -------------------------------------------------------------------------
    // Contract Getters (matching Person 1 & Person 2 API exactly)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Extended Getters (Workload, Fleet & Contention)
    // -------------------------------------------------------------------------

    public int[] getFleetSizes() { return fleetSizes.clone(); }
    public int getVictimCount() { return victimCount; }
    public long getVictimLengthMi() { return victimLengthMi; }
    public long getNeighbourLengthMi() { return neighbourLengthMi; }
    public double getNeighbourIntensity() { return neighbourIntensity; }
    public double getNeighbourDemandMultiplier() { return neighbourIntensity; }
    public double getMemContentionFactor() { return memContentionFactor; }
    public double getIoContentionFactor() { return ioContentionFactor; }

    // -------------------------------------------------------------------------
    // Sensitivity & Sweep Mutation Helpers (Non-destructive)
    // -------------------------------------------------------------------------

    /** Returns a new config with scaled startup delays (e.g. 0.75x or 1.25x). */
    public SimConfig withStartupDelayScale(double scale) {
        return new SimConfig(hostCount, hostCores, hostMipsPerCore, hostRamMb, hostBw, hostStorageMb,
                instanceVcpus, instanceMipsPerVcpu, instanceAppRamMb, instanceBw, instanceImageSizeMb,
                startupFullVmS * scale, startupContainerS * scale, startupLightweightVmS * scale,
                cpuOverheadFullVmPct, cpuOverheadContainerPct, cpuOverheadLightweightVmPct,
                memOverheadFullVmMb, memOverheadContainerMb, memOverheadLightweightVmMb,
                seed, fleetSizes, victimCount, victimLengthMi, neighbourLengthMi, neighbourIntensity,
                memContentionFactor, ioContentionFactor);
    }

    /** Returns a new config with scaled CPU overheads (e.g. 0.75x or 1.25x). */
    public SimConfig withCpuOverheadScale(double scale) {
        return new SimConfig(hostCount, hostCores, hostMipsPerCore, hostRamMb, hostBw, hostStorageMb,
                instanceVcpus, instanceMipsPerVcpu, instanceAppRamMb, instanceBw, instanceImageSizeMb,
                startupFullVmS, startupContainerS, startupLightweightVmS,
                Math.min(99.0, cpuOverheadFullVmPct * scale),
                Math.min(99.0, cpuOverheadContainerPct * scale),
                Math.min(99.0, cpuOverheadLightweightVmPct * scale),
                memOverheadFullVmMb, memOverheadContainerMb, memOverheadLightweightVmMb,
                seed, fleetSizes, victimCount, victimLengthMi, neighbourLengthMi, neighbourIntensity,
                memContentionFactor, ioContentionFactor);
    }

    /** Returns a new config with scaled host resources (cores and RAM). */
    public SimConfig withHostScale(double coresScale, double ramScale) {
        int scaledCores = Math.max(1, (int) Math.round(hostCores * coresScale));
        int scaledRam = Math.max(1024, (int) Math.round(hostRamMb * ramScale));
        return new SimConfig(hostCount, scaledCores, hostMipsPerCore, scaledRam, hostBw, hostStorageMb,
                instanceVcpus, instanceMipsPerVcpu, instanceAppRamMb, instanceBw, instanceImageSizeMb,
                startupFullVmS, startupContainerS, startupLightweightVmS,
                cpuOverheadFullVmPct, cpuOverheadContainerPct, cpuOverheadLightweightVmPct,
                memOverheadFullVmMb, memOverheadContainerMb, memOverheadLightweightVmMb,
                seed, fleetSizes, victimCount, victimLengthMi, neighbourLengthMi, neighbourIntensity,
                memContentionFactor, ioContentionFactor);
    }

    /** Returns a new config with altered neighbour intensity. */
    public SimConfig withNeighbourIntensity(double multiplier) {
        return new SimConfig(hostCount, hostCores, hostMipsPerCore, hostRamMb, hostBw, hostStorageMb,
                instanceVcpus, instanceMipsPerVcpu, instanceAppRamMb, instanceBw, instanceImageSizeMb,
                startupFullVmS, startupContainerS, startupLightweightVmS,
                cpuOverheadFullVmPct, cpuOverheadContainerPct, cpuOverheadLightweightVmPct,
                memOverheadFullVmMb, memOverheadContainerMb, memOverheadLightweightVmMb,
                seed, fleetSizes, victimCount, victimLengthMi, neighbourLengthMi, multiplier,
                memContentionFactor, ioContentionFactor);
    }

    /** Returns a new config with altered host RAM. */
    public SimConfig withHostRamMb(int ramMb) {
        return new SimConfig(hostCount, hostCores, hostMipsPerCore, ramMb, hostBw, hostStorageMb,
                instanceVcpus, instanceMipsPerVcpu, instanceAppRamMb, instanceBw, instanceImageSizeMb,
                startupFullVmS, startupContainerS, startupLightweightVmS,
                cpuOverheadFullVmPct, cpuOverheadContainerPct, cpuOverheadLightweightVmPct,
                memOverheadFullVmMb, memOverheadContainerMb, memOverheadLightweightVmMb,
                seed, fleetSizes, victimCount, victimLengthMi, neighbourLengthMi, neighbourIntensity,
                memContentionFactor, ioContentionFactor);
    }

    /** Returns a new config with altered seed. */
    public SimConfig withSeed(long newSeed) {
        return new SimConfig(hostCount, hostCores, hostMipsPerCore, hostRamMb, hostBw, hostStorageMb,
                instanceVcpus, instanceMipsPerVcpu, instanceAppRamMb, instanceBw, instanceImageSizeMb,
                startupFullVmS, startupContainerS, startupLightweightVmS,
                cpuOverheadFullVmPct, cpuOverheadContainerPct, cpuOverheadLightweightVmPct,
                memOverheadFullVmMb, memOverheadContainerMb, memOverheadLightweightVmMb,
                newSeed, fleetSizes, victimCount, victimLengthMi, neighbourLengthMi, neighbourIntensity,
                memContentionFactor, ioContentionFactor);
    }

    /**
     * Generic parameter override by key name. Supports dot and underscore formats.
     */
    public SimConfig with(String key, String value) {
        Properties p = toProperties();
        p.setProperty(key, value);
        // Also set normalized version
        p.setProperty(key.replace('_', '.'), value);
        return fromProperties(p);
    }

    /** Exports current config to {@link Properties}. */
    public Properties toProperties() {
        Properties p = new Properties();
        p.setProperty("host.count", String.valueOf(hostCount));
        p.setProperty("host.cores", String.valueOf(hostCores));
        p.setProperty("host.mips_per_core", String.valueOf(hostMipsPerCore));
        p.setProperty("host.ram_mb", String.valueOf(hostRamMb));
        p.setProperty("host.bw", String.valueOf(hostBw));
        p.setProperty("host.storage_mb", String.valueOf(hostStorageMb));
        p.setProperty("instance.vcpus", String.valueOf(instanceVcpus));
        p.setProperty("instance.mips_per_vcpu", String.valueOf(instanceMipsPerVcpu));
        p.setProperty("instance.app_ram_mb", String.valueOf(instanceAppRamMb));
        p.setProperty("instance.bw", String.valueOf(instanceBw));
        p.setProperty("instance.image_size_mb", String.valueOf(instanceImageSizeMb));
        p.setProperty("startup.delay.full_vm_s", String.valueOf(startupFullVmS));
        p.setProperty("startup.delay.container_s", String.valueOf(startupContainerS));
        p.setProperty("startup.delay.lightweight_vm_s", String.valueOf(startupLightweightVmS));
        p.setProperty("cpu.overhead.full_vm_pct", String.valueOf(cpuOverheadFullVmPct));
        p.setProperty("cpu.overhead.container_pct", String.valueOf(cpuOverheadContainerPct));
        p.setProperty("cpu.overhead.lightweight_vm_pct", String.valueOf(cpuOverheadLightweightVmPct));
        p.setProperty("mem.overhead.full_vm_mb", String.valueOf(memOverheadFullVmMb));
        p.setProperty("mem.overhead.container_mb", String.valueOf(memOverheadContainerMb));
        p.setProperty("mem.overhead.lightweight_vm_mb", String.valueOf(memOverheadLightweightVmMb));
        p.setProperty("seed", String.valueOf(seed));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fleetSizes.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(fleetSizes[i]);
        }
        p.setProperty("fleet.sizes", sb.toString());
        p.setProperty("workload.victim_count", String.valueOf(victimCount));
        p.setProperty("workload.victim_length_mi", String.valueOf(victimLengthMi));
        p.setProperty("workload.neighbour_length_mi", String.valueOf(neighbourLengthMi));
        p.setProperty("workload.neighbour_intensity", String.valueOf(neighbourIntensity));
        p.setProperty("mixed_tenancy.mem_contention_factor", String.valueOf(memContentionFactor));
        p.setProperty("mixed_tenancy.io_contention_factor", String.valueOf(ioContentionFactor));
        return p;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static int getInt(Properties p, String key1, String key2, int def) {
        String val = p.getProperty(key1, p.getProperty(key2));
        if (val == null) return def;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static long getLong(Properties p, String key1, String key2, long def) {
        String val = p.getProperty(key1, p.getProperty(key2));
        if (val == null) return def;
        try { return Long.parseLong(val.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static double getDouble(Properties p, String key1, String key2, double def) {
        String val = p.getProperty(key1, p.getProperty(key2));
        if (val == null) return def;
        try { return Double.parseDouble(val.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static int[] getIntArray(Properties p, String key1, String key2, int[] def) {
        String val = p.getProperty(key1, p.getProperty(key2));
        if (val == null || val.trim().isEmpty()) return def;
        String[] parts = val.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return result;
    }
}
