package isolationstudy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * Main entry point and CLI / console menu driver for the CloudSim 4.0 Isolation Study.
 * Owned by Person 3.
 *
 * <p>Supports:
 * <ul>
 *   <li>CLI arguments: {@code --exp 1|2|3|4|5|6|all}, {@code --config <path>}, {@code --sweep ...}, {@code --dashboard}</li>
 *   <li>Interactive console menu when launched without CLI flags</li>
 *   <li>Calls Person 2's {@link Experiment} interface implementations (Exp 1, 2, 3) and Exp 4</li>
 *   <li>Saves results to {@code code/results/<name>.csv} via {@link ResultWriter}</li>
 * </ul>
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        String configPath = SimConfig.DEFAULT_PROPERTIES_PATH;
        String expArg = null;
        boolean runDashboard = false;
        boolean runSweep = false;
        String sweepParam = null;
        double sweepMin = 0.0, sweepMax = 0.0, sweepStep = 1.0;
        int sweepExp = 1;

        // Parse CLI arguments
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i].trim();
                if ("--config".equalsIgnoreCase(arg) && i + 1 < args.length) {
                    configPath = args[++i].trim();
                } else if ("--exp".equalsIgnoreCase(arg) && i + 1 < args.length) {
                    expArg = args[++i].trim().toLowerCase(Locale.US);
                } else if ("--dashboard".equalsIgnoreCase(arg)) {
                    runDashboard = true;
                } else if ("--sweep".equalsIgnoreCase(arg) && i + 5 < args.length) {
                    runSweep = true;
                    sweepExp = Integer.parseInt(args[++i].trim());
                    sweepParam = args[++i].trim();
                    sweepMin = Double.parseDouble(args[++i].trim());
                    sweepMax = Double.parseDouble(args[++i].trim());
                    sweepStep = Double.parseDouble(args[++i].trim());
                } else if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                    printUsage();
                    return;
                }
            }
        }

        SimConfig cfg = SimConfig.load(configPath);

        // If CLI flags were specified, execute them in batch mode
        if (expArg != null || runDashboard || runSweep) {
            executeCli(cfg, expArg, runDashboard, runSweep, sweepExp, sweepParam, sweepMin, sweepMax, sweepStep);
            return;
        }

        // Otherwise launch interactive console menu
        runConsoleMenu(cfg);
    }

    private static void executeCli(SimConfig cfg, String expArg, boolean runDashboard,
                                   boolean runSweep, int sweepExp, String sweepParam,
                                   double sweepMin, double sweepMax, double sweepStep) {
        try {
            if (expArg != null) {
                if ("1".equals(expArg) || "density".equals(expArg)) {
                    runAndPrint(new Exp1_Density(), cfg);
                } else if ("2".equals(expArg) || "startup".equals(expArg)) {
                    runAndPrint(new Exp2_Startup(), cfg);
                } else if ("3".equals(expArg) || "noisy_neighbour".equals(expArg) || "noisy".equals(expArg)) {
                    runAndPrint(new Exp3_NoisyNeighbour(), cfg);
                } else if ("4".equals(expArg) || "mixed".equals(expArg) || "mixed_tenancy".equals(expArg)) {
                    runAndPrint(new Exp4_MixedTenancy(), cfg);
                } else if ("5".equals(expArg) || "energy".equals(expArg)) {
                    runAndPrint(new Exp5_Energy(), cfg);
                } else if ("6".equals(expArg) || "latency".equals(expArg)) {
                    runAndPrint(new Exp6_Latency(), cfg);
                } else if ("all".equals(expArg)) {
                    runAllExperiments(cfg);
                } else {
                    System.err.println("Unknown experiment argument: " + expArg);
                    printUsage();
                    System.exit(1);
                }
            }

            if (runSweep && sweepParam != null) {
                Experiment targetExp = selectExperiment(sweepExp);
                SweepRunner.runSweep(targetExp, cfg, sweepParam, sweepMin, sweepMax, sweepStep);
            }

            if (runDashboard) {
                DashboardGenerator.generate();
            }
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void runConsoleMenu(SimConfig initialCfg) {
        SimConfig cfg = initialCfg;
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.println("================================================================================");
            System.out.println("  CloudSim 4.0: Container vs VM vs Lightweight-VM Density & Isolation Study     ");
            System.out.println("================================================================================");
            System.out.println(String.format(Locale.US, "  Host: %d host(s), %d cores @ %.0f MIPS, %d MB RAM | Seed: %d",
                    cfg.getHostCount(), cfg.getHostCores(), cfg.getHostMipsPerCore(), cfg.getHostRamMb(), cfg.getSeed()));
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("  [1] Run Experiment 1: Host Density Limit (Exp1_Density)");
            System.out.println("  [2] Run Experiment 2: Fleet Startup Readiness (Exp2_Startup)");
            System.out.println("  [3] Run Experiment 3: Noisy-Neighbour Slowdown (Exp3_NoisyNeighbour)");
            System.out.println("  [4] Run Experiment 4: Mixed-Tenancy Uncore Contention (Exp4_MixedTenancy)");
            System.out.println("  [5] Run Experiment 5: Host Energy (Exp5_Energy)");
            System.out.println("  [6] Run Experiment 6: Request Latency (Exp6_Latency)");
            System.out.println("  [7] Run All Experiments (1 to 6) and Update Dashboard");
            System.out.println("  [8] Run Parameter Sensitivity Sweep (SweepRunner)");
            System.out.println("  [9] Generate Offline HTML Dashboard (DashboardGenerator)");
            System.out.println("  [10] Run Built-in Model Verification Checks (ModelChecks)");
            System.out.println("  [11] Reload Configuration Properties File");
            System.out.println("  [0] Exit");
            System.out.println("================================================================================");
            System.out.print("Select an option [0-11]: ");

            String line = scanner.hasNextLine() ? scanner.nextLine().trim() : "0";
            if (line.isEmpty()) continue;

            try {
                if ("0".equals(line) || "exit".equalsIgnoreCase(line) || "q".equalsIgnoreCase(line)) {
                    System.out.println("Exiting study console. Goodbye!");
                    break;
                } else if ("1".equals(line)) {
                    runAndPrint(new Exp1_Density(), cfg);
                } else if ("2".equals(line)) {
                    runAndPrint(new Exp2_Startup(), cfg);
                } else if ("3".equals(line)) {
                    runAndPrint(new Exp3_NoisyNeighbour(), cfg);
                } else if ("4".equals(line)) {
                    runAndPrint(new Exp4_MixedTenancy(), cfg);
                } else if ("5".equals(line)) {
                    runAndPrint(new Exp5_Energy(), cfg);
                } else if ("6".equals(line)) {
                    runAndPrint(new Exp6_Latency(), cfg);
                } else if ("7".equals(line)) {
                    runAllExperiments(cfg);
                    DashboardGenerator.generate();
                } else if ("8".equals(line)) {
                    handleInteractiveSweep(scanner, cfg);
                } else if ("9".equals(line)) {
                    DashboardGenerator.generate();
                } else if ("10".equals(line)) {
                    System.out.println("\nRunning ModelChecks verification suite...");
                    ModelChecks.main(new String[0]);
                } else if ("11".equals(line)) {
                    cfg = SimConfig.load(SimConfig.DEFAULT_PROPERTIES_PATH);
                    System.out.println("Configuration reloaded from " + SimConfig.DEFAULT_PROPERTIES_PATH);
                } else {
                    System.out.println("Invalid option. Please enter a number between 0 and 11.");
                }
            } catch (Exception e) {
                System.err.println("Operation failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void handleInteractiveSweep(Scanner scanner, SimConfig cfg) throws IOException {
        System.out.print("Select experiment for sweep [1=Density, 2=Startup, 3=NoisyNeighbour, 4=MixedTenancy]: ");
        int expNum = Integer.parseInt(scanner.nextLine().trim());
        Experiment exp = selectExperiment(expNum);

        System.out.print("Enter parameter name (e.g. host.ram_mb, startup.delay.container_s, workload.neighbour_intensity): ");
        String param = scanner.nextLine().trim();

        System.out.print("Enter minimum value: ");
        double min = Double.parseDouble(scanner.nextLine().trim());

        System.out.print("Enter maximum value: ");
        double max = Double.parseDouble(scanner.nextLine().trim());

        System.out.print("Enter step increment: ");
        double step = Double.parseDouble(scanner.nextLine().trim());

        SweepRunner.runSweep(exp, cfg, param, min, max, step);
    }

    public static Path runAndPrint(Experiment exp, SimConfig cfg) throws IOException {
        System.out.println();
        System.out.println(">>> Running " + exp.name() + "...");
        long start = System.currentTimeMillis();

        ExperimentResult result = exp.run(cfg);
        long elapsed = System.currentTimeMillis() - start;

        Path out = ResultWriter.write(result);
        System.out.println(String.format(Locale.US, ">>> Completed %s in %d ms (%d rows generated)",
                exp.name(), elapsed, result.getRows().size()));
        System.out.println(">>> CSV written to: " + out.toAbsolutePath());

        // Print first 5 rows to console for rapid verification
        printResultPreview(result, 5);
        return out;
    }

    public static void runAllExperiments(SimConfig cfg) throws IOException {
        System.out.println("\n=======================================================");
        System.out.println(" Executing Full Experiment Suite (Exp 1 through Exp 4)");
        System.out.println("=======================================================");
        runAndPrint(new Exp1_Density(), cfg);
        runAndPrint(new Exp2_Startup(), cfg);
        runAndPrint(new Exp3_NoisyNeighbour(), cfg);
        runAndPrint(new Exp4_MixedTenancy(), cfg);
        runAndPrint(new Exp5_Energy(), cfg);
        runAndPrint(new Exp6_Latency(), cfg);
        System.out.println("\nAll 6 experiments completed successfully.");
    }

    private static void printResultPreview(ExperimentResult result, int maxRows) {
        if (result.getRows().isEmpty()) return;
        System.out.println("\n--- Table Preview (first " + Math.min(maxRows, result.getRows().size()) + " rows) ---");
        for (String col : result.getColumns()) {
            System.out.print(col + " | ");
        }
        System.out.println();
        for (int i = 0; i < Math.min(maxRows, result.getRows().size()); i++) {
            Map<String, Object> row = result.getRows().get(i);
            for (String col : result.getColumns()) {
                System.out.print(row.get(col) + " | ");
            }
            System.out.println();
        }
    }

    private static Experiment selectExperiment(int num) {
        switch (num) {
            case 1: return new Exp1_Density();
            case 2: return new Exp2_Startup();
            case 3: return new Exp3_NoisyNeighbour();
            case 4: return new Exp4_MixedTenancy();
            case 5: return new Exp5_Energy();
            case 6: return new Exp6_Latency();
            default: throw new IllegalArgumentException("Unknown experiment number: " + num);
        }
    }

    private static void printUsage() {
        System.out.println("CloudSim 4.0 Isolation Study CLI Usage:");
        System.out.println("  java isolationstudy.Main [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --exp <1|2|3|4|5|6|all>   Run specified experiment(s)");
        System.out.println("  --config <path>           Path to custom properties file");
        System.out.println("  --sweep <exp> <param> <min> <max> <step>   Run parameter sweep");
        System.out.println("  --dashboard               Generate self-contained HTML dashboard");
        System.out.println("  --help, -h                Show this help message");
        System.out.println();
        System.out.println("Without options, launches the interactive console menu.");
    }
}

