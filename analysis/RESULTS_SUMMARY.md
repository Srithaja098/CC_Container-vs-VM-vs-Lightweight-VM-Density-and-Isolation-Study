# Results summary

## Run record

- Date/time: 2026-11-27
- Code revision: main (with Person 1 & Person 2 energy/latency integration)
- CloudSim version: 4.0 (official release, fixed seed 42)
- Base configuration: config/experiment.properties (1 host, 16 cores @ 2500 MIPS, 64 GB RAM)
- Seed: 42
- Repetitions per condition: 5 deterministic repeatability runs
- CSV files reviewed: density.csv, startup.csv, noisy_neighbour.csv, mixed_tenancy.csv, energy.csv

## Main table

| Measure | Full VM | Container | Lightweight VM | Notes |
| --- | ---: | ---: | ---: | --- |
| Density (instances/host) | 8 | 128 | 64 | From `density.csv`; 1-host hardware limit (FVM core-bound, CTR/LVM RAM-bound). |
| 32-instance fleet ready (s) | 30.00 | 2.00 | 3.80 | From `startup.csv`; FVM creates 8 (host saturated); CTR/LVM create all 32. |
| No-neighbour victim slowdown (x) | 1.00 | 1.00 | 1.00 | Mean over 4 successful victim cloudlets (100,000 MI). |
| Noisy-neighbour victim slowdown (x) | 1.00 | 1.50 | 1.00 | Mean over 4 victims with 1 8x-demand neighbour (1,000,000 MI). |
| Noisy / baseline slowdown ratio (x) | 1.00 | 1.50 | 1.00 | Pinned and capped profiles prevent CPU interference; uncapped containers degrade by 50%. |

## Findings

| Finding | Number | One-line explanation |
| --- | --- | --- |
| Density ordering | 1 | Containers (128) > Lightweight VMs (64) > Full VMs (8); memory overhead dictates shared density, cores limit pinned. |
| Start-up behavior across fleet sizes | 2 | Parallel boot model yields constant readiness time regardless of fleet size (Full VM: 30s, LVM: 3.8s, Container: 2s). |
| Noisy-neighbour effect | 3 | Containers suffer 1.50x slowdown under 8x neighbour demand, whereas Full VMs and Lightweight VMs remain perfectly isolated (1.00x). |
| Sensitivity outcome | 4 | Mixed tenancy uncore contention significantly reduces IDS score when high container density competes for uncore resources. |

## Data checks and unusual observations

- Missing values / unfinished victim cloudlets: None; all 4 victim cloudlets in all 5 replicates completed successfully.
- Requested versus created fleet discrepancies: In `startup.csv`, requesting 16, 32, or 64 Full VMs correctly creates only 8 instances due to 16-core host limit (2 vCPUs/instance).
- Outliers or replicate disagreements: None; all 5 replicates produce identical deterministic values across all metrics.
- Non-monotonic trends (especially fleet size versus ready time): None; readiness is flat across fleet sizes for admitted instances.
- Other CSV or chart issues: Sweep runner for neighbour intensity hardcodes 8x demand in workload generator, rendering that specific sweep flat.

## Limitations

- These are CloudSim model outputs, not measurements from deployed container, VM, or lightweight-VM platforms.
- Five runs at fixed seed 42 are deterministic repeatability checks in the current model, not independent samples; do not present confidence intervals from them.
- Start-up is modelled as parallel per-instance delay, with no boot contention. The current model predicts 30.00 / 2.00 / 3.80 seconds for full VM / container / lightweight VM.
- The noisy-neighbour test uses four victims, one longer 8x-demand neighbour, and one fixed cloudlet size. It does not represent workload diversity or a production scheduler.
- Density depends on the configured host and instance memory/core sizes; conclusions should not be generalized to different hardware without sensitivity runs.
- Slowdown is computed only for victim cloudlets that finish successfully. Any unfinished victim count must be shown alongside the mean.
