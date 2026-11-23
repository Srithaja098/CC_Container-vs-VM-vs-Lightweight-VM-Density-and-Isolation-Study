package isolationstudy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.lists.PeList;

/**
 * Extends Person 1's {@link VmSchedulerDemandShared} to model uncore resource contention
 * (Memory-Bandwidth saturation and Storage/Network I/O bus queuing) in addition to CPU sharing.
 *
 * <p>Physical rationale:
 * In modern multicore cloud servers, CPU cores may be partitioned or pinned, but tenants
 * share common hardware uncore structures: DDR memory channels, memory controllers, L3 cache
 * bus, and PCIe/NVMe storage controllers. High tenant density and aggressive noisy neighbours
 * cause memory bus saturation and I/O queueing delay.
 *
 * <p>Tenancy model:
 * <ul>
 *   <li><b>FULL_VM (PINNED):</b> CPU cores are private, but memory bus arbitration delay from
 *       co-located noisy tenants imposes a mild uncore penalty (e.g. 2-5%).</li>
 *   <li><b>LIGHTWEIGHT_VM (CAPPED):</b> MicroVM boundary enforces memory limits and capped vCPUs;
 *       incurs moderate uncore contention (e.g. 5-12%).</li>
 *   <li><b>CONTAINER (UNCAPPED):</b> Shares host kernel page cache, socket buffers, and memory bus
 *       without hardware virtualization boundaries; incurs and generates severe uncore contention
 *       (e.g. 15-40%).</li>
 * </ul>
 */
public class ContendedVmScheduler extends VmSchedulerDemandShared {

    private final double memContentionWeight;
    private final double ioContentionWeight;
    private final Map<String, IsolatedVm> instanceRegistry = new LinkedHashMap<String, IsolatedVm>();

    public ContendedVmScheduler(List<? extends Pe> peList, double memContentionWeight, double ioContentionWeight) {
        super(peList);
        this.memContentionWeight = Math.max(0.0, memContentionWeight);
        this.ioContentionWeight = Math.max(0.0, ioContentionWeight);
    }

    public ContendedVmScheduler(List<? extends Pe> peList) {
        this(peList, 0.15, 0.20);
    }

    @Override
    public boolean allocatePesForVm(Vm vm, List<Double> mipsShareRequested) {
        boolean ok = super.allocatePesForVm(vm, mipsShareRequested);
        if (ok && (vm instanceof IsolatedVm)) {
            instanceRegistry.put(vm.getUid(), (IsolatedVm) vm);
            recompute();
        }
        return ok;
    }

    @Override
    public void deallocatePesForVm(Vm vm) {
        instanceRegistry.remove(vm.getUid());
        super.deallocatePesForVm(vm);
    }

    @Override
    public void deallocatePesForAllVms() {
        instanceRegistry.clear();
        super.deallocatePesForAllVms();
    }

    @Override
    public void recompute() {
        // Step 1: Execute Person 1's baseline CPU demand-sharing calculation
        super.recompute();

        if (instanceRegistry.isEmpty()) {
            return;
        }

        // Step 2: Compute aggregate uncore bus load (active tenants, memory load, I/O burst pressure)
        int activeCount = 0;
        int activeContainers = 0;
        double activeDemandMips = 0.0;
        double totalHostMips = PeList.getTotalMips(getPeList());

        for (IsolatedVm vm : instanceRegistry.values()) {
            int busy = countBusyVcpus(vm);
            if (busy > 0) {
                activeCount++;
                activeDemandMips += getDemandMips(vm);
                if (vm.getCapPolicy() == CapPolicy.UNCAPPED) {
                    activeContainers++;
                }
            }
        }

        if (activeCount == 0 || totalHostMips <= 0.0) {
            return;
        }

        // Memory bus saturation ratio: proportion of active tenant pressure relative to host core capacity
        double memSaturationRatio = Math.min(2.0, (double) activeCount / (double) getPeList().size());
        // I/O burst pressure: ratio of aggregate demand to host physical capacity
        double ioPressureRatio = Math.min(3.0, activeDemandMips / totalHostMips);

        // Step 3: Apply uncore contention penalty to the MIPS allocated to each VM
        Map<String, List<Double>> grants = getMipsMap();

        for (IsolatedVm vm : instanceRegistry.values()) {
            List<Double> currentGrant = grants.get(vm.getUid());
            if (currentGrant == null || currentGrant.isEmpty()) {
                continue;
            }

            double penaltyFactor = computeContentionPenalty(vm, memSaturationRatio, ioPressureRatio, activeContainers);

            List<Double> contendedGrant = new ArrayList<Double>(currentGrant.size());
            for (Double mips : currentGrant) {
                // Effective MIPS delivered to the guest is reduced by uncore contention
                contendedGrant.add(mips / penaltyFactor);
            }
            grants.put(vm.getUid(), contendedGrant);
        }
    }

    /**
     * Computes the uncore contention penalty factor for a tenant (>= 1.00).
     */
    public double computeContentionPenalty(IsolatedVm vm, double memSaturationRatio,
                                           double ioPressureRatio, int activeContainers) {
        double memWeight;
        double ioWeight;

        switch (vm.getCapPolicy()) {
            case PINNED:
                // Full VM: private CPU cores, but shares DDR memory bus and storage controller
                memWeight = 0.20;
                ioWeight = 0.15;
                break;
            case CAPPED:
                // Lightweight VM: microVM isolation with strict memory limit and CPU cap
                memWeight = 0.45;
                ioWeight = 0.40;
                break;
            case UNCAPPED:
            default:
                // Container: shared host kernel page cache, highest uncore contention
                memWeight = 1.00;
                ioWeight = 1.00;
                break;
        }

        // Noise multiplier if aggressive uncapped containers are bursting on the same host
        double containerNoiseFactor = 1.0 + (activeContainers > 4 ? 0.10 * Math.log(activeContainers) : 0.0);

        double penalty = 1.0 + (memContentionWeight * memWeight * memSaturationRatio)
                + (ioContentionWeight * ioWeight * ioPressureRatio * containerNoiseFactor);

        return Math.max(1.0, penalty);
    }

    private int countBusyVcpus(IsolatedVm vm) {
        int n = 0;
        List<ResCloudlet> running = vm.getCloudletScheduler().getCloudletExecList();
        for (ResCloudlet rcl : running) {
            n += rcl.getNumberOfPes();
        }
        return Math.min(n, vm.getNumberOfPes());
    }

    public double getMemContentionWeight() {
        return memContentionWeight;
    }

    public double getIoContentionWeight() {
        return ioContentionWeight;
    }
}

