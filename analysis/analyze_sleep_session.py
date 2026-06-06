#!/usr/bin/env python3
"""Analyze a SleepAgent exported session directory.

Input directory must contain:
  - manifest.json
  - raw.csv

Outputs:
  - report.md
  - stats.json
  - channel_stats.csv
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from statistics import median


CHANNEL_COUNT = 8
POSITIVE_RAIL_COUNT = 8_388_607
NEGATIVE_RAIL_COUNT = -8_388_608
ADS1299_FULL_SCALE_COUNTS = (1 << 23) - 1
ADS1299_VREF = 4.5
ADS1299_GAIN = 24.0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Analyze SleepAgent raw.csv and manifest.json export."
    )
    parser.add_argument(
        "--session-dir",
        default=r"F:\data\SleepAgent\session_20260602",
        help="Directory containing manifest.json and raw.csv.",
    )
    parser.add_argument(
        "--output-dir",
        default=None,
        help="Output directory. Defaults to analysis/results/<session-dir-name>.",
    )
    return parser.parse_args()


def read_manifest(session_dir: Path) -> dict:
    manifest_path = session_dir / "manifest.json"
    with manifest_path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def read_raw_csv(session_dir: Path) -> list[dict[str, str]]:
    raw_path = session_dir / "raw.csv"
    with raw_path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def to_int(value: str | None) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except ValueError:
        return None


def to_float(value: str | None) -> float | None:
    if value is None or value == "":
        return None
    try:
        result = float(value)
    except ValueError:
        return None
    if not math.isfinite(result):
        return None
    return result


def counts_to_microvolts(counts: int) -> float:
    return counts * ADS1299_VREF / ADS1299_GAIN / ADS1299_FULL_SCALE_COUNTS * 1_000_000.0


def percentile(values: list[float], percent: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    index = (len(ordered) - 1) * percent
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return ordered[int(index)]
    ratio = index - lower
    return ordered[lower] * (1.0 - ratio) + ordered[upper] * ratio


def mean(values: list[float]) -> float | None:
    if not values:
        return None
    return sum(values) / len(values)


def stddev(values: list[float]) -> float | None:
    if len(values) < 2:
        return 0.0 if values else None
    avg = mean(values)
    assert avg is not None
    return math.sqrt(sum((value - avg) ** 2 for value in values) / (len(values) - 1))


def fmt_number(value: float | int | None, digits: int = 3) -> str:
    if value is None:
        return "n/a"
    if isinstance(value, int):
        return str(value)
    if abs(value) >= 1000:
        return f"{value:,.{digits}f}"
    return f"{value:.{digits}f}"


def fmt_epoch_ms(epoch_ms: int | None) -> str:
    if epoch_ms is None:
        return "n/a"
    dt = datetime.fromtimestamp(epoch_ms / 1000, tz=timezone.utc).astimezone()
    return dt.isoformat(timespec="seconds")


def sequence_deltas(values: list[int]) -> dict[str, object]:
    if len(values) < 2:
        return {"count": 0, "top_raw_deltas": [], "top_mod256_deltas": []}

    raw_deltas = [next_value - value for value, next_value in zip(values, values[1:])]
    mod_deltas = [(next_value - value) % 256 for value, next_value in zip(values, values[1:])]
    large_forward = [delta for delta in raw_deltas if delta > 1]
    non_forward = [delta for delta in raw_deltas if delta <= 0]

    return {
        "count": len(raw_deltas),
        "top_raw_deltas": Counter(raw_deltas).most_common(10),
        "top_mod256_deltas": Counter(mod_deltas).most_common(10),
        "large_forward_count": len(large_forward),
        "non_forward_count": len(non_forward),
        "max_raw_delta": max(raw_deltas),
        "min_raw_delta": min(raw_deltas),
    }


def summarize_intervals(host_timestamps: list[int]) -> dict[str, object]:
    if len(host_timestamps) < 2:
        return {"count": 0}
    intervals = [
        float(next_value - value)
        for value, next_value in zip(host_timestamps, host_timestamps[1:])
    ]
    positive_intervals = [value for value in intervals if value > 0]
    return {
        "count": len(intervals),
        "positive_count": len(positive_intervals),
        "non_positive_count": len(intervals) - len(positive_intervals),
        "min_ms": min(intervals),
        "max_ms": max(intervals),
        "mean_ms": mean(intervals),
        "median_ms": median(intervals),
        "p95_ms": percentile(intervals, 0.95),
        "p99_ms": percentile(intervals, 0.99),
        "gt_20ms_count": sum(1 for value in intervals if value > 20.0),
        "gt_100ms_count": sum(1 for value in intervals if value > 100.0),
        "top_intervals_ms": Counter(int(value) for value in intervals).most_common(10),
    }


def summarize_channel(rows: list[dict[str, str]], channel_index: int) -> dict[str, object]:
    count_col = f"ch{channel_index}_counts"
    uv_col = f"ch{channel_index}_uv"
    counts = [value for value in (to_int(row.get(count_col)) for row in rows) if value is not None]
    uvs = [value for value in (to_float(row.get(uv_col)) for row in rows) if value is not None]
    if not uvs:
        uvs = [counts_to_microvolts(value) for value in counts]
    rail_count = sum(
        1
        for value in counts
        if value >= POSITIVE_RAIL_COUNT or value <= NEGATIVE_RAIL_COUNT
    )
    rail_ratio = rail_count / len(counts) if counts else None
    unique_counts = len(set(counts)) if counts else 0

    return {
        "channel": channel_index,
        "samples": len(uvs),
        "count_min": min(counts) if counts else None,
        "count_max": max(counts) if counts else None,
        "count_unique": unique_counts,
        "rail_count": rail_count,
        "rail_ratio": rail_ratio,
        "uv_min": min(uvs) if uvs else None,
        "uv_max": max(uvs) if uvs else None,
        "uv_mean": mean(uvs),
        "uv_median": median(uvs) if uvs else None,
        "uv_std": stddev(uvs),
        "uv_p05": percentile(uvs, 0.05),
        "uv_p95": percentile(uvs, 0.95),
    }


def analyze(session_dir: Path, output_dir: Path) -> dict[str, object]:
    manifest = read_manifest(session_dir)
    rows = read_raw_csv(session_dir)
    session = manifest.get("session", {})

    host_timestamps = [
        value for value in (to_int(row.get("host_timestamp")) for row in rows) if value is not None
    ]
    sequences = [value for value in (to_int(row.get("sequence")) for row in rows) if value is not None]
    sample_numbers = [
        value for value in (to_int(row.get("sample_number")) for row in rows) if value is not None
    ]
    states = [value for value in (to_int(row.get("state")) for row in rows) if value is not None]

    reported_started = to_int(str(session.get("started_at_epoch_ms", "")))
    reported_ended = to_int(str(session.get("ended_at_epoch_ms", "")))
    reported_duration_ms = (
        reported_ended - reported_started
        if reported_started is not None and reported_ended is not None
        else None
    )
    observed_duration_ms = (
        host_timestamps[-1] - host_timestamps[0] if len(host_timestamps) >= 2 else None
    )

    channel_stats = [summarize_channel(rows, index) for index in range(1, CHANNEL_COUNT + 1)]
    saturated_channels = [
        item["channel"]
        for item in channel_stats
        if item["rail_ratio"] is not None and item["rail_ratio"] >= 0.5
    ]

    warnings = []
    reported_packet_count = session.get("packet_count")
    if reported_packet_count is not None and int(reported_packet_count) != len(rows):
        warnings.append(
            f"manifest packet_count={reported_packet_count} but raw.csv rows={len(rows)}"
        )
    if reported_duration_ms and observed_duration_ms:
        ratio = reported_duration_ms / max(observed_duration_ms, 1)
        if ratio > 5:
            warnings.append(
                "manifest duration is much longer than observed raw timestamp span"
            )
    if saturated_channels:
        warnings.append(
            "channels saturated at ADC rail for >=50% rows: "
            + ", ".join(f"ch{channel}" for channel in saturated_channels)
        )

    stats = {
        "session_dir": str(session_dir),
        "output_dir": str(output_dir),
        "manifest_session": session,
        "row_count": len(rows),
        "reported_packet_count": reported_packet_count,
        "reported_started": fmt_epoch_ms(reported_started),
        "reported_ended": fmt_epoch_ms(reported_ended),
        "reported_duration_ms": reported_duration_ms,
        "observed_first_host_timestamp": fmt_epoch_ms(host_timestamps[0] if host_timestamps else None),
        "observed_last_host_timestamp": fmt_epoch_ms(host_timestamps[-1] if host_timestamps else None),
        "observed_duration_ms": observed_duration_ms,
        "host_interval_stats": summarize_intervals(host_timestamps),
        "sequence_stats": sequence_deltas(sequences),
        "sample_number_stats": sequence_deltas(sample_numbers),
        "state_distribution": Counter(states).most_common(),
        "channel_stats": channel_stats,
        "warnings": warnings,
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    write_stats_json(output_dir / "stats.json", stats)
    write_channel_csv(output_dir / "channel_stats.csv", channel_stats)
    write_report(output_dir / "report.md", stats)
    return stats


def write_stats_json(path: Path, stats: dict[str, object]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        json.dump(stats, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def write_channel_csv(path: Path, channel_stats: list[dict[str, object]]) -> None:
    fields = [
        "channel",
        "samples",
        "count_min",
        "count_max",
        "count_unique",
        "rail_count",
        "rail_ratio",
        "uv_min",
        "uv_max",
        "uv_mean",
        "uv_median",
        "uv_std",
        "uv_p05",
        "uv_p95",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(channel_stats)


def write_report(path: Path, stats: dict[str, object]) -> None:
    session = stats["manifest_session"]
    assert isinstance(session, dict)
    intervals = stats["host_interval_stats"]
    assert isinstance(intervals, dict)
    sequence_stats = stats["sequence_stats"]
    assert isinstance(sequence_stats, dict)
    sample_stats = stats["sample_number_stats"]
    assert isinstance(sample_stats, dict)
    channel_stats = stats["channel_stats"]
    assert isinstance(channel_stats, list)
    warnings = stats["warnings"]
    assert isinstance(warnings, list)

    lines = [
        "# SleepAgent Session Analysis",
        "",
        "## Session",
        "",
        f"- Session id: `{session.get('session_id', 'n/a')}`",
        f"- Source: `{session.get('source_type', 'n/a')}`",
        f"- Device: `{session.get('device_name', 'n/a')}` / `{session.get('device_address', 'n/a')}`",
        f"- Sampling rate: `{session.get('sampling_rate_hz', 'n/a')}` Hz",
        f"- Manifest packet count: `{stats['reported_packet_count']}`",
        f"- raw.csv rows: `{stats['row_count']}`",
        f"- Manifest time: {stats['reported_started']} -> {stats['reported_ended']}",
        f"- Raw host timestamp span: {stats['observed_first_host_timestamp']} -> {stats['observed_last_host_timestamp']}",
        f"- Manifest duration: {fmt_number(ms_to_seconds(stats['reported_duration_ms']), 3)} s",
        f"- Raw observed duration: {fmt_number(ms_to_seconds(stats['observed_duration_ms']), 3)} s",
        "",
        "## Timing",
        "",
        f"- Interval median: {fmt_number(intervals.get('median_ms'))} ms",
        f"- Interval mean: {fmt_number(intervals.get('mean_ms'))} ms",
        f"- Interval p95/p99: {fmt_number(intervals.get('p95_ms'))} / {fmt_number(intervals.get('p99_ms'))} ms",
        f"- Interval min/max: {fmt_number(intervals.get('min_ms'))} / {fmt_number(intervals.get('max_ms'))} ms",
        f"- Intervals >20 ms: `{intervals.get('gt_20ms_count', 0)}`",
        f"- Intervals >100 ms: `{intervals.get('gt_100ms_count', 0)}`",
        "",
        "## Sequence",
        "",
        f"- Sequence top deltas: `{sequence_stats.get('top_raw_deltas')}`",
        f"- Sample number top deltas: `{sample_stats.get('top_raw_deltas')}`",
        f"- Sequence non-forward count: `{sequence_stats.get('non_forward_count', 0)}`",
        f"- Sample number non-forward count: `{sample_stats.get('non_forward_count', 0)}`",
        "",
        "## State",
        "",
        f"- State distribution: `{stats['state_distribution']}`",
        "",
        "## Channel Summary",
        "",
        "| Channel | Samples | Rail ratio | UV mean | UV std | UV min | UV max | UV p05 | UV p95 |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]

    for item in channel_stats:
        assert isinstance(item, dict)
        lines.append(
            "| "
            + " | ".join(
                [
                    str(item["channel"]),
                    str(item["samples"]),
                    fmt_number(as_percent(item["rail_ratio"]), 2) + "%",
                    fmt_number(item["uv_mean"]),
                    fmt_number(item["uv_std"]),
                    fmt_number(item["uv_min"]),
                    fmt_number(item["uv_max"]),
                    fmt_number(item["uv_p05"]),
                    fmt_number(item["uv_p95"]),
                ]
            )
            + " |"
        )

    lines.extend(["", "## Warnings", ""])
    if warnings:
        lines.extend(f"- {warning}" for warning in warnings)
    else:
        lines.append("- None")

    lines.extend(
        [
            "",
            "## Output Files",
            "",
            "- `stats.json`: full machine-readable stats",
            "- `channel_stats.csv`: per-channel signal stats",
        ]
    )

    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))
        handle.write("\n")


def ms_to_seconds(value: object) -> float | None:
    if value is None:
        return None
    return float(value) / 1000.0


def as_percent(value: object) -> float | None:
    if value is None:
        return None
    return float(value) * 100.0


def main() -> None:
    args = parse_args()
    session_dir = Path(args.session_dir)
    if not session_dir.exists():
        raise SystemExit(f"Session directory does not exist: {session_dir}")
    if not (session_dir / "manifest.json").exists():
        raise SystemExit(f"manifest.json not found in: {session_dir}")
    if not (session_dir / "raw.csv").exists():
        raise SystemExit(f"raw.csv not found in: {session_dir}")

    if args.output_dir:
        output_dir = Path(args.output_dir)
    else:
        output_dir = Path(__file__).resolve().parent / "results" / session_dir.name

    stats = analyze(session_dir=session_dir, output_dir=output_dir)
    print(f"Analyzed {stats['row_count']} rows from {session_dir}")
    print(f"Wrote report to {output_dir / 'report.md'}")
    if stats["warnings"]:
        print("Warnings:")
        for warning in stats["warnings"]:
            print(f"  - {warning}")


if __name__ == "__main__":
    main()
