#!/usr/bin/env python3
"""Visualise timing differences across multiple PRISM runs.

Given one or more JSON files produced by TimingReporter, the script aggregates
per-step runtimes across runs and renders a comparison plot with means and
standard-deviation bands for each dataset.

Example usage:
python compare_timing_runs.py explicit-mdp-ltl-*.json -o comparison.png
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable, List, Optional, Sequence, Tuple

import matplotlib.pyplot as plt
import numpy as np


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare timing runs exported by TimingReporter.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "json_files",
        metavar="JSON",
        nargs="+",
        help="Timing JSON files to aggregate (each contains multiple runs).",
    )
    parser.add_argument(
        "-o",
        "--output",
        default="timing_comparison.png",
        help="Destination PNG filename.",
    )
    parser.add_argument(
        "--title",
        default="PRISM Explicit MDP LTL Timing Comparison",
        help="Plot title.",
    )
    return parser.parse_args()


def load_step_matrix(json_path: Path) -> Tuple[List[str], np.ndarray]:
    """Return (step_labels, matrix[runs, steps]) for *json_path*."""

    try:
        content = json.loads(json_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as err:
        raise RuntimeError(f"Failed to parse {json_path}: {err}") from err

    if isinstance(content, dict):
        runs = [content]
    elif isinstance(content, list):
        runs = content
    else:
        raise ValueError(f"Unexpected JSON structure in {json_path}")

    labels: List[str] = []
    per_run: List[List[float]] = []
    for idx, run in enumerate(runs):
        steps = run.get("steps")
        if not isinstance(steps, Sequence):
            raise ValueError(f"Run {idx} in {json_path} is missing a 'steps' array")
        values: List[float] = []
        names: List[str] = []
        for step in steps:
            try:
                values.append(float(step["time"]))
                names.append(str(step["name"]))
            except (KeyError, TypeError, ValueError) as err:
                raise ValueError(
                    f"Malformed step in run {idx} of {json_path}: {step}") from err
        if not labels:
            labels = names
        elif labels != names:
            raise ValueError(
                f"Step ordering mismatch in run {idx} of {json_path}; ensure consistent instrumentation")
        per_run.append(values)

    matrix = np.asarray(per_run, dtype=float)
    return labels, matrix


def label_from_path(path: Path) -> str:
    name = path.stem.replace("explicit-mdp-ltl-", "")
    return name.replace("-", " ").strip().title() or path.stem


def plot_datasets(
    datasets: Iterable[Tuple[str, Sequence[str], np.ndarray]],
    output: Path,
    title: str,
) -> None:
    plt.style.use("seaborn-v0_8-paper")
    fig, ax = plt.subplots(figsize=(8, 5))

    canonical_labels: Optional[List[str]] = None

    for label, step_labels, matrix in datasets:
        if canonical_labels is None:
            canonical_labels = list(step_labels)
        elif canonical_labels != list(step_labels):
            raise ValueError("Datasets have different timing steps; cannot compare.")

        mean = matrix.mean(axis=0)
        std = matrix.std(axis=0, ddof=1) if matrix.shape[0] > 1 else np.zeros_like(mean)
        x = np.arange(len(step_labels))
        lower = np.maximum(mean - std, 0)
        upper = mean + std
        ax.plot(x, mean, label=label)
        ax.fill_between(x, lower, upper, alpha=0.2)

    if canonical_labels is None:
        raise ValueError("No datasets to plot.")

    ax.set_xlabel("Timing step")
    ax.set_ylabel("Runtime per step (ms)")
    ax.set_xticks(np.arange(len(canonical_labels)))
    ax.set_xticklabels(canonical_labels, rotation=45, ha="right")
    ax.set_title(title)
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(output, dpi=300)
    plt.close(fig)


def main() -> None:
    args = parse_args()
    output_path = Path(args.output)

    datasets = []
    for json_file in args.json_files:
        path = Path(json_file)
        if not path.is_file():
            raise FileNotFoundError(f"JSON file not found: {path}")
        step_labels, matrix = load_step_matrix(path)
        label = label_from_path(path)
        datasets.append((label, step_labels, matrix))

    plot_datasets(datasets, output_path, args.title)
    print(f"Saved comparison plot to {output_path}")


if __name__ == "__main__":
    main()
