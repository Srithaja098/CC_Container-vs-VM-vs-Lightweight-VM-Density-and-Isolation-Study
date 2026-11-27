package isolationstudy;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;

/** Measures energy for long-running tenant jobs at two packing policies. */
public final class Exp5_Energy implements Experiment {
    private static final int[] TENANT_COUNTS = {8, 16, 32, 64, 128};
    private static final int REPETITIONS = 5;
    private static final int MAX_DENSITY = 0;
    private static final int CPU_SAFE = 1;

    @Override public String name() { return "energy"; }

    @Override
    public ExperimentResult run(SimConfig cfg) {
        ExperimentResult result = new ExperimentResult(name());
        for (IsolationLevel level : IsolationLevel.values()) {
            int footprintMb = cfg.getInstanceAppRamMb()
                    + IsolationProfile.of(level, cfg).getMemoryOverheadMb();
            int density = level == IsolationLevel.FULL_VM ? 8
                    : level == IsolationLevel.CONTAINER ? 128 : 64;
            for (int packing = MAX_DENSITY; packing <= CPU_SAFE; packing++) {
                String packingName = packing == MAX_DENSITY ? "max_density" : "cpu_safe";
                for (int tenants : TENANT_COUNTS) {
                    int hosts = packing == MAX_DENSITY
                            ? ceilDiv(tenants, density) : ceilDiv(tenants, 8);
                    SimConfig runCfg = cfg.withHostCount(hosts);
                    if (packing == CPU_SAFE) {
                        // Host RAM differs between levels only in cpu_safe, to fit eight instances.
                        runCfg = runCfg.withHostRamMb(8 * footprintMb);
                    }
                    for (int replicate = 1; replicate <= REPETITIONS; replicate++) {
                        Scenario scenario = SimulationFactory.build(level, runCfg);
                        List<Cloudlet> jobs = new ArrayList<Cloudlet>();
                        for (int i = 0; i < tenants; i++) {
                            IsolatedVm vm = scenario.addInstance();
                            // Long jobs keep the 30 s full-VM start-up from dominating this energy comparison.
                            jobs.add(scenario.addCloudlet(vm, 1_000_000L, 2));
                        }
                        scenario.run();
                        if (!scenario.getRejectedVmIds().isEmpty()) {
                            throw new IllegalStateException("unexpected rejected instances in "
                                    + level.label() + "/" + packingName + "/" + tenants + ": "
                                    + scenario.getRejectedVmIds());
                        }
                        if (scenario.getFinishedCount() != tenants) {
                            throw new IllegalStateException("expected " + tenants + " finished jobs, got "
                                    + scenario.getFinishedCount());
                        }
                        double slowdownSum = 0.0;
                        for (Cloudlet job : jobs) slowdownSum += scenario.slowdown(job);
                        double meanSlowdown = slowdownSum / jobs.size();
                        result.addRow("level", level.label(),
                                "packing", packingName,
                                "tenants", tenants,
                                "replicate", replicate,
                                "hosts_powered", scenario.getPoweredHostCount(),
                                "makespan_s", scenario.getMakespanS(),
                                "energy_j", scenario.getEnergyJ(),
                                "energy_per_job_j", scenario.getEnergyPerCloudletJ(),
                                "avg_power_w", scenario.getAvgPowerW(),
                                "mean_slowdown_x", meanSlowdown);

                        /* Hand check: one powered host at full utilisation for T seconds has
                         * E = sum_hosts[P_idle*makespan + (P_max-P_idle)*u*T]. For the N=8
                         * full_vm/max_density row, all eight two-core jobs fill one 16-core host;
                         * once running, u=1, so E = 100*makespan + 200*(makespan-30) J.
                         * The first 30 s contributes idle power only, as required by the model.
                         */
                    }
                }
            }
        }
        return result;
    }

    private static int ceilDiv(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
