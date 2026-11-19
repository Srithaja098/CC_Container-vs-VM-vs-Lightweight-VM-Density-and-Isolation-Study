package isolationstudy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.lists.PeList;

/**
 * Host CPU scheduler that shares MIPS by <b>actual demand</b>, so a busy neighbour slows others.
 *
 * <h3>How it works</h3>
 * Every call to {@link #recompute()} takes a snapshot of who is running and re-derives each
 * instance's MIPS from scratch:
 * <ol>
 *   <li><b>PINNED</b> instances (full VMs) own whole physical cores. They always get
 *       {@code vCPU MIPS x (1 - CPU overhead)} per vCPU, busy or idle, and their cores are
 *       removed from the common pool.</li>
 *   <li>Every other instance has a <b>demand</b>: 0 if it has no running cloudlet, otherwise
 *       {@code MIPS per vCPU x busy vCPUs x demandMultiplier}. For CAPPED instances the demand is
 *       clipped to {@code MIPS per vCPU x busy vCPUs} (the cap).</li>
 *   <li>The common pool is the host's MIPS minus the pinned cores. If total demand fits the pool
 *       everyone gets what they ask for. Otherwise everyone's grant is scaled by the same factor
 *       {@code pool / total demand}: sharing proportional to demand.</li>
 *   <li>The guest sees its grant times {@code (1 - CPU overhead)}; the lost part is burnt on the
 *       host (it is not handed to neighbours).</li>
 * </ol>
 *
 * <h3>Difference from {@link org.cloudbus.cloudsim.VmSchedulerTimeShared}</h3>
 * <ul>
 *   <li><b>When shares are decided.</b> TimeShared fixes each VM's MIPS once, at VM creation, from
 *       the requested MIPS, and returns that same list forever. Here the shares are recomputed on
 *       every host update from the current demand (see {@link IsolatedHost}).</li>
 *   <li><b>Interference.</b> TimeShared never lets one VM affect another once created, so noisy
 *       neighbours are invisible. Here, total demand above the pool scales everybody down.</li>
 *   <li><b>Admission.</b> TimeShared refuses a VM when the sum of requested MIPS exceeds the host
 *       ("does not support over-subscription"). Here only PINNED instances need free cores;
 *       UNCAPPED and CAPPED instances may over-subscribe CPU, so memory (RAM provisioner) decides
 *       how many containers fit.</li>
 *   <li><b>Idle instances.</b> TimeShared keeps MIPS reserved for idle VMs. Here an idle
 *       UNCAPPED/CAPPED instance holds nothing; an idle PINNED one still owns its cores.</li>
 *   <li><b>Overhead.</b> TimeShared hands over the full MIPS. Here the level's CPU overhead is
 *       taken off what the guest sees.</li>
 * </ul>
 */
public class VmSchedulerDemandShared extends VmScheduler {

    /** Registered instances in creation order (LinkedHashMap keeps runs deterministic). */
    private final Map<String, IsolatedVm> instances = new LinkedHashMap<String, IsolatedVm>();
    private int pinnedPesInUse = 0;
    private double shareFactor = 1.0;

    public VmSchedulerDemandShared(List<? extends Pe> peList) {
        super(peList);
    }

    // ------------------------------------------------------------------ admission

    @Override
    public boolean allocatePesForVm(Vm vm, List<Double> mipsShareRequested) {
        if (!(vm instanceof IsolatedVm)) {
            throw new IllegalArgumentException("VmSchedulerDemandShared only accepts IsolatedVm, got "
                    + vm.getClass().getName() + ". Build instances with SimulationFactory / Scenario.");
        }
        IsolatedVm instance = (IsolatedVm) vm;
        if (instances.containsKey(vm.getUid())) {
            recompute();
            return true;
        }

        // a vCPU cannot be faster than a physical core (same rule as VmSchedulerTimeShared)
        double peMips = getPeCapacity();
        for (Double mips : mipsShareRequested) {
            if (mips > peMips) {
                return false;
            }
        }

        if (instance.getCapPolicy() == CapPolicy.PINNED) {
            int needed = vm.getNumberOfPes();
            List<Pe> assigned = new ArrayList<Pe>();
            for (Pe pe : getPeList()) {
                if (assigned.size() == needed) {
                    break;
                }
                if (pe.getStatus() == Pe.FREE) {
                    pe.setStatusBusy();
                    assigned.add(pe);
                }
            }
            if (assigned.size() < needed) {          // not enough free cores: reject
                for (Pe pe : assigned) {
                    pe.setStatusFree();
                }
                return false;
            }
            getPeMap().put(vm.getUid(), assigned);
            pinnedPesInUse += needed;
        }
        // UNCAPPED and CAPPED instances are always admitted by the CPU scheduler (over-subscription allowed).

        instances.put(vm.getUid(), instance);
        recompute();
        return true;
    }

