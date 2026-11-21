package isolationstudy;

/** Finds the maximum placed instance count on the configured host set for each level. */
public final class Exp1_Density implements Experiment {
    private static final int REPETITIONS = 5;

    @Override public String name() { return "density"; }

    @Override
    public ExperimentResult run(SimConfig cfg) {
        ExperimentResult result = new ExperimentResult(name());
        for (IsolationLevel level : IsolationLevel.values()) {
            IsolationProfile profile = IsolationProfile.of(level, cfg);
            int footprintMb = cfg.getInstanceAppRamMb() + profile.getMemoryOverheadMb();
            int maxAttempts = Math.max(2, cfg.getHostRamMb() / footprintMb + 2);
            for (int replicate = 1; replicate <= REPETITIONS; replicate++) {
                int requestCount = 1;
                int placed;
                int rejectedId;
                while (true) {
                    if (requestCount > maxAttempts) {
                        throw new IllegalStateException("no density rejection for " + level
                                + " within RAM-derived attempt bound " + maxAttempts);
                    }
                    // CloudSim placement outcomes are available after run(), so each candidate
                    // count is evaluated in its own fresh scenario.
                    Scenario scenario = SimulationFactory.build(level, cfg);
                    for (int i = 0; i < requestCount; i++) scenario.addInstance();
                    scenario.run();
                    if (!scenario.getRejectedVmIds().isEmpty()) {
                        placed = scenario.getPlacedVms().size();
                        rejectedId = scenario.getRejectedVmIds().get(0);
                        break;
                    }
                    requestCount++;
                }
                result.addRow("level", level.label(),
                        "replicate", replicate,
                        "host_count", cfg.getHostCount(),
                        "placed_instances_per_host", cfg.getHostCount() == 1 ? placed : null,
                        "placed_instances_total", placed,
                        "first_rejected_instance_id", rejectedId,
                        "instance_footprint_mb", footprintMb);
            }
        }
        return result;
    }
}
