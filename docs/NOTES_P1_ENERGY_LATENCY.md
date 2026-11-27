# Person 1: Energy and Latency Hooks Reference & Calibration Notes

## Overview
This document records the architectural hooks, power model calibration, and latency calculation methodology implemented for the **Container vs VM vs Lightweight VM Density and Isolation Study** in CloudSim 4.0.

---

## 1. Core Energy & Power Model (`PowerModel.java`, `IsolatedHost.java`)

### A. Power Specification
- **Idle Power ($P_{\text{idle}}$)**: `100 W` (from `power.host.idle_w=100` in `config/experiment.properties`)
- **Peak / Max Power ($P_{\text{max}}$)**: `300 W` (from `power.host.max_w=300` in `config/experiment.properties`)
- **Off Hosts**: Any host without placed instances is unpowered and consumes `0 W`.

### B. Analytical Energy Formulation
For any powered host active over a makespan of $T$ seconds:
$$P(t) = P_{\text{idle}} + (P_{\text{max}} - P_{\text{idle}}) \times u(t)$$
$$\text{Energy } (J) = P_{\text{idle}} \times T + (P_{\text{max}} - P_{\text{idle}}) \times \int_0^T u(t) \, dt$$
where $u(t) \in [0, 1]$ represents host CPU utilisation:
$$u(t) = \frac{\text{Busy MIPS}}{\text{Total Host MIPS}}$$

### C. Hand-Calculation Verification
For a full-capacity 16-core host running 8 two-vCPU Full VMs ($u = 1.0$) with 30.0 s startup delay:
- During $[0, 30.0\text{ s}]$, instances are booting: $u(t) = 0.0 \implies P = 100\text{ W}$.
- During $[30.0\text{ s}, \text{makespan}]$, instances execute: $u(t) = 1.0 \implies P = 300\text{ W}$.
- Total Energy:
  $$E = 100 \times \text{makespan} + 200 \times (\text{makespan} - 30.0) \text{ Joules}$$
  For makespan $= 451.052632\text{ s}$:
  $$E = 100 \times 451.052632 + 200 \times 421.052632 = 129315.789474\text{ J}$$
  *Verified: Simulation outputs in `energy.csv` match this to 6 decimal places.*

---

## 2. Latency & Arrival Model (`Arrivals.java`, `LatencyStats.java`)

### A. Arrivals
- **Distribution**: Poisson arrival process generated using inverse transform sampling:
  $$\Delta t = -\frac{\ln(1 - U)}{\lambda}$$
- Deterministically seeded with fixed seeds (`42L + replicate`) to ensure exact reproducibility across multiple runs.

### B. Response Time Definition
$$\text{Response Time} = T_{\text{completion}} - T_{\text{arrival}}$$
- Includes instance boot delay if a request arrives before the instance completes startup (cold-start penalty).
- Pure execution latency is observed when arrival occurs post-readiness ($T_{\text{arrival}} \ge T_{\text{ready}}$).

### C. Percentile Accounting
- Uses standard nearest-rank percentile estimation:
  $$k = \lceil \frac{p \times N}{100} \rceil$$
- Accurately tracks P50, P90, P95, and P99 response times.

---

## 3. Verification Suite (`ModelChecks.java`)
- 36 automated assertions cover:
  1. Single-instance slowdown and placement rejection boundaries.
  2. Multi-tenant density limits (Full VM: 8, Lightweight VM: 64, Container: 128).
  3. Noisy-neighbour interference (Pinned: 1.00×, Capped: 1.00×, Uncapped: 1.50×).
  4. Startup delays (30.0 s, 3.8 s, 2.0 s).
  5. Energy consumption against analytical hand calculations.
  6. Multi-host consolidation and idle power scaling.
  7. Poisson request generation and response time percentiles.
  8. Run-to-run determinism across all three isolation profiles.
