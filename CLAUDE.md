# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Releases
When asked to create a release:
1. **Find the last tag**: `git tag --sort=-version:refname | head -5`. Tags follow the format `v{versionName}` (e.g. `v2.1.0`).
2. **Inspect commits since that tag**: `git log {last-tag}..HEAD --oneline`. Ignore `chore:` commits about CLAUDE.md, CI, dependency bumps, and internal refactors — only keep what users would notice.
3. **Bump the version** in `app/build.gradle.kts` (both `versionCode` +1 and `versionName`). Never bump the major version unless explicitly instructed. Bump the minor version for new features or changes users would notice (new UI element, changed font, moved setting, new option). Bump the patch version for bug fixes and small tweaks (e.g. slightly adjusted font size, minor layout fix).
4. **Write a changelog** at `fastlane/metadata/android/en-US/changelogs/{versionCode}.txt` in this order:
   - Header line: `Version {versionName}`
   - New features and major visible changes first (new screens, new settings, visual overhauls, changed fonts, moved controls)
   - Smaller changes and bug fixes second
   - Use emojis to make entries engaging (e.g. ✨ for new features, 🐛 for bug fixes, 🎨 for visual changes, ⚡ for performance, 🌍 for translations, 🔧 for settings/config changes)
   - Omit anything invisible to users (refactors, CI, internal architecture)
   - Note: `fastlane/metadata/android/de-DE/changelogs/` exists but is often outdated; ask the user whether to add a German changelog.
5. **Commit** both changed files (`app/build.gradle.kts` and the new changelog), then **tag** the commit: `git tag v{versionName}`.

## Git workflow
After each change, commit it with a semantic commit message (`feat:`, `fix:`, `chore:`, etc.). **Always amend the preceding commit** unless it has already been pushed, or the new change is about an unrelated feature, aspect, or part of the app/code. To undo a commit that has not been pushed, drop it from history (reset/rebase) rather than creating a revert commit. Never force-push.

## Project
FloFla Cards — offline, ad-free Android flashcard app (Kotlin, minSdk 24, targetSdk 36; F-Droid). Pops a system overlay with a flashcard at user-set intervals; tap to reveal back. User studies Chinese: front = word, back = meaning + pronunciation.

**minSdk 24 guards**: Any API added after 24 must be wrapped in a `Build.VERSION.SDK_INT` check. Use the deprecated pre-29 overload (with `@Suppress("DEPRECATION")`) when a newer overload requires API ≥ 29. Example: `AppOpsManager.unsafeCheckOpNoThrow` requires API 29 — use `checkOpNoThrow` below that.

## Build & test
```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test                      # all unit tests
./gradlew lint
./gradlew :app:testDebugUnitTest --tests "com.floflacards.app.data.csv.CsvParserTest"
```
Prefer `./gradlew assembleRelease` over `assembleDebug` unless there is a specific reason to build a debug variant.

Unit tests are JVM-only (Robolectric). No instrumented tests are run in CI. FSRS logic (`domain/fsrs/`) has no Android imports and is fully testable without a device.

`AnkiParserTest` uses Robolectric, which downloads Android SDK JARs on first run. If running behind a proxy, configure `systemProp.http(s).proxyHost/Port` in `~/.gradle/gradle.properties` — `app/build.gradle.kts` forwards those into the test JVM automatically.

**Translations**: All string resources must be present in `values/` (en), `values-pl/` (pl), and `values-de/` (de). `./gradlew lint` reports `MissingTranslation` errors that block release builds. When adding new strings, add translations for all three locales at the same time.

## Architecture layers

**`data/`** — `entity/` Room (`FlashcardEntity`, `CategoryEntity`); `dao/`; `repository/` (`FlashcardRepository`, `SettingsRepository` SharedPrefs, `BackupRepository` SAF JSON); `source/` (`FlashcardUiPreferences`, `StreakPreferences`, `ReviewHistoryPreferences`, `ImageManager`, `BackupPreferences`); `csv/`; `anki/AnkiParser` (.apkg import); `model/` (`AppTheme`, `FlashcardTheme`, `Language`).

**`domain/`** — `fsrs/` pure FSRS v6 port (`Fsrs`, `FsrsCard`, `FsrsRating`, `FsrsCardState`, `FsrsGrade`, `FsrsParameters`); JVM-testable, no Android imports. `usecase/` (CSV i/o, backup, `SrsUseCase`, `SimpleStreakUseCase`, statistics). `model/` (`FlashcardRating`, `StreakData`). `manager/ServiceStateManager` singleton.

**`presentation/`** — `screen/` (`MainActivity` hosts Compose nav; `WelcomeActivity`; per-feature route composables). `viewmodel/` one-per-screen. `component/` by feature (`flashcard/`, `main/`, `settings/`, `welcome/`, `dialog/`, `csv/`, `statistics/`, `shared/`, `text/`). `navigation/AppNavigation` single `NavHost`: `main`, `categories`, `statistics`, `app-settings`, `flashcard-management/{categoryId}`, `add-edit-flashcard`, `csv-import|export|bulk-export`. `theme/` Material3.

**`service/`** — `OverlayService` foreground service implementing `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner` to host Compose in a `ComposeView` added to `WindowManager` via `OverlayManager` (`SYSTEM_ALERT_WINDOW`). `TimerForegroundService` repeating alarm. `LearningServiceManager` start/stop. `SnoozeBroadcastReceiver`. `ViewModelStoreManager`.

**`di/`** — Hilt: `DatabaseModule`, `BackupModule`, `CsvModule`.

