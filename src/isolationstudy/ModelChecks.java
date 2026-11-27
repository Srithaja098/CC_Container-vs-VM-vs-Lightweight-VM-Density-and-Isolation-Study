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

        // ---- energy and latency (hand-calculated)
        loneInstanceEnergyMatchesHand(cfg);
        saturatedHostDrawsPeakPower(cfg);
        consolidationUsesFewerHostsAndLessEnergy(cfg);
        emptyHostsAreOff(cfg);
        responseTimeIncludesStartupWait(cfg);
        percentileAndMeanArithmetic();
        poissonArrivalsAreReproducible();
        shortRequestsAtLowLoadRunAtServiceTime(cfg);
        energyAndLatencyAreDeterministic(cfg);

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

    // ================================================================== energy and latency

    /**
     * One instance, one 2-vCPU job. Hand calculation, with T = job length / (vCPU MIPS x (1 - overhead)):
     * host utilisation while running = 2 x 2500 / (16 x 2500) = 0.125, makespan = ready time + T, so
     * E = P_idle x (ready + T) + (P_max - P_idle) x 0.125 x T.
     * Container: ready 2 s, T = 100000 / 2450 = 40.816 s, E = 4281.63 + 1020.41 = 5302.04 J (default 100/300 W).
     */
    private static void loneInstanceEnergyMatchesHand(SimConfig cfg) {
        for (IsolationLevel level : IsolationLevel.values()) {
            IsolationProfile p = IsolationProfile.of(level, cfg);
            Scenario s = SimulationFactory.build(level, cfg);
            IsolatedVm vm = s.addInstance();
            s.addCloudlet(vm, LENGTH_MI, cfg.getInstanceVcpus());
            s.run();
            double t = LENGTH_MI / p.effectiveMips(cfg.getInstanceMipsPerVcpu());
            double u = hostShareOfOneInstance(cfg);
            double hand = cfg.getHostIdlePowerW() * (p.getStartupDelayS() + t)
                    + (cfg.getHostMaxPowerW() - cfg.getHostIdlePowerW()) * u * t;
            double sim = s.getEnergyJ();
            check("energy lone " + level + ": simulation " + fmt2(sim) + " J = hand " + fmt2(hand) + " J",
                    Math.abs(sim - hand) < 1e-5 * hand && s.getPoweredHostCount() == 1,
                    "powered=" + s.getPoweredHostCount());
        }
    }

    /** 8 pinned 2-vCPU VMs keep all 16 cores busy: u = 1, so power while running is P_max. */
    private static void saturatedHostDrawsPeakPower(SimConfig cfg) {
        IsolationProfile p = IsolationProfile.of(IsolationLevel.FULL_VM, cfg);
        Scenario s = SimulationFactory.build(IsolationLevel.FULL_VM, cfg, 8);
        for (IsolatedVm vm : s.getVms()) {
            s.addCloudlet(vm, LENGTH_MI, cfg.getInstanceVcpus());
        }
        s.run();
        double t = LENGTH_MI / p.effectiveMips(cfg.getInstanceMipsPerVcpu());
        double hand = cfg.getHostIdlePowerW() * (p.getStartupDelayS() + t)
                + (cfg.getHostMaxPowerW() - cfg.getHostIdlePowerW()) * t;
        double sim = s.getEnergyJ();
        double powerWhileRunning = (sim - cfg.getHostIdlePowerW() * p.getStartupDelayS()) / t;
        check("saturated host: energy " + fmt2(sim) + " J = hand " + fmt2(hand) + " J, power while running "
                + fmt2(powerWhileRunning) + " W = P_max " + fmt2(cfg.getHostMaxPowerW()) + " W",
                Math.abs(sim - hand) < 1e-5 * hand
                        && Math.abs(powerWhileRunning - cfg.getHostMaxPowerW()) < 1e-3,
                "avg=" + fmt2(s.getAvgPowerW()) + " W");
    }

    /**
     * 16 tenants. Full VMs need 2 hosts (8 slots each); containers all fit on 1 host. Full VM: each host is
     * saturated, E = 2 x (P_idle x (30 + T) + (P_max - P_idle) x T) with T = 42.105 s. Containers: demand
     * 16 x 5000 = 80000 on a 40000 pool, slowdown 2, run time 2 x 40.816 = 81.633 s at u = 1, so
     * E = P_idle x (2 + 81.633) + (P_max - P_idle) x 81.633.
     */
    private static void consolidationUsesFewerHostsAndLessEnergy(SimConfig cfg) {
        int tenants = 16;
        double idle = cfg.getHostIdlePowerW();
        double max = cfg.getHostMaxPowerW();

        IsolationProfile pf = IsolationProfile.of(IsolationLevel.FULL_VM, cfg);
        Scenario full = SimulationFactory.build(IsolationLevel.FULL_VM, cfg.withHostCount(2), tenants);
        for (IsolatedVm vm : full.getVms()) {
            full.addCloudlet(vm, LENGTH_MI, cfg.getInstanceVcpus());
        }
        full.run();
        double tf = LENGTH_MI / pf.effectiveMips(cfg.getInstanceMipsPerVcpu());
        double handFull = 2 * (idle * (pf.getStartupDelayS() + tf) + (max - idle) * tf);

        IsolationProfile pc = IsolationProfile.of(IsolationLevel.CONTAINER, cfg);
        Scenario ctr = SimulationFactory.build(IsolationLevel.CONTAINER, cfg.withHostCount(2), tenants);
        for (IsolatedVm vm : ctr.getVms()) {
            ctr.addCloudlet(vm, LENGTH_MI, cfg.getInstanceVcpus());
        }
        ctr.run();
        double demand = tenants * cfg.getInstanceVcpus() * cfg.getInstanceMipsPerVcpu();
        double pool = cfg.getHostCores() * cfg.getHostMipsPerCore();
        double tc = (demand / pool) * LENGTH_MI / pc.effectiveMips(cfg.getInstanceMipsPerVcpu());
        double handCtr = idle * (pc.getStartupDelayS() + tc) + (max - idle) * tc;

        check("16 tenants: full VM " + full.getPoweredHostCount() + " hosts " + fmt2(full.getEnergyJ())
                        + " J (hand " + fmt2(handFull) + "), container " + ctr.getPoweredHostCount() + " host "
                        + fmt2(ctr.getEnergyJ()) + " J (hand " + fmt2(handCtr) + ")",
                full.getPoweredHostCount() == 2 && ctr.getPoweredHostCount() == 1
                        && Math.abs(full.getEnergyJ() - handFull) < 1e-4 * handFull
                        && Math.abs(ctr.getEnergyJ() - handCtr) < 1e-4 * handCtr,
                "full=" + fmt2(full.getEnergyJ()) + " ctr=" + fmt2(ctr.getEnergyJ()));
        check("consolidation: containers use fewer hosts and less energy than full VMs",
                ctr.getPoweredHostCount() < full.getPoweredHostCount() && ctr.getEnergyJ() < full.getEnergyJ(),
                fmt2(ctr.getEnergyJ()) + " < " + fmt2(full.getEnergyJ()));
    }

    /** A second host that never receives an instance is off and adds no energy. */
    private static void emptyHostsAreOff(SimConfig cfg) {
        Scenario one = SimulationFactory.build(IsolationLevel.CONTAINER, cfg);
        one.addCloudlet(one.addInstance(), LENGTH_MI, 2);
        one.run();
        Scenario two = SimulationFactory.build(IsolationLevel.CONTAINER, cfg.withHostCount(3));
        two.addCloudlet(two.addInstance(), LENGTH_MI, 2);
        two.run();
        check("3 hosts, 1 instance: only 1 host powered, same energy as the 1-host run",
                two.getPoweredHostCount() == 1 && one.getEnergyJ() == two.getEnergyJ(),
                "powered=" + two.getPoweredHostCount() + " " + fmt2(two.getEnergyJ()) + " J");
    }

    /**
     * Response = finish - arrival. Container (ready at 2 s, T = 40.816 s): arrival 10 -> T; arrival 1 -> 1 + T
     * (waits 1 s for the instance). Full VM, arrival 0 -> 30 + T (cold start).
     */
    private static void responseTimeIncludesStartupWait(SimConfig cfg) {
        IsolationProfile pc = IsolationProfile.of(IsolationLevel.CONTAINER, cfg);
        double tc = LENGTH_MI / pc.effectiveMips(cfg.getInstanceMipsPerVcpu());

        Scenario late = SimulationFactory.build(IsolationLevel.CONTAINER, cfg);
        late.addCloudlet(late.addInstance(), LENGTH_MI, 1, pc.getStartupDelayS() + 8.0);
        late.run();
        Scenario early = SimulationFactory.build(IsolationLevel.CONTAINER, cfg);
        early.addCloudlet(early.addInstance(), LENGTH_MI, 1, pc.getStartupDelayS() - 1.0);
        early.run();
        IsolationProfile pf = IsolationProfile.of(IsolationLevel.FULL_VM, cfg);
        double tf = LENGTH_MI / pf.effectiveMips(cfg.getInstanceMipsPerVcpu());
        Scenario cold = SimulationFactory.build(IsolationLevel.FULL_VM, cfg);
        cold.addCloudlet(cold.addInstance(), LENGTH_MI, 1, 0.0);
        cold.run();

        double rLate = late.getResponseTimesS().get(0);
        double rEarly = early.getResponseTimesS().get(0);
        double rCold = cold.getResponseTimesS().get(0);
        check("response time: container arriving after start-up = " + fmt2(rLate) + " s (hand " + fmt2(tc) + "), "
                        + "1 s before ready = " + fmt2(rEarly) + " s (hand " + fmt2(tc + 1.0) + "), "
                        + "full VM cold start = " + fmt2(rCold) + " s (hand " + fmt2(tf + 30.0) + ")",
                Math.abs(rLate - tc) < 1e-4 && Math.abs(rEarly - (tc + 1.0)) < 1e-4
                        && Math.abs(rCold - (tf + pf.getStartupDelayS())) < 1e-4,
                "ok");
    }

    /** Nearest-rank percentile of 1..10: P10 = 1, P50 = 5, P90 = 9, P95 = 10, P99 = 10; mean = 5.5. */
    private static void percentileAndMeanArithmetic() {
        List<Double> v = new ArrayList<Double>();
        for (int i = 10; i >= 1; i--) {                    // unsorted on purpose
            v.add((double) i);
        }
        boolean ok = LatencyStats.percentile(v, 10) == 1.0 && LatencyStats.percentile(v, 50) == 5.0
                && LatencyStats.percentile(v, 90) == 9.0 && LatencyStats.percentile(v, 95) == 10.0
                && LatencyStats.percentile(v, 99) == 10.0 && LatencyStats.percentile(v, 100) == 10.0
                && Math.abs(LatencyStats.mean(v) - 5.5) < 1e-12;
        check("nearest-rank percentiles of 1..10: P10 1, P50 5, P90 9, P95 10, P99 10, mean 5.5", ok,
                "P50=" + LatencyStats.percentile(v, 50) + " P95=" + LatencyStats.percentile(v, 95));
    }

    /** Same seed -> same arrivals; strictly increasing; mean gap close to 1 / rate. */
    private static void poissonArrivalsAreReproducible() {
        double[] a = Arrivals.poisson(new java.util.Random(42), 5.0, 2000, 2.0);
        double[] b = Arrivals.poisson(new java.util.Random(42), 5.0, 2000, 2.0);
        boolean same = Arrays.equals(a, b);
        boolean increasing = a[0] > 2.0;
        for (int i = 1; i < a.length; i++) {
            increasing &= a[i] > a[i - 1];
        }
        double meanGap = (a[a.length - 1] - 2.0) / a.length;
        check("Poisson arrivals: reproducible, increasing, mean gap " + fmt(meanGap) + " s ~ 1/rate 0.2 s",
                same && increasing && Math.abs(meanGap - 0.2) < 0.02, "n=" + a.length);
    }

    /**
     * 4 containers, 200 requests of 500 MI (1 vCPU) at 4 requests/s, all arriving after start-up. Demand never
     * exceeds a few thousand MIPS on a 40000 pool, so nothing is contended and every request runs at its service
     * time 500 / 2450 = 0.2041 s (to within CloudSim's 1 MI granularity, 0.4 ms). Also checks P50 <= P95 <= P99.
     */
    private static void shortRequestsAtLowLoadRunAtServiceTime(SimConfig cfg) {
        Scenario s = shortRequestScenario(cfg, 4.0, 200, 42L);
        IsolationProfile p = IsolationProfile.of(IsolationLevel.CONTAINER, cfg);
        double service = 500.0 / p.effectiveMips(cfg.getInstanceMipsPerVcpu());
        List<Double> r = s.getResponseTimesS();
        double min = Double.MAX_VALUE;
        double max = 0.0;
        for (Double x : r) {
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        double p50 = s.getPercentileResponseS(50);
        double p95 = s.getPercentileResponseS(95);
        double p99 = s.getPercentileResponseS(99);
        check("200 short requests: all finished, response " + fmt(min) + ".." + fmt(max) + " s = service time "
                        + fmt(service) + " s, P50 <= P95 <= P99",
                r.size() == 200 && min > service - 1e-3 && max < service + 1e-3 && p50 <= p95 && p95 <= p99,
                "mean=" + fmt(s.getMeanResponseS()));
    }

    /** Two identical runs give exactly the same energy, mean and P99 (compared as doubles, no tolerance). */
    private static void energyAndLatencyAreDeterministic(SimConfig cfg) {
        for (IsolationLevel level : IsolationLevel.values()) {
            Scenario a = noisyShortRequests(cfg, level);
            Scenario b = noisyShortRequests(cfg, level);
            boolean same = a.getEnergyJ() == b.getEnergyJ() && a.getMeanResponseS() == b.getMeanResponseS()
                    && a.getPercentileResponseS(99) == b.getPercentileResponseS(99)
                    && a.getMakespanS() == b.getMakespanS();
            check("energy and latency identical across two runs for " + level, same,
                    "E=" + fmt2(a.getEnergyJ()) + " J, P99=" + fmt(a.getPercentileResponseS(99)) + " s");
        }
    }

    private static Scenario shortRequestScenario(SimConfig cfg, double ratePerS, int requests, long seed) {
        Scenario s = SimulationFactory.build(IsolationLevel.CONTAINER, cfg);
        int instances = 4;
        for (int i = 0; i < instances; i++) {
            s.addInstance();
        }
        double[] arr = Arrivals.poisson(new java.util.Random(seed), ratePerS, requests,
                s.getProfile().getStartupDelayS());
        for (int i = 0; i < requests; i++) {
            s.addCloudlet(s.getVms().get(i % instances), 500L, 1, arr[i]);
        }
        s.run();
        return s;
    }

    /** Requests plus one noisy neighbour (instance 0 asks for 8x CPU), arrivals from the seed-42 stream. */
    private static Scenario noisyShortRequests(SimConfig cfg, IsolationLevel level) {
        Scenario s = SimulationFactory.build(level, cfg);
        int instances = 4;
        for (int i = 0; i < instances; i++) {
            s.addInstance(i == 0 ? 8.0 : 1.0);
        }
        double[] arr = Arrivals.poisson(s.getRandom(), 20.0, 300, s.getProfile().getStartupDelayS());
        for (int i = 0; i < arr.length; i++) {
            s.addCloudlet(s.getVms().get(i % instances), 500L, 1, arr[i]);
        }
        s.run();
        return s;
    }

    /** Host CPU used by one instance with all its vCPUs busy, as a fraction of the host: 2 x 2500 / 40000. */
    private static double hostShareOfOneInstance(SimConfig cfg) {
        return (cfg.getInstanceVcpus() * cfg.getInstanceMipsPerVcpu())
                / (cfg.getHostCores() * cfg.getHostMipsPerCore());
    }

    private static String fmt2(double v) {
        return String.format(Locale.US, "%.2f", v);
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
