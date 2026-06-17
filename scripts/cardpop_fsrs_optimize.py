#!/usr/bin/env python3
"""Optimize FSRS-6 weights from a CardPop/FloFla review-log CSV export.

The app ships generic default FSRS weights. This script fits the 21 FSRS-6
parameters to *your* review history and reports how much better the fit is, so
you can paste tuned weights into Settings -> FSRS parameters (bracketed list of
21 values).

Input is the review-log CSV the app exports:
    card_id,review_time,review_rating,review_state,review_duration
where review_rating is 1=Again 2=Hard 3=Good 4=Easy and review_time is epoch ms.

Dependency (Rust-backed, no torch):
    python3 -m venv .venv && .venv/bin/pip install fsrs-rs-python

Usage:
    .venv/bin/python scripts/cardpop_fsrs_optimize.py REVIEWS.csv

Caveats
-------
Optimization needs a spread of *long* intervals to estimate the forgetting
curve. On a young deck (few weeks old, mostly short intervals) the weights are
preliminary and can swing a lot — re-run every couple of months. The reported
"predicted vs actual recall" tells you the direction of the default's bias:
if predicted < actual, the defaults schedule too conservatively for you.
"""
import argparse, collections, csv, math, sys

try:
    import fsrs_rs_python as F
except ImportError:
    sys.exit("error: pip install fsrs-rs-python (see module docstring)")

DAY = 86_400_000


def build(path):
    rows = [(int(r["card_id"]), int(r["review_time"]), int(r["review_rating"]))
            for r in csv.DictReader(open(path, newline=""))]
    by_card = collections.defaultdict(list)
    for c, t, rating in rows:
        by_card[c].append((t, rating))
    full, deltas, ratings, train = [], [], [], []
    for c, rv in by_card.items():
        rv.sort()
        hist, last_t, ds, rs = [], None, [], []
        for t, rating in rv:
            d = 0 if last_t is None else max(0, round((t - last_t) / DAY))
            hist.append(F.FSRSReview(rating, d)); ds.append(d); rs.append(rating); last_t = t
            snap = F.FSRSItem(list(hist))
            if snap.long_term_review_cnt() > 0:      # progressive snapshots for training
                train.append(snap)
        full.append(F.FSRSItem(list(hist))); deltas.append(ds); ratings.append(rs)
    return len(rows), len(by_card), full, deltas, ratings, train


def metrics(params, full, deltas, ratings, label):
    """Log-loss + calibration RMSE over long-term recall predictions."""
    fsrs = F.FSRS(parameters=params)
    DECAY = -params[20]
    FACTOR = 0.9 ** (1.0 / DECAY) - 1.0
    ys, ps = [], []
    for item, ds, rs in zip(full, deltas, ratings):
        if len(rs) < 2:
            continue
        states = fsrs.historical_memory_states(item)   # state AFTER each review
        for i in range(1, len(rs)):
            if ds[i] <= 0:                             # long-term (cross-day) only
                continue
            S = states[i - 1].stability
            R = min(max((1 + FACTOR * ds[i] / S) ** DECAY, 1e-6), 1 - 1e-6)
            ys.append(1 if rs[i] >= 2 else 0); ps.append(R)
    n = len(ys)
    ll = -sum(y * math.log(p) + (1 - y) * math.log(1 - p) for y, p in zip(ys, ps)) / n
    bins = collections.defaultdict(lambda: [0, 0.0, 0.0])
    for y, p in zip(ys, ps):
        b = min(int(p * 20), 19); bins[b][0] += 1; bins[b][1] += y; bins[b][2] += p
    rmse = math.sqrt(sum(cnt * ((sy / cnt) - (sp / cnt)) ** 2 for cnt, sy, sp in bins.values()) / n)
    print(f"  {label:9s} log_loss={ll:.4f}  cal_rmse={rmse:.4f}  "
          f"pred_recall={sum(ps)/n:.3f}  actual_recall={sum(ys)/n:.3f}  (n={n})")
    return ll, rmse


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv", help="review-log CSV export")
    args = ap.parse_args()

    nrev, ncard, full, deltas, ratings, train = build(args.csv)
    print(f"reviews={nrev}  cards={ncard}  training items={len(train)}")
    default = list(F.DEFAULT_PARAMETERS)
    opt = F.FSRS(parameters=[]).compute_parameters(train, card_ids=None)

    print("\nModel fit (lower log_loss / cal_rmse = better):")
    ld, rd = metrics(default, full, deltas, ratings, "default")
    lo, ro = metrics(opt, full, deltas, ratings, "optimized")
    print(f"\n  log_loss: {(ld-lo)/ld*100:+.1f}%   cal_rmse: {(rd-ro)/rd*100:+.1f}%")
    print("\nOptimized 21 parameters (paste into Settings -> FSRS parameters):")
    print("[" + ", ".join(f"{w:.4f}" for w in opt) + "]")


if __name__ == "__main__":
    main()
