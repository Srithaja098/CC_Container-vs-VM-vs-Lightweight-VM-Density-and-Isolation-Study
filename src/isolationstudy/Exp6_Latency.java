package isolationstudy;

import java.util.Random;

/** Compares paired request latency and energy over warm, noisy-neighbour, and cold starts. */
public final class Exp6_Latency implements Experiment {
    private static final int INSTANCES = 8;
    private static final int REQUESTS = 2000;
    private static final int REPETITIONS = 5;
    private static final int[] LOAD_PCT = {20, 40, 60, 80};
    private static final String[] SCENARIOS = {"warm", "noisy", "cold"};

    @Override public String name() { return "latency"; }

    @Override
    public ExperimentResult run(SimConfig cfg) {
        ExperimentResult result = new ExperimentResult(name());
        for (int loadPct : LOAD_PCT) {
            double load = loadPct / 100.0;
            // 8 instances * 2 vCPUs, each 2500 MIPS; a 500 MI, 1-vCPU request takes 0.2 s.
            double ratePerS = load * (INSTANCES * 2.0) / (500.0 / 2500.0);
            for (int replicate = 1; replicate <= REPETITIONS; replicate++) {
                double[] warmArrivals = Arrivals.poisson(new Random(42L + replicate), ratePerS,
                        REQUESTS, 30.0);
                double[] coldArrivals = new double[REQUESTS];
                double[] generatedCold = Arrivals.poisson(new Random(42L + replicate), ratePerS,
                        REQUESTS, 0.0);
                System.arraycopy(generatedCold, 0, coldArrivals, 0, REQUESTS);
                // warm/noisy share warmArrivals; cold has an independent start offset but identical Poisson gaps.
                for (IsolationLevel level : IsolationLevel.values()) {
                    for (String scenarioName : SCENARIOS) {
                        double[] arrivals = "cold".equals(scenarioName) ? coldArrivals : warmArrivals;
                        Scenario scenario = SimulationFactory.build(level, cfg.withHostCount(1));
                        IsolatedVm[] vms = new IsolatedVm[INSTANCES];
                        for (int i = 0; i < INSTANCES; i++) {
                            vms[i] = scenario.addInstance("noisy".equals(scenarioName) && i == 0 ? 8.0 : 1.0);
                        }
                        for (int request = 0; request < REQUESTS; request++) {
                            scenario.addCloudlet(vms[request % INSTANCES], 500L, 1, arrivals[request]);
                        }
                        scenario.run();
                        result.addRow("level", level.label(),
                                "scenario", scenarioName,
                                "load", load,
                                "replicate", replicate,
                                "requests", REQUESTS,
                                "finished", scenario.getFinishedCount(),
                                "mean_response_s", scenario.getMeanResponseS(),
                                "p50_response_s", scenario.getPercentileResponseS(50.0),
                                "p95_response_s", scenario.getPercentileResponseS(95.0),
                                "p99_response_s", scenario.getPercentileResponseS(99.0),
                                "energy_j", scenario.getEnergyJ(),
                                "energy_delay_product", scenario.getEnergyDelayProduct());
                    }
                }
            }
        }
        return result;
    }
}
