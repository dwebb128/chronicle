# Plex Sign-In: the plex.tv/link Flow

This documents how the Wear OS app (`:wear`) signs in. The phone app signs in through a Chrome
Custom Tabs browser redirect (with a manual plex.tv/link fallback); a watch has no usable browser,
so the watch app uses Plex's plex.tv/link short-code flow instead: the watch displays a short
human-typeable code, the user enters that code at `https://plex.tv/link` on any other device with a
browser, and the watch polls until that code resolves to an auth token.

See [`docs/features/wear-ui.md`](wear-ui.md) for how `LinkAccountScreen` fits into the overall
screen set, and [`docs/features/login.md`](login.md) for the user/server/library selection state
machine this flow feeds into unchanged.

## Strong vs. non-strong PINs

[`PlexService.kt`](../../core/src/main/java/local/oss/chronicle/data/sources/plex/PlexService.kt)
(the Retrofit interface, `PlexLoginService` at the injection point) creates PINs with:

```kotlin
@POST("https://plex.tv/api/v2/pins.json")
suspend fun postAuthPin(@Query("strong") strong: Boolean = true): OAuthResponse
```

A `strong` PIN has a long code (25 characters, expires in 30 minutes) for the app.plex.tv/auth
browser flow; a non-strong PIN has the 4-character code plex.tv/link accepts (expires in 15
minutes), as verified against the live API. Both are polled via `GET /api/v2/pins/{id}.json`. The
phone app uses a strong PIN for its browser sign-in and a non-strong one for its manual
plex.tv/link fallback; the watch app's `PlexLinkAuthCoordinator` always asks for a non-strong one.

## `PlexLinkAuthCoordinator` state machine

[`PlexLinkAuthCoordinator`](../../wear/src/main/java/local/oss/chronicle/features/auth/PlexLinkAuthCoordinator.kt)
is a small, self-contained coroutine-based state machine (constructor-injected with
`IPlexLoginRepo`, `PlexLoginService`, `PlexPrefsRepo`, and a `CoroutineScope`) exposing
`state: StateFlow<PlexLinkAuthState>`. States, defined in
[`PlexLinkAuthState.kt`](../../wear/src/main/java/local/oss/chronicle/features/auth/PlexLinkAuthState.kt):

```
Idle → CreatingPin → WaitingForUser(pinId, pinCode) → Polling(pinId, elapsedMs)
                                                          ↓
                                    Success | Error(message) | Timeout | Cancelled
```

- **`startAuth()`** calls `postAuthPin(strong = false)`, stashes the returned PIN's `id` into
  `PlexPrefsRepo.oAuthTempId` (mirroring the side effect `IPlexLoginRepo.postOAuthPin()` would have
  performed for the strong-PIN flow, so the existing, unmodified
  `IPlexLoginRepo.checkForOAuthAccessToken()` polls the right PIN), transitions to
  `WaitingForUser`, and starts polling.
- **Polling** runs every 1.5 seconds (`POLLING_INTERVAL_MS`), calling
  `IPlexLoginRepo.checkForOAuthAccessToken()` and checking whether `IPlexLoginRepo.loginEvent` has
  advanced past `NOT_LOGGED_IN`/`AWAITING_LOGIN_RESULTS`/`FAILED_TO_LOG_IN` (i.e. the underlying
  login state machine — user, then server, then library — has actually started resolving).
  Transient exceptions during a single poll are logged and swallowed rather than failing the whole
  flow; only the timeout or a successful resolution ends the loop.
- **`cancelAuth()`** and **`reset()`** both cancel the polling job; `reset()` additionally returns
  to `Idle` so the same coordinator instance can be reused for a retry, and `dispose()` (called
  from `LoginViewModel.onCleared()`) cancels the job without touching the terminal state, so the
  last state remains observable briefly after teardown.

## The 5-minute timeout

`TIMEOUT_MS = 300_000L` (5 minutes), compared with the phone app's ~2-minute Chrome Custom Tabs
timeout.
Reasoning: the phone flow's 2-minute window assumed a browser redirect completing in seconds: the
watch flow instead requires the user to read a code off a small screen, physically go to another
device, open a browser, navigate to plex.tv/link, and type the code in — all before the PIN
expires. Five minutes was chosen as a more realistic budget for that sequence, not derived from any
Plex-side PIN expiry constant.

## Retry / timeout UI

`LinkAccountScreen` (see [`docs/features/wear-ui.md`](wear-ui.md)) renders every state, not just
the happy path:

| `PlexLinkAuthState` | UI |
|---|---|
| `Idle`, `CreatingPin` | `LoadingScreen` |
| `WaitingForUser`, `Polling` | The short code, plus a cancel `Button` |
| `Success` | `LoadingScreen` (login-state-driven navigation takes over from here) |
| `Timeout` | `ErrorScreen("Sign-in timed out.")` with a "Try again" `Button` |
| `Error` | `ErrorScreen(state.message)` with a "Try again" `Button` |
| `Cancelled` | `ErrorScreen("Sign-in cancelled.")` with a "Try again" `Button` |

The screen keeps the display on (`LocalView.current.keepScreenOn`) for as long as the state is
non-terminal, since the code has to stay legible long enough to type into another device — Wear OS
would otherwise blank the screen aggressively.

## Recommended follow-up: QR code login

A QR code (shown on the watch, scanned by a phone's camera to complete the link) would be
meaningfully better UX than typing a short code by hand, and is the recommended next improvement
to this flow. It isn't implemented; it needs a QR-encoding dependency.
