# Plex Sign-In: the plex.tv/link Flow

This documents the authentication flow used on Wear OS, which replaces the phone app's Chrome
Custom Tabs browser-redirect OAuth flow. A watch has no browser of its own, so Chronicle now uses
Plex's plex.tv/link short-code flow instead: the watch displays a short human-typeable code, the
user enters that code at `https://plex.tv/link` on any other device with a browser, and the watch
polls until that code resolves to an auth token.

See [`docs/features/wear-ui.md`](wear-ui.md) for how `LinkAccountScreen` fits into the overall
screen set, and [`docs/features/login.md`](login.md) (STALE — phone-era) for the pre-existing
user/server/library selection state machine this flow feeds into unchanged.

## Plain PIN vs. the old "strong" browser PIN

[`PlexService.kt`](../../app/src/main/java/local/oss/chronicle/data/sources/plex/PlexService.kt)
(the low-level Retrofit interface, named `PlexLoginService` at the injection point) now exposes
two PIN-creation endpoints:

```kotlin
@POST("https://plex.tv/api/v2/pins.json?strong=true")
suspend fun postAuthPin(): OAuthResponse   // pre-existing: strong PIN, embedded in a browser-redirect URL

@POST("https://plex.tv/api/v2/pins.json")
suspend fun postLinkPin(): OAuthResponse   // new: plain PIN, short human-typeable code for plex.tv/link
```

Both return the same `OAuthResponse(id, clientIdentifier, code, authToken?)` shape and are polled
identically via `GET /api/v2/pins/{id}.json`. `postAuthPin()`/`strong=true` is kept exactly as it
was — it's still used by `IPlexLoginRepo.postOAuthPin()` for the wider account/server/user/library
state machine. `postLinkPin()` is new, and only `PlexAuthCoordinator` calls it.

> **This assumption is unverified against the live Plex API.** The plain (non-`strong`) PIN
> variant is assumed, based on Plex's public plex.tv/link documentation, to return a short code
> (on the order of 4 characters) suitable for reading off a watch face and typing elsewhere, as
> opposed to the long opaque string a `strong` PIN returns for embedding in a URL. This has not
> been exercised against a running Plex server. **Verifying this against the real API is the
> single highest-value thing to check before shipping this flow** — if the "plain" PIN's code
> turns out not to be meaningfully shorter/more typeable than the strong one, this flow doesn't
> achieve its purpose and needs rethinking.

## `PlexAuthCoordinator` state machine

[`PlexAuthCoordinator`](../../app/src/main/java/local/oss/chronicle/features/auth/PlexAuthCoordinator.kt)
is a small, self-contained coroutine-based state machine (constructor-injected with
`IPlexLoginRepo`, `PlexLoginService`, `PlexPrefsRepo`, and a `CoroutineScope`) exposing
`state: StateFlow<PlexAuthState>`. States, defined in
[`PlexAuthState.kt`](../../app/src/main/java/local/oss/chronicle/features/auth/PlexAuthState.kt):

```
Idle → CreatingPin → WaitingForUser(pinId, pinCode) → Polling(pinId, elapsedMs)
                                                          ↓
                                    Success | Error(message) | Timeout | Cancelled
```

- **`startAuth()`** calls `postLinkPin()`, stashes the returned PIN's `id` into
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

`TIMEOUT_MS = 300_000L` (5 minutes), up from the phone app's ~2-minute Chrome Custom Tabs timeout.
Reasoning: the phone flow's 2-minute window assumed a browser redirect completing in seconds: the
watch flow instead requires the user to read a code off a small screen, physically go to another
device, open a browser, navigate to plex.tv/link, and type the code in — all before the PIN
expires. Five minutes was chosen as a more realistic budget for that sequence, not derived from any
Plex-side PIN expiry constant.

## Retry / timeout UI

`LinkAccountScreen` (see [`docs/features/wear-ui.md`](wear-ui.md)) renders every state, not just
the happy path:

| `PlexAuthState` | UI |
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

## Known integration issue: a method-name mismatch

While documenting this flow, a real (not merely stylistic) discrepancy was found between two files
built in different waves of this conversion:

- [`LoginViewModel`](../../app/src/main/java/local/oss/chronicle/features/login/LoginViewModel.kt)
  exposes `startLinkAccountAuth()`, `cancelAuth()`, `resetAuth()`, and `authState`.
- [`LinkAccountScreen`](../../app/src/main/java/local/oss/chronicle/ui/screens/LinkAccountScreen.kt)
  calls `viewModel.startChromeCustomTabsAuth()` — a method that does not exist on `LoginViewModel`
  as written.

This is a real cross-file naming mismatch, most likely because the UI screen was written against
an earlier/assumed method name for what became `startLinkAccountAuth()`. Since nothing in this
repository can currently be compiled, this was not caught by a build. **This needs a one-line fix
in `LinkAccountScreen.kt`** (rename the call to `startLinkAccountAuth()`) before this flow can
compile, let alone run. It is called out here rather than fixed directly because
`ui/screens/**` is outside this documentation pass's file ownership — see the wave report for
details.

## Recommended follow-up: QR code login

A QR code (shown on the watch, scanned by a phone's camera to complete the link) would be
meaningfully better UX than typing a short code by hand, and is the recommended next improvement
to this flow. It was not implemented here because it requires a QR-encoding dependency that could
not be evaluated or version-pinned with any confidence in an environment where no dependency can
be resolved or compiled against. Tracked in [`todo.md`](../../todo.md).
