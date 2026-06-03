#!/usr/bin/env python3
"""
cardpop_study_analysis.py — Analyze a FloFla Cards / CardPop deck and answer
"should I add new cards, or wait?"

Takes the two artifacts the app can export:
  1. A backup JSON (Settings → Backup)        — the deck snapshot (cards + settings)
  2. A review-log CSV (FSRS review-log export) — the per-review history

and reports:
  • The Study Health verdict, recomputed with the SAME thresholds the app uses
    (mirror of domain/usecase/StudyHealthUseCase.kt — keep in sync if those change).
  • The daily review cadence + recall trend from the CSV, which the in-app health
    check does NOT show (it uses a lifetime average that lags your current state).

Usage:
    python3 cardpop_study_analysis.py BACKUP.json [REVIEWS.csv]

CSV columns expected: card_id,review_time,review_rating,review_state,review_duration
Rating codes: 1=Again  2=Hard  3=Good  4=Easy   (recall = not-Again share)

Why both files matter:
  On a young deck the in-app "low accuracy" flag uses a LIFETIME average, which is
  dragged down by the week-1 learning hump even when recent recall is fine. The
  daily trend (CSV) reveals whether accuracy is actually rising toward target.
  Likewise "no cards due right now" is usually a lull from reviewing ahead of the
  natural due rate, not an empty deck. Maturation runs on CALENDAR DAYS, not review
  count, so cramming reviews doesn't speed it up — calendar time plus a few new
  cards/day does. Always read the daily trend before trusting the lifetime flag.
"""

import argparse
import collections
import csv
import json
import sys
import time

# ── Thresholds: mirror of StudyHealthUseCase.kt companion object ──────────────
MIN_REVIEWS_TO_JUDGE          = 10
MIN_REVIEWS_FOR_ACCURACY      = 30
BACKLOG_MIN_ABS               = 15
BACKLOG_RATIO                 = 0.4
BACKLOG_LIGHT_RATIO           = 0.2
ACCURACY_TOLERANCE            = 0.05
MIN_REVIEW_CARDS_FOR_STABILITY = 20
LOW_STABILITY_SHARE_THRESHOLD = 0.6
LOW_STABILITY_DAYS            = 7      # b0to3 + b3to7 buckets => stability < 7d
MIN_ACTIVE_FOR_MATURATION     = 20
UNCONSOLIDATED_RATIO_THRESHOLD = 0.6
NEW_INTAKE_RATIO              = 0.5
LEECH_LAPSES                  = 4
LEECH_COUNT_THRESHOLD         = 5
MIN_REVIEW_CARDS_FOR_DIFFICULTY = 20
HARD_DIFF_THRESHOLD           = 7.0   # FSRS difficulty > 7 = hard (1=easy .. 10=hard)
HARD_DIFF_SHARE_THRESHOLD     = 0.4
SPARE_CAPACITY_MARGIN         = 0.07
ZERO_DAYS_THRESHOLD           = 3
MATURE_STABILITY_DAYS         = 21
MATURE_MIN_REPS               = 3

DAY_MS = 86_400_000.0


def fday(ts_ms):
    return time.strftime("%Y-%m-%d", time.localtime(ts_ms / 1000))


