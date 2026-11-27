package isolationstudy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;

/**
 * Everything one simulation run needs, as returned by {@link SimulationFactory#build}:
 * datacenter, broker, hosts, instances (VMs) and cloudlets, plus the level's profile.
 *
 * <h3>Typical use (Person 2 / Person 3)</h3>
 * <pre>
 *   Scenario s = SimulationFactory.build(IsolationLevel.CONTAINER, cfg);
 *   IsolatedVm a = s.addInstance();
 *   IsolatedVm noisy = s.addInstance(8.0);        // asks for 8x the CPU it needs
 *   s.addCloudlet(a, 100000, 2);
 *   s.addCloudlet(noisy, 100000, 2);
 *   s.run();
 *   double slowdown = s.slowdown(s.getCloudlets().get(0));
 * </pre>
 *
 * <p><b>One live scenario at a time:</b> CloudSim 4.0 keeps its clock and entity list in static
 * fields, and {@code build} resets them. Finish (or discard) a scenario before building the next.
 * Its results stay readable after {@link #run()}.
 */
public final class Scenario {

    private final IsolationLevel level;
    private final IsolationProfile profile;
    private final SimConfig config;
    private final IsolatedDatacenter datacenter;
    private final StartupBroker broker;
    private final List<IsolatedHost> hosts;
    private final VmAllocationPolicyFirstFit allocationPolicy;
    private final Random random;
    private final PowerModel powerModel;

    /** Requested arrival time per cloudlet id (0 when added without one). */
    private final Map<Integer, Double> arrivals = new HashMap<Integer, Double>();
    private final List<IsolatedVm> vms = new ArrayList<IsolatedVm>();
    private final List<Cloudlet> cloudlets = new ArrayList<Cloudlet>();
    private boolean ran = false;

    Scenario(IsolationLevel level, IsolationProfile profile, SimConfig config,
             IsolatedDatacenter datacenter, StartupBroker broker, List<IsolatedHost> hosts,
             VmAllocationPolicyFirstFit allocationPolicy, Random random) {
        this.level = level;
        this.profile = profile;
        this.config = config;
        this.datacenter = datacenter;
        this.broker = broker;
        this.hosts = hosts;
        this.allocationPolicy = allocationPolicy;
        this.random = random;
        this.powerModel = PowerModel.fromConfig(config);
    }

    // ------------------------------------------------------------------ building

    /** Adds one standard-size instance of this scenario's level. Ids are 0, 1, 2, ... in call order. */
    public IsolatedVm addInstance() {
        return addInstance(1.0);
    }

    /** Adds an instance whose CPU demand is {@code demandMultiplier} times what its job needs. */
    public IsolatedVm addInstance(double demandMultiplier) {
        requireNotRun();
        IsolatedVm vm = new IsolatedVm(vms.size(), broker.getId(), profile, config);
        vm.setDemandMultiplier(demandMultiplier);
        vms.add(vm);
        broker.submitVmList(Collections.singletonList(vm));
        return vm;
    }

