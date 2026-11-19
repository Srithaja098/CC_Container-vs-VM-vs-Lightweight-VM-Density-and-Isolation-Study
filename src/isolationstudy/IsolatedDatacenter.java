package isolationstudy;

import java.util.List;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

/**
 * Datacenter that keeps demand-based shares and finish-time events consistent.
 *
 * <p>Two changes to stock {@link Datacenter}, both needed so that a cloudlet's recorded finish time
 * is the true finish time under changing neighbour demand:
 * <ul>
 *   <li>{@link #processCloudletSubmit}: right after a cloudlet starts, demand has changed, so
 *       shares are recomputed and the next update event is scheduled from the new shares. (Stock
 *       CloudSim schedules it from the old shares, and for an idle instance from a share of zero,
 *       so no event would be scheduled at all.)</li>
 *   <li>{@link #updateCloudletProcessing}: stock CloudSim skips an update that comes within
 *       {@code minTimeBetweenEvents} of the previous one and pushes the next event at least 0.11 s
 *       ahead, which rounds finish times up. Here an update runs once per distinct simulation
 *       instant and the next event is scheduled exactly at the earliest expected finish.</li>
 * </ul>
 */
public class IsolatedDatacenter extends Datacenter {

    private double lastProcessedInstant = Double.NEGATIVE_INFINITY;
    private double lastScheduledEvent = Double.NEGATIVE_INFINITY;

    public IsolatedDatacenter(String name, DatacenterCharacteristics characteristics,
                              VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList,
                              double schedulingInterval) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
    }

    @Override
    protected void updateCloudletProcessing() {
        double now = CloudSim.clock();
        if (now == lastProcessedInstant) {
            return;                                   // nothing can have elapsed since the last pass
        }
        lastProcessedInstant = now;
        double next = Double.MAX_VALUE;
        List<? extends Host> hosts = getVmAllocationPolicy().getHostList();
        for (Host host : hosts) {
            double time = host.updateVmsProcessing(now);
            if (time < next) {
                next = time;
            }
        }
        scheduleUpdate(now, next);
    }

    @Override
    protected void processCloudletSubmit(SimEvent ev, boolean ack) {
        super.processCloudletSubmit(ev, ack);         // updates to now, then starts the cloudlet
        double now = CloudSim.clock();
        double next = Double.MAX_VALUE;
        List<? extends Host> hosts = getVmAllocationPolicy().getHostList();
        for (Host host : hosts) {
            if (host instanceof IsolatedHost) {
                double time = ((IsolatedHost) host).refreshShares(now);
                if (time < next) {
                    next = time;
                }
            }
        }
        scheduleUpdate(now, next);
    }

    /** Schedules one VM_DATACENTER_EVENT at the earliest finish time (never in the past). */
    private void scheduleUpdate(double now, double nextFinish) {
        if (nextFinish == Double.MAX_VALUE) {
            return;
        }
        double target = Math.max(nextFinish, now + CloudSim.getMinTimeBetweenEvents());
        if (target == lastScheduledEvent) {
            return;                                   // identical event already queued
        }
        lastScheduledEvent = target;
        schedule(getId(), target - now, CloudSimTags.VM_DATACENTER_EVENT);
    }
}
