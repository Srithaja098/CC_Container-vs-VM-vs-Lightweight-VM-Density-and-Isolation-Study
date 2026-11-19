package isolationstudy;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Vm;

/**
 * One instance (full VM, container or lightweight VM). A normal CloudSim {@link Vm} that also
 * remembers its {@link IsolationProfile} so the host scheduler can apply the right cap policy
 * and CPU overhead.
 *
 * <p>The CloudSim "RAM" of the Vm is the placement footprint: application memory plus the level's
 * per-instance memory overhead. Inside the instance, cloudlets are time-shared by the stock
 * {@link CloudletSchedulerTimeShared}.
 *
 * <p>{@link #setDemandMultiplier(double)} is the hook for the noisy neighbour: 1.0 (default)
 * means the instance asks for exactly the vCPUs its running cloudlets use; 8.0 means it tries
 * to use eight times that (many threads, bursting). A PINNED instance ignores it (it only ever
 * owns its own cores) and a CAPPED instance has the demand clipped back to its provisioned vCPUs.
 */
public class IsolatedVm extends Vm {

    private final IsolationProfile profile;
    private final int appRamMb;
    private double demandMultiplier = 1.0;

    /** Instance of the standard size from the configuration. */
    public IsolatedVm(int id, int userId, IsolationProfile profile, SimConfig cfg) {
        this(id, userId, profile, cfg.getInstanceMipsPerVcpu(), cfg.getInstanceVcpus(),
                cfg.getInstanceAppRamMb(), cfg.getInstanceBw(), cfg.getInstanceImageSizeMb());
    }

    /** Instance with an explicit size (for experiments that vary instance size). */
    public IsolatedVm(int id, int userId, IsolationProfile profile, double mipsPerVcpu, int vcpus,
                      int appRamMb, long bw, long imageSizeMb) {
        super(id, userId, mipsPerVcpu, vcpus, appRamMb + profile.getMemoryOverheadMb(), bw,
                imageSizeMb, "isolationstudy-" + profile.getLevel().label(),
                new CloudletSchedulerTimeShared());
        this.profile = profile;
        this.appRamMb = appRamMb;
    }

    public IsolationProfile getProfile() { return profile; }
    public IsolationLevel getLevel() { return profile.getLevel(); }
    public CapPolicy getCapPolicy() { return profile.getCapPolicy(); }

    /** Application memory only (without the level's overhead), MB. */
    public int getAppRamMb() { return appRamMb; }

    /** Placement footprint = application memory + per-instance overhead, MB. */
    public int getFootprintMb() { return getRam(); }

    /** Provisioned CPU, MIPS (vCPUs x MIPS per vCPU), before overhead. */
    public double getNominalTotalMips() { return getMips() * getNumberOfPes(); }

    public double getDemandMultiplier() { return demandMultiplier; }

    public void setDemandMultiplier(double multiplier) {
        if (multiplier <= 0.0) {
            throw new IllegalArgumentException("demand multiplier must be > 0, got " + multiplier);
        }
        this.demandMultiplier = multiplier;
    }
}
