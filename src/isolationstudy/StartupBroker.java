package isolationstudy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.lists.VmList;

/**
 * Broker that applies each isolation level's start-up delay.
 *
 * <p>An instance is "ready" at {@code launch time + start-up delay of its level}, where launch time
 * is the simulation clock when the broker starts (0 s). A cloudlet bound for an instance is only
 * delivered to the datacenter at that moment, so it cannot start earlier. Everything else is stock
 * {@link DatacenterBroker} behaviour (VM creation, round-robin binding of unbound cloudlets,
 * completion handling).
 *
 * <p>Start-up is modelled as a pure delay per instance: all instances boot in parallel and a
 * fleet is ready when its slowest instance is ready. No boot-time contention is modelled.
 */
public class StartupBroker extends DatacenterBroker {

    private double launchTimeS = 0.0;
    /**
     * Instances that were created, in creation order. Kept here because stock CloudSim clears its own
     * created-VM list when the run ends, and results are read afterwards.
     */
    private final Map<Integer, Vm> createdVms = new LinkedHashMap<Integer, Vm>();

    public StartupBroker(String name) throws Exception {
        super(name);
    }

    /** Records each successful VM creation, then lets stock CloudSim carry on. */
    @Override
    protected void processVmCreate(SimEvent ev) {
        int[] data = (int[]) ev.getData();
        if (data[2] == CloudSimTags.TRUE) {
            Vm vm = VmList.getById(this.<Vm>getVmList(), data[1]);
            createdVms.put(vm.getId(), vm);
        }
        super.processVmCreate(ev);
    }

    @Override
    public void startEntity() {
        launchTimeS = CloudSim.clock();
        super.startEntity();
    }

    /** Simulation time at which the launch was requested. */
    public double getLaunchTimeS() {
        return launchTimeS;
    }

    /** Absolute simulation time at which this instance can run its first cloudlet. */
    public double getReadyTimeS(Vm vm) {
        double delay = (vm instanceof IsolatedVm) ? ((IsolatedVm) vm).getProfile().getStartupDelayS() : 0.0;
        return launchTimeS + delay;
    }

    /** Ready time of every instance that was actually created, keyed by VM id, in creation order. */
    public Map<Integer, Double> getReadyTimesS() {
        Map<Integer, Double> map = new LinkedHashMap<Integer, Double>();
        for (Vm vm : createdVms.values()) {
            map.put(vm.getId(), getReadyTimeS(vm));
        }
        return map;
    }

    /** Time until the whole created fleet is ready (slowest instance), seconds since launch. */
    public double getFleetReadyTimeS() {
        double latest = launchTimeS;
        for (Vm vm : createdVms.values()) {
            latest = Math.max(latest, getReadyTimeS(vm));
        }
        return latest - launchTimeS;
    }

    /** Number of instances that were created (valid after the run). */
    public int getCreatedCount() {
        return createdVms.size();
    }

    /** Ids of requested instances that no host accepted (valid after the run). */
    public List<Integer> getNotCreatedVmIds() {
        List<Integer> ids = new ArrayList<Integer>();
        for (Vm vm : this.<Vm>getVmList()) {
            if (!createdVms.containsKey(vm.getId())) {
                ids.add(vm.getId());
            }
        }
        return ids;
    }

    /** Same as the stock method, except each cloudlet is sent at max(now, ready time of its instance). */
    @Override
    protected void submitCloudlets() {
        int vmIndex = 0;
        List<Cloudlet> successfullySubmitted = new ArrayList<Cloudlet>();
        for (Cloudlet cloudlet : getCloudletList()) {
            Vm vm;
            if (cloudlet.getVmId() == -1) {            // not bound: round robin over created VMs
                vm = this.<Vm>getVmsCreatedList().get(vmIndex);
            } else {                                    // bound to a specific VM
                vm = VmList.getById(this.<Vm>getVmsCreatedList(), cloudlet.getVmId());
                if (vm == null) {                       // that VM was not created
                    continue;
                }
            }
            cloudlet.setVmId(vm.getId());
            double delay = Math.max(0.0, getReadyTimeS(vm) - CloudSim.clock());
            send(getVmsToDatacentersMap().get(vm.getId()), delay, CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
            cloudletsSubmitted++;
            vmIndex = (vmIndex + 1) % getVmsCreatedList().size();
            getCloudletSubmittedList().add(cloudlet);
            successfullySubmitted.add(cloudlet);
        }
        getCloudletList().removeAll(successfullySubmitted);
    }
}
