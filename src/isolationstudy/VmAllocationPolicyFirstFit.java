package isolationstudy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;

/**
 * First-fit bin packing: an instance goes on the first host (in list order) that accepts it.
 *
 * <p>"Accepts" is decided by {@link Host#vmCreate(Vm)}: storage, RAM, bandwidth and the host's
 * {@link VmSchedulerDemandShared} (free cores for PINNED instances). Host 0 is therefore filled
 * completely before host 1 is touched, which makes the per-host density limit visible. A rejected
 * instance is remembered in {@link #getRejectedVmIds()}.
 *
 * <p>Stock {@code VmAllocationPolicySimple} instead picks the host with the most free PEs
 * (spreading load), which would hide the density limit.
 */
public class VmAllocationPolicyFirstFit extends VmAllocationPolicy {

    private final Map<String, Host> vmTable = new HashMap<String, Host>();
    private final List<Integer> rejectedVmIds = new ArrayList<Integer>();
    /** Every instance that was ever placed. Kept after the run: CloudSim destroys all VMs at the end. */
    private final List<Integer> placedVmIds = new ArrayList<Integer>();
    /** Ids of hosts that received at least one instance (these count as powered on). */
    private final Set<Integer> usedHostIds = new LinkedHashSet<Integer>();

    public VmAllocationPolicyFirstFit(List<? extends Host> list) {
        super(list);
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        if (vmTable.containsKey(vm.getUid())) {
            return false;                              // already placed
        }
        for (Host host : this.<Host>getHostList()) {
            if (host.vmCreate(vm)) {
                vmTable.put(vm.getUid(), host);
                placedVmIds.add(vm.getId());
                usedHostIds.add(host.getId());
                return true;
            }
        }
        rejectedVmIds.add(vm.getId());
        return false;
    }

    @Override
    public boolean allocateHostForVm(Vm vm, Host host) {
        if (vmTable.containsKey(vm.getUid())) {
            return false;
        }
        if (host.vmCreate(vm)) {
            vmTable.put(vm.getUid(), host);
            placedVmIds.add(vm.getId());
            usedHostIds.add(host.getId());
            return true;
        }
        rejectedVmIds.add(vm.getId());
        return false;
    }

    @Override
    public void deallocateHostForVm(Vm vm) {
        Host host = vmTable.remove(vm.getUid());
        if (host != null) {
            host.vmDestroy(vm);
        }
    }

    @Override
    public Host getHost(Vm vm) {
        return vmTable.get(vm.getUid());
    }

    @Override
    public Host getHost(int vmId, int userId) {
        return vmTable.get(Vm.getUid(userId, vmId));
    }

    /** No VM migration in this study. */
    @Override
    public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
        return null;
    }

    /** Ids of instances that were placed, in placement order (still readable after the run). */
    public List<Integer> getPlacedVmIds() {
        return placedVmIds;
    }

    /** Ids of hosts that received at least one instance, in first-use order (readable after the run). */
    public Set<Integer> getUsedHostIds() {
        return usedHostIds;
    }

    /** Ids of instances that no host could take, in the order they were refused. */
    public List<Integer> getRejectedVmIds() {
        return rejectedVmIds;
    }
}
