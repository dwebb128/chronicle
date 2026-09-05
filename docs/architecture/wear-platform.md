# Wear OS Platform Notes

This document covers the Wear OS–specific platform concerns introduced when Chronicle was
converted from a phone app to a standalone Wear OS app. It complements
[`docs/architecture/layers.md`](layers.md) (which covers the general layered architecture) and
[`docs/features/wear-ui.md`](../features/wear-ui.md) (which covers the screens themselves).

Target device class: Wear OS 6 (API 36), validated conceptually against Pixel Watch 4.
`compileSdk`/`targetSdk` are 36; `minSdk` is 34 (Wear OS 5) — see
[`app/build.gradle.kts`](../../app/build.gradle.kts).

> As with the rest of this conversion, none of this could be compiled or run on real Wear OS
> hardware while it was written (no Android SDK, Google Maven blocked). Treat API names and
> version pins here as reviewed-but-unverified until a real build exists.

## Single Activity + `SwipeDismissableNavHost`

There is exactly one Activity, [`MainActivity`](../../app/src/main/java/local/oss/chronicle/application/MainActivity.kt),
a thin `ComponentActivity` that builds the Dagger `ActivityComponent`, injects itself, and hands
the entire UI to Compose via `setContent { ChronicleWearApp(...) }`. All of the phone app's
Fragment/bottom-nav/draggable-mini-player/`GestureDetector`/`OnBackPressedCallback` machinery is
gone.

[`ui/ChronicleWearApp.kt`](../../app/src/main/java/local/oss/chronicle/ui/ChronicleWearApp.kt) is
the root composable: one `Scaffold` (Wear Compose Material, with `timeText` and a `Vignette`)
wrapping an `androidx.wear.compose.navigation.SwipeDismissableNavHost`. Back navigation is the
watch's native swipe-to-dismiss gesture — there is no back-stack management to reimplement.

Login-state-driven navigation (advancing automatically from link → choose user → choose server →
choose library → library as `IPlexLoginRepo.loginEvent` progresses) is wired directly in
`ChronicleWearApp` via a `LaunchedEffect` observing that event, using
`popUpTo(navController.graph.id) { inclusive = true }` on each transition so the back gesture
never returns to a login step the user has already completed. See
[`docs/features/wear-ui.md`](../features/wear-ui.md) for the full route table.

## Rotary input (crown / bezel)

The crown scrolls, on every screen. Nothing in this app wires rotary input by hand: since
wear-compose-foundation 1.4 `ScalingLazyColumn`'s `rotaryScrollableBehavior` parameter defaults to
`RotaryScrollableDefaults.behavior(state)`, so a `ScalingLazyColumn` given a `ScalingLazyListState`
already handles the crown — with fling and haptics — and already requests the focus that rotary
events are delivered to. A hand-rolled `Modifier.onRotaryScrollEvent` on top of that is not just
redundant, it fights the built-in one for focus.

`NowPlayingScreen` used to spend the crown on media volume instead, on the grounds that it had no
scrolling list. It has one now — its controls are far taller than a 41mm display — so the crown
scrolls there too, and volume lives on the on-screen `InlineSlider`.

## `ScalingLazyColumn` / `PositionIndicator`

All list-shaped screens use `androidx.wear.compose.foundation.lazy.ScalingLazyColumn` (note the
package — it is in `compose-foundation`, not `compose-material`) paired with
`androidx.wear.compose.material.PositionIndicator` rendered alongside it inside a `Box`. This is
the standard Wear Compose list pattern: items shrink/fade near the top and bottom of the viewport,
and the position indicator gives the user a persistent sense of where they are in the list.

## Ongoing Activity on the playback notification

`androidx.wear.wear-ongoing`'s `OngoingActivity` surfaces the current playback state as a
system-level "ongoing activity" indicator (the small icon Wear OS shows for things like an active
workout or timer) driven off the same notification the phone app already built.

The one non-obvious constraint: `OngoingActivity.apply(context)` writes its extras into the
`NotificationCompat.Builder` itself, so it must be called **before** that builder's `.build()` —
calling it after `.build()`/`notify()`/`startForeground()` is a silent no-op (no crash, nothing
logged, the Ongoing Activity indicator just never appears). In
[`NotificationBuilder.kt`](../../app/src/main/java/local/oss/chronicle/features/player/NotificationBuilder.kt),
`OngoingActivity.Builder(...).build().apply(context)` is threaded into
`buildNotificationInternal()` immediately before the builder's own `.build()` call, for exactly
this reason.

## `AudioOutputMonitor` and the Bluetooth prompt

