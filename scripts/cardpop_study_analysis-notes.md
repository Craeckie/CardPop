# CardPop study analysis — notes

Answers "should I add new cards to my FloFla Cards / CardPop deck, or wait?" from
exported data, and explains *why* — including the trap where the in-app Study
Health flag lags your actual current state.

## Files you need from the app

1. **Backup JSON** — Settings → Backup (the SAF JSON). The deck snapshot: every
   card's `state / stability / difficulty / reps / dueAt / createdAt`, plus
   `settings.targetRetention` and the category enable flags.
2. **Review-log CSV** — the FSRS review-log export.
   Columns: `card_id,review_time,review_rating,review_state,review_duration`.
   Ratings: `1=Again 2=Hard 3=Good 4=Easy`. Recall = not-Again share.

## Run

```bash
# from the repo root
python3 scripts/cardpop_study_analysis.py BACKUP.json [REVIEWS.csv]
```

The CSV is optional but **bring it every time** — the daily trend is the whole
point (see below). Stdlib only, no venv needed.

## What it prints

- **Deck snapshot** — active total; New/Young/Mature split; unconsolidated share;
  low-stability & hard-difficulty shares; leeches; due-now; due distribution over
  the next month; lifetime reviews + lifetime recall vs target.
- **Study Health** — `status` + `tips`, recomputed with the *same* thresholds as
  the app (`StudyHealthUseCase.kt`). This matches what the app shows you.
- **Daily cadence & recall trend** — reviews/day, recall/day, avg/day, distinct
  cards, zero-review days, and a first-half → second-half trend arrow. The app
  does **not** show this.

## How to read it (the actual lesson)

The in-app "low accuracy" / "hold off" flags use **lifetime** averages, so on a
**young deck** they lag reality:

- A lifetime recall below target can be pure week-1 drag. If the **daily trend is
  rising toward target**, you're climbing out of the initial learning hump, not
  failing. Trust the trend over the lifetime number.
- **"No cards due right now" is usually a lull, not an empty deck.** Check the due
  distribution — if hundreds come due over the next week, you're just reviewing
  ahead of schedule (avg/day ≫ natural due rate).
- **Maturation runs on calendar days, not review count.** Reviewing a card days
  before it's due barely moves its stability — the spacing effect needs the gap.
  So grinding 100+/day on a caught-up deck has diminishing returns; spend that
  effort on the **hard cards / leeches** instead.

### When it's actually OK to add new cards
All of these, together:
- recall has **held ≥ target** for several days (not just lifetime average), and
- unconsolidated share is **below ~60%** (most cards have matured), and
- backlog is light (`due/active < 20%`), and intake last 7d is modest.

Then **trickle** new cards (~10–20/day), don't dump a big batch — big initial
dumps are what create a lumpy maturation curve and a deck that stays mostly young.

## Leeches can be present but hidden

The app computes `leechCount = cards with lapses >= 4` and only surfaces the
`LEECHES` tip when that count is >= 5 — but the Study Health **card shows only the
top two tips** (`StudyHealthCard.kt:67`, `health.tips.take(2)`). Severe tips
(LOW_ACCURACY etc.) and the higher-ranked minor tip (HOLD_OFF_NEW_CARDS) can crowd
the leech tip off the card, so "the app reports no leeches" usually means *hidden*,
not *zero*. Worse, the leech tip is the only entry point to the leech filter, so
those cards are unreachable in the UI until a higher-priority flag clears. The
script's `Leeches` line shows the true count regardless of display order.

Note on the export: `lapses` is omitted from the JSON when it's 0
(kotlinx.serialization `encodeDefaults=false`), so a missing key means 0 — the
script defaults it to 0, matching the app. (Don't fall back to `incorrectCount`:
that counts every wrong answer, not just FSRS lapses, and over-counts.)

## Keep in sync

The thresholds at the top of `cardpop_study_analysis.py` mirror the companion
object of
`CardPop/app/src/main/java/com/cardpop/app/domain/usecase/StudyHealthUseCase.kt`.
If that file's constants change, update the script to match.
