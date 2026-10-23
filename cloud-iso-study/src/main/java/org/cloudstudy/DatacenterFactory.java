package org.cloudstudy;

import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G4Xeon3040;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Factory class that builds N identical PowerHost objects and encapsulates them
 * inside a PowerDatacenter, following the CloudSim power-aware simulation patterns.
 *
 * Host specifications:
 * - 4 CPU Cores (PEs) @ 2000 MIPS each
 * - 32 GB RAM (32,768 MB)
 * - Large Bandwidth (1,000,000 Mbps) & Storage (10 TB) so placement bottleneck is strictly RAM or MIPS.
 * - Power Model: SPECpower HP ProLiant ML110 G4 Xeon 3040 or Linear power model.
 */
public class DatacenterFactory {

    // Host Hardware Specifications
    public static final int HOST_CORES = 4;
    public static final double HOST_CORE_MIPS = 2000.0;     // 4 x 2000 = 8000 MIPS total per host
    public static final int HOST_RAM_MB = 32 * 1024;        // 32 GB = 32,768 MB
    public static final long HOST_BW = 1_000_000L;          // 1 Tbps (oversized so network is never a bottleneck)
    public static final long HOST_STORAGE = 10_000_000L;    // 10 TB (oversized so disk is never a bottleneck)
    public static final double SCHEDULING_INTERVAL = 1.0;   // Power simulation update interval in seconds

    /**
     * Supported power models for PowerHost instances.
     */
    public enum PowerModelType {
        SPEC_POWER_HP_PROLIANT,
        LINEAR
    }

    /**
     * Builds a list of N identical PowerHost instances.
     *
     * @param hostCount The number of identical hosts (N)
     * @param modelType Power consumption model (SPEC_POWER_HP_PROLIANT or LINEAR)
     * @return List of configured PowerHost objects
     */
    public static List<PowerHost> createPowerHosts(int hostCount, PowerModelType modelType) {
        List<PowerHost> hostList = new ArrayList<>();

        for (int i = 0; i < hostCount; i++) {
            // 1. Build 4 cores of 2000 MIPS each
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < HOST_CORES; p++) {
                peList.add(new Pe(p, new PeProvisionerSimple(HOST_CORE_MIPS)));
            }

            // 2. Select Power Model
            PowerModel powerModel;
            if (modelType == PowerModelType.LINEAR) {
                // Linear model: max power 250W, static power ratio 70% (idle power = 175W)
                powerModel = new PowerModelLinear(250.0, 0.70);
            } else {
                // Benchmark-measured SPECpower model for HP ProLiant ML110 G4 (Xeon 3040)
                powerModel = new PowerModelSpecPowerHpProLiantMl110G4Xeon3040();
            }

            // 3. Assemble PowerHost with oversized network/storage provisioners
            PowerHost host = new PowerHost(
                    i,
                    new RamProvisionerSimple(HOST_RAM_MB),
                    new BwProvisionerSimple(HOST_BW),
                    HOST_STORAGE,
                    peList,
                    new VmSchedulerTimeShared(peList),
                    powerModel
            );

            hostList.add(host);
        }

        return hostList;
    }

    /**
     * Convenience method to build a PowerDatacenter using default SPECpower model and simple allocation.
     *
     * @param name Datacenter name
     * @param hostCount Number of identical PowerHosts (N)
     * @return Fully initialized PowerDatacenter
     * @throws Exception if datacenter creation fails
     */
    public static PowerDatacenter createPowerDatacenter(String name, int hostCount) throws Exception {
        return createPowerDatacenter(name, hostCount, PowerModelType.SPEC_POWER_HP_PROLIANT);
    }

    /**
     * Builds a PowerDatacenter containing N identical PowerHosts with the specified power model.
     *
     * @param name Datacenter name
     * @param hostCount Number of identical PowerHosts (N)
     * @param modelType Power model to attach to each host
     * @return Fully initialized PowerDatacenter
     * @throws Exception if datacenter creation fails
     */
    public static PowerDatacenter createPowerDatacenter(String name, int hostCount, PowerModelType modelType) throws Exception {
        List<PowerHost> hostList = createPowerHosts(hostCount, modelType);
        VmAllocationPolicy allocationPolicy = new VmAllocationPolicySimple(hostList);
        return createPowerDatacenter(name, hostList, allocationPolicy);
    }

    /**
     * Wraps a list of PowerHosts and a VM allocation policy into a PowerDatacenter.
     *
     * @param name Datacenter name
     * @param hostList Pre-constructed list of PowerHosts
     * @param vmAllocationPolicy Allocation policy governing VM placement
     * @return Fully initialized PowerDatacenter
     * @throws Exception if datacenter creation fails
     */
    public static PowerDatacenter createPowerDatacenter(
            String name,
            List<PowerHost> hostList,
            VmAllocationPolicy vmAllocationPolicy) throws Exception {

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch,
                os,
                vmm,
                hostList,
                timeZone,
                cost,
                costPerMem,
                costPerStorage,
                costPerBw
        );

        return new PowerDatacenter(
                name,
                characteristics,
                vmAllocationPolicy,
                storageList,
                SCHEDULING_INTERVAL
        );
    }
}
