package isolationstudy;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;

/** Shared fixed-size victim and noisy-neighbour workload for experiment 3. */
public final class NoisyNeighbourWorkload {
    public static final int VICTIM_COUNT = 4;
    public static final long VICTIM_LENGTH_MI = 100000L;
    public static final long NEIGHBOUR_LENGTH_MI = 1000000L;
    public static final double NEIGHBOUR_DEMAND_MULTIPLIER = 8.0;

    private NoisyNeighbourWorkload() { }

    public static List<Cloudlet> addVictims(Scenario scenario, SimConfig cfg) {
        List<Cloudlet> cloudlets = new ArrayList<Cloudlet>();
        for (int i = 0; i < VICTIM_COUNT; i++) {
            IsolatedVm victim = scenario.addInstance();
            cloudlets.add(scenario.addCloudlet(victim, VICTIM_LENGTH_MI,
                    cfg.getInstanceVcpus()));
        }
        return cloudlets;
    }

    public static Cloudlet addNeighbour(Scenario scenario, SimConfig cfg) {
        IsolatedVm neighbour = scenario.addInstance(NEIGHBOUR_DEMAND_MULTIPLIER);
        return scenario.addCloudlet(neighbour, NEIGHBOUR_LENGTH_MI,
                cfg.getInstanceVcpus());
    }
}