    @Override
    public void deallocatePesForVm(Vm vm) {
        IsolatedVm instance = instances.remove(vm.getUid());
        getMipsMap().remove(vm.getUid());
        List<Pe> pes = getPeMap().remove(vm.getUid());
        if (instance != null && instance.getCapPolicy() == CapPolicy.PINNED && pes != null) {
            for (Pe pe : pes) {
                pe.setStatusFree();
            }
            pinnedPesInUse -= pes.size();
        }
        recompute();
    }

    @Override
    public void deallocatePesForAllVms() {
        super.deallocatePesForAllVms();
        for (List<Pe> pes : getPeMap().values()) {
            for (Pe pe : pes) {
                pe.setStatusFree();
            }
        }
        getPeMap().clear();
        instances.clear();
        pinnedPesInUse = 0;
        shareFactor = 1.0;
        setAvailableMips(PeList.getTotalMips(getPeList()));
    }

    // ------------------------------------------------------------------ the sharing rule

    /**
     * Re-derives every instance's MIPS from the current demand snapshot. Called by
     * {@link IsolatedHost} whenever demand may have changed (cloudlet start/finish, VM create/destroy).
     * Results are read back through {@link #getAllocatedMipsForVm(Vm)}.
     */
    public void recompute() {
        double peMips = getPeCapacity();
        double pool = Math.max(0.0, PeList.getTotalMips(getPeList()) - pinnedPesInUse * peMips);
        setAvailableMips(pool);

        Map<String, Double> demand = new LinkedHashMap<String, Double>();
        double totalDemand = 0.0;
        for (IsolatedVm vm : instances.values()) {
            if (vm.getCapPolicy() == CapPolicy.PINNED) {
                continue;
            }
            double d = getDemandMips(vm);
            demand.put(vm.getUid(), d);
            totalDemand += d;
        }
        shareFactor = (totalDemand > pool && totalDemand > 0.0) ? pool / totalDemand : 1.0;

        Map<String, List<Double>> grants = getMipsMap();
        grants.clear();
        for (IsolatedVm vm : instances.values()) {
            int pes = vm.getNumberOfPes();
            double guestFraction = 1.0 - vm.getProfile().cpuOverheadFraction();
            List<Double> list = new ArrayList<Double>(pes);
            if (vm.getCapPolicy() == CapPolicy.PINNED) {
                for (int i = 0; i < pes; i++) {
                    list.add(vm.getMips() * guestFraction);
                }
            } else {
                // Spread the grant over the vCPUs that are actually busy, so a cloudlet that uses
                // k vCPUs runs at grant/k per vCPU (CloudletSchedulerTimeShared divides by busy PEs).
                int busy = busyVcpus(vm);
                // A job cannot run faster than the vCPUs it has: grant above nominal (noisy neighbour
                // spinning / extra threads) is burnt on the host and does not speed the job up.
                double granted = demand.get(vm.getUid()) * shareFactor;
                double useful = Math.min(granted, vm.getMips() * busy);
                double guestTotal = useful * guestFraction;
                for (int i = 0; i < pes; i++) {
                    list.add(i < busy ? guestTotal / busy : 0.0);
                }
            }
            grants.put(vm.getUid(), list);
        }
    }

    /**
     * Current CPU demand of a non-pinned instance, MIPS. 0 when nothing is running; otherwise
     * MIPS per vCPU x busy vCPUs x demand multiplier, with the multiplier ignored for CAPPED
     * instances (the cap equals the busy vCPUs' nominal speed).
     */
    public double getDemandMips(IsolatedVm vm) {
        int busy = busyVcpus(vm);
        if (busy == 0) {
            return 0.0;
        }
        double nominal = vm.getMips() * busy;
        return vm.getCapPolicy() == CapPolicy.CAPPED ? nominal : nominal * vm.getDemandMultiplier();
    }

    /** Number of vCPUs the running cloudlets can use (cloudlet PEs, at most the instance's vCPUs). */
    private int busyVcpus(IsolatedVm vm) {
        int n = 0;
        List<ResCloudlet> running = vm.getCloudletScheduler().getCloudletExecList();
        for (ResCloudlet rcl : running) {
            n += rcl.getNumberOfPes();
        }
        return Math.min(n, vm.getNumberOfPes());
    }

    // ------------------------------------------------------------------ read-outs

    /** MIPS of the common pool = host MIPS minus pinned cores, as of the last recompute. */
    public double getSharedPoolMips() {
        return getAvailableMips();
    }

    /** Scale factor applied to every non-pinned grant at the last recompute (1.0 = demand fully met). */
    public double getShareFactor() {
        return shareFactor;
    }

    public int getPinnedPesInUse() {
        return pinnedPesInUse;
    }

    @Override
    public double getMaxAvailableMips() {
        return getAvailableMips();
    }
}
