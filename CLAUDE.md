# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FloFla Cards is an Android flashcard app (Kotlin, minSdk 24, targetSdk 36) that displays flashcards as system overlays on top of other apps at user-defined intervals. It is fully offline, ad-free, and distributed on F-Droid.
I use FloFlaCards to study Chinese characters. I use FloFlaCards to passively study characters. In a defined interval it shows an overlay with a chinese word. After clicking a button, it shows the meaning and pronounciation. I want to improve the app so that it supports me in learning chinese.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.floflacards.app.data.csv.CsvParserTest"

# Run lint
./gradlew lint
```

## Architecture

The app follows a clean architecture pattern with three layers:

**Data layer** (`data/`)
- `entity/` — Room database entities (`FlashcardEntity`, `CategoryEntity`)
- `dao/` — Room DAOs for database access
- `repository/` — Repositories: `FlashcardRepository` (Room), `SettingsRepository` (SharedPreferences), `BackupRepository` (file-based JSON backup via SAF)
- `source/` — Additional preference/manager classes (`FlashcardUiPreferences`, `StreakPreferences`, `ImageManager`, `BackupPreferences`)
- `csv/` — CSV parsing and export logic
- `model/` — Data-layer models (`AppTheme`, `FlashcardTheme`, `Language`)

**Domain layer** (`domain/`)
- `usecase/` — Business logic: CSV import/export, backup CRUD, SRS scheduling (`SrsUseCase`), streak tracking (`SimpleStreakUseCase`), statistics
- `model/` — Domain models (`SrsConfig`, `StreakData`, `InteractionMode`)
- `manager/ServiceStateManager` — Singleton tracking whether the overlay service is running

**Presentation layer** (`presentation/`)
- `screen/` — Full screens: `MainActivity` (main entry, hosts Compose nav), `WelcomeActivity` (onboarding), and route composables for each feature
- `viewmodel/` — One ViewModel per screen/feature area
- `component/` — Reusable Compose components grouped by feature (`flashcard/`, `main/`, `settings/`, `welcome/`, `dialog/`, `csv/`, `statistics/`, `shared/`, `text/`)
- `navigation/AppNavigation` — Single `NavHost` with all routes (`main`, `categories`, `statistics`, `app-settings`, `flashcard-management/{categoryId}`, `add-edit-flashcard`, `csv-import`, `csv-export`, `csv-bulk-export`)
- `theme/` — Material3 theme

**Service layer** (`service/`)
- `OverlayService` — The core feature: a foreground `Service` that implements `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner` so it can host Compose UI. Renders flashcard overlays using `SYSTEM_ALERT_WINDOW` permission via `OverlayManager`.
- `TimerForegroundService` — Manages the repeating alarm/timer that triggers overlay display at the configured interval.
- `LearningServiceManager` — Coordinates starting/stopping both services.
- `ViewModelStoreManager` — Custom `ViewModelStore` holder for the service context.

**DI** (`di/`) — Hilt modules: `DatabaseModule` (Room + DAOs), `BackupModule`, `CsvModule`.

## Key Architectural Notes

- The overlay is rendered by `OverlayService` using Jetpack Compose drawn into a `ComposeView` added to the `WindowManager`. The service must implement lifecycle interfaces manually since it isn't an Activity.
- `SettingsRepository` uses `SharedPreferences` directly (not DataStore). Settings include overlay interval, flashcard size/opacity, SRS config, selected category, and app theme/language.
- Backup format is JSON serialized with `kotlinx.serialization`. The backup uses Android's Storage Access Framework (SAF) for file access.
- CSV import/export supports a two-column format (front, back). Import shows a preview before saving.
- Streak and statistics data are stored in separate `SharedPreferences` via `StreakPreferences`.
- Localization: English (default), Polish (`values-pl`), German (`values-de`). Language switching uses `AppCompatDelegate.setApplicationLocales`.
- The app requires `SYSTEM_ALERT_WINDOW` (overlay) and `POST_NOTIFICATIONS` permissions; `PermissionHelper`/`PermissionLauncher` handle the runtime permission flow.
