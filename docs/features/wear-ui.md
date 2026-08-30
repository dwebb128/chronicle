# Wear OS UI

This documents the ten screens actually implemented under
[`ui/screens/`](../../app/src/main/java/local/oss/chronicle/ui/screens/), the navigation routes
that connect them, and which Wear Compose components back each one. It replaces the phone app's
Fragment/RecyclerView/DataBinding UI layer entirely; see
[`docs/architecture/wear-platform.md`](../architecture/wear-platform.md) for the platform-level
concerns (rotary input, Ongoing Activity, etc.) and
[`docs/architecture/layers.md`](../architecture/layers.md) for how this fits the overall layered
architecture.

## Package layout

```
ui/
├── ChronicleWearApp.kt      # Root composable: Scaffold + SwipeDismissableNavHost + login-state routing
├── Nav.kt                   # Route constants
├── RotaryScroll.kt          # Modifier.rotaryScrollable() — wires rotary input to ScalingLazyListState
├── theme/
│   └── Theme.kt              # ChronicleTheme — Wear Compose Colors seeded from colors.xml
├── components/
│   ├── BookRow.kt            # Chip
│   ├── ChapterRow.kt         # Chip
│   ├── ErrorScreen.kt        # Centered message + optional "Try again" Button
│   ├── LoadingScreen.kt      # Centered CircularProgressIndicator
│   ├── NowPlayingChip.kt     # Chip — "continue listening" shortcut
│   └── OptionsDialog.kt      # Dialog — replaces the phone's BottomSheetChooser
└── screens/
    ├── LinkAccountScreen.kt
    ├── ChooseUserScreen.kt
    ├── ChooseServerScreen.kt
    ├── ChooseLibraryScreen.kt
    ├── LibraryScreen.kt
    ├── BookDetailsScreen.kt
    ├── NowPlayingScreen.kt
    ├── PlaybackSpeedScreen.kt
    ├── SleepTimerScreen.kt
    └── SettingsScreen.kt
```

## Navigation routes

Defined in [`Nav.kt`](../../app/src/main/java/local/oss/chronicle/ui/Nav.kt) and registered in
[`ChronicleWearApp.kt`](../../app/src/main/java/local/oss/chronicle/ui/ChronicleWearApp.kt) against
a single `SwipeDismissableNavHost`:

| Route | Screen |
|---|---|
| `link_account` | `LinkAccountScreen` (start destination) |
| `choose_user` | `ChooseUserScreen` |
| `choose_server` | `ChooseServerScreen` |
| `choose_library` | `ChooseLibraryScreen` |
| `library` | `LibraryScreen` |
| `book_details/{bookId}` | `BookDetailsScreen` |
| `now_playing` | `NowPlayingScreen` |
| `playback_speed` | `PlaybackSpeedScreen` |
| `sleep_timer` | `SleepTimerScreen` |
| `settings` | `SettingsScreen` |

The route order in `Nav.kt` mirrors `IPlexLoginRepo.LoginState`'s real transition chain — user,
then server, then library — not the order screens are reachable from once logged in. Only
`book_details/{bookId}` carries an argument; the book's title is deliberately never put in a route
(titles can contain `/`).

`ChronicleWearApp` observes `IPlexLoginRepo.loginEvent` and auto-navigates as the login state
machine advances, using `popUpTo(navController.graph.id) { inclusive = true }` on each step so the
swipe-back gesture can't return to an already-completed login screen. Back navigation elsewhere is
native `SwipeDismissableNavHost` behavior — no custom back-stack code.

## Screen-by-screen

**`LinkAccountScreen`** — the plex.tv/link sign-in flow (see
[`docs/features/plex-link-login.md`](plex-link-login.md) for the full auth-flow writeup). Renders
`LoadingScreen` while a PIN is being created, a centered code display (`Text` styled with
`MaterialTheme.typography.title1`) plus a cancel `Button` while waiting/polling, and
`ErrorScreen`-with-"Try again" for Timeout/Error/Cancelled. Uses `LocalView.current.keepScreenOn`
to keep the display on for the whole non-terminal flow, since the code has to stay legible long
enough to type into another device.

**`ChooseUserScreen`** — a `ScalingLazyColumn` of `Chip`s, one per Plex user, driven by
`ChooseUserViewModel`. When the ViewModel signals `showPin` (a managed/PIN-protected user), it
switches to an in-file `PinEntryScreen` private composable instead: a numeric keypad built from
`CompactChip`s in three `Row`s of three digits plus a bottom row for delete/0/submit, all inside a
`ScalingLazyColumn` so it scrolls if it doesn't fit.

**`ChooseServerScreen`** / **`ChooseLibraryScreen`** — the remaining two login-chain steps,
structurally identical to `ChooseUserScreen`'s list case: `ScalingLazyColumn` of `Chip`s (one per
server / library) with a `PositionIndicator`, `LoadingScreen` while loading, `ErrorScreen` with
retry on failure. `ChooseLibraryScreen` calls `IPlexLoginRepo.chooseLibrary()` directly via
`Injector` rather than through `ChooseLibraryViewModel` — it mirrors how the phone-era
`ChooseLibraryFragment` did it, since `ChooseLibraryViewModel` never wrapped that call either.

