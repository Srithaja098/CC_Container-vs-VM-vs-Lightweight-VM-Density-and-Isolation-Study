package isolationstudy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tabular output from an experiment. Each row uses the same ordered columns. */
public final class ExperimentResult {
    private final String name;
    private final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    private List<String> columns;

    public ExperimentResult(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("experiment name must not be empty");
        }
        this.name = name;
    }

    public String getName() { return name; }

    /** Adds one row. Keys establish CSV column order on the first row. */
    public void addRow(Map<String, Object> row) {
        if (row == null) {
            throw new IllegalArgumentException("row must not be null");
        }
        if (columns == null) {
            columns = new ArrayList<String>(row.keySet());
        } else if (!columns.equals(new ArrayList<String>(row.keySet()))) {
            throw new IllegalArgumentException("all result rows must have the same ordered columns");
        }
        rows.add(Collections.unmodifiableMap(new LinkedHashMap<String, Object>(row)));
    }

    public void addRow(Object... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("provide alternating column names and values");
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (!(keyValues[i] instanceof String)) {
                throw new IllegalArgumentException("column names must be strings");
            }
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        addRow(row);
    }

    public List<String> getColumns() {
        return columns == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(columns);
    }

    public List<Map<String, Object>> getRows() {
        return Collections.unmodifiableList(rows);
    }
}
