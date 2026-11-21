package isolationstudy;

/** One reproducible experiment producing a table of observations. */
public interface Experiment {
    String name();

    ExperimentResult run(SimConfig cfg);
}
