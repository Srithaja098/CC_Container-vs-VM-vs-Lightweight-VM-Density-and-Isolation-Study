package org.cloudstudy;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.power.PowerDatacenter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Baseline Power-Aware CloudSim Simulation:
 * - Uses DatacenterFactory to construct 1 PowerHost (4 cores of 2000 MIPS, 32 GB RAM,
 *   oversized Bandwidth & Storage so placement bottleneck is strictly RAM or MIPS)
 *   with PowerModelSpecPowerHpProLiantMl110G4Xeon3040.
 * - Deploys 10 VM instances (2048 MB RAM, 1 core @ 500 MIPS each).
 * - Runs 10 Cloudlets (40,000 MI each).
 * - Confirms that all cloudlets finish successfully and prints any failed VMs.
 */
public class Main {

    /**
     * Extended DatacenterBroker that tracks VM allocation successes and failures
     * before the standard broker lifecycle cleans up VM state at simulation completion.
     */
    public static class TrackingBroker extends DatacenterBroker {
        private final List<Vm> successfullyCreatedVms = new ArrayList<>();
        private final List<Vm> failedVms = new ArrayList<>();

        public TrackingBroker(String name) throws Exception {
            super(name);
        }

        @Override
        protected void processVmCreateAck(SimEvent ev) {
            int[] data = (int[]) ev.getData();
            int vmId = data[1];
            int result = data[2];

            Vm vm = (Vm) VmList.getById(getGuestList(), vmId);

            if (result == CloudSimTags.TRUE) {
                successfullyCreatedVms.add(vm);
            } else {
                failedVms.add(vm);
            }

            super.processVmCreateAck(ev);
        }

        public List<Vm> getSuccessfullyCreatedVms() {
            return successfullyCreatedVms;
        }

        public List<Vm> getFailedVms() {
            return failedVms;
        }
    }