## Two clocks — keep decoupled
- **FSRS (days)** — *which* card. `FlashcardEntity` has `stability/difficulty/scheduledDays/reps/lapses/state/dueAt`. `SrsUseCase` calls `Fsrs.apply()`, writes `dueAt = now + scheduledDays·86_400_000` (Review) or short-term ms (Learning/Relearning). `FlashcardDao.getNextDueFlashcard` orders by state (Relearning→Learning→Review→New), then `dueAt`, then `difficulty`.
- **Overlay (minutes)** — *how often*. `TimerForegroundService`, FSRS-independent. Tick → `getNextAvailableFlashcard()`; if none due, falls back to closest-to-due. Interval has a "Now" option for immediate display.

## Service communication
`ServiceStateManager` (singleton, `domain/manager/`) holds two `StateFlow`s — `countdownTime: Long` (seconds) and `isServiceActive: Boolean` — that `TimerForegroundService` writes and `MainViewModel` collects. It is the only in-process bridge between the timer service and the UI; do not add new inter-service state outside it.

## Key non-obvious behaviors

**Auto-backup on every write**: `FlashcardRepository` calls `CreateBackupUseCase()` after every insert/update/delete. This is by design — backups are cheap SAF JSON writes. Do not remove these calls.

**Guaranteed overlay flashcard**: `FlashcardRepository.getNextAvailableFlashcard()` never returns null. When no cards are available/due it returns `EmptyStateFlashcard.create()` so `TimerForegroundService` always has something to display and the overlay shows an empty-state prompt.

**Flashcard passed via Intent extras**: `TimerForegroundService` → `OverlayService` passes all `FlashcardEntity` fields as extras (not just an ID). `OverlayService.handleFlashcardRating()` re-fetches from DB by ID before writing to avoid stale data races.

**Screen-off postpone**: If the interval alarm fires while `PowerManager.isInteractive == false`, `TimerForegroundService` sets `postponePending = true` instead of showing the overlay. The `ACTION_SCREEN_ON` receiver then waits 60 s before surfacing the card. If the screen goes off again during that wait, `postponePending` is re-armed.

**Demo flashcard**: `OverlayService.startWithDemoFlashcard()` creates a synthetic card with `id = -1`. Rating/close handlers detect this sentinel and call `handleDemoCompletion()` instead of writing to the DB. Demo completion auto-starts the real learning session.

## Ratings & FSRS
- `FlashcardRating { WRONG, HARD, GOOD, EASY, CLOSED }`; `WRONG` displays as "Again". Buttons in `component/flashcard/FlashcardControls.kt`; collapses to 2×2 below 240dp. `CLOSED` = no-op for FSRS.
- **Mastered** (`StatisticsViewModel`): `stability ≥ 21d && reps ≥ 3`.
- **Statistics charts** (`presentation/component/statistics/`): two Vico bar charts on the statistics screen.
  - `ReviewHistoryChart` — daily review counts for the last 30 days; Y-axis with ticks, X-axis label every 5 bars, tap marker. Hidden when all counts are zero.
  - `RatingDistributionChart` — four bars (Again/Hard/Good/Easy) aggregated across all cards; one series per rating so each bar has its own color (Red/Amber/Teal/Blue). Hidden when `ratingDistribution` in `ModernStatisticsUiState` is null (no reviews yet). Both charts use `ExtraStore.Key` to pass label lists into Vico `CartesianValueFormatter`s.
- **FSRS difficulty is 1..10, low = easy, high = hard** (inverse of old SM-2 EF). Any difficulty→label/color mapping must respect this.
- **SM-2 → FSRS migration**: DB v8 / backup v2. Legacy `easinessFactor`/`reviewCount`/`cooldownUntil` gone; every card reset to FSRS-`New` (history counters kept, scheduling zeroed). Backup v1 imports same.

## Features
- **APKG import** (`data/anki/AnkiParser`) alongside two-column CSV (preview before save).
- **Pleco lookup** button on overlay (opens front term in Pleco app).
- **Snooze** — pause overlay for N minutes; duration in `SettingsRepository`, end-time persisted, triggered by `SnoozeBroadcastReceiver`; main screen shows state.
- **App blocklist** (`blocklist_packages`) — overlay suppressed while a listed package is foreground; uses `UsageStatsHelper` (degrades gracefully if permission not granted).
- **Long-press overlay** — reveals answer type/meta.
- **In-popup resize & drag**; popup opacity in settings.
- **FSRS target retention** 0.80–0.95, default 0.90 (`SettingsRepository`).

## Misc
- `SettingsRepository` = SharedPreferences (not DataStore). Streak/stats in separate `StreakPreferences`. Daily review history (for statistics chart) in `ReviewHistoryPreferences`.
- Backup: `kotlinx.serialization` JSON via SAF.
- Locales: en (default), pl (`values-pl`), de (`values-de`); switch via `AppCompatDelegate.setApplicationLocales`.
- Permissions: `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS` via `PermissionHelper`/`PermissionLauncher`.
- **Charts**: `com.patrykandpatrick.vico:compose-m3:2.1.4` (Kotlin 2.1.x Compose-first charting, Apache 2.0). Requires Kotlin ≥ 2.1.x — project uses 2.1.21. Do not downgrade Kotlin below 2.1.x or update Vico to 3.x (compiled with Kotlin 2.3.x, incompatible with Hilt's `kotlin-metadata-jvm`).
- Room schema exported to `app/schemas/`; migrations live in `FloatingLearningDatabase`.
