# SleepAgent Session Analysis

## Session

- Session id: `20260602_020422`
- Source: `BLE`
- Device: `MindBridge-v3.11` / `74:4D:BD:66:59:72`
- Sampling rate: `250` Hz
- Manifest packet count: `2220`
- raw.csv rows: `2397`
- Manifest time: 2026-06-02T02:04:22+08:00 -> 2026-06-02T09:07:03+08:00
- Raw host timestamp span: 2026-06-02T02:04:23+08:00 -> 2026-06-02T02:05:20+08:00
- Manifest duration: 25,360.241 s
- Raw observed duration: 57.169 s

## Timing

- Interval median: 2.000 ms
- Interval mean: 23.860 ms
- Interval p95/p99: 124.000 / 431.200 ms
- Interval min/max: 0.000 / 1,030.000 ms
- Intervals >20 ms: `422`
- Intervals >100 ms: `149`

## Sequence

- Sequence top deltas: `[(1, 1328), (4, 726), (8, 70), (2, 67), (12, 41), (3, 37), (16, 18), (5, 16), (6, 16), (7, 14)]`
- Sample number top deltas: `[(1, 1328), (4, 726), (8, 70), (2, 67), (12, 41), (3, 37), (16, 18), (5, 16), (6, 16), (7, 14)]`
- Sequence non-forward count: `37`
- Sample number non-forward count: `37`

## State

- State distribution: `[(5, 2397)]`

## Channel Summary

| Channel | Samples | Rail ratio | UV mean | UV std | UV min | UV max | UV p05 | UV p95 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2397 | 100.00% | 187,500.000 | 0.000 | 187,500.000 | 187,500.000 | 187,500.000 | 187,500.000 |
| 2 | 2397 | 0.00% | 56,030.530 | 9,695.256 | 38,098.527 | 88,929.789 | 38,747.052 | 75,119.420 |
| 3 | 2397 | 100.00% | 187,500.000 | 0.000 | 187,500.000 | 187,500.000 | 187,500.000 | 187,500.000 |
| 4 | 2397 | 100.00% | 187,500.000 | 0.000 | 187,500.000 | 187,500.000 | 187,500.000 | 187,500.000 |
| 5 | 2397 | 3.88% | 1,945.580 | 45,026.647 | -187,500.016 | 187,500.000 | -22,632.608 | 59,394.217 |
| 6 | 2397 | 3.80% | 1,921.566 | 44,485.030 | -187,500.016 | 187,500.000 | -22,374.109 | 58,651.376 |
| 7 | 2397 | 3.96% | 14,925.944 | 45,050.578 | -187,500.016 | 187,500.000 | -9,272.210 | 73,964.406 |
| 8 | 2397 | 100.00% | 187,500.000 | 0.000 | 187,500.000 | 187,500.000 | 187,500.000 | 187,500.000 |

## Warnings

- manifest packet_count=2220 but raw.csv rows=2397
- manifest duration is much longer than observed raw timestamp span
- channels saturated at ADC rail for >=50% rows: ch1, ch3, ch4, ch8

## Output Files

- `stats.json`: full machine-readable stats
- `channel_stats.csv`: per-channel signal stats
