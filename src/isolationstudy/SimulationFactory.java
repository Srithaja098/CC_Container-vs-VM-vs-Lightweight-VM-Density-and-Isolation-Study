package isolationstudy;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;

/**
 * Builds a ready-to-use {@link Scenario} for one isolation level.
 *
 * <p>The hardware comes only from {@link SimConfig} and never from the level, so the three levels
 * run on identical hosts and only isolation differs. The returned scenario has the datacenter,
 * broker and hosts but no instances yet: the experiment decides how many to add
 * ({@link Scenario#addInstance()}), or uses {@link #build(IsolationLevel, SimConfig, int)}.
 *
 * <p>Calling {@code build} resets CloudSim's static state (clock, entities). Console logging of
 * CloudSim is switched off so runs print nothing and stay fast.
 */
public final class SimulationFactory {

    /**
     * Smallest gap CloudSim enforces between events, seconds. The CloudSim default of 0.1 s would
     * delay the recorded finish of short requests (a 500 MI request runs about 0.2 s), so it is
     * set to one microsecond. Event times then match the exact finish times.
     */
    public static final double MIN_EVENT_GAP_S = 1e-6;

    private SimulationFactory() { }

    /** Scenario with hosts, datacenter and broker, and no instances. */
    public static Scenario build(IsolationLevel level, SimConfig cfg) {
        try {
            Log.disable();
            // Fixed calendar: CloudSim only uses it for time zone bookkeeping, and a fixed value
            // keeps runs identical whatever the wall clock says.
            Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(0L);
            CloudSim.init(1, cal, false, MIN_EVENT_GAP_S);

            IsolationProfile profile = IsolationProfile.of(level, cfg);

            List<IsolatedHost> hosts = new ArrayList<IsolatedHost>();
            for (int h = 0; h < cfg.getHostCount(); h++) {
                List<Pe> pes = new ArrayList<Pe>();
                for (int p = 0; p < cfg.getHostCores(); p++) {
                    pes.add(new Pe(p, new PeProvisionerSimple(cfg.getHostMipsPerCore())));
                }
                hosts.add(new IsolatedHost(h, cfg.getHostRamMb(), cfg.getHostBw(),
                        cfg.getHostStorageMb(), pes));
            }

            VmAllocationPolicyFirstFit policy = new VmAllocationPolicyFirstFit(hosts);
            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                    "x86", "Linux", "isolationstudy", hosts, 0.0, 0.0, 0.0, 0.0, 0.0);
            IsolatedDatacenter datacenter = new IsolatedDatacenter("Datacenter_" + level.label(),
                    characteristics, policy, new ArrayList<Storage>(), 0.0);
            StartupBroker broker = new StartupBroker("Broker_" + level.label());

            return new Scenario(level, profile, cfg, datacenter, broker, hosts, policy,
                    new Random(cfg.getSeed()));
        } catch (Exception e) {
            throw new IllegalStateException("could not build scenario for " + level + ": " + e, e);
        }
    }

    /** Same, with {@code instances} identical instances already added (no cloudlets). */
    public static Scenario build(IsolationLevel level, SimConfig cfg, int instances) {
        Scenario s = build(level, cfg);
        for (int i = 0; i < instances; i++) {
            s.addInstance();
        }
        return s;
    }
}
