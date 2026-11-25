# Wear OS Platform Notes

The `:wear` module is a standalone Wear OS app: once signed in it needs no paired phone. It sits on
the same `:core` library as the phone app (`:app`) and adds only its own UI and wiring. This
document covers the watch-specific platform concerns; [`wear-ui.md`](../features/wear-ui.md) covers
the screens and [`plex-link-login.md`](../features/plex-link-login.md) the sign-in flow.

`minSdk` is 34 (Wear OS 5), `targetSdk` 36, `compileSdk` 37 — see
[`wear/build.gradle.kts`](../../wear/build.gradle.kts).

## How `:wear` plugs into `:core`

Like `:app`, the watch app has its own `ChronicleApplication` and Dagger `AppComponent`, which
extends `CoreComponent`. Its `Application` installs that component into `Injector` and, before the
playback service can start, installs a watch [`PlaybackProfile`](#playback-tuning-playbackprofile).
`:core` stays unaware of which app it is running in.

Two `:core` seams are filled differently from the phone:

- **`BitmapLoader`** (notification artwork) is backed by Coil
  ([`CoilBitmapLoader`](../../wear/src/main/java/local/oss/chronicle/util/CoilBitmapLoader.kt))
  instead of Fresco. Compose loads cover art through the same Coil `ImageLoader`, which is built
  on the media OkHttp client so image requests carry the Plex token.
- **`@AppVersionName`** is the watch app's own `versionName`.

The playback service, its media-button receiver and their permissions are declared in the watch
app's own manifest, as the phone app declares them in its manifest.

## Single Activity + `SwipeDismissableNavHost`

There is exactly one Activity,
[`MainActivity`](../../wear/src/main/java/local/oss/chronicle/application/MainActivity.kt), a thin
`ComponentActivity` that builds the Dagger `ActivityComponent`, injects itself, and hands the UI to
Compose via `setContent { ChronicleWearApp(...) }`. There are no Fragments and no Data Binding.

[`ui/ChronicleWearApp.kt`](../../wear/src/main/java/local/oss/chronicle/ui/ChronicleWearApp.kt) is
the root composable: one `Scaffold` (Wear Compose Material, with `timeText` and a `Vignette`)
wrapping an `androidx.wear.compose.navigation.SwipeDismissableNavHost`. Back navigation is the
watch's native swipe-to-dismiss gesture.

Login-state-driven navigation (link → choose user → choose server → choose library → library, as
`IPlexLoginRepo.loginEvent` progresses) is a `LaunchedEffect` in `ChronicleWearApp` observing that
event, using `popUpTo(navController.graph.id) { inclusive = true }` on each transition so the back
gesture never returns to a completed login step.

## Rotary input (crown / bezel)

The crown scrolls, on every screen. Nothing wires rotary input by hand: `ScalingLazyColumn`'s
`rotaryScrollableBehavior` defaults to `RotaryScrollableDefaults.behavior(state)`, so a
`ScalingLazyColumn` given a `ScalingLazyListState` already handles the crown, with fling and
haptics, and requests the focus rotary events are delivered to. A hand-rolled
`Modifier.onRotaryScrollEvent` on top would fight it for focus. Media volume on the now-playing
screen is an on-screen `InlineSlider`.

## `ScalingLazyColumn` / `PositionIndicator`

All list-shaped screens use `androidx.wear.compose.foundation.lazy.ScalingLazyColumn` (in
`compose-foundation`, not `compose-material`) with a
`androidx.wear.compose.material.PositionIndicator` alongside it in a `Box`.

## Playback tuning (`PlaybackProfile`)

[`WatchPlaybackProfile.kt`](../../wear/src/main/java/local/oss/chronicle/features/player/WatchPlaybackProfile.kt)
sets up the shared `MediaPlayerService` for a watch:

- **Small buffers with a byte cap.** The phone's two-minute back buffer and six-minute forward
  buffer risk `OutOfMemoryError` on a watch. `DefaultLoadControl` requires both "buffer for
  playback" values to be no larger than the minimum buffer, so they're set explicitly.
- **`C.WAKE_MODE_NETWORK`**, so streaming keeps going while the screen is off. The manifest
  declares `WAKE_LOCK` for it.
- **An Ongoing Activity** on the now-playing notification, so playback shows on the watch face.
  `OngoingActivity.apply(context)` writes its extras into the `NotificationCompat.Builder`, so it
  must run before `.build()`; applied to a built notification it silently does nothing. The
  profile's notification hook is called at exactly that point.

The phone app never installs a profile and runs with `PlaybackProfile.DEFAULT`, which is its
existing configuration (a unit test pins this).

## `AudioOutputMonitor` and the Bluetooth prompt

[`AudioOutputMonitor`](../../wear/src/main/java/local/oss/chronicle/features/player/AudioOutputMonitor.kt)
watches `AudioManager` output devices via `registerAudioDeviceCallback` and exposes
`hasBluetoothAudio: StateFlow<Boolean>`: true when a Bluetooth-family output (A2DP, a BLE
headset/speaker, or a hearing aid) is attached. The now-playing screen registers it while it's
showing and, when no such output is attached, shows a chip that opens the system Bluetooth
settings. Playback through the watch speaker still works; this is advice, not a gate.

## Downloads

Fetch is capped to one concurrent download in the watch app's `AppModule` (the phone allows
three), since watches have less bandwidth and storage. Book details shows per-book download
progress from `ICachedFileManager.bookDownloadProgress`.

## Not in this module

- **Android Auto** browsing lives in the shared service and behaves as it does for the phone app;
  the watch UI has no Auto settings.
- **Ambient / always-on display**, **Tiles** and **complications** aren't implemented. The
  `MediaSessionCompat` already gives the watch's system media controls without the app's own UI
  on screen.
