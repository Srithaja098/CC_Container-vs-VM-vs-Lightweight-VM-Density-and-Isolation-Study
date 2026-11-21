# Sensitivity plan

Use the same three experiments, output columns, workload, and fixed seed 42 for every scenario. Change one parameter family at a time, then run a combined corner check if time allows. Preserve the base CSVs and write sensitivity runs to separate named files or folders so they cannot be mistaken for the main result.

| Parameter family | Low case | Base | High case | Application |
| --- | ---: | ---: | ---: | --- |
| Start-up delay | 0.75 × each configured level delay | 1.00 × | 1.25 × | Re-run startup for fleet sizes 1, 8, 16, 32, 64. Preserve zero as zero. |
| CPU overhead | 0.75 × each configured level overhead | 1.00 × | 1.25 × | Re-run all experiments because effective CPU service affects completion time and noisy-neighbour slowdown. Keep overhead below 100%. |
| Host size | 0.75 × host cores and RAM | 1.00 × | 1.25 × host cores and RAM | Re-run density and noisy-neighbour experiments. For integer resources, round cores and RAM to the nearest whole unit and record the actual values used. Keep per-core MIPS and instance size fixed. |

## Judging whether conclusions change

1. Confirm each sensitivity run's configuration in its output or a companion run manifest. Do not compare a result if its actual rounded host size or varied values are unknown.
2. Re-evaluate H1, H2, and H3 separately against their stated support rules. A rank reversal in density or level start-up order changes that conclusion. For start-up, a fleet-size trend that breaks the near-constant-ready-time claim changes H2 even if the level ranking stays the same.
3. For H3, compare each replicate's neighbour condition with its same-replicate baseline. A change from above-baseline to no measurable increase, or a reversal in which policy is more affected, changes that conclusion. Report the actual slowdown ratios; do not impose an unsupported arbitrary tolerance.
4. Report absolute values and ordering changes beside the base case. Integer density can change in steps, so describe the observed count change directly. Keep deterministic reruns as repeatability checks, not confidence intervals.
5. If only magnitude changes while the hypothesis's ordering and direction remain, say the conclusion is directionally stable and quantify the changed magnitude from the CSVs. Flag non-monotonic fleet trends, missing values, rejected requested instances, and unexpected replicate disagreement.

## Implementation note

`SimConfig` currently has fixed final fields and no parameter-copy methods. Person 3 (or the config owner) should provide a way to construct configs with scaled start-up delays, CPU overheads, and host resources before running this plan. Do not edit the model constants silently or edit generated CSVs by hand.