[`AudioOutputMonitor`](../../app/src/main/java/local/oss/chronicle/features/player/AudioOutputMonitor.kt)
is a hand-rolled (no Horologist dependency), `@ServiceScope`-injected class that watches
`AudioManager.getDevices(GET_DEVICES_OUTPUTS)` via `registerAudioDeviceCallback`, and exposes
`hasBluetoothAudio: StateFlow<Boolean>` — true when a Bluetooth-family output (A2DP, a BLE
headset/speaker, or a hearing aid) is currently attached. `MediaPlayerService` registers it in
`onCreate()` and unregisters it in `onDestroy()`.

This exists because playing an audiobook out loud through a watch's built-in speaker is a much
worse default than it is on a phone. **As built, the monitor's `hasBluetoothAudio` state is not
yet consumed anywhere in the UI** — no screen currently renders a "no headphones connected"
prompt. The plumbing to detect the condition is in place and registered with the service's
lifecycle; wiring a non-blocking banner into `NowPlayingScreen` (or similar) to actually surface it
to the user is outstanding work, not a design decision to leave it silent. It must stay
non-blocking when built — this is advisory, not a hard gate on speaker playback.

## `WAKE_MODE_NETWORK`

`AndroidManifest.xml` declares `android.permission.WAKE_LOCK`, and
`MediaPlayerService`'s ExoPlayer setup calls `exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK)` so
network-backed playback can keep the CPU (and, per ExoPlayer's `WAKE_MODE_NETWORK` semantics, WiFi)
awake during streaming even if the screen turns off — important on a watch, where the screen turns
off far more aggressively than on a phone.

## Watch storage constraints and the download free-space guard

Wear OS devices have materially less storage than a phone, so two things changed in the download
path:

- **Fetch is capped to one concurrent download** (`AppModule.kt`'s
  `Fetch.Builder(...).setDownloadConcurrentLimit(1)`), rather than downloading multiple audiobooks'
  tracks in parallel.
- **A free-space guard runs before a download starts.**
  [`CachedFileManager`](../../app/src/main/java/local/oss/chronicle/data/sources/plex/CachedFileManager.kt)'s
  `hasEnoughSpaceForBook()` sums the size of every not-yet-cached track for the book and compares
  it against `PrefsRepo.cachedMediaDir.bytesAvailable()` (a `StatFs`-backed helper in
  `util/StorageUtils.kt` that existed before this conversion but was never wired to anything).
  If the book wouldn't fit, the download is refused with a "Not enough storage space" `Toast`
  rather than starting and failing partway through.
- Separately, `SharedPreferencesPrefsRepo`'s use of `externalDeviceDirs().first()` (which throws
  `NoSuchElementException` if `getExternalFilesDirs()` returns an empty array — plausible on a
  watch) was changed to `firstOrNull() ?: context.filesDir`, so a watch with no external storage
  volume falls back to internal app storage instead of crashing at startup.

## Retained TTS error announcements (`VoiceCommandBridgeAudio`)

Despite this being an Android-Auto-flavored class by history,
[`VoiceCommandBridgeAudio`](../../app/src/main/java/local/oss/chronicle/features/player/VoiceCommandBridgeAudio.kt)
was kept **entirely unchanged** in this conversion. It is constructor-injected into
`AudiobookMediaSessionCallback` and field-injected into `MediaPlayerService`, and it drives
spoken/TTS announcements of playback errors (login failures, network errors, playback failures,
and so on) through the same `MediaSessionCompat`/`AudiobookMediaSessionCallback` error paths the
phone app used. The two docs that previously described it
(`docs/architecture/voice-command-error-handling.md`,
`docs/architecture/voice-command-latency-analysis.md`) were deleted because they framed it purely
as Android Auto latency plumbing, which no longer applies — but the underlying behavior itself is
genuinely useful on a watch used with headphones or a hearing aid, where a glance at the screen
isn't always convenient, so it was deliberately kept rather than removed along with the rest of the
Android Auto surface. Voice search (`onSearch`, `MEDIA_PLAY_FROM_SEARCH`) is likewise kept.

## Explicitly out of scope

The following are deliberate exclusions from this conversion, not oversights:

- **Ambient / always-on display.** No ambient-mode callback, no low-bit/burn-in-safe rendering.
  Mitigated partially by the fact that the kept `MediaSessionCompat` already gives the watch's
  system media controls (and, on supporting watch faces, a glanceable media indicator) without the
  app's own UI needing to be on screen.
- **Tiles and complications.** There is no Tile (e.g. a quick "resume audiobook" swipe-in panel)
  and no complication (a watch-face element showing/resuming current playback). Close to table
  stakes for a modern Wear OS media app, but 100% new, unverifiable-without-a-build surface, so
  deferred rather than guessed at.

Both are tracked as follow-ups in [`todo.md`](../../todo.md).
