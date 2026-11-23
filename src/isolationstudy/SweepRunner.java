package isolationstudy;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sweeps one {@link SimConfig} parameter across a range for any {@link Experiment}
 * and outputs results to {@code code/results/sweep_<parameter>.csv}.
 * Owned by Person 3.
 */
public final class SweepRunner {

    private SweepRunner() { }

    /**
     * Executes a parameter sweep over [min, max] with specified step.
     */
    public static Path runSweep(Experiment exp, SimConfig baseCfg, String parameter,
                                double min, double max, double step) throws IOException {
        if (step <= 0.0) {
            throw new IllegalArgumentException("sweep step must be > 0, got " + step);
        }
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
        }

        List<Double> values = new ArrayList<Double>();
        for (double v = min; v <= max + (step * 0.001); v += step) {
            values.add(v);
        }

        double[] arr = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            arr[i] = values.get(i);
        }
        return runSweep(exp, baseCfg, parameter, arr);
    }

    /**
     * Executes a parameter sweep over a discrete array of values.
     */
    public static Path runSweep(Experiment exp, SimConfig baseCfg, String parameter,
                                double[] values) throws IOException {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("sweep values must not be empty");
        }

        String safeParamName = parameter.trim().toLowerCase(Locale.US).replace('.', '_').replace('-', '_');
        String resultName = "sweep_" + safeParamName;
        ExperimentResult combinedResult = new ExperimentResult(resultName);

        System.out.println(String.format(Locale.US, "Starting sweep on '%s' across %d points for experiment '%s'...",
                parameter, values.length, exp.name()));

        for (int idx = 0; idx < values.length; idx++) {
            double val = values[idx];
            // Format parameter string (integer if whole, otherwise 6 decimals)
            String valStr = (val == Math.floor(val)) ? String.valueOf((long) val) : String.format(Locale.US, "%.4f", val);

            SimConfig modifiedCfg = baseCfg.with(parameter, valStr);
            ExperimentResult pointResult = exp.run(modifiedCfg);

            for (Map<String, Object> pointRow : pointResult.getRows()) {
                Map<String, Object> augmentedRow = new LinkedHashMap<String, Object>();
                augmentedRow.put("sweep_parameter", safeParamName);
                augmentedRow.put("sweep_value", val);
                augmentedRow.putAll(pointRow);
                combinedResult.addRow(augmentedRow);
            }

            System.out.println(String.format(Locale.US, "  [Point %d/%d] %s = %s -> collected %d observations",
                    idx + 1, values.length, safeParamName, valStr, pointResult.getRows().size()));
        }

        Path outputPath = Paths.get("code", "results", resultName + ".csv");
        ResultWriter.write(outputPath, combinedResult);
        System.out.println("Sweep complete! Output written to: " + outputPath.toAbsolutePath());
        return outputPath;
    }

    /**
     * CLI entry point for standalone sweeps.
     * Usage: SweepRunner &lt;exp 1|2|3|4&gt; &lt;param&gt; &lt;min&gt; &lt;max&gt; &lt;step&gt;
     */
    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: java isolationstudy.SweepRunner <1|2|3|4> <paramName> <min> <max> <step> [configPath]");
            System.out.println("Example: java isolationstudy.SweepRunner 1 host.ram_mb 16384 65536 8192");
            System.out.println("Example: java isolationstudy.SweepRunner 3 workload.neighbour_intensity 1.0 16.0 2.0");
            System.exit(1);
        }

        try {
            int expNum = Integer.parseInt(args[0].trim());
            String param = args[1].trim();
            double min = Double.parseDouble(args[2].trim());
            double max = Double.parseDouble(args[3].trim());
            double step = Double.parseDouble(args[4].trim());

            SimConfig cfg = (args.length >= 6) ? SimConfig.load(args[5].trim()) : SimConfig.load();

            Experiment exp;
            switch (expNum) {
                case 1: exp = new Exp1_Density(); break;
                case 2: exp = new Exp2_Startup(); break;
                case 3: exp = new Exp3_NoisyNeighbour(); break;
                case 4: exp = new Exp4_MixedTenancy(); break;
                default:
                    System.err.println("Unknown experiment number: " + expNum);
                    System.exit(1);
                    return;
            }

            runSweep(exp, cfg, param, min, max, step);
        } catch (Exception e) {
            System.err.println("Sweep failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}

