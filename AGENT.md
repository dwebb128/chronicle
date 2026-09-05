# AGENT.md - AI Agent Reference for Chronicle Android Project

This document helps AI agents understand and work effectively with the Chronicle Epilogue Android codebase.

## 1. Project Overview

Chronicle Epilogue is an **unofficial fork** of
[mattttvaughn/chronicle](https://github.com/mattttvaughn/chronicle) that adds Wear OS support. It
is a Plex audiobook player that ships **two apps** — one for Android phones and a standalone one
for Wear OS — with adjustable playback speed, sleep timer, chapter navigation and offline
playback.

**Module layout — three modules, and the names are not obvious:**

| Module | What it is | minSdk | UI |
| --- | --- | --- | --- |
| `:core` | Shared library: Plex client, Room databases, playback service, DI contract | 30 | none |
| `:wear` | **The Wear OS watch app** (standalone — needs no phone once signed in) | 34 | Compose for Wear |
| `:mobile` | The Android phone app | 30 | Fragments + Data Binding |

Both app modules depend on `:core` and share the applicationId `local.oss.chronicle`, so Play
treats them as one listing and a single device holds only one of them.

`:core` reaches the graph through `Injector`, which holds a `CoreComponent` each `Application`
installs in `onCreate`; both `AppComponent`s extend it. Never make `:core` depend on either app
module. See [`docs/architecture/wear-platform.md`](docs/architecture/wear-platform.md) for platform
specifics, [`docs/features/wear-ui.md`](docs/features/wear-ui.md) for the watch screens, and
[`docs/INSTALLING.md`](docs/INSTALLING.md) for building and sideloading both apps.

**Key Details:**
- **Language:** Kotlin
- **Platforms:** Wear OS (`:wear`, minSdk 34) and Android phone (`:mobile`, minSdk 30); targetSdk 36, compileSdk 36
- **Build System:** Gradle with Kotlin DSL
- **License:** GPLv3 (code) + All Rights Reserved (branding assets)

For complete project information, see [`README.md`](README.md).

## 2. Architecture

Chronicle follows a **layered MVVM architecture** with clear separation of concerns:

- **Presentation Layer:** Compose for Wear OS (`ui/screens/`, `ui/components/`, `ui/theme/`) + ViewModels (per feature). One Activity (`MainActivity`, a `ComponentActivity`) hosts a single `SwipeDismissableNavHost`; there are no Fragments and no Data Binding.
- **Domain Layer:** Business logic in repositories and use cases
- **Data Layer:** Room databases + Plex API integration
- **Service Layer:** [`MediaPlayerService`](core/src/main/java/local/oss/chronicle/features/player/MediaPlayerService.kt) with ExoPlayer for background audio playback

### Key Architectural Patterns

1. **MVVM (Model-View-ViewModel):** Each feature module uses this pattern; Compose screens read ViewModel `LiveData` via `observeAsState()`
2. **Repository Pattern:** Single source of truth combining local (Room) and remote (Plex API) data
3. **MediaBrowserService:** For background audio playback and media controls via Media3
4. **Dependency Injection:** Dagger 2 with 3-component hierarchy:
   - [`AppComponent`](wear/src/main/java/local/oss/chronicle/injection/components/AppComponent.kt) (@Singleton) - Application-wide dependencies
   - [`ActivityComponent`](wear/src/main/java/local/oss/chronicle/injection/components/ActivityComponent.kt) (@ActivityScope) - Activity-scoped dependencies, provided to Compose via `LocalActivityComponent` (a `staticCompositionLocalOf`)
   - [`ServiceComponent`](core/src/main/java/local/oss/chronicle/injection/components/ServiceComponent.kt) (@ServiceScope) - MediaPlayerService dependencies

**For detailed architecture diagrams and patterns, see:**
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - Main architecture overview and index
- [`docs/architecture/wear-platform.md`](docs/architecture/wear-platform.md) - Wear OS platform specifics (rotary input, Ongoing Activity, storage constraints, etc.)
- [`docs/architecture/layers.md`](docs/architecture/layers.md) - Detailed layer architecture
- [`docs/architecture/dependency-injection.md`](docs/architecture/dependency-injection.md) - Dagger 2 DI setup (STALE — predates the Wear conversion; component shape is unchanged but see `wear-platform.md`/`wear-ui.md` for current usage)
- [`docs/architecture/patterns.md`](docs/architecture/patterns.md) - Architectural patterns (STALE — phone-era)
- [`docs/architecture/plex-integration.md`](docs/architecture/plex-integration.md) - Plex-specific implementation (STALE — phone-era)
- [`docs/DATA_LAYER.md`](docs/DATA_LAYER.md) - Database and repository patterns
- [`docs/FEATURES.md`](docs/FEATURES.md) - Feature-specific architecture
- [`docs/features/wear-ui.md`](docs/features/wear-ui.md) - The ten Wear OS screens, nav routes, and components
- [`docs/features/plex-link-login.md`](docs/features/plex-link-login.md) - The plex.tv/link sign-in flow

## 3. Code Structure

The codebase is organized by feature and layer:

```
wear/src/main/java/local/oss/chronicle/
├── application/              # App initialization, MainActivity, DI Injector
│   ├── ChronicleApplication.kt
│   ├── MainActivity.kt         # Single Activity; setContent { ChronicleWearApp(...) }
│   ├── MainActivityViewModel.kt
│   ├── Injector.kt
│   └── Constants.kt
│
├── data/
│   ├── local/               # Room databases, DAOs, Repositories
│   │   ├── AccountDatabase.kt / AccountRepository.kt
│   │   ├── BookDatabase.kt / BookRepository.kt
│   │   ├── ChapterDatabase.kt / ChapterRepository.kt
│   │   ├── CollectionsDatabase.kt / CollectionsRepository.kt
│   │   ├── LibraryRepository.kt
│   │   ├── TrackDatabase.kt / TrackRepository.kt
│   │   └── LibrarySyncRepository.kt
│   │
│   ├── model/               # Domain models (data classes)
│   │   ├── Audiobook.kt
│   │   ├── Chapter.kt
│   │   ├── Collection.kt
│   │   ├── MediaItemTrack.kt
│   │   └── PlexLibrary.kt
│   │
│   └── sources/
│       ├── plex/            # Plex API integration (Retrofit + OkHttp)
│       │   ├── PlexService.kt      # incl. postLinkPin() for the Wear login flow
│       │   ├── PlexMediaSource.kt
│       │   ├── PlexMediaRepository.kt
│       │   ├── PlexLoginRepo.kt
│       │   ├── PlexConfig.kt
│       │   ├── PlexInterceptor.kt
│       │   ├── PlaybackUrlResolver.kt
│       │   ├── CachedFileManager.kt   # download cache; storage free-space guard lives here
│       │   └── model/       # Plex-specific models (Moshi JSON)
│       └── local/           # Local media source
│
├── features/                # Feature modules (ViewModels; UI lives in ui/ — see below)
│   ├── account/            # Account/library data layer (kept) — NOT a UI: AccountManager,
│   │                       # CredentialManager, ActiveLibraryProvider, LegacyAccountMigration
│   ├── auth/               # plex.tv/link state machine: PlexAuthCoordinator, PlexAuthState
│   ├── login/              # ViewModels for user/server/library selection (LoginViewModel,
│   │                       # ChooseUserViewModel, ChooseServerViewModel, ChooseLibraryViewModel)
│   ├── library/            # LibraryViewModel
│   ├── bookdetails/        # AudiobookDetailsViewModel
│   ├── currentlyplaying/   # CurrentlyPlayingViewModel — now playing, speed, sleep timer
│   ├── player/             # MediaPlayerService, ExoPlayer (Media3), AudioOutputMonitor
│   ├── settings/           # SettingsViewModel (rewritten for Wear — see docs/features/wear-ui.md)
│   └── download/           # Download management (Fetch library)
│
├── ui/                     # Compose for Wear OS presentation layer
│   ├── ChronicleWearApp.kt    # Root composable: Scaffold + SwipeDismissableNavHost
│   ├── Nav.kt                 # Route constants
│   ├── RotaryScroll.kt         # rotaryScrollable() modifier
│   ├── theme/Theme.kt
│   ├── components/            # BookRow, ChapterRow, LoadingScreen, ErrorScreen,
│   │                          # NowPlayingChip, OptionsDialog
│   └── screens/                # LinkAccountScreen, ChooseUserScreen, ChooseServerScreen,
│                                # ChooseLibraryScreen, LibraryScreen, BookDetailsScreen,
│                                # NowPlayingScreen, PlaybackSpeedScreen, SleepTimerScreen,
│                                # SettingsScreen — see docs/features/wear-ui.md
│
├── injection/              # Dagger 2 DI setup
│   ├── components/         # AppComponent, ActivityComponent, ServiceComponent
│   ├── modules/            # AppModule, ActivityModule, ServiceModule
│   └── scopes/             # Custom scopes (@ActivityScope, @ServiceScope)
│
└── util/                   # Extension functions, utilities
    ├── ErrorHandling.kt        # ChronicleError sealed class for structured errors
    ├── RetryHandler.kt         # withRetry() with exponential backoff
    ├── NetworkMonitor.kt       # Real-time network connectivity monitoring
    ├── StorageUtils.kt         # bytesAvailable() — wired into the download free-space guard
    └── ScopedCoroutineManager.kt  # Lifecycle-aware coroutine management
```

There is no `navigation/` package (the phone app's `Navigator.kt` was deleted — Compose screens
navigate via `NavHostController` lambdas instead) and no `views/` package (Data Binding is gone;
`views/BottomSheetChooser.kt`'s data types moved to `ui/components/OptionsDialog.kt`). Fragments,
RecyclerView adapters, and `*BindingAdapters.kt` files are gone from every feature package above
except three known leftovers — see the note at the end of this section.

> **Known leftover dead code:** `features/login/{ChooseUserFragment,ChooseServerFragment,
> ChooseLibraryFragment}.kt` were not deleted during the Compose conversion, even though
> `ui/screens/{ChooseUserScreen,ChooseServerScreen,ChooseLibraryScreen}.kt` fully replace them.
> All three still import generated Data Binding classes
> (e.g. `local.oss.chronicle.databinding.OnboardingPlexChooseUserBinding`) that no longer exist
> now that `dataBinding = false` and `res/layout/` is deleted — **these three files need deleting**
> before the module can compile.

## 4. Development Commands

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore configuration)
./gradlew assembleRelease

# Clean build
./gradlew clean
```

### Testing Commands
```bash
# Run unit tests
./gradlew test

# Run tests with coverage
./gradlew testDebugUnitTest

# Run specific test class
./gradlew :wear:testDebugUnitTest --tests "local.oss.chronicle.features.player.TrackListStateManagerTest"
```

## 5. Code Style

Chronicle follows the [Kotlin Style Guide](https://developer.android.com/kotlin/style-guide) enforced by **Ktlint**.

**Key conventions:**
- 4 spaces for indentation
- Max line length: 120 characters
- Curly braces on same line
- Use trailing commas in multi-line declarations
- Prefer `val` over `var` when possible
- Use explicit types for public APIs

**Pre-commit hooks** automatically run [`ktlintCheck`](pre-commit) to prevent style violations.

For complete contribution guidelines, see [`CONTRIBUTING.md`](CONTRIBUTING.md).

## 6. Key Technical Details

### 6.1 Plex API Integration

Chronicle interacts with **two separate Plex endpoints**:

1. **plex.tv** - Authentication and account management (Retrofit)
   - OAuth flow
   - User profile information
   - Server discovery

2. **Plex Media Server** - Content delivery (user's server URL, Retrofit)
   - Library browsing
   - Metadata fetching
   - Audio streaming

**Critical Headers** (handled by [`PlexInterceptor`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexInterceptor.kt)):
- `X-Plex-Token` - Authentication token (all requests)
- `X-Plex-Client-Identifier` - Unique device ID
- **`X-Plex-Client-Profile-Extra`** - **CRITICAL for playback** - Tells Plex which formats the app supports

For implementation details, see [`PlexInterceptor.kt`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexInterceptor.kt).

### 6.2 Audio Playback Architecture

The player uses **Media3 (ExoPlayer)** with:
- [`MediaPlayerService`](core/src/main/java/local/oss/chronicle/features/player/MediaPlayerService.kt) - Foreground service for background playback
- [`AudiobookMediaSessionCallback`](core/src/main/java/local/oss/chronicle/features/player/AudiobookMediaSessionCallback.kt) - Handles play/pause/seek commands
- [`PlaybackStateController`](core/src/main/java/local/oss/chronicle/features/player/PlaybackStateController.kt) - **Single source of truth** for playback state
- [`PlaybackState`](core/src/main/java/local/oss/chronicle/features/player/PlaybackState.kt) - Immutable playback state data class
- [`TrackListStateManager`](core/src/main/java/local/oss/chronicle/features/player/TrackListStateManager.kt) - Manages playlist state and chapter detection (Mutex-protected)
- [`SeekHandler`](core/src/main/java/local/oss/chronicle/features/player/SeekHandler.kt) - Atomic seek operations with timeout
- [`ChapterValidator`](core/src/main/java/local/oss/chronicle/features/player/ChapterValidator.kt) - Validates positions against chapter bounds
- [`PlaybackUrlResolver`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlaybackUrlResolver.kt) - Resolves streaming URLs with retry logic and caching
- [`PlexHttpDataSourceFactory`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexHttpDataSourceFactory.kt) - Custom DataSource.Factory for ExoPlayer that performs lazy token injection on each HTTP request, preventing stale auth tokens
- [`ServerConnectionResolver`](core/src/main/java/local/oss/chronicle/data/sources/plex/ServerConnectionResolver.kt) - Resolves library-specific server URLs and auth tokens for multi-library playback
- [`PlexProgressReporter`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexProgressReporter.kt) - Thread-safe, library-aware progress reporting to Plex with request-scoped Retrofit instances

All media playback follows Android's MediaSession/MediaBrowser API.

### 6.3 Playback State Management

Chronicle uses a centralized state management pattern with [`PlaybackStateController`](core/src/main/java/local/oss/chronicle/features/player/PlaybackStateController.kt):

- **Immutable State**: Updates create new `PlaybackState` instances via `copy()`
- **Thread Safety**: All updates go through `Mutex.withLock {}`
- **Reactive**: State exposed via `StateFlow` for observation
- **Debounced Persistence**: Database writes debounced (3 seconds) to reduce I/O
- **StateFlow → LiveData Bridge**: [`CurrentlyPlayingSingleton`](core/src/main/java/local/oss/chronicle/features/currentlyplaying/CurrentlyPlayingSingleton.kt) converts StateFlow to LiveData for UI
- **Library-Aware Progress Reporting**: [`PlexProgressReporter`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexProgressReporter.kt) ensures progress is synced to the correct Plex server per library
- **Play Queue Item Cache**: [`PlexProgressReporter`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexProgressReporter.kt) includes a `playQueueItemCache` for Plex dashboard activity correlation, using consistent Plex headers via `PlexPrefsRepo`

See [`docs/architecture/patterns.md`](docs/architecture/patterns.md) for detailed patterns.

### 6.4 Data Persistence (Room)

Chronicle uses **Room** (AndroidX) for local data:

| Database | Purpose | Key Entities |
|----------|---------|-------------|
| [`BookDatabase`](core/src/main/java/local/oss/chronicle/data/local/BookDatabase.kt) | Audiobook metadata | `Audiobook` |
| [`TrackDatabase`](core/src/main/java/local/oss/chronicle/data/local/TrackDatabase.kt) | Audio file information | `MediaItemTrack` |
| [`ChapterDatabase`](core/src/main/java/local/oss/chronicle/data/local/ChapterDatabase.kt) | Chapter markers | `Chapter` |
| [`CollectionsDatabase`](core/src/main/java/local/oss/chronicle/data/local/CollectionsDatabase.kt) | Plex collections | `Collection` |

**Database migrations** are defined within database class files. Schema versions are stored in [`core/schemas/`](core/schemas/).

**ChapterDatabase Migration v1→v2**: Changed primary key from `id` (Plex rating key) to `uid` (auto-generated) to prevent overwrites when multiple chapters share the same track rating key. This was a destructive migration since chapters are transient data that can be refetched.

**Library-Aware Repositories**: [`BookRepository`](core/src/main/java/local/oss/chronicle/data/local/BookRepository.kt), [`TrackRepository`](core/src/main/java/local/oss/chronicle/data/local/TrackRepository.kt), and [`ChapterRepository`](core/src/main/java/local/oss/chronicle/data/local/ChapterRepository.kt) use [`ServerConnectionResolver`](core/src/main/java/local/oss/chronicle/data/sources/plex/ServerConnectionResolver.kt) + [`ScopedPlexServiceFactory`](core/src/main/java/local/oss/chronicle/data/sources/plex/ScopedPlexServiceFactory.kt) to fetch metadata from the correct Plex server for each library in multi-server setups.

### 6.5 Offline Playback

Uses **[Fetch library](https://github.com/tonyofrancis/Fetch)** for downloads:
- [`CachedFileManager`](core/src/main/java/local/oss/chronicle/data/sources/plex/CachedFileManager.kt) - Manages cached audio files (uses ScopedCoroutineManager)
- [`DownloadNotificationWorker`](core/src/main/java/local/oss/chronicle/features/download/DownloadNotificationWorker.kt) - Background download handling (WorkManager)

### 6.6 Important Implementation Notes

- **Authentication tokens expire** - Implement token refresh logic when modifying auth code
- **Chapter detection** is complex - See [`TrackListStateManager`](core/src/main/java/local/oss/chronicle/features/player/TrackListStateManager.kt) for current implementation
- **Playback position syncing** is library-aware and thread-safe via [`PlexProgressReporter`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexProgressReporter.kt), which creates request-scoped Retrofit instances to avoid global state mutation
- **Progress reporting worker** - [`PlexSyncScrobbleWorker`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexSyncScrobbleWorker.kt) is now a `CoroutineWorker` (not `Worker`) for proper async handling
- **Play queue item IDs** from `POST /playQueues` responses are cached in-memory and included in timeline updates for Plex dashboard visibility
- **Media sessions** must be properly released to avoid memory leaks
- **Compose for Wear OS** is the entire presentation layer (`androidx.wear.compose.material`/`.foundation`/`.navigation`); there is no Data Binding, no XML layouts, and no RecyclerView adapters

### 6.7 Multi-Account System

Chronicle supports multiple accounts and libraries:

- **AccountDatabase** - Stores Account and Library entities
- **AccountManager** - Coordinates account operations (add, remove, switch)
- **ActiveLibraryProvider** - StateFlow-based current library state
- **CredentialManager** - Encrypted credential storage using AndroidX Security
- **LegacyAccountMigration** - Migrates single-account data on first launch

**ID Format**: Content IDs use prefixed strings:
- Audiobooks/Tracks: `"plex:{ratingKey}"` (e.g., `"plex:12345"`)
- Libraries: `"plex:library:{sectionId}"` (e.g., `"plex:library:1"`)
- Accounts: `"plex:account:{uuid}"` (e.g., `"plex:account:abc-123"`)

#### Unified Library View

Chronicle displays all libraries together in a unified view:
- [`LibrarySyncRepository.refreshLibrary()`](core/src/main/java/local/oss/chronicle/data/local/LibrarySyncRepository.kt) syncs ALL libraries sequentially
- [`PlexSyncScrobbleWorker`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexSyncScrobbleWorker.kt) uses `audiobook.libraryId` for contextual API calls to the correct server
- ViewModels query all books without library filtering - unified data access
- The Wear OS `LibraryScreen` is the sole browsing surface (there is no separate Home/Collections/Search screen — see [`docs/features/wear-ui.md`](docs/features/wear-ui.md) for the current screen set and what was cut)
- **No on-watch account/user switching UI.** `AccountManager`/`AccountRepository`/`CredentialManager` remain as the data layer backing multi-account/multi-library storage (still wired into `PlexLoginRepo`), but the phone app's account-list/library-selector screens are gone; on Wear, whichever Plex user the plex.tv/link flow resolves to is what you get, and changing accounts means re-running the link flow from Settings → Log out

#### Library-Aware Playback

Each library stores its own `serverUrl` and `authToken` in the database. During playback,
[`ServerConnectionResolver`](core/src/main/java/local/oss/chronicle/data/sources/plex/ServerConnectionResolver.kt)
resolves the correct server connection for a track's library, ensuring multi-server setups work correctly.

Progress reporting and `startMediaSession()` now use per-library server connections via [`PlexProgressReporter`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexProgressReporter.kt),
eliminating race conditions when reporting progress for books from different libraries.

The data layer repositories ([`BookRepository`](core/src/main/java/local/oss/chronicle/data/local/BookRepository.kt), [`TrackRepository`](core/src/main/java/local/oss/chronicle/data/local/TrackRepository.kt), and [`ChapterRepository`](core/src/main/java/local/oss/chronicle/data/local/ChapterRepository.kt)) all use the same library-aware pattern via `ServerConnectionResolver` and `ScopedPlexServiceFactory` to fetch metadata from the correct server per library.

**Library-Aware Thumbnail URLs**: [`PlexConfig.makeThumbUriForLibrary()`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexConfig.kt) resolves library-specific server URLs for thumbnail images, preventing 404 errors when displaying books from non-active libraries. UI layouts pass `libraryId` to [`BindingAdapters.bindImageRounded()`](mobile/src/main/java/local/oss/chronicle/views/BindingAdapters.kt) for library-aware image loading.

See [`docs/architecture/library-aware-playback.md`](docs/architecture/library-aware-playback.md) for architecture details.

## 7. Documentation Index

### Architecture & Design
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - Main architecture overview and index
- [`docs/architecture/wear-platform.md`](docs/architecture/wear-platform.md) - Wear OS platform specifics: rotary input, Ongoing Activity, `AudioOutputMonitor`, storage constraints, what's out of scope
- [`docs/architecture/layers.md`](docs/architecture/layers.md) - Detailed layer architecture (Presentation, Domain, Data)
- [`docs/architecture/dependency-injection.md`](docs/architecture/dependency-injection.md) - Dagger 2 DI component hierarchy and modules (STALE — phone-era; component *shape* is unchanged)
- [`docs/architecture/patterns.md`](docs/architecture/patterns.md) - Architectural patterns (Repository, MVVM, State Machines, etc.) (STALE — phone-era)
- [`docs/architecture/plex-integration.md`](docs/architecture/plex-integration.md) - Plex API integration details (client profile, headers, OAuth) (STALE — phone-era)
- [`docs/architecture/library-aware-playback.md`](docs/architecture/library-aware-playback.md) - Multi-library server resolution for playback
- [`docs/architecture/progress-reporting-overhaul.md`](docs/architecture/progress-reporting-overhaul.md) - Thread-safe, library-aware progress reporting (`PlexProgressReporter`)
- [`docs/architecture/scoped-plex-service-factory.md`](docs/architecture/scoped-plex-service-factory.md) - Per-library request-scoped Retrofit/Plex service construction
- [`docs/architecture/lazy-token-injection.md`](docs/architecture/lazy-token-injection.md) - ExoPlayer HTTP DataSource lazy token injection pattern
- [`docs/architecture/plex-dashboard-activity.md`](docs/architecture/plex-dashboard-activity.md) - Plex "now playing" dashboard visibility (play queue item cache)

### Data Layer
- [`docs/DATA_LAYER.md`](docs/DATA_LAYER.md) - Database and repository patterns
  - Room database schemas
  - Repository implementations
  - Sync mechanisms
  - Data flow diagrams

### Features
- [`docs/FEATURES.md`](docs/FEATURES.md) - Feature documentation index
- [`docs/features/wear-ui.md`](docs/features/wear-ui.md) - The ten Wear OS screens, nav routes, and Wear Compose components behind each
- [`docs/features/plex-link-login.md`](docs/features/plex-link-login.md) - The plex.tv/link short-code sign-in flow
- [`docs/features/login.md`](docs/features/login.md) - User/server/library selection state machine (STALE — phone-era UI, but the underlying `IPlexLoginRepo` state machine is unchanged)
- [`docs/features/library.md`](docs/features/library.md) - Library browsing documentation (STALE — phone-era)
- [`docs/features/playback.md`](docs/features/playback.md) - Media playback documentation (STALE — phone-era UI, player internals mostly unchanged)
- [`docs/features/chapters.md`](docs/features/chapters.md) - Chapter data flow, detection algorithm, navigation (STALE — phone-era UI, detection logic unchanged)
- [`docs/features/downloads.md`](docs/features/downloads.md) - Download management documentation (STALE — phone-era; see `wear-platform.md` for the Wear-specific storage guard)
- [`docs/features/settings.md`](docs/features/settings.md) - Settings/preferences documentation (STALE — phone-era; see `wear-ui.md` for the rewritten Wear settings screen)
- [`docs/features/debug-easter-egg.md`](docs/features/debug-easter-egg.md) - (STALE — the debug-info dialog this describes was cut entirely in the Wear conversion)

### API Integration
- [`docs/API_FLOWS.md`](docs/API_FLOWS.md) - Plex API integration details
  - Authentication flows
  - Endpoint documentation
  - Request/response formats
  - Error handling

### Specific Topics
- [`docs/example-query-responses/`](docs/example-query-responses/) - Real API response examples
  - [`README.md`](docs/example-query-responses/README.md) - Index of captured responses
  - [`oauth-flow.md`](docs/example-query-responses/oauth-flow.md) - OAuth flow examples
  - [`query-providers.md`](docs/example-query-responses/query-providers.md) - Provider/server query examples
  - [`request-album-info.md`](docs/example-query-responses/request-album-info.md) - Album/audiobook metadata
  - [`request_track_info.md`](docs/example-query-responses/request_track_info.md) - Track information
  - [`request-collections-info.md`](docs/example-query-responses/request-collections-info.md) - Collections data
  - [`managed_users.md`](docs/example-query-responses/managed_users.md) - Managed user accounts

### Project Management
- [`README.md`](README.md) - Project overview, features, links
- [`CONTRIBUTING.md`](CONTRIBUTING.md) - Contribution guidelines, code style, building
- [`CHANGELOG.md`](CHANGELOG.md) - Version history and changes
- [`PRIVACY.md`](PRIVACY.md) - Privacy policy

## 8. Common Tasks

### 8.1 Adding a New Feature

**Technology:** Kotlin, MVVM with AndroidX Lifecycle, Compose for Wear OS, Dagger 2

**General Strategy:**
Start by understanding the feature scope and user requirements — and whether it makes sense on a
watch-sized screen at all (see the cut-features list in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
for what was deliberately left out of this form factor). Research the existing codebase for
similar patterns to maintain consistency — [`ui/screens/LibraryScreen.kt`](wear/src/main/java/local/oss/chronicle/ui/screens/LibraryScreen.kt)
is a good reference for a simple list screen. Design the data model and any required API changes
first before touching UI code. Implement in layers following the architecture: data layer (models,
DAOs, API calls) → domain/repository layer (business logic) → presentation layer (Composable
screen + ViewModel). Write tests alongside implementation to validate behavior as you build.
Document the feature in [`docs/`](docs/) when complete, including architecture diagrams for
complex flows.

1. **Create feature ViewModel** in [`features/`](wear/src/main/java/local/oss/chronicle/features/)
   - Structure: a `ViewModel` + its `Factory`, exposing state as `LiveData` (this codebase does
     not use `StateFlow` for ViewModel-exposed state — see `docs/ARCHITECTURE.md` D9)
   - Reference example: [`features/library/LibraryViewModel.kt`](wear/src/main/java/local/oss/chronicle/features/library/LibraryViewModel.kt)

2. **Create the screen composable** in [`ui/screens/`](wear/src/main/java/local/oss/chronicle/ui/screens/)
   - Read the ViewModel's `LiveData` with `observeAsState()`
   - Wrap list content in `androidx.wear.compose.foundation.lazy.ScalingLazyColumn` (not
     `compose-material` — see [`docs/architecture/wear-platform.md`](docs/architecture/wear-platform.md)),
     with `Modifier.rotaryScrollable(listState)` and a `PositionIndicator`
   - Reuse [`ui/components/`](wear/src/main/java/local/oss/chronicle/ui/components/) (`LoadingScreen`,
     `ErrorScreen`, `OptionsDialog`, etc.) rather than duplicating their patterns

3. **Add a navigation destination** in [`ui/Nav.kt`](wear/src/main/java/local/oss/chronicle/ui/Nav.kt)
   (a route constant) and register it with `composable(...)` in
   [`ui/ChronicleWearApp.kt`](wear/src/main/java/local/oss/chronicle/ui/ChronicleWearApp.kt)'s
   `SwipeDismissableNavHost`

4. **Setup dependency injection:**
   - Add an injection accessor (typically a ViewModel `Factory` provider) to
     [`ActivityComponent`](wear/src/main/java/local/oss/chronicle/injection/components/ActivityComponent.kt)
     if the ViewModel needs Activity-scoped dependencies, or construct the `Factory` directly from
     [`Injector`](core/src/main/java/local/oss/chronicle/application/Injector.kt) if all its
     dependencies are already `AppComponent`-scoped singletons (see `LibraryScreen` for that
     pattern)
   - Obtain the factory in the composable via `LocalActivityComponent.current` and
     `androidx.lifecycle.viewmodel.compose.viewModel(factory = ...)`

5. **Follow MVVM pattern:**
   - Composable screen: rendering only, no business logic
   - ViewModel: state management with `LiveData`, navigation via lambdas passed down from
     `ChronicleWearApp` (ViewModels never navigate directly)
   - Repository: data access abstraction

6. **Document:**
   - Architecture: update corresponding documentation, split to new file if necessary for readability
   - Feature: describe the feature in [`docs/features/wear-ui.md`](docs/features/wear-ui.md) (or a
     new dedicated file in [`docs/features/`](docs/features/) if it's substantial enough)
   - Tests: ensure unit tests have been written and run successfully

### 8.2 Modifying Plex API Calls

**Technology:** Retrofit 2, OkHttp3, Moshi (JSON parsing)

**General Strategy:**
First capture real API responses using a proxy or logging to understand the actual data structure. Understand the existing Plex data flow by reviewing [`PlexMediaRepository.kt`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexMediaRepository.kt) and related code. Create model classes that match actual API responses, not assumptions. Handle error cases and edge cases (network failures, malformed responses, authentication issues). Always save example responses in [`docs/example-query-responses/`](docs/example-query-responses/) for future reference and testing.

1. **Define endpoint** in [`PlexService.kt`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexService.kt) interface using Retrofit annotations

2. **Create/update model classes** in [`data/sources/plex/model/`](core/src/main/java/local/oss/chronicle/data/sources/plex/model/) with Moshi annotations

3. **Update repository** in [`PlexMediaRepository.kt`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexMediaRepository.kt)

4. **Save example responses** in [`docs/example-query-responses/`](docs/example-query-responses/)

5. **Headers handled by** [`PlexInterceptor`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexInterceptor.kt) - modify if needed for new endpoints

### 8.3 Adding Database Entities

**Technology:** Room (AndroidX), Kotlin Coroutines

**General Strategy:**
Plan the schema changes carefully, considering upgrade paths for existing users who already have data. Always write migrations for existing users - never use destructive migrations in production. Consider data relationships and foreign keys to maintain referential integrity. Test migrations with existing database files from previous versions to ensure data is preserved correctly. Remember that database changes are permanent once users upgrade.

1. **Create entity** in [`data/model/`](core/src/main/java/local/oss/chronicle/data/model/) with Room `@Entity` annotation
   - Reference: See existing entities like [`Audiobook.kt`](core/src/main/java/local/oss/chronicle/data/model/Audiobook.kt)

2. **Create DAO interface** in appropriate database file with Room `@Dao` annotation
   - Reference: See DAOs in [`BookDatabase.kt`](core/src/main/java/local/oss/chronicle/data/local/BookDatabase.kt)

3. **Increment database version** in `@Database` annotation

4. **Add migration** in database class extending `Migration`
   - Reference: See migrations in existing database files

5. **Update repository** to use new DAO

6. **Schema auto-generated** on build to [`core/schemas/`](core/schemas/)

### 8.4 Testing

**Technology:** JUnit 4, Mockito, Kotlin Coroutines Test

**General Strategy:**
Test-driven development is encouraged - write tests before or during implementation, not after. Unit test business logic in isolation from Android framework dependencies. Mock external dependencies (API calls, database access) to ensure tests are fast and deterministic. Write tests that document expected behavior and edge cases. Use real-world scenarios for integration tests to validate complex flows. Tests serve as living documentation of how code should behave.

**Test location:** [`wear/src/test/java/local/oss/chronicle/`](wear/src/test/java/local/oss/chronicle/)

**Reference examples:**
- [`TrackListStateManagerTest.kt`](core/src/test/java/local/oss/chronicle/features/player/TrackListStateManagerTest.kt) - Player state management testing
- [`ChapterDetectionRealWorldTest.kt`](core/src/test/java/local/oss/chronicle/features/player/ChapterDetectionRealWorldTest.kt) - Complex chapter detection logic
- [`AudiobookDetailsViewModelTest.kt`](wear/src/test/java/local/oss/chronicle/features/bookdetails/AudiobookDetailsViewModelTest.kt) - ViewModel testing with Mockito
- [`TrackRepositoryTest.kt`](core/src/test/java/local/oss/chronicle/data/local/TrackRepositoryTest.kt) - Library-aware repository testing with ServerConnectionResolver
- [`ChapterPrimaryKeyTest.kt`](core/src/test/java/local/oss/chronicle/data/local/ChapterPrimaryKeyTest.kt) - Chapter primary key validation
- [`ChapterRepositoryTest.kt`](core/src/test/java/local/oss/chronicle/data/local/ChapterRepositoryTest.kt) - Library-aware chapter loading and bookId association

**Testing approach:**
- Mock repositories and dependencies with Mockito
- Use `@RunWith(MockitoJUnitRunner::class)` for tests
- Test ViewModels independently of Android framework
- Use Kotlin coroutines test utilities for async code

### 8.5 Adding Dependencies

**Technology:** Gradle Kotlin DSL

**General Strategy:**
Evaluate necessity first - prefer using existing solutions already in the project over adding new dependencies. Check license compatibility with GPLv3 to ensure legal compliance. Consider impact on app size (APK bloat) and method count. Verify Android/Wear OS compatibility and that it supports the project's minimum SDK (34). Research the library's maintenance status, community support, and security track record before adding.

1. **Edit** [`wear/build.gradle.kts`](wear/build.gradle.kts) in the `dependencies` block

2. **Sync project** with Gradle files

3. **For Dagger-provided dependencies:**
   - Add provider method in appropriate module ([`AppModule`](wear/src/main/java/local/oss/chronicle/injection/modules/AppModule.kt), [`ActivityModule`](wear/src/main/java/local/oss/chronicle/injection/modules/ActivityModule.kt), or [`ServiceModule`](core/src/main/java/local/oss/chronicle/injection/modules/ServiceModule.kt))
   - Add to component if needed

### 8.6 Writing Documentation

**Technology:** Markdown, Mermaid diagrams

**General Strategy:**
Document as you develop, not after - it's easier to document while the context is fresh in your mind. Keep [`AGENT.md`](AGENT.md) updated for AI agent context whenever adding new patterns or architectural decisions. Use relative paths to reference code files so links remain valid as the project evolves. Include diagrams for complex flows using Mermaid syntax in markdown for visual clarity. Document API responses in [`docs/example-query-responses/`](docs/example-query-responses/) as reference material for debugging and testing. Update [`CHANGELOG.md`](CHANGELOG.md) for user-facing changes so users understand what's new or fixed.

1. **Choose appropriate documentation location:**
   - Architecture changes → [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
   - Feature documentation → new file in [`docs/features/`](docs/features/)
   - API integration → [`docs/API_FLOWS.md`](docs/API_FLOWS.md)
   - AI agent context → [`AGENT.md`](AGENT.md)
   - User-facing changes → [`CHANGELOG.md`](CHANGELOG.md)

2. **Use consistent formatting:**
   - Link to code files with backticks and relative paths: \[`FileName.kt`\](path/to/FileName.kt)
   - Use Mermaid for architecture diagrams, sequence diagrams, and flowcharts
   - Include code examples where helpful

3. **Document API responses:**
   - Save real API responses in [`docs/example-query-responses/`](docs/example-query-responses/)
   - Include request details (endpoint, headers, parameters)
   - Document edge cases and error responses

4. **Update AGENT.md for AI agents:**
   - Add new patterns to appropriate sections
   - Document technology choices and rationale
   - Include troubleshooting tips for common issues

### 8.7 Making Architecture Changes

**Technology:** MVVM, Dagger 2, Room, Retrofit

**General Strategy:**
Propose changes in [`docs/architecture/`](docs/architecture/) first before implementing to ensure alignment with project goals. Consider impact on existing features - architecture changes can have far-reaching effects on the codebase. Plan an incremental migration path that allows gradual transition without breaking existing functionality. Update [`ARCHITECTURE.md`](docs/ARCHITECTURE.md) after changes are complete to keep documentation current. Ensure the Dagger DI component hierarchy is maintained to avoid circular dependencies or scope violations.

1. **Document the proposal:**
   - Create proposal document in [`docs/architecture/`](docs/architecture/)
   - Explain problem being solved and proposed solution
   - Identify affected components and features
   - Include migration plan for existing code

2. **Consider dependencies:**
   - Map out which components depend on what's being changed
   - Ensure Dagger component hierarchy remains valid:
     - `AppComponent` (@Singleton) → Application-wide
     - `ActivityComponent` (@ActivityScope) → Activity-scoped
     - `ServiceComponent` (@ServiceScope) → Service-scoped
   - Check for circular dependencies

3. **Plan incremental migration:**
   - Break changes into small, reviewable steps
   - Maintain backward compatibility during transition
   - Test existing features after each step
   - Document migration steps for team awareness

4. **Update documentation:**
   - Update [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
   - Update affected feature documentation in [`docs/FEATURES.md`](docs/FEATURES.md)
   - Update [`AGENT.md`](AGENT.md) with new patterns
   - Add diagrams showing new architecture

5. **Testing:**
   - Write tests for new architecture components
   - Verify existing tests still pass
   - Add integration tests for cross-layer interactions

## 9. Troubleshooting Common Issues

### General Strategy
For reported and confirmed bugs a test recreating the scenario is required. The approach is to have the test fail (as expected with the bug present) and defining the desired state criteria before attempting a fix. Once the fix has been applied and the test passes it is encouraged to still test manually to verify that the issue has been resolved.

### Build Issues
- **Ktlint failures:** Run `./gradlew ktlintFormat` before committing
- **Missing keystore:** Copy [`keystore.properties.example`](keystore.properties.example) to `keystore.properties` for release builds
- **Room schema errors:** Delete `core/schemas/` and rebuild to regenerate
- **failing tests:** Rerun the tests with `--stacktrace` to get more details on the cause of the issue
### Runtime Issues
- **Playback fails:** Check `X-Plex-Client-Profile-Extra` header in [`PlexInterceptor`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexInterceptor.kt)
- **Authentication errors:** Verify token is valid and server URL is correct in [`PlexConfig`](core/src/main/java/local/oss/chronicle/data/sources/plex/PlexConfig.kt)
- **Database crashes:** Check for missing migrations between schema versions
- **Chapter detection issues:** See [`TrackListStateManager`](core/src/main/java/local/oss/chronicle/features/player/TrackListStateManager.kt) implementation

### Plex-Specific Issues
- **401 Unauthorized:** Token expired or invalid - trigger re-authentication
- **Media not playing:** Ensure server supports audiobook formats (mp3, m4a, m4b)
- **Managed users:** Switching users requires re-authentication flow - see [`docs/example-query-responses/managed_users.md`](docs/example-query-responses/managed_users.md)

---

**Last Updated:** 2026-08-30
**Project Version:** Check [`CHANGELOG.md`](CHANGELOG.md) for current version
