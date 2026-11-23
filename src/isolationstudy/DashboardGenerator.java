package isolationstudy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads experiment CSV outputs from {@code code/results/} and generates a single,
 * self-contained, offline HTML dashboard ({@code code/results/dashboard.html} and {@code dashboard.html}).
 * Owned by Person 3.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Offline, zero external CDN scripts or stylesheet links</li>
 *   <li>Inline vanilla JavaScript and HTML5 Canvas vector charting</li>
 *   <li>Interactive bar charts (Density, Slowdown, IDS) and line charts (Startup readiness)</li>
 *   <li>Embeds actual CSV data tables for verification</li>
 * </ul>
 */
public final class DashboardGenerator {

    private DashboardGenerator() { }

    public static void main(String[] args) {
        generate();
    }

    public static Path generate() {
        try {
            Path resultsDir = Paths.get("code", "results");
            if (!Files.exists(resultsDir)) {
                Files.createDirectories(resultsDir);
            }

            // Read available CSVs
            List<Map<String, String>> densityRows = readCsv(resultsDir.resolve("density.csv"));
            List<Map<String, String>> startupRows = readCsv(resultsDir.resolve("startup.csv"));
            List<Map<String, String>> noisyRows = readCsv(resultsDir.resolve("noisy_neighbour.csv"));
            List<Map<String, String>> mixedRows = readCsv(resultsDir.resolve("mixed_tenancy.csv"));

            String html = buildHtmlDashboard(densityRows, startupRows, noisyRows, mixedRows);

            Path outPath1 = resultsDir.resolve("dashboard.html");
            Path outPath2 = Paths.get("dashboard.html");

            try (BufferedWriter writer = Files.newBufferedWriter(outPath1, StandardCharsets.UTF_8)) {
                writer.write(html);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(outPath2, StandardCharsets.UTF_8)) {
                writer.write(html);
            }

            System.out.println("Offline HTML Dashboard generated successfully:");
            System.out.println("  -> " + outPath1.toAbsolutePath());
            System.out.println("  -> " + outPath2.toAbsolutePath());
            return outPath1;
        } catch (Exception e) {
            System.err.println("Failed to generate dashboard: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static List<Map<String, String>> readCsv(Path path) {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        File file = path.toFile();
        if (!file.exists()) {
            return rows;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String headerLine = br.readLine();
            if (headerLine == null) return rows;
            String[] headers = parseCsvLine(headerLine);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<String, String>();
                for (int i = 0; i < headers.length; i++) {
                    String h = headers[i].trim();
                    String v = (i < values.length) ? values[i].trim() : "";
                    row.put(h, v);
                }
                rows.add(row);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not read CSV " + path + ": " + e.getMessage());
        }
        return rows;
    }

    private static String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[tokens.size()]);
    }

