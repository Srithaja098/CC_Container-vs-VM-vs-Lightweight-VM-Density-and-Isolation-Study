package isolationstudy;

/** Measures fleet readiness for requested fleet sizes. */
public final class Exp2_Startup implements Experiment {
    private static final int[] FLEET_SIZES = {1, 8, 16, 32, 64};
    private static final int REPETITIONS = 5;

    @Override public String name() { return "startup"; }

    @Override
    public ExperimentResult run(SimConfig cfg) {
        ExperimentResult result = new ExperimentResult(name());
        for (IsolationLevel level : IsolationLevel.values()) {
            for (int fleetSize : FLEET_SIZES) {
                for (int replicate = 1; replicate <= REPETITIONS; replicate++) {
                    Scenario scenario = SimulationFactory.build(level, cfg);
                    for (int i = 0; i < fleetSize; i++) scenario.addInstance();
                    scenario.run();
                    result.addRow("level", level.label(),
                            "requested_fleet_size_instances", fleetSize,
                            "replicate", replicate,
                            "created_instances", scenario.getBroker().getCreatedCount(),
                            "fleet_ready_time_s", scenario.getBroker().getFleetReadyTimeS());
                }
            }
        }
        return result;
    }
}
