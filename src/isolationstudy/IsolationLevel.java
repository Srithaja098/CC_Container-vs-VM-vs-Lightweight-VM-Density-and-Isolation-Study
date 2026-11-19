package isolationstudy;

/**
 * The three isolation technologies compared in the study.
 *
 * <p>Contract (shared ground rules): the constant names FULL_VM, CONTAINER and
 * LIGHTWEIGHT_VM must not change. {@link #label()} gives the lower_snake_case
 * form that Person 2 can use in CSV cells.
 *
 * <ul>
 *   <li>FULL_VM - KVM style: dedicated (pinned) cores, large memory footprint, slow start.</li>
 *   <li>CONTAINER - Docker style: no CPU cap, tiny footprint, fast start.</li>
 *   <li>LIGHTWEIGHT_VM - Kata/gVisor style: capped vCPUs, mid footprint, mid start.</li>
 * </ul>
 */
public enum IsolationLevel {
    FULL_VM("full_vm"),
    CONTAINER("container"),
    LIGHTWEIGHT_VM("lightweight_vm");

    private final String label;

    IsolationLevel(String label) {
        this.label = label;
    }

    /** lower_snake_case name, safe for CSV cells and file names. */
    public String label() {
        return label;
    }
}
