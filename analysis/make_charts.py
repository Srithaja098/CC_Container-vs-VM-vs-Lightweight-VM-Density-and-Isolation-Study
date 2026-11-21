"""Generate labelled charts from the experiment CSV files (pandas + matplotlib only)."""

from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "code" / "results"
OUTPUT = ROOT / "analysis" / "charts"
LEVELS = ["full_vm", "container", "lightweight_vm"]
COLORS = {
    "full_vm": "#0072B2",          # blue
    "container": "#D55E00",        # vermilion
    "lightweight_vm": "#009E73",   # bluish green
}
LABELS = {
    "full_vm": "Full VM (pinned)",
    "container": "Container (uncapped)",
    "lightweight_vm": "Lightweight VM (capped)",
}


def read_csv(name, required):
    path = RESULTS / name
    if not path.is_file():
        raise FileNotFoundError(
            "Missing {}. Run the Java experiment and write its CSV first.".format(path)
        )
    frame = pd.read_csv(path)
    missing = sorted(set(required) - set(frame.columns))
    if missing:
        raise ValueError("{} is missing columns: {}".format(name, ", ".join(missing)))
    return frame


def save(fig, filename):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(OUTPUT / filename, dpi=180, bbox_inches="tight")
    plt.close(fig)


def density_chart():
    data = read_csv("density.csv", ["level", "placed_instances_per_host"])
    data["placed_instances_per_host"] = pd.to_numeric(
        data["placed_instances_per_host"], errors="coerce"
    )
    means = data.groupby("level")["placed_instances_per_host"].mean().reindex(LEVELS)
    fig, ax = plt.subplots(figsize=(7.2, 4.4))
    x = range(len(LEVELS))
    ax.bar(x, means.values, color=[COLORS[level] for level in LEVELS], width=0.66)
    ax.set_xticks(list(x), [LABELS[level] for level in LEVELS])
    ax.set_ylabel("Placed instances per host (instances/host)")
    ax.set_title("Maximum instance density")
    ax.grid(axis="y", color="#D9DEE3", linewidth=0.8)
    ax.set_axisbelow(True)
    save(fig, "density.png")


def startup_chart():
    data = read_csv("startup.csv", [
        "level", "requested_fleet_size_instances", "fleet_ready_time_s"
    ])
    for column in ("requested_fleet_size_instances", "fleet_ready_time_s"):
        data[column] = pd.to_numeric(data[column], errors="coerce")
    fig, ax = plt.subplots(figsize=(7.2, 4.4))
    for level in LEVELS:
        subset = data[data["level"] == level].groupby(
            "requested_fleet_size_instances", as_index=False
        )["fleet_ready_time_s"].mean().sort_values("requested_fleet_size_instances")
        ax.plot(subset["requested_fleet_size_instances"], subset["fleet_ready_time_s"],
                marker="o", linewidth=2, markersize=5, color=COLORS[level],
                label=LABELS[level])
    ax.set_xlabel("Requested fleet size (instances)")
    ax.set_ylabel("Fleet ready time (s)")
    ax.set_title("Fleet start-up readiness")
    ax.set_xticks([1, 8, 16, 32, 64])
    ax.grid(color="#D9DEE3", linewidth=0.8)
    ax.set_axisbelow(True)
    ax.legend(frameon=False)
    save(fig, "startup.png")


def noisy_neighbour_chart():
    data = read_csv("noisy_neighbour.csv", [
        "level", "condition", "mean_victim_slowdown_x"
    ])
    data["mean_victim_slowdown_x"] = pd.to_numeric(
        data["mean_victim_slowdown_x"], errors="coerce"
    )
    summary = data.groupby(["level", "condition"])["mean_victim_slowdown_x"].mean()
    fig, ax = plt.subplots(figsize=(8.0, 4.6))
    positions = list(range(len(LEVELS)))
    width = 0.34
    for offset, condition, label, color in [
        (-width / 2, "baseline", "No neighbour", "#777777"),
        (width / 2, "noisy_neighbour", "Noisy neighbour", "#CC79A7"),
    ]:
        values = [summary.get((level, condition), float("nan")) for level in LEVELS]
        ax.bar([p + offset for p in positions], values, width=width,
               label=label, color=color)
    ax.axhline(1.0, color="#444444", linestyle="--", linewidth=1,
               label="No slowdown (1.00x)")
    ax.set_xticks(positions, [LABELS[level] for level in LEVELS])
    ax.set_ylabel("Mean victim slowdown (x)")
    ax.set_title("Victim slowdown with and without a noisy neighbour")
    ax.grid(axis="y", color="#D9DEE3", linewidth=0.8)
    ax.set_axisbelow(True)
    ax.legend(frameon=False)
    save(fig, "noisy_neighbour.png")


def main():
    density_chart()
    startup_chart()
    noisy_neighbour_chart()
    print("Charts written to {}".format(OUTPUT))


if __name__ == "__main__":
    main()
