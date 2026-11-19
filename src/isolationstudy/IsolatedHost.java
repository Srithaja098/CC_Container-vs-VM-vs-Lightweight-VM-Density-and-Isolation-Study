package isolationstudy;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

/**
 * Host that drives a {@link VmSchedulerDemandShared} correctly.
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
 */
public class IsolatedHost extends Host {

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
        getDemandScheduler().recompute();
        double smallest = Double.MAX_VALUE;
        for (Vm vm : this.<Vm>getVmList()) {
            double time = vm.updateVmProcessing(now, getVmScheduler().getAllocatedMipsForVm(vm));
            if (time > 0.0 && time < smallest) {
                smallest = time;
            }
        }
        return smallest;
    }
}
