# Results summary

## Run record

- Date/time:
- Code revision:
- CloudSim version:
- Base configuration:
- Seed: 42
- Repetitions per condition: 5 deterministic repeatability runs
- CSV files reviewed:

## Main table

| Measure | Full VM | Container | Lightweight VM | Notes |
| --- | ---: | ---: | ---: | --- |
| Density (instances/host) | — | — | — | Fill from `density.csv`; confirm host count is 1. |
| 32-instance fleet ready (s) | — | — | — | Fill from `startup.csv`; also report created count. |
| No-neighbour victim slowdown (x) | — | — | — | Mean over successful victim cloudlets only. |
| Noisy-neighbour victim slowdown (x) | — | — | — | Mean over successful victim cloudlets only. |
| Noisy / baseline slowdown ratio (x) | — | — | — | Pair by replicate; summarize actual CSV values. |

## Findings

| Finding | Number | One-line explanation |
| --- | --- | --- |
| Density ordering | — | — |
| Start-up behavior across fleet sizes | — | — |
| Noisy-neighbour effect | — | — |
| Sensitivity outcome | — | — |

## Data checks and unusual observations

- Missing values / unfinished victim cloudlets:
- Requested versus created fleet discrepancies:
- Outliers or replicate disagreements:
- Non-monotonic trends (especially fleet size versus ready time):
- Other CSV or chart issues:

## Limitations

- These are CloudSim model outputs, not measurements from deployed container, VM, or lightweight-VM platforms.
- Five runs at fixed seed 42 are deterministic repeatability checks in the current model, not independent samples; do not present confidence intervals from them.
- Start-up is modelled as parallel per-instance delay, with no boot contention. The current model predicts 30.00 / 2.00 / 3.80 seconds for full VM / container / lightweight VM, which differs from the supplied 30.00 / 2.07 / 3.79-second reference.
- The noisy-neighbour test uses four victims, one longer 8x-demand neighbour, and one fixed cloudlet size. It does not represent workload diversity or a production scheduler.
- Density depends on the configured host and instance memory/core sizes; conclusions should not be generalized to different hardware without sensitivity runs.
- Slowdown is computed only for victim cloudlets that finish successfully. Any unfinished victim count must be shown alongside the mean.
