package isolationstudy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Writes experiment tables as UTF-8 CSV with locale-independent numeric formatting. */
public final class ResultWriter {
    private ResultWriter() { }

    /** Writes to code/results/{experiment-name}.csv. */
    public static Path write(ExperimentResult result) throws IOException {
        Path output = Paths.get("code", "results", result.getName() + ".csv");
        write(output, result);
        return output;
    }

    public static void write(Path output, ExperimentResult result) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> columns = result.getColumns();
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writeFields(writer, columns);
            writer.newLine();
            for (Map<String, Object> row : result.getRows()) {
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) writer.write(',');
                    Object value = row.get(columns.get(i));
                    writeField(writer, format(value));
                }
                writer.newLine();
            }
        }
    }

    private static String format(Object value) {
        if (value == null) return "";
        if (value instanceof Double || value instanceof Float) {
            return String.format(Locale.US, "%.6f", ((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    private static void writeFields(BufferedWriter writer, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) writer.write(',');
            writeField(writer, fields.get(i));
        }
    }

    private static void writeField(BufferedWriter writer, String value) throws IOException {
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (quote) writer.write('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') writer.write('"');
            writer.write(c);
        }
        if (quote) writer.write('"');
    }
}
