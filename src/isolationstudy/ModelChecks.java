package isolationstudy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.cloudbus.cloudsim.Cloudlet;

/**
 * Self-checks of the core model. Run without Main:
 * <pre>java -cp "bin:lib/*" isolationstudy.ModelChecks</pre>
 * Prints PASS/FAIL per check and exits with status 1 if any check fails.
 *
 * <p>Required checks: (1) a lone instance has slowdown 1.00, (2) the 9th pinned full VM is rejected
 * on a host with 8 slots, (3) two runs give identical results. The rest compare the simulation with
 * numbers worked out by hand (the guide's "hand validation").
 */
public final class ModelChecks {

    /** Tolerance for exact-by-construction values (lone slowdown, start times). */
    private static final double EPS = 1e-6;
    /** Tolerance under contention: CloudSim counts whole MI, which gives errors near 1e-6. */
    private static final double EPS_SHARED = 1e-5;
    private static final long LENGTH_MI = 100000L;     // work of one vCPU, million instructions

    private static int failures = 0;

    public static void main(String[] args) {
        SimConfig cfg = SimConfig.defaults();
        System.out.println("Host used: " + cfg.getHostCount() + " host(s), " + cfg.getHostCores()
                + " cores x " + cfg.getHostMipsPerCore() + " MIPS, " + cfg.getHostRamMb() + " MB RAM; seed "
                + cfg.getSeed());

        // ---- required checks
        loneInstanceSlowdownIsOne(cfg);
        ninthPinnedVmIsRejected(cfg);
        twoRunsAreIdentical(cfg);

        // ---- hand-calculation checks
        densityMatchesRamAndCoreArithmetic(cfg);
        oversubscriptionScalesByDemand(cfg);
        pinnedIgnoresNoisyNeighbour(cfg);
        cappedIgnoresNoisyNeighbour(cfg);
        uncappedFeelsNoisyNeighbour(cfg);
        startupDelayIsApplied(cfg);

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    // ================================================================== required

    /** 1. A lone instance has slowdown exactly 1.00, at every level, for 1-vCPU and 2-vCPU jobs. */
    private static void loneInstanceSlowdownIsOne(SimConfig cfg) {
        for (IsolationLevel level : IsolationLevel.values()) {
            for (int pes = 1; pes <= 2; pes++) {
                Scenario s = SimulationFactory.build(level, cfg);
                IsolatedVm vm = s.addInstance();
                Cloudlet c = s.addCloudlet(vm, LENGTH_MI, pes);
                s.run();
                double sd = s.slowdown(c);
                check("lone " + level + " " + pes + "-vCPU job: slowdown 1.00",
                        s.getBroker().getCloudletReceivedList().size() == 1 && Math.abs(sd - 1.0) < EPS,
                        fmt(sd));
            }
        }
    }

    /** 2. With 16 cores and 2-vCPU pinned VMs there are 8 slots; the 9th is rejected. */
    private static void ninthPinnedVmIsRejected(SimConfig cfg) {
        int slots = cfg.getHostCores() / cfg.getInstanceVcpus();
        Scenario s = SimulationFactory.build(IsolationLevel.FULL_VM, cfg, slots + 1);
        for (IsolatedVm vm : s.getVms()) {
            s.addCloudlet(vm, LENGTH_MI, cfg.getInstanceVcpus());
        }
        s.run();
        List<Integer> rejected = s.getRejectedVmIds();
        check("host has " + slots + " slots", slots == 8, "slots=" + slots);
        check("9th pinned full VM rejected (placed=" + s.getPlacedVms().size() + ", rejected=" + rejected + ")",
                s.getPlacedVms().size() == slots && rejected.equals(Arrays.asList(slots))
                        && s.getBroker().getNotCreatedVmIds().equals(Arrays.asList(slots)),
                "placed=" + s.getPlacedVms().size());
        boolean placedFinished = s.getBroker().getCloudletReceivedList().size() == slots;
        check("the 8 placed VMs still ran their cloudlets", placedFinished,
                "finished=" + s.getBroker().getCloudletReceivedList().size());
    }

    /** 3. Same scenario twice -> exactly the same finish times (compared as full-precision text). */
    private static void twoRunsAreIdentical(SimConfig cfg) {
        for (IsolationLevel level : IsolationLevel.values()) {
            String first = fingerprint(level, cfg);
            String second = fingerprint(level, cfg);
            check("two runs identical for " + level, first.equals(second),
                    first.equals(second) ? "fingerprint length " + first.length() : "DIFFERENT");
        }
    }

    /** One instance per slot, instance 0 noisy, mixed job sizes: finish times as exact text. */
    private static String fingerprint(IsolationLevel level, SimConfig cfg) {
        Scenario s = SimulationFactory.build(level, cfg);
        int n = 8;
        for (int i = 0; i < n; i++) {
            s.addInstance(i == 0 ? 4.0 : 1.0);
        }
        for (int i = 0; i < n; i++) {
            s.addCloudlet(s.getVms().get(i), LENGTH_MI + 5000L * i, 1 + (i % 2));
        }
        s.run();
        StringBuilder sb = new StringBuilder();
        for (Cloudlet c : s.getCloudlets()) {
            sb.append(c.getCloudletId()).append(':').append(c.getExecStartTime()).append(':')
                    .append(c.getFinishTime()).append(';');
        }
        return sb.toString();
    }

    // ================================================================== hand validation

    /**
     * Density by arithmetic: placed = min(cores / vCPUs when pinned, RAM / footprint).
     * Full VM 16/2 = 8; container 65536/(256+256) = 128; lightweight 65536/(256+768) = 64.
     */
    private static void densityMatchesRamAndCoreArithmetic(SimConfig cfg) {
        int[] expected = {8, 128, 64};
        IsolationLevel[] levels = IsolationLevel.values();
        for (int k = 0; k < levels.length; k++) {
            IsolationProfile p = IsolationProfile.of(levels[k], cfg);
            int footprint = cfg.getInstanceAppRamMb() + p.getMemoryOverheadMb();
            int byRam = cfg.getHostRamMb() / footprint;
            int byCores = p.getCapPolicy() == CapPolicy.PINNED
                    ? cfg.getHostCores() / cfg.getInstanceVcpus() : Integer.MAX_VALUE;
            int hand = Math.min(byRam, byCores);

            Scenario s = SimulationFactory.build(levels[k], cfg, hand + 3);  // 3 more than should fit
            for (IsolatedVm vm : s.getVms()) {
                s.addCloudlet(vm, 1000L, 1);
            }
            s.run();
            int placed = s.getPlacedVms().size();
            check("density " + levels[k] + ": simulation " + placed + " = hand " + hand + " = reference "
                    + expected[k], placed == hand && hand == expected[k], "placed=" + placed);
        }
    }

    /**
     * 20 uncapped containers each want 2 x 2500 = 5000 MIPS. Pool = 16 x 2500 = 40000.
     * Share factor = 40000 / 100000 = 0.4, so each runs 2.5x slower than alone.
     */
    private static void oversubscriptionScalesByDemand(SimConfig cfg) {
        Scenario s = SimulationFactory.build(IsolationLevel.CONTAINER, cfg, 20);
        for (IsolatedVm vm : s.getVms()) {
            s.addCloudlet(vm, LENGTH_MI, 2);
        }
        s.run();
        double worst = 0, best = Double.MAX_VALUE;
        for (Cloudlet c : s.getCloudlets()) {
            worst = Math.max(worst, s.slowdown(c));
            best = Math.min(best, s.slowdown(c));
        }
        check("20 containers x 5000 MIPS demand on 40000 MIPS: slowdown 2.50 (hand: 100000/40000)",
                Math.abs(worst - 2.5) < EPS_SHARED && Math.abs(best - 2.5) < EPS_SHARED,
                fmt(best) + " .. " + fmt(worst));
    }

    /** 8 pinned VMs fill the host; an 8x-hungry neighbour changes nothing for the others. */
    private static void pinnedIgnoresNoisyNeighbour(SimConfig cfg) {
        double[] sd = slowdownsOfVictims(IsolationLevel.FULL_VM, cfg, 8, 8.0);
        check("pinned: victims keep slowdown 1.00 next to an 8x noisy VM", allEqual(sd, 1.0), fmtAll(sd));
    }

    /** Capped: the noisy VM's extra demand is clipped, so victims see the same as with no noise. */
    private static void cappedIgnoresNoisyNeighbour(SimConfig cfg) {
        // 9 instances x 5000 = 45000 > 40000 pool, so there IS contention: factor 40000/45000, slowdown 1.125
        double[] quiet = slowdownsOfVictims(IsolationLevel.LIGHTWEIGHT_VM, cfg, 9, 1.0);
        double[] noisy = slowdownsOfVictims(IsolationLevel.LIGHTWEIGHT_VM, cfg, 9, 8.0);
        check("capped: contention gives slowdown 1.125 (hand: 45000/40000) with or without noise",
                allEqual(quiet, 1.125) && allEqual(noisy, 1.125), fmtAll(quiet) + " vs " + fmtAll(noisy));
    }

    /**
     * Uncapped: 8 normal instances (5000 each) + one at 8x (40000) = 80000 demand on 40000 pool,
     * factor 0.5, victims slowdown 2.00. Without noise: 45000 demand, 1.125.
     */
    private static void uncappedFeelsNoisyNeighbour(SimConfig cfg) {
        double[] quiet = slowdownsOfVictims(IsolationLevel.CONTAINER, cfg, 9, 1.0);
        double[] noisy = slowdownsOfVictims(IsolationLevel.CONTAINER, cfg, 9, 8.0);
        check("uncapped: victims 1.125 quiet, 2.00 next to an 8x neighbour (hand: 80000/40000)",
                allEqual(quiet, 1.125) && allEqual(noisy, 2.0), fmtAll(quiet) + " vs " + fmtAll(noisy));
    }

    /** First cloudlet cannot start before the level's delay: exec start = 30 / 2 / 3.8 s. */
    private static void startupDelayIsApplied(SimConfig cfg) {
        double[] expected = {cfg.getStartupDelayFullVmS(), cfg.getStartupDelayContainerS(),
                cfg.getStartupDelayLightweightVmS()};
        IsolationLevel[] levels = IsolationLevel.values();
        for (int k = 0; k < levels.length; k++) {
            Scenario s = SimulationFactory.build(levels[k], cfg, 4);
            for (IsolatedVm vm : s.getVms()) {
                s.addCloudlet(vm, LENGTH_MI, 1);
            }
            s.run();
            boolean ok = true;
            for (Cloudlet c : s.getCloudlets()) {
                ok &= Math.abs(c.getExecStartTime() - expected[k]) < EPS;
            }
            check("start-up " + levels[k] + ": first cloudlet starts at " + expected[k] + " s, fleet ready "
                    + fmt(s.getBroker().getFleetReadyTimeS()) + " s",
                    ok && Math.abs(s.getBroker().getFleetReadyTimeS() - expected[k]) < EPS, "ok=" + ok);
        }
    }

    // ================================================================== helpers

    /** Slowdown of instances 1..n-1 when instance 0 has the given demand multiplier. */
    private static double[] slowdownsOfVictims(IsolationLevel level, SimConfig cfg, int n, double noise) {
        Scenario s = SimulationFactory.build(level, cfg);
        for (int i = 0; i < n; i++) {
            s.addInstance(i == 0 ? noise : 1.0);
        }
        for (IsolatedVm vm : s.getVms()) {
            // The neighbour's job is 10x longer so it keeps running for the victims' whole run;
            // if it finished first, the victims would speed up and the hand figure would not apply.
            long length = vm.getId() == 0 ? 10 * LENGTH_MI : LENGTH_MI;
            s.addCloudlet(vm, length, cfg.getInstanceVcpus());
        }
        s.run();
        List<Double> out = new ArrayList<Double>();
        for (int i = 1; i < n; i++) {
            out.add(s.slowdown(s.getCloudlets().get(i)));
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    private static boolean allEqual(double[] values, double target) {
        if (values.length == 0) {
            return false;
        }
        for (double v : values) {
            if (Math.abs(v - target) > EPS_SHARED) {
                return false;
            }
        }
        return true;
    }

    private static String fmtAll(double[] v) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double x : v) {
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        return fmt(min) + ".." + fmt(max);
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    private static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + "   [" + detail + "]");
        if (!ok) {
            failures++;
        }
    }

    private ModelChecks() { }
}
