# Contributing Wear OS support upstream

This fork adds Wear OS support to Chronicle on top of a `:core`/`:wear`/`:mobile` module split
(PR #1, branch `claude/chronicle-android-to-watch-uuur6z`). This document is the plan for getting
that work — or the useful parts of it — merged into the project this fork descends from. PR #1
stays as the working reference implementation; the upstream-ready slices live on separate
`upstream/*` branches described in §4.

Nothing has been posted to any third-party repository. The draft issue and PR descriptions in
§8–§9 are for the maintainer of this fork to post when they choose to.

## 1. Which repo is "upstream"

**Upstream is [`fabiogermann/chronicle`](https://github.com/fabiogermann/chronicle), default
branch `main`.** The GitHub API reports `dwebb128/chronicle` as a fork whose parent is
`fabiogermann/chronicle` and whose source (root) is `mattttvaughn/chronicle`:

```
mattttvaughn/chronicle   (original; GPLv3; applicationId io.github.mattpvaughn.chronicle)
        │
        ▼
fabiogermann/chronicle   ("Chronicle Epilogue": the branding, applicationId local.oss.chronicle,
        │                  chronicleapp.net, the Play listing and alpha/beta Google Groups,
        │                  ASSETS-LICENSE — all of it is this project's own)
        ▼
dwebb128/chronicle       (this fork — adds Wear OS, restructures into :core/:wear/:mobile)
```

`fabiogermann/chronicle` is public and can be read anonymously
(`git fetch https://github.com/fabiogermann/chronicle main`). The README's bug-report link
(`github.com/germann/chronicle/issues`) is upstream's own text, not a pointer to a different
project. `mattttvaughn/chronicle` is upstream-of-upstream and not the target here: it has a
different applicationId, package, and module layout, and this work would not apply there.

What upstream looks like (as of `d7ef399`, 2026-10):

- a single `:app` module — the phone app, Fragments + Data Binding (kapt), Dagger, Room (KSP);
- still uses **Fresco** for images and **Play Billing** (`billing-ktx`);
- Gradle 9.8.0, AGP 9.3.3, Kotlin 2.4.20, Kotlin/daemon JVM toolchain pinned to JDK 17,
  `compileSdk 37`, `minSdk 30`, `targetSdk 36`, version 0.62.5 (68);
- OkHttp 5 and kotlin-result 2.x;
- a `CONTRIBUTING.md`: comment on the corresponding issue before working on it, include tests,
  keep `ktlintCheck` green (a `pre-commit` hook runs it), keep code GPLv3-compatible, and don't
  modify files listed in `ASSETS-LICENSE` without permission;
- CI (`.github/workflows/ci.yml`) runs `./gradlew testDebugUnitTest` then
  `./gradlew assembleDebug` on JDK 17, on pushes/PRs to `main`, `feat/*`, `fix/*`, `feature/*`.

## 2. Step 0: gauge interest before sending a large PR

Upstream's `CONTRIBUTING.md` asks contributors to comment on the corresponding issue before
working on something, so the first step is an issue, not a PR. A Wear OS app is a large,
opinionated addition — a new module, a shared-code extraction that moves most existing files, and
a second UI toolkit (Compose for Wear) next to the Fragments/Data Binding phone app. A
maintainer is far more likely to engage with "would you want this, and does this shape work for
you?" than with a several-hundred-file PR arriving unannounced.

The draft issue is in §8. It links PR #1 as a working reference implementation and the
`upstream/*` branches as the proposed reviewable sequence.

The two fix branches (§4, stages 1–2) don't depend on that answer: they fix real bugs in
upstream's existing phone app and can be offered on their own whatever the maintainer thinks of
Wear OS. The extraction and Wear branches (stages 3–4) should wait for a positive signal. If the
answer is no, this fork carries on as "Chronicle Epilogue + Wear OS" downstream, and the fix
branches still go up.

## 3. Why PR #1 can't go upstream as-is

- **It's based on a fork that is behind upstream.** This fork's `main` is exactly upstream's
  `main` minus 4 newer commits (0 ahead / 4 behind; merge-base `7ad5491`) — it has no fork-only
  commits of its own. Those 4 upstream commits are a toolchain and library upgrade (Gradle 9.8.0
  / AGP 9.3.3 / Kotlin 2.4.20; OkHttp 5, kotlin-result 2.x, Fresco 3.9, `onNewIntent`; JDK 17
  toolchain pins). PR #1 was written against the older toolchain, so every slice has to be
  re-verified against upstream's current one.
- **Its `:mobile` module is not a pure refactor of upstream's `:app`.** While restoring the phone
  app next to the watch app, PR #1 also modernised it: Coil instead of Fresco, Play Billing
  removed, plex.tv/link login. Upstream still ships Fresco and billing. Those are product
  decisions for upstream's maintainer, not something to slip in under a module move, so the
  `:core` extraction is redone from scratch against upstream rather than cherry-picked.
- **It mixes generic fixes with the Wear feature.** Several data-layer and security fixes found
  while building the watch app apply to the phone app unchanged and are easier to review alone.
- **It's one enormous diff.** Section 4 breaks it into four branches that can each be reviewed
  and merged on their own.
- **It carries fork-specific framing.** The only fork-specific identity PR #1 added is wording:
  the README's "Unofficial fork … adds Wear OS support" blockquote and the "unofficial fork"
  description in `AGENT.md`. None of that goes on an upstream branch. Everything else that looks
  like branding — "Chronicle Epilogue", `local.oss.chronicle`, chronicleapp.net, the Google
  Groups, the `ASSETS-LICENSE` assets — is upstream's own and stays exactly as upstream has it.

## 4. The branches

Four branches in `dwebb128/chronicle`, all based on upstream `main` at `d7ef399`. Stages 1–3 are
each based directly on that commit; stage 4 is stacked on stage 3. Smaller, standalone fixes go
first; the extraction and the Wear module follow once the maintainer has said they want them.

| Stage | Branch | Base | Commits |
| --- | --- | --- | --- |
| 1 | `upstream/fixes-progress-sync` | `d7ef399` | 3 |
| 2 | `upstream/fixes-security` | `d7ef399` | 3 |
| 3 | `upstream/core-extraction` | `d7ef399` | 3 |
| 4 | `upstream/wear-app` | stage 3 | 3 on top of stage 3 |

### Upstream's own baseline is not green

Verified by building untouched upstream `d7ef399` with its own toolchain (Gradle 9.8.0, AGP 9.3.3,
Kotlin 2.4.20, JDK 17 toolchain, compileSdk 37):

- `assembleDebug` passes.
- `testDebugUnitTest`: 688 tests, **8 fail** — all of `CredentialManagerTest`, with
  `KeyStoreException: AndroidKeyStore not found` (Robolectric has no AndroidKeyStore).
- `ktlintCheck` (what the `pre-commit` hook runs) **fails with ~730 violations** across all source
  sets, apparently from rules added by the ktlint upgrade in `fd22366`.

None of the branches fix those; that would be its own PR. "Green" for each branch below therefore
means: `assembleDebug` passes for every module, the only test failures are those same 8, and there
are **no ktlint violations on any line the branch adds or changes**.

### Stage 1 — `upstream/fixes-progress-sync`

Progress-sync and download-integrity fixes in upstream's single `:app` module. No module changes.
708 tests (688 + 20 new), 8 baseline failures only.

- **Cross-device progress never synced.** `MediaItemTrack.merge` always kept the local
  `progress`/`lastViewedAt`, so a position set on one device never reached another. Now
  last-writer-wins on `lastViewedAt`, with a guard against adopting a "newer" network position
  that throws away most of a substantial local one (clock drift).
- **Plex seconds vs. local milliseconds.** Plex's `lastViewedAt` is seconds; everything local
  writes milliseconds. `PlexDirectory.lastViewedAtMillis` converts at the wire boundary, which also
  corrects `Audiobook.merge`, `getActiveTrack()`, and auto-rewind duration.
- **Partially-downloaded files trusted as complete.** `refreshTrackDownloadedStatus` and
  `getTrackSource` now check the file against the size Plex reported and stream instead when it's
  short or missing. (New tests; PR #1 had none for this.)
- **"Book finished" fired on every tick** for the last two minutes, with no sanity bound on the
  computed duration. Now it requires being past halfway too, and fires once per book per session
  (a concurrent set, since `updateProgress` runs on `Dispatchers.IO`). The new tests wait for the
  background tick; the file's existing `never()` assertions don't, so they pass vacuously.

### Stage 2 — `upstream/fixes-security`

697 tests (688 + 9 new), 8 baseline failures only.

- The Plex token is attached to a cleartext request only if the host is loopback, RFC1918,
  link-local or `*.plex.direct` — in both `PlexInterceptor` and `ScopedPlexServiceFactory`'s
  interceptor (PR #1 only covered the first). The host comes from OkHttp's `HttpUrl`: PR #1's
  hand-rolled parser could be fooled by `http://evil.example/?a=@10.0.0.1`. Plus a
  `network_security_config.xml` that documents why the declarative config can't express this.
- Plex auth tokens move from plain `SharedPreferences` into `CredentialManager`'s Keystore-backed
  store, with a one-time migration that wipes the plaintext copy.
- Download-cancel broadcasts are scoped to the app's own package.

### Stage 3 — `upstream/core-extraction`

A pure refactor: `:core` library module extracted from `:app`, redone against upstream rather than
cherry-picked from PR #1. The phone app keeps its module name (`:app`), Fresco, billing, Data
Binding and everything else. Three commits: the seams, made in place in `:app` (builds and passes
on its own); the move; docs/CI paths.

The seams `:core` needed, all behaviour-neutral: a `CoreComponent` interface that `AppComponent`
extends and `Injector` returns (installed lazily from `ChronicleApplication`'s `init`, preserving
when the component is created); `LaunchFlags` for the notification intent constants (aliased from
`MainActivity`); `@AppVersionName`, since a library's `BuildConfig` has no `VERSION_NAME` and the
release workflow bumps it in `app/build.gradle.kts`; and a `BitmapLoader` interface, implemented by
`FrescoBitmapLoader`, so `:core` depends on no image library. **No Coil, and no image loader at all,
on `CoreComponent`.**

Verified beyond the tests (688 tests, 52 in `:app` and 636 in `:core`, 8 baseline failures): the
phone APK's merged manifest, resource names and every resource value are identical to upstream's,
and its dex gains only new classes (none removed). `assembleRelease` (R8) builds and the androidTest
sources compile.

### Stage 4 — `upstream/wear-app`

On top of stage 3. Adds no code to `:app`: the phone APK's manifest and resources are identical to
stage 3's, and it gains only the `PlaybackProfile` classes. 714 tests (52 + 638 + 24), 8 baseline
failures only; release builds of both apps and the androidTest sources compile.

- One commit of additive `:core` hooks: `postLinkPin()`, `ICachedFileManager.bookDownloadProgress`
  (per-book download progress, which PR #1 had mixed into a data-layer fix commit), and
  `PlaybackProfile`. That last one replaces PR #1's direct edits to the shared player for the watch
  (smaller buffers, network wake mode, Ongoing Activity). Its default is the phone's existing
  configuration, pinned by a test; the watch installs its own.
- The `:wear` module, ported to Kotlin 2.4.20 / Compose 1.12.1 / Wear Compose 1.7.1, kotlin-result
  2.x and upstream's DI. Watch-only types (`AudioOutputMonitor`, the link-login state machine as
  `PlexLinkAuthCoordinator`, `FormattableString`) live in `:wear`, not `:core`. The Coil
  `ImageLoader` stays in `:wear`'s own component.
- Docs: `INSTALLING.md`, `EMULATOR-UBUNTU.md`, `wear-platform.md`, `wear-ui.md`,
  `plex-link-login.md`, a README feature line, an `AGENT.md` module entry.

### They combine

A trial merge of all four (stage 4, then 1, then 2) needs only trivial resolution: git moves the
fix branches' new test files into `core/` itself, and one import line conflicts in
`PlexInterceptor`. The result builds all three APKs and runs 743 tests with only the 8 baseline
failures.

## 5. Left out, and why

- **Storage free-space guard** (`CachedFileManager.hasEnoughSpaceForBook`) and the
  `externalDeviceDirs().firstOrNull()` fallback: PR #1 added them for the watch, but they change
  shared download/prefs behaviour for the phone too, so they don't belong in a Wear branch that
  claims to change nothing for the phone. They're reasonable small fixes on their own.
- **PR #1's other edits to the shared player** — the Android Auto browse tree flattened,
  `PackageValidator` replaced by an own-uid/system-uid check, Auto-disable checks removed, and
  "Open Chronicle to finish setup" wording. They would change the phone. The watch runs the
  shared service as the phone does; Auto browsing there behaves as it does for the phone app.
- **`LibrarySyncRepository` syncing all libraries on refresh** (PR #1's "empty library on Wear"
  fix): it changes phone sync behaviour. The watch UI still syncs through the existing paths.
  Worth checking on a real watch whether the empty-library symptom recurs.
- **`:mobile`'s modernisation** (Coil instead of Fresco, billing removed, plex.tv/link login for
  the phone): product decisions for the maintainer, not part of any branch.
- **Wear Play listing and Play Publisher config**: publishing is the maintainer's call. The watch
  module has the same `versionCode` as `:app` (68). Play needs distinct version codes per artifact
  in one release, so the maintainer will need a scheme (e.g. an offset) before shipping both.
- **Wear's `networkSecurityConfig`**: the security branch adds `network_security_config.xml` to
  `:app`; whichever of stages 2 and 4 merges second should give `:wear` the same attribute (the
  token gate itself is in `:core`, so it already covers the watch).
- **Wear androidTest sources**: PR #1's copied the phone's database migration tests; dropped.
- **Upstream's red baseline** (ktlint, `CredentialManagerTest`): pre-existing, out of scope.

## 6. Before opening each PR

- [ ] Open the issue in §8 first and wait for a signal (stages 3–4 especially).
- [ ] Re-fetch upstream `main`. If it has moved, merge it into the branch and re-run
      `./gradlew assembleDebug testDebugUnitTest ktlintCheck`. If stage 3 goes badly stale, it's
      simpler to redo the extraction (the commit message lists every seam) than to resolve a
      large conflicting move.
- [ ] Optionally rename to upstream's branch convention: upstream CI triggers on pushes to `feat/*`,
      `fix/*`, `feature/*` and `main`, and on PRs to `main`. A PR to `main` runs CI whatever the
      branch is called.
- [ ] Open stage 4 as a PR that names stage 3 as its dependency, or after stage 3 merges.

## 7. Licensing

Upstream and this fork are both GPLv3; contributing back is license-compatible. The
`ASSETS-LICENSE` assets (logo, launcher icons, promotional graphics) are upstream's own, All Rights
Reserved by upstream, and `CONTRIBUTING.md` asks contributors not to modify them. No branch modifies
them. The Wear module contains byte-identical copies of `:app`'s launcher icons (`mipmap-*`) and no
other branding asset (PR #1's copies of `example_cover.png`, the Play listing graphics and
`play_store_512.png` were dropped). The PR should ask the maintainer whether they want
`ASSETS-LICENSE` extended to `wear/src/main/res/mipmap-*/`, or different watch artwork.

## 8. Draft issue (Step 0)

To be posted on `fabiogermann/chronicle` by this fork's maintainer.

> **Title:** Proposal: Wear OS app (standalone), plus a `:core` module split to support it
>
> Hi! I've been building a standalone Wear OS version of Chronicle Epilogue: browse your Plex
> library, download, and play on the watch, with no phone needed once it's signed in. I'd like to
> contribute it, but it's a big change, so I'm asking before sending PRs.
>
> **What it looks like:** Compose for Wear OS; sign-in via the plex.tv/link short code (a watch has
> no browser for the OAuth redirect); library with sort & filter, book details with download
> progress, now playing (crown scrolling, on-screen volume, a nudge when no Bluetooth headphones
> are connected), playback speed, sleep timer, settings. Media notification shows as an Ongoing
> Activity on the watch face.
>
> **How it would fit the codebase:** the non-UI layer (Plex client, Room, repositories, downloads,
> playback service) moves from `:app` into a new `:core` library, and `:wear` sits on top of it
> next to `:app`. I've prepared this as two separate PRs:
>
> 1. **`:core` extraction** — a pure refactor. `:app` keeps Fresco, billing, Data Binding and
>    everything else; I've checked that the phone APK's manifest and resources come out identical.
> 2. **`:wear` module** — additive; no changes to `:app`.
>
> Branches, based on current `main` (`d7ef399`):
> https://github.com/dwebb128/chronicle/tree/upstream/core-extraction and
> https://github.com/dwebb128/chronicle/tree/upstream/wear-app
>
> Separately, while building it I found some bugs in the shared code that affect the phone app
> today, and I'll send those as two small standalone PRs whatever you decide about Wear OS:
> cross-device progress sync (it never syncs, partly a seconds-vs-milliseconds mix-up with Plex
> timestamps), partially downloaded files being played as complete, the "book finished" check
> firing every second near the end, and three security issues (Plex token over cleartext to
> non-local hosts, tokens stored in plain SharedPreferences, implicit download-cancel broadcasts).
>
> Questions for you:
> - Would you want Wear OS support in this repo at all?
> - Is a `:core`/`:app`/`:wear` split acceptable, or would you prefer a different shape?
> - Standalone watch app (current design) vs. a phone companion?
> - The watch reuses the existing launcher icons. OK, or would you like different watch artwork?
>
> Also, FYI: on current `main` with the new toolchain, `ktlintCheck` reports ~730 violations and the
> 8 `CredentialManagerTest` tests fail under Robolectric (`AndroidKeyStore not found`).
> `assembleDebug` is fine.

## 9. Draft PR descriptions

Upstream has no PR template; these follow the repo's commit style. Each PR's branch is listed in §4.

### PR 1 — `upstream/fixes-progress-sync`

> **Title:** Fix cross-device progress sync and partially-downloaded tracks
>
> ## Summary
> - **Progress now syncs between devices.** `MediaItemTrack.merge` always kept the local
>   `progress`/`lastViewedAt`, so a position reached on one device never reached another. It's now
>   last-writer-wins on `lastViewedAt`. The in-flight-playback race the old comment guarded against
>   is still covered: `ProgressUpdater` stamps the local row every second while playing, so a sync
>   landing mid-playback finds local newer. A guard also refuses a "newer" network position that
>   would erase more than half of at least a minute of local progress (clock skew); "force sync from
>   server" is unaffected.
> - **Plex timestamps are seconds, ours are milliseconds.** The comparison above couldn't have
>   worked before: any locally-touched row compared as 1000× newer. `PlexDirectory.lastViewedAtMillis`
>   converts at the wire boundary. This also fixes `Audiobook.merge`, `getActiveTrack()` and
>   auto-rewind, which was always applying the maximum rewind for Plex-sourced books. Existing rows
>   correct themselves on the next sync.
> - **Partially downloaded files aren't played as complete.** `refreshTrackDownloadedStatus` and
>   `getTrackSource()` check the file against the size Plex reported and stream when it's short or
>   missing. Symptom before: playback starts, then a chapter seek stalls with no error.
> - **"Book finished" fires once, and only near the real end.** It ran every second for the last two
>   minutes (each call hits Plex and resets progress) with no bound on the computed duration, so a
>   too-small duration could mark a book watched mid-listen. Now it also requires being past
>   halfway, and fires once per book per session.
>
> ## Behaviour change to note
> `merge` previously treated the local device as the source of truth for progress, deliberately.
> This PR changes that; the reasoning is above and in the commit message.
>
> ## Testing
> `./gradlew assembleDebug testDebugUnitTest`: 708 tests, 20 new (merge rules, the unit conversion,
> the completeness checks, the finished-book bounds). The only failures are the 8
> `CredentialManagerTest` tests that also fail on `main` (`AndroidKeyStore not found` under
> Robolectric). No new ktlint violations on changed lines. Note: `ProgressUpdater.updateProgress`
> works on `Dispatchers.IO`, so the new tests wait for that work before asserting; the file's
> existing `never()` assertions don't, and pass regardless.

### PR 2 — `upstream/fixes-security`

> **Title:** Don't leak the Plex token over cleartext; encrypt stored tokens
>
> ## Summary
> - **Cleartext token gate.** `usesCleartextTraffic` is needed for LAN servers over plain HTTP, but
>   it meant the `X-Plex-Token` header was attached to plain-HTTP requests to *any* host. Both OkHttp
>   interceptors that add it (`PlexInterceptor`, `ScopedPlexServiceFactory`) now only do so over HTTPS
>   or to loopback/RFC1918/link-local/`*.plex.direct` hosts, using OkHttp's own URL parser.
>   `network_security_config.xml` is added to document why the declarative config can't express
>   this (no IP ranges); it permits exactly what `usesCleartextTraffic` already did.
> - **Encrypted token storage.** The account token and server access token move from plain
>   `SharedPreferences` to `CredentialManager` (Keystore-backed). Existing users stay signed in: a
>   plaintext token from an older version is migrated on first read and then deleted.
> - **Scoped broadcasts.** Download-cancel notification actions call `setPackage`, so other apps
>   can't receive them.
>
> ## User-visible effect
> A server reached over plain HTTP at a **public** address or hostname no longer receives the token,
> so it will fail auth until it's reached over HTTPS or a `plex.direct` address. That's the intent,
> but worth a release note.
>
> ## Not covered
> The ExoPlayer data source and `PlexConfig`'s helpers add the token through their own paths; the
> user token inside the serialized `PlexUser` and the `Library.authToken` Room column are still
> plaintext at rest (the latter needs a migration).
>
> ## Testing
> 697 tests, 9 new (gate cases including URL-parsing tricks; token migration). Only the 8
> `CredentialManagerTest` failures that also fail on `main`. No new ktlint violations on changed
> lines; androidTest sources compile.

### PR 3 — `upstream/core-extraction`

> **Title:** Extract a `:core` library module (pure refactor)
>
> Prerequisite for the Wear OS app proposed in #<issue>, but useful on its own: it separates the
> Plex/data/playback layer from the phone UI.
>
> ## Summary
> Moves everything that isn't phone UI into a new `:core` Android library: the Plex client and
> models, Room databases and repositories, accounts/credentials, downloads, the playback service and
> `ServiceComponent`. Packages are unchanged, `:app` keeps its name and depends on `:core`, and
> nothing about the phone app's behaviour changes. Fresco, billing, Data Binding and the UI all stay
> in `:app`.
>
> Three commits, best reviewed one at a time:
> 1. **Seams, in place in `:app`.** Builds and passes on its own. `CoreComponent` (what shared code
>    reaches through `Injector`; `AppComponent` extends it), a lazily-installed `Injector`,
>    `LaunchFlags` (notification intent constants, aliased from `MainActivity`), `@AppVersionName`
>    (the app's `VERSION_NAME`, which a library's `BuildConfig` lacks), a `BitmapLoader` interface
>    implemented with the same Fresco calls (so `:core` doesn't depend on an image library), and a
>    few one-line decouplings.
> 2. **The move.** Renames, plus `core/build.gradle.kts` (same toolchain as `:app`), resources used by
>    moved code moved to `core/src/main/res`, Room schemas, and the tests for moved classes.
> 3. **Docs/CI paths.**
>
> ## How I checked "no behaviour change"
> Compared with the APK from `main`: merged manifest identical, set of resource names identical,
> every resource value identical, and the dex only gains classes (the new seams, `:core`'s
> `R`/`BuildConfig`, compiler-generated SAM wrappers). `assembleRelease` with R8 builds; the
> androidTest sources compile.
>
> ## Testing
> The same 688 tests run (52 in `:app`, 636 in `:core`); the only failures are the 8
> `CredentialManagerTest` ones that fail on `main`. No new ktlint violations on changed lines.

### PR 4 — `upstream/wear-app`

> **Title:** Add a standalone Wear OS app (`:wear`)
>
> Depends on #<PR 3>. As discussed in #<issue>.
>
> ## Summary
> A Wear OS app on top of `:core`, built with Compose for Wear OS. Once signed in it needs no
> phone. No code in `:app` changes; the phone APK's manifest and resources are identical with and
> without this PR.
>
> - **Sign-in** with the plex.tv/link short code; it then feeds the existing user/server/library
>   selection.
> - **Screens:** library (with sort & filter), book details (with download progress), now playing
>   (crown scrolling, on-screen volume, a prompt when no Bluetooth audio is connected), playback
>   speed, sleep timer, settings. `SwipeDismissableNavHost` + `ScalingLazyColumn`.
> - **Playback** uses the shared service with a watch `PlaybackProfile`: small buffers and a 2 MB
>   byte cap (the phone's 6-minute buffer risks OOM on a watch), network wake mode for screen-off
>   streaming, and an Ongoing Activity on the media notification.
>
> ## Changes to `:core` (one commit, additive)
> `PlexLoginService.postLinkPin()`; `ICachedFileManager.bookDownloadProgress`; `PlaybackProfile`,
> whose `DEFAULT` is the phone's existing player configuration (pinned by a unit test). The phone
> never installs another profile.
>
> ## For you to decide
> - The watch uses byte-identical copies of `:app`'s launcher icons, and nothing else from
>   `ASSETS-LICENSE`. Should `ASSETS-LICENSE` list `wear/src/main/res/mipmap-*/`, or would you prefer
>   other artwork?
> - Same `applicationId` as the phone app (one store listing). `versionCode` currently matches
>   `:app`'s (68); Play needs them to differ within a release.
> - No Play listing or Play Publisher config for `:wear` is included.
>
> ## Testing
> `./gradlew assembleDebug testDebugUnitTest`: 714 tests (52 `:app`, 638 `:core`, 24 `:wear`); only
> the 8 `CredentialManagerTest` failures from `main`. `assembleRelease` builds both apps. `:wear` is
> ktlint-clean (with `.editorconfig` allowing PascalCase `@Composable` functions under `wear/` only).
> These branches were built and unit-tested, not run on a watch or emulator: <say here how you've
> tested it on a device before posting>.