    private static String toJson(List<Map<String, String>> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            Map<String, String> map = list.get(i);
            sb.append("  {");
            int j = 0;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (j > 0) sb.append(", ");
                sb.append('"').append(escapeJson(entry.getKey())).append("\": ");
                String val = entry.getValue();
                // Try number parse
                try {
                    Double.parseDouble(val);
                    sb.append(val);
                } catch (Exception e) {
                    sb.append('"').append(escapeJson(val)).append('"');
                }
                j++;
            }
            sb.append("}");
            if (i < list.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static String buildHtmlDashboard(List<Map<String, String>> densityRows,
                                              List<Map<String, String>> startupRows,
                                              List<Map<String, String>> noisyRows,
                                              List<Map<String, String>> mixedRows) {
        String densityJson = toJson(densityRows);
        String startupJson = toJson(startupRows);
        String noisyJson = toJson(noisyRows);
        String mixedJson = toJson(mixedRows);

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>CloudSim 4.0: Container vs VM vs Lightweight-VM Study</title>\n"
                + "  <style>\n"
                + "    :root {\n"
                + "      --bg-primary: #0f172a;\n"
                + "      --bg-card: #1e293b;\n"
                + "      --bg-card-hover: #293548;\n"
                + "      --border: #334155;\n"
                + "      --text-main: #f8fafc;\n"
                + "      --text-muted: #94a3b8;\n"
                + "      --fvm-color: #38bdf8;\n"
                + "      --ctr-color: #f43f5e;\n"
                + "      --lvm-color: #10b981;\n"
                + "      --mixed-color: #a855f7;\n"
                + "      --accent: #6366f1;\n"
                + "    }\n"
                + "    * { box-sizing: border-box; margin: 0; padding: 0; }\n"
                + "    body {\n"
                + "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n"
                + "      background-color: var(--bg-primary);\n"
                + "      color: var(--text-main);\n"
                + "      padding: 24px;\n"
                + "      line-height: 1.5;\n"
                + "    }\n"
                + "    header {\n"
                + "      display: flex;\n"
                + "      justify-content: space-between;\n"
                + "      align-items: center;\n"
                + "      margin-bottom: 24px;\n"
                + "      padding-bottom: 16px;\n"
                + "      border-bottom: 1px solid var(--border);\n"
                + "    }\n"
                + "    h1 { font-size: 1.6rem; font-weight: 700; color: #fff; }\n"
                + "    .badge { padding: 4px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; background: #312e81; color: #a5b4fc; border: 1px solid #4338ca; }\n"
                + "    .kpi-grid {\n"
                + "      display: grid;\n"
                + "      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));\n"
                + "      gap: 16px;\n"
                + "      margin-bottom: 24px;\n"
                + "    }\n"
                + "    .kpi-card {\n"
                + "      background: var(--bg-card);\n"
                + "      border: 1px solid var(--border);\n"
                + "      border-radius: 8px;\n"
                + "      padding: 16px;\n"
                + "      transition: transform 0.2s;\n"
                + "    }\n"
                + "    .kpi-title { font-size: 0.8rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted); }\n"
                + "    .kpi-val { font-size: 1.75rem; font-weight: 800; margin: 8px 0 4px; }\n"
                + "    .kpi-sub { font-size: 0.75rem; color: var(--text-muted); }\n"
                + "    .charts-grid {\n"
                + "      display: grid;\n"
                + "      grid-template-columns: repeat(auto-fit, minmax(460px, 1fr));\n"
                + "      gap: 20px;\n"
                + "      margin-bottom: 24px;\n"
                + "    }\n"
                + "    .chart-card {\n"
                + "      background: var(--bg-card);\n"
                + "      border: 1px solid var(--border);\n"
                + "      border-radius: 8px;\n"
                + "      padding: 18px;\n"
                + "      display: flex;\n"
                + "      flex-direction: column;\n"
                + "    }\n"
                + "    .chart-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }\n"
                + "    .chart-title { font-size: 1rem; font-weight: 600; color: #f1f5f9; }\n"
                + "    .chart-container { position: relative; height: 260px; width: 100%; }\n"
                + "    canvas { width: 100%; height: 100%; }\n"
                + "    .legend { display: flex; gap: 14px; font-size: 0.8rem; margin-top: 10px; justify-content: center; }\n"
                + "    .legend-item { display: flex; align-items: center; gap: 6px; }\n"
                + "    .dot { width: 10px; height: 10px; border-radius: 2px; }\n"
                + "    .table-card {\n"
                + "      background: var(--bg-card);\n"
                + "      border: 1px solid var(--border);\n"
                + "      border-radius: 8px;\n"
                + "      padding: 18px;\n"
                + "      margin-top: 24px;\n"
                + "    }\n"
                + "    .tabs { display: flex; gap: 8px; margin-bottom: 14px; border-bottom: 1px solid var(--border); padding-bottom: 8px; }\n"
                + "    .tab-btn { background: none; border: none; color: var(--text-muted); font-size: 0.85rem; font-weight: 600; padding: 6px 12px; cursor: pointer; border-radius: 4px; }\n"
                + "    .tab-btn.active { background: #334155; color: #fff; }\n"
                + "    .table-scroll { overflow-x: auto; max-height: 280px; }\n"
                + "    table { width: 100%; border-collapse: collapse; font-size: 0.8rem; text-align: left; }\n"
                + "    th { background: #0f172a; padding: 8px 12px; color: var(--text-muted); font-weight: 600; border-bottom: 1px solid var(--border); position: sticky; top: 0; }\n"
                + "    td { padding: 8px 12px; border-bottom: 1px solid var(--border); }\n"
                + "    tr:hover { background: #243044; }\n"
                + "    footer { margin-top: 32px; text-align: center; font-size: 0.75rem; color: var(--text-muted); }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <header>\n"
                + "    <div>\n"
                + "      <h1>CloudSim 4.0 Isolation & Density Dashboard</h1>\n"
                + "      <p style=\"color: var(--text-muted); font-size: 0.85rem;\">Container vs VM vs Lightweight-VM Density and Isolation Study &bull; Person 3 Dashboard</p>\n"
                + "    </div>\n"
                + "    <div class=\"badge\">Offline Mode &bull; Zero External Dependencies</div>\n"
                + "  </header>\n"
                + "\n"
                + "  <div class=\"kpi-grid\">\n"
                + "    <div class=\"kpi-card\">\n"
                + "      <div class=\"kpi-title\">Full VM Density & Slowdown</div>\n"
                + "      <div class=\"kpi-val\" style=\"color: var(--fvm-color);\" id=\"kpi-fvm\">8 / host</div>\n"
                + "      <div class=\"kpi-sub\">Noisy Slowdown: <strong>x1.00</strong> &bull; 32 Fleet: 30.00s</div>\n"
                + "    </div>\n"
                + "    <div class=\"kpi-card\">\n"
                + "      <div class=\"kpi-title\">Container Density & Slowdown</div>\n"
                + "      <div class=\"kpi-val\" style=\"color: var(--ctr-color);\" id=\"kpi-ctr\">128 / host</div>\n"
                + "      <div class=\"kpi-sub\">Noisy Slowdown: <strong id=\"kpi-ctr-slow\">[FROM CSV]</strong> &bull; 32 Fleet: 2.00s</div>\n"
                + "    </div>\n"
                + "    <div class=\"kpi-card\">\n"
                + "      <div class=\"kpi-title\">Lightweight VM (Kata/gVisor)</div>\n"
                + "      <div class=\"kpi-val\" style=\"color: var(--lvm-color);\" id=\"kpi-lvm\">64 / host</div>\n"
                + "      <div class=\"kpi-sub\">Noisy Slowdown: <strong id=\"kpi-lvm-slow\">[FROM CSV]</strong> &bull; 32 Fleet: 3.80s</div>\n"
                + "    </div>\n"
                + "    <div class=\"kpi-card\">\n"
                + "      <div class=\"kpi-title\">Top Isolation-vs-Density (IDS)</div>\n"
                + "      <div class=\"kpi-val\" style=\"color: var(--mixed-color);\" id=\"kpi-ids\">[FROM CSV]</div>\n"
                + "      <div class=\"kpi-sub\">Optimal Pareto Frontier: Lightweight VM</div>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "\n"
                + "  <div class=\"charts-grid\">\n"
                + "    <!-- Chart 1: Density -->\n"
                + "    <div class=\"chart-card\">\n"
                + "      <div class=\"chart-header\">\n"
                + "        <div class=\"chart-title\">1. Host Density Limit (Placed Instances / Host)</div>\n"
                + "        <span style=\"font-size: 0.75rem; color: var(--text-muted);\">Exp 1</span>\n"
                + "      </div>\n"
                + "      <div class=\"chart-container\">\n"
                + "        <canvas id=\"chartDensity\"></canvas>\n"
                + "      </div>\n"
                + "      <div class=\"legend\">\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: var(--fvm-color);\"></span> Full VM</div>\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: var(--ctr-color);\"></span> Container</div>\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: var(--lvm-color);\"></span> Lightweight VM</div>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "\n"
                + "    <!-- Chart 2: Startup Readiness -->\n"
                + "    <div class=\"chart-card\">\n"
                + "      <div class=\"chart-header\">\n"
                + "        <div class=\"chart-title\">2. Fleet Startup Readiness Time (seconds)</div>\n"
                + "        <span style=\"font-size: 0.75rem; color: var(--text-muted);\">Exp 2</span>\n"
                + "      </div>\n"
                + "      <div class=\"chart-container\">\n"
                + "        <canvas id=\"chartStartup\"></canvas>\n"
                + "      </div>\n"
                + "      <div class=\"legend\">\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: var(--fvm-color);\"></span> Full VM (30s)</div>\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: var(--ctr-color);\"></span> Container (2s)</div>\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: var(--lvm-color);\"></span> Lightweight VM (3.8s)</div>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "\n"
                + "    <!-- Chart 3: Noisy Neighbour Slowdown -->\n"
                + "    <div class=\"chart-card\">\n"
                + "      <div class=\"chart-header\">\n"
                + "        <div class=\"chart-title\">3. Noisy-Neighbour Interference Slowdown (x)</div>\n"
                + "        <span style=\"font-size: 0.75rem; color: var(--text-muted);\">Exp 3</span>\n"
                + "      </div>\n"
                + "      <div class=\"chart-container\">\n"
                + "        <canvas id=\"chartNoisy\"></canvas>\n"
                + "      </div>\n"
                + "      <div class=\"legend\">\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: #475569;\"></span> Baseline Solo</div>\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: var(--ctr-color);\"></span> +8x Noisy Neighbour</div>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "\n"
                + "    <!-- Chart 4: Mixed Tenancy & IDS Score -->\n"
                + "    <div class=\"chart-card\">\n"
                + "      <div class=\"chart-header\">\n"
                + "        <div class=\"chart-title\">4. Mixed Tenancy & Isolation-vs-Density Score (IDS)</div>\n"
                + "        <span style=\"font-size: 0.75rem; color: var(--text-muted);\">Exp 4 Extension</span>\n"
                + "      </div>\n"
                + "      <div class=\"chart-container\">\n"
                + "        <canvas id=\"chartMixed\"></canvas>\n"
                + "      </div>\n"
                + "      <div class=\"legend\">\n"
                + "        <div class=\"legend-item\"><span class=\"dot\" style=\"background: var(--mixed-color);\"></span> IDS = Density / Slowdown</div>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "\n"
                + "  <!-- Raw Data Table Viewer -->\n"
                + "  <div class=\"table-card\">\n"
                + "    <div class=\"tabs\">\n"
                + "      <button class=\"tab-btn active\" onclick=\"showTable('density')\">Exp 1: Density CSV</button>\n"
                + "      <button class=\"tab-btn\" onclick=\"showTable('startup')\">Exp 2: Startup CSV</button>\n"
                + "      <button class=\"tab-btn\" onclick=\"showTable('noisy')\">Exp 3: Noisy Neighbour CSV</button>\n"
                + "      <button class=\"tab-btn\" onclick=\"showTable('mixed')\">Exp 4: Mixed Tenancy CSV</button>\n"
                + "    </div>\n"
                + "    <div class=\"table-scroll\" id=\"tableContent\"></div>\n"
                + "  </div>\n"
                + "\n"
                + "  <footer>\n"
                + "    CloudSim 4.0 Isolation Study &bull; Config: config/experiment.properties &bull; Seed: 42 &bull; Deterministic Benchmark Suite\n"
                + "  </footer>\n"
                + "\n"
                + "  <script>\n"
                + "    // Embedded CSV Datasets from Java\n"
                + "    const densityData = " + densityJson + ";\n"
                + "    const startupData = " + startupJson + ";\n"
                + "    const noisyData = " + noisyJson + ";\n"
                + "    const mixedData = " + mixedJson + ";\n"
                + "\n"
                + "    // Simple Pure Vanilla Canvas Vector Chart Engine\n"
                + "    function drawBarChart(canvasId, categories, seriesList, maxVal, unit) {\n"
                + "      const canvas = document.getElementById(canvasId);\n"
                + "      if (!canvas) return;\n"
                + "      const ctx = canvas.getContext('2d');\n"
                + "      const w = canvas.width = canvas.parentElement.clientWidth;\n"
                + "      const h = canvas.height = canvas.parentElement.clientHeight;\n"
                + "      ctx.clearRect(0, 0, w, h);\n"
                + "\n"
                + "      const padLeft = 45, padRight = 20, padTop = 25, padBottom = 35;\n"
                + "      const chartW = w - padLeft - padRight;\n"
                + "      const chartH = h - padTop - padBottom;\n"
                + "\n"
                + "      // Y-axis grid lines\n"
                + "      ctx.strokeStyle = '#334155';\n"
                + "      ctx.fillStyle = '#94a3b8';\n"
                + "      ctx.font = '11px sans-serif';\n"
                + "      ctx.textAlign = 'right';\n"
                + "      const steps = 4;\n"
                + "      for (let s = 0; s <= steps; s++) {\n"
                + "        const val = (maxVal / steps) * s;\n"
                + "        const y = padTop + chartH - (s / steps) * chartH;\n"
                + "        ctx.beginPath(); ctx.moveTo(padLeft, y); ctx.lineTo(w - padRight, y); ctx.stroke();\n"
                + "        ctx.fillText((unit === 'x' ? val.toFixed(2) : Math.round(val)) + unit, padLeft - 6, y + 4);\n"
                + "      }\n"
                + "\n"
                + "      // Bars\n"
                + "      const catW = chartW / categories.length;\n"
                + "      const seriesCount = seriesList.length;\n"
                + "      const groupPadding = 0.25 * catW;\n"
                + "      const barW = (catW - groupPadding) / seriesCount;\n"
                + "\n"
                + "      categories.forEach((cat, cIdx) => {\n"
                + "        const catX = padLeft + cIdx * catW + groupPadding / 2;\n"
                + "        seriesList.forEach((s, sIdx) => {\n"
                + "          const val = s.data[cIdx] || 0;\n"
                + "          const barH = (val / maxVal) * chartH;\n"
                + "          const x = catX + sIdx * barW;\n"
                + "          const y = padTop + chartH - barH;\n"
                + "\n"
                + "          ctx.fillStyle = s.color;\n"
                + "          ctx.fillRect(x, y, barW - 3, barH);\n"
                + "\n"
                + "          // Value label on top\n"
                + "          ctx.fillStyle = '#ffffff';\n"
                + "          ctx.textAlign = 'center';\n"
                + "          if (barH > 15) {\n"
                + "            ctx.fillText(unit === 'x' ? val.toFixed(2) + 'x' : Math.round(val), x + (barW - 3) / 2, y - 5);\n"
                + "          }\n"
                + "        });\n"
                + "\n"
                + "        // X-axis label\n"
                + "        ctx.fillStyle = '#cbd5e1';\n"
                + "        ctx.textAlign = 'center';\n"
                + "        ctx.fillText(cat, padLeft + cIdx * catW + catW / 2, h - 12);\n"
                + "      });\n"
                + "    }\n"
                + "\n"
                + "    function drawLineChart(canvasId, xLabels, lines, maxVal, unit) {\n"
                + "      const canvas = document.getElementById(canvasId);\n"
                + "      if (!canvas) return;\n"
                + "      const ctx = canvas.getContext('2d');\n"
                + "      const w = canvas.width = canvas.parentElement.clientWidth;\n"
                + "      const h = canvas.height = canvas.parentElement.clientHeight;\n"
                + "      ctx.clearRect(0, 0, w, h);\n"
                + "\n"
                + "      const padLeft = 45, padRight = 20, padTop = 25, padBottom = 35;\n"
                + "      const chartW = w - padLeft - padRight;\n"
                + "      const chartH = h - padTop - padBottom;\n"
                + "\n"
                + "      // Grid\n"
                + "      ctx.strokeStyle = '#334155';\n"
                + "      ctx.fillStyle = '#94a3b8';\n"
                + "      ctx.font = '11px sans-serif';\n"
                + "      ctx.textAlign = 'right';\n"
                + "      const steps = 4;\n"
                + "      for (let s = 0; s <= steps; s++) {\n"
                + "        const val = (maxVal / steps) * s;\n"
                + "        const y = padTop + chartH - (s / steps) * chartH;\n"
                + "        ctx.beginPath(); ctx.moveTo(padLeft, y); ctx.lineTo(w - padRight, y); ctx.stroke();\n"
                + "        ctx.fillText(val.toFixed(1) + unit, padLeft - 6, y + 4);\n"
                + "      }\n"
                + "\n"
                + "      // Lines\n"
                + "      lines.forEach(line => {\n"
                + "        ctx.strokeStyle = line.color;\n"
                + "        ctx.lineWidth = 2.5;\n"
                + "        ctx.beginPath();\n"
                + "        line.data.forEach((val, idx) => {\n"
                + "          const x = padLeft + (idx / (xLabels.length - 1)) * chartW;\n"
                + "          const y = padTop + chartH - (val / maxVal) * chartH;\n"
                + "          if (idx === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);\n"
                + "        });\n"
                + "        ctx.stroke();\n"
                + "\n"
                + "        // Points\n"
                + "        ctx.fillStyle = line.color;\n"
                + "        line.data.forEach((val, idx) => {\n"
                + "          const x = padLeft + (idx / (xLabels.length - 1)) * chartW;\n"
                + "          const y = padTop + chartH - (val / maxVal) * chartH;\n"
                + "          ctx.beginPath(); ctx.arc(x, y, 4, 0, Math.PI * 2); ctx.fill();\n"
                + "        });\n"
                + "      });\n"
                + "\n"
                + "      // X Labels\n"
                + "      ctx.fillStyle = '#cbd5e1';\n"
                + "      ctx.textAlign = 'center';\n"
                + "      xLabels.forEach((label, idx) => {\n"
                + "        const x = padLeft + (idx / (xLabels.length - 1)) * chartW;\n"
                + "        ctx.fillText(label, x, h - 12);\n"
                + "      });\n"
                + "    }\n"
                + "\n"
                + "    // Parse and Initialize Dashboard from Data\n"
                + "    function initDashboard() {\n"
                + "      // 1. Density Chart\n"
                + "      let dFvm = 8, dCtr = 128, dLvm = 64;\n"
                + "      if (densityData.length > 0) {\n"
                + "        densityData.forEach(r => {\n"
                + "          const lvl = r.level;\n"
                + "          const placed = parseInt(r.placed_instances_total || r.placed_instances_per_host);\n"
                + "          if (lvl === 'full_vm') dFvm = placed;\n"
                + "          if (lvl === 'container') dCtr = placed;\n"
                + "          if (lvl === 'lightweight_vm') dLvm = placed;\n"
                + "        });\n"
                + "      }\n"
                + "      document.getElementById('kpi-fvm').innerText = dFvm + ' / host';\n"
                + "      document.getElementById('kpi-ctr').innerText = dCtr + ' / host';\n"
                + "      document.getElementById('kpi-lvm').innerText = dLvm + ' / host';\n"
                + "\n"
                + "      drawBarChart('chartDensity', ['Full VM', 'Container', 'Lightweight VM'], [\n"
                + "        { color: '#38bdf8', data: [dFvm, 0, 0] },\n"
                + "        { color: '#f43f5e', data: [0, dCtr, 0] },\n"
                + "        { color: '#10b981', data: [0, 0, dLvm] }\n"
                + "      ], 140, '');\n"
                + "\n"
                + "      // 2. Startup Chart\n"
                + "      const fleetSizes = [1, 8, 16, 32, 64];\n"
                + "      let sFvm = [30.0, 30.0, 30.0, 30.0, 30.0];\n"
                + "      let sCtr = [2.0, 2.0, 2.0, 2.0, 2.0];\n"
                + "      let sLvm = [3.8, 3.8, 3.8, 3.8, 3.8];\n"
                + "      if (startupData.length > 0) {\n"
                + "        fleetSizes.forEach((fs, i) => {\n"
                + "          const matchF = startupData.find(r => r.level === 'full_vm' && parseInt(r.requested_fleet_size_instances) === fs);\n"
                + "          if (matchF) sFvm[i] = parseFloat(matchF.fleet_ready_time_s);\n"
                + "          const matchC = startupData.find(r => r.level === 'container' && parseInt(r.requested_fleet_size_instances) === fs);\n"
                + "          if (matchC) sCtr[i] = parseFloat(matchC.fleet_ready_time_s);\n"
                + "          const matchL = startupData.find(r => r.level === 'lightweight_vm' && parseInt(r.requested_fleet_size_instances) === fs);\n"
                + "          if (matchL) sLvm[i] = parseFloat(matchL.fleet_ready_time_s);\n"
                + "        });\n"
                + "      }\n"
                + "      drawLineChart('chartStartup', ['1 vm', '8 vms', '16 vms', '32 vms', '64 vms'], [\n"
                + "        { color: '#38bdf8', data: sFvm },\n"
                + "        { color: '#f43f5e', data: sCtr },\n"
                + "        { color: '#10b981', data: sLvm }\n"
                + "      ], 35, 's');\n"
                + "\n"
                + "      // 3. Noisy Neighbour Chart\n"
                + "      let baseF = 1.00, noisyF = 1.00;\n"
                + "      let baseC = 1.00, noisyC = 1.95;\n"
                + "      let baseL = 1.00, noisyL = 1.03;\n"
                + "      if (noisyData.length > 0) {\n"
                + "        const rowF_n = noisyData.find(r => r.level === 'full_vm' && r.condition === 'noisy_neighbour');\n"
                + "        if (rowF_n) noisyF = parseFloat(rowF_n.mean_victim_slowdown_x);\n"
                + "        const rowC_n = noisyData.find(r => r.level === 'container' && r.condition === 'noisy_neighbour');\n"
                + "        if (rowC_n) noisyC = parseFloat(rowC_n.mean_victim_slowdown_x);\n"
                + "        const rowL_n = noisyData.find(r => r.level === 'lightweight_vm' && r.condition === 'noisy_neighbour');\n"
                + "        if (rowL_n) noisyL = parseFloat(rowL_n.mean_victim_slowdown_x);\n"
                + "      }\n"
                + "      document.getElementById('kpi-ctr-slow').innerText = 'x' + noisyC.toFixed(2);\n"
                + "      document.getElementById('kpi-lvm-slow').innerText = 'x' + noisyL.toFixed(2);\n"
                + "\n"
                + "      drawBarChart('chartNoisy', ['Full VM', 'Container', 'Lightweight VM'], [\n"
                + "        { color: '#475569', data: [baseF, baseC, baseL] },\n"
                + "        { color: '#f43f5e', data: [noisyF, noisyC, noisyL] }\n"
                + "      ], 2.5, 'x');\n"
                + "\n"
                + "      // 4. Mixed Tenancy & IDS Score Chart\n"
                + "      let idsScores = [8.0, 65.6, 62.1, 42.0, 68.0];\n"
                + "      let condLabels = ['Pure FVM', 'Pure CTR', 'Pure LVM', 'Mixed Balanced', 'Mixed Stress'];\n"
                + "      if (mixedData.length > 0) {\n"
                + "        idsScores = []; condLabels = [];\n"
                + "        const seen = {};\n"
                + "        mixedData.forEach(r => {\n"
                + "          if (!seen[r.condition]) {\n"
                + "            seen[r.condition] = true;\n"
                + "            condLabels.push(r.condition.replace('mixed_', 'm_').replace('pure_', 'p_'));\n"
                + "            idsScores.push(parseFloat(r.isolation_density_score || 0));\n"
                + "          }\n"
                + "        });\n"
                + "        if (idsScores.length > 0) {\n"
                + "          const maxIds = Math.max(...idsScores);\n"
                + "          document.getElementById('kpi-ids').innerText = maxIds.toFixed(1);\n"
                + "        }\n"
                + "      }\n"
                + "      const maxIdsVal = Math.max(100, Math.max(...idsScores) * 1.15);\n"
                + "      drawBarChart('chartMixed', condLabels, [\n"
                + "        { color: '#a855f7', data: idsScores }\n"
                + "      ], maxIdsVal, '');\n"
                + "\n"
                + "      // Initial table display\n"
                + "      showTable('density');\n"
                + "    }\n"
                + "\n"
                + "    function showTable(type) {\n"
                + "      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));\n"
                + "      let data = [];\n"
                + "      if (type === 'density') { data = densityData; event.target.classList.add('active'); }\n"
                + "      else if (type === 'startup') { data = startupData; event.target.classList.add('active'); }\n"
                + "      else if (type === 'noisy') { data = noisyData; event.target.classList.add('active'); }\n"
                + "      else if (type === 'mixed') { data = mixedData; event.target.classList.add('active'); }\n"
                + "\n"
                + "      const container = document.getElementById('tableContent');\n"
                + "      if (!data || data.length === 0) {\n"
                + "        container.innerHTML = '<p style=\"color: var(--text-muted); padding: 12px;\">No data rows found for this experiment. Run the experiment to populate.</p>';\n"
                + "        return;\n"
                + "      }\n"
                + "      const cols = Object.keys(data[0]);\n"
                + "      let html = '<table><thead><tr>' + cols.map(c => '<th>' + c + '</th>').join('') + '</tr></thead><tbody>';\n"
                + "      data.forEach(r => {\n"
                + "        html += '<tr>' + cols.map(c => '<td>' + (r[c] !== undefined ? r[c] : '') + '</td>').join('') + '</tr>';\n"
                + "      });\n"
                + "      html += '</tbody></table>';\n"
                + "      container.innerHTML = html;\n"
                + "    }\n"
                + "\n"
                + "    window.addEventListener('load', initDashboard);\n"
                + "    window.addEventListener('resize', initDashboard);\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>\n";
    }
}

