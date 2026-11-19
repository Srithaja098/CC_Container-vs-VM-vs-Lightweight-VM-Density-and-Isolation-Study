package isolationstudy;

/**
 * How an instance may use host CPU (item 5 of the model brief).
 *
 * <ul>
 *   <li>{@link #PINNED}: owns whole physical cores exclusively. Reserved even when idle,
 *       never slowed by neighbours, and limits how many instances fit on a host.</li>
 *   <li>{@link #UNCAPPED}: no limit. Takes a share of the common pool proportional to what it
 *       asks for, so a neighbour that asks for more (threads, bursting) takes more.</li>
 *   <li>{@link #CAPPED}: like UNCAPPED, but what it asks for is clipped to its provisioned
 *       vCPUs, so a neighbour cannot claim more than it was sold.</li>
 * </ul>
 */
public enum CapPolicy {
    PINNED,
    UNCAPPED,
    CAPPED
}