def analyze_deck(backup):
    cards = backup["flashcards"]
    cats = {c["id"]: c for c in backup.get("categories", [])}
    # Snapshot "now" = when the backup was written.
    now = backup.get("updatedAt") or backup.get("createdAt") or int(time.time() * 1000)

    def cat_enabled(c):
        cat = cats.get(c["categoryId"])
        return cat is None or cat.get("isEnabled", True)

    active = [c for c in cards if c.get("isEnabled", True) and cat_enabled(c)]
    active_total = len(active)

    new_cards   = [c for c in active if c["state"] == 0]
    mature      = [c for c in active if c["stability"] >= MATURE_STABILITY_DAYS and c["reps"] >= MATURE_MIN_REPS]
    mature_ids  = {id(c) for c in mature}
    young       = [c for c in active if c["state"] in (1, 3)
                   or (c["state"] == 2 and id(c) not in mature_ids and c["state"] != 0)]
    review_cards = [c for c in active if c["state"] == 2]
    low_stab    = [c for c in review_cards if c["stability"] < LOW_STABILITY_DAYS]
    hard_diff   = [c for c in review_cards if c["difficulty"] > HARD_DIFF_THRESHOLD]
    due_now     = [c for c in active if c["dueAt"] <= now]
    # 'lapses' is omitted from the JSON when it's 0 (kotlinx.serialization default),
    # so a missing key means 0 — NOT unknown. Default to 0 to match the app exactly.
    leeches     = [c for c in active if c.get("lapses", 0) >= LEECH_LAPSES]
    added_7d    = [c for c in active if now - c.get("createdAt", now) <= 7 * DAY_MS]

    # Lifetime recall from per-card counters (what the in-app check uses).
    good  = sum(c.get("correctCount", 0) for c in active)
    hard  = sum(c.get("hardCount", 0) for c in active)
    wrong = sum(c.get("incorrectCount", 0) for c in active)
    total_reviews = good + hard + wrong
    recall = (good + hard) / total_reviews if total_reviews else 0.0

    target = backup.get("settings", {}).get("targetRetention", 0.9)

    # Due distribution over the coming days.
    buckets = collections.OrderedDict(
        [("overdue/now", 0), ("<1d", 0), ("1-2d", 0), ("2-7d", 0), ("7-30d", 0), (">30d", 0)]
    )
    for c in active:
        d = (c["dueAt"] - now) / DAY_MS
        if d <= 0: buckets["overdue/now"] += 1
        elif d < 1: buckets["<1d"] += 1
        elif d < 2: buckets["1-2d"] += 1
        elif d < 7: buckets["2-7d"] += 1
        elif d < 30: buckets["7-30d"] += 1
        else: buckets[">30d"] += 1

    return {
        "now": now, "active_total": active_total,
        "new": len(new_cards), "young": len(young), "mature": len(mature),
        "review_cards": len(review_cards), "low_stab": len(low_stab),
        "hard_diff": len(hard_diff), "due_now": len(due_now),
        "leeches": len(leeches), "added_7d": len(added_7d),
        "total_reviews": total_reviews, "recall": recall, "target": target,
        "buckets": buckets,
    }


def daily_trend(rows):
    byday = collections.OrderedDict()
    for r in rows:
        d = fday(int(r["review_time"]))
        byday.setdefault(d, []).append(int(r["review_rating"]))
    out = []
    for d, ratings in byday.items():
        tot = len(ratings)
        again = ratings.count(1)
        out.append((d, tot, (tot - again) / tot, again))
    return out


def study_health(deck, zero_review_days_7):
    """Recompute the StudyHealthUseCase verdict. Returns (status, tips[])."""
    tr = deck["total_reviews"]
    if tr < MIN_REVIEWS_TO_JUDGE:
        return "GETTING_STARTED", ["KEEP_GOING"]

    severe, minor, positive = [], [], []
    at, due, recall, target = deck["active_total"], deck["due_now"], deck["recall"], deck["target"]

    if due >= BACKLOG_MIN_ABS and at > 0 and due / at > BACKLOG_RATIO:
        severe.append("CATCH_UP_BACKLOG")
    if tr >= MIN_REVIEWS_FOR_ACCURACY and recall < target - ACCURACY_TOLERANCE:
        severe.append("LOW_ACCURACY")
    if deck["review_cards"] >= MIN_REVIEW_CARDS_FOR_STABILITY \
            and deck["low_stab"] / deck["review_cards"] > LOW_STABILITY_SHARE_THRESHOLD:
        severe.append("LOW_STABILITY_CHURN")

    unconsolidated = (deck["new"] + deck["young"]) / at if at >= MIN_ACTIVE_FOR_MATURATION else 0.0
    if unconsolidated > UNCONSOLIDATED_RATIO_THRESHOLD:
        minor.append("HOLD_OFF_NEW_CARDS")
    if deck["leeches"] >= LEECH_COUNT_THRESHOLD:
        minor.append("LEECHES")
    if deck["review_cards"] >= MIN_REVIEW_CARDS_FOR_DIFFICULTY \
            and deck["hard_diff"] / deck["review_cards"] > HARD_DIFF_SHARE_THRESHOLD:
        minor.append("DECK_TOO_HARD")
    if zero_review_days_7 >= ZERO_DAYS_THRESHOLD:
        minor.append("STUDY_DAILY")

    if tr >= MIN_REVIEWS_FOR_ACCURACY and recall > target + SPARE_CAPACITY_MARGIN:
        positive.append("RETENTION_HIGH")
    backlog_light = at == 0 or due / at < BACKLOG_LIGHT_RATIO
    intake_ok     = at == 0 or deck["added_7d"] / at <= NEW_INTAKE_RATIO
    if backlog_light and recall >= target and unconsolidated <= UNCONSOLIDATED_RATIO_THRESHOLD and intake_ok:
        positive.append("ADD_MORE_CARDS")
    if not positive:
        positive.append("KEEP_GOING")

    status = "NEEDS_ATTENTION" if severe else ("GOOD" if minor else "ON_TRACK")
    return status, severe + minor + positive, unconsolidated