**`LibraryScreen`** — the audiobook list. A `ScalingLazyColumn` whose first item is always a
`NowPlayingChip` (when something is playing/paused), followed by one `BookRow` `Chip` per
audiobook, or an empty-state `Text` if the library has no books yet. `LibraryViewModel`'s `Factory`
is built directly from `Injector` (all its dependencies are `AppComponent`-scoped singletons, so
there's no Activity-scoped dependency to justify going through `ActivityComponent`).

**`BookDetailsScreen`** (route `book_details/{bookId}`) — cover art (`AsyncImage`/Coil, clipped to
a circle), title/author/progress `Text`, a row of three icon `Button`s (play/pause, download
toggle, mark watched/unwatched), a "Now playing" `Chip` shortcut, and the chapter list as `Chip`
rows via the shared `ChapterRow` component. Defers creating `AudiobookDetailsViewModel` until
`IBookRepository.getAudiobook(bookId)` resolves a non-null `Audiobook` (showing `LoadingScreen`
until then), and uses `viewModel(key = bookId, factory = ...)` so navigating from one book to
another gets a fresh ViewModel rather than the previous book's cached instance.

**`NowPlayingScreen`** — the transport-controls screen: title/chapter/progress `Text`, a
play/pause + skip-forward/back `Button` row, a previous/next-chapter `Button` row, and
`CompactChip`s to `playback_speed` and `sleep_timer`. `CurrentlyPlayingViewModel` here is
Activity-scoped (passed the host `ComponentActivity` as `viewModelStoreOwner`) so this screen and
`NowPlayingChip` share the exact same ViewModel instance. This is also the one screen where rotary
input is wired to system volume rather than list scroll — see
[`docs/architecture/wear-platform.md`](../architecture/wear-platform.md).

**`PlaybackSpeedScreen`** — replaces the phone's `ModalBottomSheetSpeedChooser` bottom sheet with a
Wear Compose `Picker` offering 0.1x-stepped speeds across
`CurrentlyPlayingViewModel.PLAYBACK_SPEED_MIN`..`PLAYBACK_SPEED_MAX`; selecting an option calls
`setPlaybackSpeed()` immediately (no separate confirm step).

**`SleepTimerScreen`** — a `Picker` over a fixed list of preset minute values
(5/15/30/40/60/90/120) with a "Start" `Button`, or — when a timer is already running — a countdown
`Text` and a cancel `Button`. Uses `CurrentlyPlayingViewModel.beginSleepTimer()`/
`cancelSleepTimer()`, added in this conversion; the older `showSleepTimerOptions()`/
`BottomChooserState` path on the same ViewModel is left in place but unused by this screen.

**`SettingsScreen`** — a fixed, hand-picked set of rows rendered directly against typed `LiveData`
on the rewritten `SettingsViewModel` (see below), rather than the phone's ~40-row generic
preference-list-plus-RecyclerView. Rows: a `NowPlayingChip`; `ToggleChip`s for offline mode,
skip-silent-audio, auto-rewind, and pause-on-focus-lost; `Chip`s for jump-forward/backward interval
(opens `OptionsDialog`), refresh rate (opens `OptionsDialog`), delete downloaded files, and log
out; and a plain version/about `Text` row.

## Shared components

- **`NowPlayingChip`** renders on `LibraryScreen`, `BookDetailsScreen`, and `SettingsScreen` (not
  everywhere) so Now Playing is reachable without swiping all the way back to the navigation root.
  This is a deliberate, documented gap versus the phone app's everywhere-persistent mini-player,
  not an oversight.
- **`OptionsDialog`** (plus the `FormattableString`/`BottomChooserState`/`BottomChooserListener`
  types it renders) replaces the phone's View-based `BottomSheetChooser` as a Compose `Dialog`. The
  types moved here unchanged from the deleted `views/BottomSheetChooser.kt` — every ViewModel that
  needs a "pick one of these options" or confirmation prompt (Library, BookDetails,
  CurrentlyPlaying, Settings) already emitted this same contract before the conversion; only the
  rendering changed.
- **`ErrorScreen`** and **`LoadingScreen`** are the two generic states almost every data-driven
  screen renders while its ViewModel/repository call is in flight or has failed.

## The `SettingsViewModel` rewrite

`features/settings/SettingsViewModel.kt` was rewritten (not just trimmed) from roughly 1,000 lines
building a generic ~40-row preference list (much of it for cut features — premium, book-cover
style, sync location, Android Auto, subreddit/GitHub/licenses links, the debug-info Easter egg) to
about 330 lines exposing the surviving preferences individually as typed `LiveData`, matching what
`SettingsScreen` reads directly. The `navigator` dependency and its one call site
(`showAccountList()`, for a screen that no longer exists) were removed along with `Navigator`
itself — Compose screens navigate via `NavHostController` lambdas passed down from
`ChronicleWearApp`; ViewModels do not navigate.

## Cut screens (not carried forward)

Collections browsing, a dedicated search screen, the Home "recently listened"/"recently added"
rails (folded conceptually into Library, with no dedicated rail UI), the multi-account browsing
list and library-selector bottom sheet, the sort/view-style matrix, the sync-location picker, the
debug-info dialog, Play Billing/premium, and the OSS licenses screen. See `PLAN.md` section 1.2 and
[`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) for the full cut list and rationale.
