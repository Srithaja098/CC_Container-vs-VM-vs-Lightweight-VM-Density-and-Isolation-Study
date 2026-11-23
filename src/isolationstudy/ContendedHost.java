package isolationstudy;

import java.util.List;
import org.cloudbus.cloudsim.Pe;

/**
 * Extends Person 1's {@link IsolatedHost} with a {@link ContendedVmScheduler} to model
 * uncore memory-bandwidth and I/O bus contention for mixed-tenancy environments.
 */
public class ContendedHost extends IsolatedHost {

    public ContendedHost(int id, int ramMb, long bw, long storageMb, List<Pe> peList,
                         double memContentionWeight, double ioContentionWeight) {
        super(id, ramMb, bw, storageMb, peList);
        setVmScheduler(new ContendedVmScheduler(peList, memContentionWeight, ioContentionWeight));
    }

    public ContendedVmScheduler getContendedScheduler() {
        return (ContendedVmScheduler) getVmScheduler();
    }
}

