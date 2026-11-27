package isolationstudy;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.lists.PeList;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

/**
 * Host that drives a {@link VmSchedulerDemandShared} correctly and keeps the integral of its own
 * CPU utilisation for the energy model.
 *
 * <p>Stock {@link Host#updateVmsProcessing(double)} asks the scheduler for each VM's MIPS and
 * applies it to the <em>whole</em> time since the last update. That is only right if shares never
 * change between updates, and it also returns "next finish" estimates computed from shares that
 * are already out of date when a neighbour finishes in the same pass. With demand-based sharing
 * that would blur finish times, so this host works in three steps:
 * <ol>
 *   <li>advance every VM to now with the shares that held during the elapsed interval;</li>
 *   <li>recompute all shares from the new demand (some cloudlets may have just finished);</li>
 *   <li>hand the new shares to every VM at zero elapsed time, which yields correct next-finish
 *       estimates, and return the earliest one.</li>
 * </ol>
 * Steps 2 and 3 are also exposed as {@link #refreshShares(double)} for the moment a cloudlet is
 * submitted (see {@link IsolatedDatacenter}).
 *
 * <h3>Utilisation for energy</h3>
 * Shares, and so utilisation, only change at the instants where {@link #refreshShares} runs. The
 * host therefore keeps the utilisation set at the last refresh and, at the next one, adds
 * {@code utilisation x elapsed time} to {@link #getUtilisationSeconds()}. The sum is exact, not a
 * sampled estimate. Utilisation counts what the CPU actually burns: pinned cores running a job,
 * plus everything granted from the shared pool (including virtualisation overhead and a noisy
 * neighbour's spinning, and CPU stalled by uncore contention).
 */
public class IsolatedHost extends Host {

    private double lastAccrualTime = 0.0;
    private double lastUtilisation = 0.0;
    private double utilisationSeconds = 0.0;

    public IsolatedHost(int id, int ramMb, long bw, long storageMb, List<Pe> peList) {
        super(id, new RamProvisionerSimple(ramMb), new BwProvisionerSimple(bw), storageMb,
                peList, new VmSchedulerDemandShared(peList));
    }

    /** The demand-sharing scheduler of this host. */
    public VmSchedulerDemandShared getDemandScheduler() {
        return (VmSchedulerDemandShared) getVmScheduler();
    }

    @Override
    public double updateVmsProcessing(double currentTime) {
        accrue(currentTime);
        // step 1: progress with the shares that were valid over the elapsed interval
        for (Vm vm : this.<Vm>getVmList()) {
            vm.updateVmProcessing(currentTime, getVmScheduler().getAllocatedMipsForVm(vm));
        }
        // steps 2 and 3
        return refreshShares(currentTime);
    }

    /**
     * Recomputes shares from the current demand and returns the earliest expected cloudlet finish
     * time on this host (absolute simulation time), or {@link Double#MAX_VALUE} if nothing is running.
     * No simulated time passes in this call.
     */
    public double refreshShares(double now) {
        accrue(now);                                   // close the interval that just ended
        getDemandScheduler().recompute();
        lastUtilisation = hostUtilisation();           // utilisation that holds from now on
        double smallest = Double.MAX_VALUE;
        for (Vm vm : this.<Vm>getVmList()) {
            double time = vm.updateVmProcessing(now, getVmScheduler().getAllocatedMipsForVm(vm));
            if (time > 0.0 && time < smallest) {
                smallest = time;
            }
        }
        return smallest;
    }

    /** Adds the interval since the last accrual at the utilisation that held during it. */
    private void accrue(double now) {
        if (now > lastAccrualTime) {
            utilisationSeconds += lastUtilisation * (now - lastAccrualTime);
            lastAccrualTime = now;
        }
    }

    private double hostUtilisation() {
        double total = PeList.getTotalMips(getPeList());
        if (total <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, getDemandScheduler().getBusyMips() / total));
    }

    /** Integral of CPU utilisation (0..1) over time up to the last update, in seconds. */
    public double getUtilisationSeconds() {
        return utilisationSeconds;
    }

    /** Utilisation (0..1) that held after the last update. */
    public double getLastUtilisation() {
        return lastUtilisation;
    }
}