def main():
    ap = argparse.ArgumentParser(description="Analyze a CardPop deck + review log.")
    ap.add_argument("backup", help="backup JSON exported from the app")
    ap.add_argument("reviews", nargs="?", help="review-log CSV (optional but recommended)")
    args = ap.parse_args()

    with open(args.backup) as f:
        backup = json.load(f)
    deck = analyze_deck(backup)

    rows = []
    zero_days = 0
    if args.reviews:
        with open(args.reviews) as f:
            rows = list(csv.DictReader(f))
        # zero-review days in the last 7 calendar days before the snapshot
        reviewed = {fday(int(r["review_time"])) for r in rows}
        for i in range(7):
            day = fday(deck["now"] - i * int(DAY_MS))
            if day not in reviewed:
                zero_days += 1

    status, tips, unconsolidated = study_health(deck, zero_days)

    print("=" * 64)
    print(f"DECK SNAPSHOT  ({fday(deck['now'])})")
    print("=" * 64)
    print(f"  Active cards          : {deck['active_total']}")
    print(f"  New / Young / Mature  : {deck['new']} / {deck['young']} / {deck['mature']}")
    print(f"  Unconsolidated share  : {unconsolidated:.1%}   (HOLD_OFF if > {UNCONSOLIDATED_RATIO_THRESHOLD:.0%})")
    print(f"  Review cards          : {deck['review_cards']}")
    print(f"  Low-stability (<7d)   : {deck['low_stab']}  ({deck['low_stab']/max(deck['review_cards'],1):.1%}  churn if > {LOW_STABILITY_SHARE_THRESHOLD:.0%})")
    print(f"  Hard (difficulty > 7) : {deck['hard_diff']}  ({deck['hard_diff']/max(deck['review_cards'],1):.1%}  too-hard if > {HARD_DIFF_SHARE_THRESHOLD:.0%})")
    print(f"  Leeches (lapses>={LEECH_LAPSES})    : {deck['leeches']}  (NOTE: app's health card shows only the top 2 tips, so this can be hidden)")
    print(f"  Due right now         : {deck['due_now']}")
    print(f"  Cards added last 7d   : {deck['added_7d']}")
    print(f"  Lifetime reviews      : {deck['total_reviews']}")
    print(f"  Lifetime recall       : {deck['recall']:.1%}   (target {deck['target']:.1%})")
    print()
    print("  Due distribution:")
    for k, v in deck["buckets"].items():
        print(f"    {k:>12} : {v}")
    print()
    print("=" * 64)
    print("STUDY HEALTH  (recomputed with app thresholds)")
    print("=" * 64)
    print(f"  Status: {status}")
    print(f"  Tips  : {', '.join(tips)}")
    print()

    if rows:
        print("=" * 64)
        print("DAILY REVIEW CADENCE & RECALL TREND  (from CSV)")
        print("=" * 64)
        print(f"  {'date':<12} {'reviews':>8} {'recall':>8} {'again':>6}")
        trend = daily_trend(rows)
        for d, tot, rec, again in trend:
            print(f"  {d:<12} {tot:>8} {rec:>7.1%} {again:>6}")
        ndays = len(trend)
        print()
        print(f"  Days: {ndays}   Total reviews: {len(rows)}   Avg/day: {len(rows)/ndays:.1f}")
        print(f"  Distinct cards reviewed: {len({r['card_id'] for r in rows})}")
        print(f"  Zero-review days in last 7: {zero_days}")
        if ndays >= 4:
            first = sum(t[2] for t in trend[:ndays//2]) / (ndays//2)
            last  = sum(t[2] for t in trend[ndays//2:]) / (ndays - ndays//2)
            arrow = "↑ improving" if last > first + 0.02 else ("↓ declining" if last < first - 0.02 else "→ flat")
            print(f"  Recall trend: {first:.1%} → {last:.1%}  ({arrow})")
            print()
            print("  NOTE: read the trend, not just the lifetime number. A young deck's")
            print("  lifetime recall is dragged down by week-1; if the trend is rising")
            print("  toward target, 'low accuracy' is a lag artifact. Maturation needs")
            print("  CALENDAR DAYS, not more reviews — let young cards settle, add a few")
            print("  new cards/day (trickle, don't dump) once recall holds >= target and")
            print("  the unconsolidated share drops below 60%.")
    else:
        print("(No CSV given — run with the review-log export too for the cadence/trend,")
        print(" which reveals whether a 'low accuracy' flag is just week-1 lag.)")


if __name__ == "__main__":
    sys.exit(main())