    /**
     * Adds a cloudlet bound to {@code vm}. {@code lengthMi} is the work of ONE vCPU in million
     * instructions; a cloudlet with {@code pes} vCPUs does {@code lengthMi} on each.
     */
    public Cloudlet addCloudlet(IsolatedVm vm, long lengthMi, int pes) {
        requireNotRun();
        Cloudlet c = new Cloudlet(cloudlets.size(), lengthMi, pes, 0L, 0L,
                new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
        c.setUserId(broker.getId());
        c.setVmId(vm.getId());
        cloudlets.add(c);
        broker.submitCloudletList(Collections.singletonList(c));
        return c;
    }

    /**
     * Adds a cloudlet that arrives at {@code arrivalS} (simulation seconds). It starts at the later of
     * its arrival and the moment its instance is ready, so a request that arrives before the instance
     * has started waits, and that wait is part of its response time.
     */
    public Cloudlet addCloudlet(IsolatedVm vm, long lengthMi, int pes, double arrivalS) {
        if (arrivalS < 0.0) {
            throw new IllegalArgumentException("arrival time must be >= 0, got " + arrivalS);
        }
        Cloudlet c = addCloudlet(vm, lengthMi, pes);
        arrivals.put(c.getCloudletId(), arrivalS);
        broker.setArrivalS(c.getCloudletId(), arrivalS);
        return c;
    }

    // ------------------------------------------------------------------ running

    /** Runs the simulation to completion. Can be called once. */
    public void run() {
        requireNotRun();
        ran = true;
        CloudSim.startSimulation();
        CloudSim.stopSimulation();
    }

    private void requireNotRun() {
        if (ran) {
            throw new IllegalStateException("scenario already ran; build a new one");
        }
    }

    // ------------------------------------------------------------------ results

    /** Run time of a finished cloudlet: finish time minus the time it started executing, seconds. */
    public double runtimeS(Cloudlet c) {
        return c.getFinishTime() - c.getExecStartTime();
    }

    /**
     * Run time the cloudlet would have had alone on its instance at the same level: length divided
     * by the vCPU speed after CPU overhead. This is the 1.00 reference for slowdown.
     */
    public double idealRuntimeS(Cloudlet c) {
        IsolatedVm vm = vms.get(c.getVmId());
        return c.getCloudletLength() / profile.effectiveMips(vm.getMips());
    }

    /** Slowdown = run time / run time alone. 1.00 means no interference. */
    public double slowdown(Cloudlet c) {
        return runtimeS(c) / idealRuntimeS(c);
    }

    /** Instances that were actually placed, in placement order. Valid after {@link #run()} too. */
    public List<IsolatedVm> getPlacedVms() {
        List<IsolatedVm> placed = new ArrayList<IsolatedVm>();
        for (Integer id : allocationPolicy.getPlacedVmIds()) {
            placed.add(vms.get(id));
        }
        return placed;
    }

    /** Ids of instances no host accepted (for example the 9th pinned full VM). */
    public List<Integer> getRejectedVmIds() {
        return allocationPolicy.getRejectedVmIds();
    }

    /**
     * Memory used by the placed instances (application + per-instance overhead), MB. Computed from
     * the placement record, because CloudSim frees host RAM when the run ends.
     */
    public int getPlacedFootprintMb() {
        int used = 0;
        for (IsolatedVm vm : getPlacedVms()) {
            used += vm.getFootprintMb();
        }
        return used;
    }

    // ------------------------------------------------------------------ latency

    private void requireRan() {
        if (!ran) {
            throw new IllegalStateException("call run() before reading results");
        }
    }

    private static boolean finished(Cloudlet c) {
        return c.getStatus() == Cloudlet.SUCCESS;
    }

    /** Requested arrival time of a cloudlet, seconds (0 when none was given). */
    public double arrivalS(Cloudlet c) {
        Double a = arrivals.get(c.getCloudletId());
        return a == null ? 0.0 : a;
    }

    /** Response time of a finished cloudlet: finish time minus arrival, seconds (includes any start-up wait). */
    public double responseTimeS(Cloudlet c) {
        return c.getFinishTime() - arrivalS(c);
    }

    /** Response times of all finished cloudlets, in cloudlet order. Rejected instances' cloudlets never run and are skipped. */
    public List<Double> getResponseTimesS() {
        requireRan();
        List<Double> out = new ArrayList<Double>();
        for (Cloudlet c : cloudlets) {
            if (finished(c)) {
                out.add(responseTimeS(c));
            }
        }
        return out;
    }

    public double getMeanResponseS() {
        return LatencyStats.mean(getResponseTimesS());
    }

    /** Nearest-rank percentile of response time, {@code 0 < p <= 100} (see {@link LatencyStats}). */
    public double getPercentileResponseS(double p) {
        return LatencyStats.percentile(getResponseTimesS(), p);
    }

    /** Number of cloudlets that finished. */
    public int getFinishedCount() {
        requireRan();
        int n = 0;
        for (Cloudlet c : cloudlets) {
            if (finished(c)) {
                n++;
            }
        }
        return n;
    }

    /** Time the last cloudlet finished, seconds (0 if none finished). */
    public double getMakespanS() {
        requireRan();
        double last = 0.0;
        for (Cloudlet c : cloudlets) {
            if (finished(c)) {
                last = Math.max(last, c.getFinishTime());
            }
        }
        return last;
    }

    // ------------------------------------------------------------------ energy

    /** Hosts that received at least one instance; the others count as switched off. */
    public int getPoweredHostCount() {
        requireRan();
        return allocationPolicy.getUsedHostIds().size();
    }

    /**
     * Energy of all powered hosts from time 0 to the makespan, joules: idle power for the whole
     * period (including start-up) plus (P_max - P_idle) times the integral of CPU utilisation.
     */
    public double getEnergyJ() {
        requireRan();
        double makespan = getMakespanS();
        double total = 0.0;
        for (Integer hostId : allocationPolicy.getUsedHostIds()) {
            total += powerModel.energyJ(makespan, hosts.get(hostId).getUtilisationSeconds());
        }
        return total;
    }

    public double getEnergyKWh() {
        return getEnergyJ() / 3.6e6;
    }

    /** Mean power of all powered hosts over the run, watts (0 if nothing finished). */
    public double getAvgPowerW() {
        double makespan = getMakespanS();
        return makespan > 0.0 ? getEnergyJ() / makespan : 0.0;
    }

    /** Energy divided by the number of finished cloudlets, joules (NaN if none finished). */
    public double getEnergyPerCloudletJ() {
        int n = getFinishedCount();
        return n > 0 ? getEnergyJ() / n : Double.NaN;
    }

    /** Energy (J) times mean response time (s): lower is better on both axes at once. */
    public double getEnergyDelayProduct() {
        return getEnergyJ() * getMeanResponseS();
    }

    public PowerModel getPowerModel() { return powerModel; }

    // ------------------------------------------------------------------ parts

    public IsolationLevel getLevel() { return level; }
    public IsolationProfile getProfile() { return profile; }
    public SimConfig getConfig() { return config; }
    public IsolatedDatacenter getDatacenter() { return datacenter; }
    public StartupBroker getBroker() { return broker; }
    public List<IsolatedHost> getHosts() { return hosts; }
    public VmAllocationPolicyFirstFit getAllocationPolicy() { return allocationPolicy; }
    public List<IsolatedVm> getVms() { return vms; }
    public List<Cloudlet> getCloudlets() { return cloudlets; }

    /** Random source seeded with 42 (the model itself uses none; this is for workload generators). */
    public Random getRandom() { return random; }
}