    public static void main(String[] args) {
        Log.println("=========================================================================");
        Log.println(" Starting Power-Aware CloudSim Simulation (DatacenterFactory Baseline)  ");
        Log.println("=========================================================================");

        try {
            // Step 1: Initialize CloudSim core
            int numUser = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;
            CloudSim.init(numUser, calendar, traceFlag);

            // Step 2: Use DatacenterFactory to build PowerDatacenter with 1 PowerHost
            int hostCount = 1;
            PowerDatacenter datacenter = DatacenterFactory.createPowerDatacenter(
                    "PowerDatacenter_0",
                    hostCount,
                    DatacenterFactory.PowerModelType.SPEC_POWER_HP_PROLIANT
            );

            Log.println(String.format("Created %s with %d PowerHost(s): [4 Cores x 2000 MIPS, 32 GB RAM, Large BW/Disk]",
                    datacenter.getName(), hostCount));

            // Step 3: Create TrackingBroker
            TrackingBroker broker = new TrackingBroker("DatacenterBroker_0");
            int brokerId = broker.getId();

            // Step 4: Create 10 VM instances
            // Host has 32,768 MB RAM and 8,000 MIPS across 4 cores.
            // 10 VMs * 2048 MB = 20,480 MB <= 32,768 MB RAM -> Placement succeeds!
            int vmCount = 10;
            int vmMips = 500;
            int vmPes = 1;
            int vmRam = 2048;
            long vmBw = 1000;
            long vmSize = 10000;
            String vmm = "Xen";

            List<Vm> vmList = new ArrayList<>();
            for (int i = 0; i < vmCount; i++) {
                Vm vm = new Vm(
                        i,
                        brokerId,
                        vmMips,
                        vmPes,
                        vmRam,
                        vmBw,
                        vmSize,
                        vmm,
                        new CloudletSchedulerTimeShared()
                );
                // Mark inWaiting so CloudSim 7's container-host check does not deallocate pure VMs
                vm.setInWaiting(true);
                vmList.add(vm);
            }
            broker.submitGuestList(vmList);
            Log.println("Submitted " + vmCount + " VM instances (2048 MB RAM, 500 MIPS each) to broker.");

            // Step 5: Create 10 Cloudlets (1 task per VM instance)
            int cloudletCount = 10;
            long cloudletLength = 40000; // 40,000 MI / 500 MIPS = 80 seconds
            long fileSize = 300;
            long outputSize = 300;
            int cloudletPes = 1;

            List<Cloudlet> cloudletList = new ArrayList<>();
            for (int i = 0; i < cloudletCount; i++) {
                UtilizationModel utilizationModel = new UtilizationModelFull();
                Cloudlet cloudlet = new Cloudlet(
                        i,
                        cloudletLength,
                        cloudletPes,
                        fileSize,
                        outputSize,
                        utilizationModel,
                        utilizationModel,
                        utilizationModel
                );
                cloudlet.setUserId(brokerId);
                cloudlet.setGuestId(i); // Assign cloudlet i to VM i
                cloudletList.add(cloudlet);
            }
            broker.submitCloudletList(cloudletList);
            Log.println("Submitted " + cloudletCount + " Cloudlets (40,000 MI each) to broker.");

            // Step 6: Execute simulation
            Log.println("\nRunning simulation engine...");
            CloudSim.startSimulation();

            // Step 7: Retrieve simulation results
            List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
            List<Vm> submittedVms = broker.getGuestList();
            List<Vm> createdVms = broker.getSuccessfullyCreatedVms();
            List<Vm> failedVms = broker.getFailedVms();

            CloudSim.stopSimulation();

            // Step 8: Confirm VM creation status & print any failed VMs
            printVmPlacementReport(submittedVms, createdVms, failedVms);

            // Step 9: Confirm that all cloudlets finished
            printCloudletExecutionReport(finishedCloudlets, cloudletCount);

            Log.println("=========================================================================");
            Log.println(" Simulation Completed Successfully!                                     ");
            Log.println("=========================================================================");

        } catch (Exception e) {
            Log.println("Simulation execution error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Inspects VM creation results and prints any failed VMs.
     */
    private static void printVmPlacementReport(List<Vm> submittedVms, List<Vm> createdVms, List<Vm> failedVms) {
        int submittedCount = (submittedVms != null) ? submittedVms.size() : 0;
        int createdCount = (createdVms != null) ? createdVms.size() : 0;
        int failedCount = (failedVms != null) ? failedVms.size() : 0;

        Log.println("\n----------------------- VM PLACEMENT REPORT ------------------------");
        Log.println("Total VMs Submitted: " + submittedCount);
        Log.println("Total VMs Allocated: " + createdCount);
        Log.println("Total VMs Failed:    " + failedCount);

        if (failedCount == 0) {
            Log.println(">> Placement Status: ALL VMs SUCCESSFULLY ALLOCATED (0 Failed) <<");
        } else {
            Log.println(">> Placement Status: WARNING - THE FOLLOWING VMs FAILED PLACEMENT <<");
            for (Vm failed : failedVms) {
                Log.println(String.format("  - FAILED VM ID: %d | RAM: %d MB | PEs: %d | MIPS: %.1f",
                        failed.getId(), failed.getRam(), failed.getNumberOfPes(), failed.getMips()));
            }
        }
        Log.println("--------------------------------------------------------------------\n");
    }

    /**
     * Prints detailed report of all finished cloudlets and validates completion.
     */
    private static void printCloudletExecutionReport(List<Cloudlet> list, int expectedCount) {
        DecimalFormat dft = new DecimalFormat("###.##");
        int successCount = 0;

        Log.println("------------------------- CLOUDLET EXECUTION REPORT --------------------------");
        Log.println(String.format("%-14s %-12s %-10s %-14s %-14s %-14s",
                "Cloudlet ID", "STATUS", "VM ID", "Start Time", "Actual CPU", "Finish Time"));
        Log.println("------------------------------------------------------------------------------");

        if (list != null) {
            for (Cloudlet cloudlet : list) {
                boolean isSuccess = (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS);
                if (isSuccess) successCount++;

                String status = isSuccess ? "SUCCESS" : "FAILED";
                Log.println(String.format("%-14d %-12s %-10d %-14s %-14s %-14s",
                        cloudlet.getCloudletId(),
                        status,
                        cloudlet.getGuestId(),
                        dft.format(cloudlet.getExecStartTime()) + " s",
                        dft.format(cloudlet.getActualCPUTime()) + " s",
                        dft.format(cloudlet.getExecFinishTime()) + " s"
                ));
            }
        }

        Log.println("------------------------------------------------------------------------------");
        Log.println(String.format("Cloudlets Finished: %d / %d (%.1f%% Success Rate)",
                successCount, expectedCount, (100.0 * successCount / expectedCount)));

        if (successCount == expectedCount) {
            Log.println(">> Confirmation: ALL " + expectedCount + " CLOUDLETS FINISHED SUCCESSFULLY <<");
        } else {
            Log.println(">> Warning: Some cloudlets did not finish successfully! <<");
        }
        Log.println("------------------------------------------------------------------------------\n");
    }
}
