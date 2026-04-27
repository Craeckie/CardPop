# CLAUDE.md

## Project
FloFla Cards — offline, ad-free Android flashcard app (Kotlin, minSdk 24, targetSdk 36; F-Droid). Pops a system overlay with a flashcard at user-set intervals; tap to reveal back. User studies Chinese: front = word, back = meaning + pronunciation.

## Build
```bash
./gradlew assembleDebug | assembleRelease | test | lint
./gradlew :app:testDebugUnitTest --tests "com.floflacards.app.data.csv.CsvParserTest"
```

## Layers

**`data/`** — `entity/` Room (`FlashcardEntity`, `CategoryEntity`); `dao/`; `repository/` (`FlashcardRepository`, `SettingsRepository` SharedPrefs, `BackupRepository` SAF JSON); `source/` (`FlashcardUiPreferences`, `StreakPreferences`, `ImageManager`, `BackupPreferences`); `csv/`; `anki/AnkiParser` (.apkg import); `model/` (`AppTheme`, `FlashcardTheme`, `Language`).

**`domain/`** — `fsrs/` pure FSRS v6 port (`Fsrs`, `FsrsCard`, `FsrsRating`, `FsrsCardState`, `FsrsGrade`, `FsrsParameters`); JVM-testable, no Android imports. `usecase/` (CSV i/o, backup, `SrsUseCase`, `SimpleStreakUseCase`, statistics). `model/` (`FlashcardRating`, `StreakData`). `manager/ServiceStateManager` singleton.

**`presentation/`** — `screen/` (`MainActivity` hosts Compose nav; `WelcomeActivity`; per-feature route composables). `viewmodel/` one-per-screen. `component/` by feature (`flashcard/`, `main/`, `settings/`, `welcome/`, `dialog/`, `csv/`, `statistics/`, `shared/`, `text/`). `navigation/AppNavigation` single `NavHost`: `main`, `categories`, `statistics`, `app-settings`, `flashcard-management/{categoryId}`, `add-edit-flashcard`, `csv-import|export|bulk-export`. `theme/` Material3.

**`service/`** — `OverlayService` foreground service implementing `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner` to host Compose in a `ComposeView` added to `WindowManager` via `OverlayManager` (`SYSTEM_ALERT_WINDOW`). `TimerForegroundService` repeating alarm. `LearningServiceManager` start/stop. `SnoozeBroadcastReceiver`. `ViewModelStoreManager`.

**`di/`** — Hilt: `DatabaseModule`, `BackupModule`, `CsvModule`.

## Two clocks — keep decoupled
- **FSRS (days)** — *which* card. `FlashcardEntity` has `stability/difficulty/scheduledDays/reps/lapses/state/dueAt`. `SrsUseCase` calls `Fsrs.apply()`, writes `dueAt = now + scheduledDays·86_400_000` (Review) or short-term ms (Learning/Relearning). `FlashcardDao.getNextDueFlashcard` orders by state (Relearning→Learning→Review→New), then `dueAt`, then `difficulty`.
- **Overlay (minutes)** — *how often*. `TimerForegroundService`, FSRS-independent. Tick → `getNextAvailableFlashcard()`; if none due, falls back to closest-to-due. Interval has a "Now" option for immediate display.

## Ratings & FSRS
- `FlashcardRating { WRONG, HARD, GOOD, EASY, CLOSED }`; `WRONG` displays as "Again". Buttons in `component/flashcard/FlashcardControls.kt`; collapses to 2×2 below 240dp. `CLOSED` = no-op for FSRS.
- **Mastered** (`StatisticsViewModel`): `stability ≥ 21d && reps ≥ 3`.
- **FSRS difficulty is 1..10, low = easy, high = hard** (inverse of old SM-2 EF). Any difficulty→label/color mapping must respect this.
- **SM-2 → FSRS migration**: DB v8 / backup v2. Legacy `easinessFactor`/`reviewCount`/`cooldownUntil` gone; upgrade made every card FSRS-`New` (history counters kept, scheduling zeroed). Backup v1 imports same.

## Features
- **APKG import** (`data/anki/AnkiParser`) alongside two-column CSV (preview before save).
- **Pleco lookup** button on overlay (opens front term in Pleco app).
- **Snooze** — pause overlay for N minutes; duration in `SettingsRepository`, end-time persisted, triggered by `SnoozeBroadcastReceiver`; main screen shows state.
- **App blocklist** (`blocklist_packages`) — overlay suppressed while a listed package is foreground.
- **Long-press overlay** — reveals answer type/meta.
- **In-popup resize & drag**; popup opacity moved into settings. Old `OpacityControls`/`ResizeHandles`/`CompactOpacitySlider`/`FlashcardModeSelector`/`InteractionMode` removed.
- **FSRS target retention** 0.80–0.95, default 0.90 (`SettingsRepository`).

## Misc
- `SettingsRepository` = SharedPreferences (not DataStore). Streak/stats in separate `StreakPreferences`.
- Backup: `kotlinx.serialization` JSON via SAF.
- Locales: en (default), pl (`values-pl`), de (`values-de`); switch via `AppCompatDelegate.setApplicationLocales`.
- Permissions: `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS` via `PermissionHelper`/`PermissionLauncher`.
