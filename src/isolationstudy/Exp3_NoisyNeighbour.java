package isolationstudy;

import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;

/** Compares victim slowdown with and without one longer, high-demand neighbour. */
public final class Exp3_NoisyNeighbour implements Experiment {
    private static final int REPETITIONS = 5;

    @Override public String name() { return "noisy_neighbour"; }

    @Override
    public ExperimentResult run(SimConfig cfg) {
        ExperimentResult result = new ExperimentResult(name());
        for (IsolationLevel level : IsolationLevel.values()) {
            double[] baselineByReplicate = new double[REPETITIONS];
            for (int replicate = 1; replicate <= REPETITIONS; replicate++) {
                baselineByReplicate[replicate - 1] = runOne(result, cfg, level, replicate,
                        "baseline", false, Double.NaN);
            }
            for (int replicate = 1; replicate <= REPETITIONS; replicate++) {
                runOne(result, cfg, level, replicate, "noisy_neighbour", true,
                        baselineByReplicate[replicate - 1]);
            }
        }
        return result;
    }

    private double runOne(ExperimentResult result, SimConfig cfg, IsolationLevel level,
                          int replicate, String condition, boolean addNeighbour,
                          double baselineSlowdown) {
        Scenario scenario = SimulationFactory.build(level, cfg);
        List<Cloudlet> victims = NoisyNeighbourWorkload.addVictims(scenario, cfg);
        if (addNeighbour) NoisyNeighbourWorkload.addNeighbour(scenario, cfg);
        scenario.run();

        double sum = 0.0;
        int finished = 0;
        for (Cloudlet cloudlet : victims) {
            if (cloudlet.getStatus() == Cloudlet.SUCCESS) {
                sum += scenario.slowdown(cloudlet);
                finished++;
            }
        }
        Double mean = finished == 0 ? null : Double.valueOf(sum / finished);
        Double relative = finished == 0 || Double.isNaN(baselineSlowdown) ? null
                : Double.valueOf((sum / finished) / baselineSlowdown);
        result.addRow("level", level.label(),
                "cap_policy", IsolationProfile.of(level, cfg).getCapPolicy().name().toLowerCase(),
                "condition", condition,
                "replicate", replicate,
                "victim_cloudlet_length_mi", NoisyNeighbourWorkload.VICTIM_LENGTH_MI,
                "neighbour_cloudlet_length_mi", addNeighbour
                        ? NoisyNeighbourWorkload.NEIGHBOUR_LENGTH_MI : null,
                "neighbour_demand_multiplier_x", addNeighbour
                        ? NoisyNeighbourWorkload.NEIGHBOUR_DEMAND_MULTIPLIER : null,
                "victim_cloudlets_finished", finished,
                "mean_victim_slowdown_x", mean,
                "slowdown_vs_baseline_x", relative);
        return finished == 0 ? Double.NaN : sum / finished;
    }
}
