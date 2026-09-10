# Contributing Wear OS support upstream

This branch adds Wear OS support to Chronicle on top of a `:core`/`:wear`/`:mobile` module
split. This document is a plan for getting that work — or the useful parts of it — merged into
the project this fork actually descends from, rather than a description of work already done.
Nothing below has been executed; it is a proposal to review before spending time on it.

## 1. Which repo is "upstream" — this fork's real lineage

This repo's own branding is the tell: the app is called **Chronicle Epilogue**, uses the
applicationId `local.oss.chronicle`, has its own website (chronicleapp.net), its own Play Store
submission in progress, and its own alpha/beta Google Groups. `README.md` sends bug reports to
[`germann/chronicle`](https://github.com/germann/chronicle), not to the original project. Compare
that against [`mattttvaughn/chronicle`](https://github.com/mattttvaughn/chronicle) — the original,
inspected directly for this plan — which uses applicationId `io.github.mattpvaughn.chronicle`, is
branded plainly "Chronicle Audiobook Player," is a single `app` module (no `:core` split), sits at
`minSdk 27` / `versionCode 27` / `versionName 0.55.0` against a `develop` default branch, and whose
most recent visible commit is a from-scratch "Massive refactor to support modern APIs and
Android 14."

That's two different, actively-diverging projects. The fork chain is:

```
mattttvaughn/chronicle  (original; GPLv3; own recent Android-14 refactor)
        │
        ▼
germann/chronicle        ("Chronicle Epilogue" — branding, applicationId, and Play listing
        │                  all trace to here, per this repo's own README)
        ▼
dwebb128/chronicle        (this fork — adds Wear OS, restructures into :core/:wear/:mobile)
```

**"Upstream" for this plan means `germann/chronicle`** — it's the immediate parent, the one this
fork's own docs point contributors and bug reporters at, and the one whose file layout, package
name, and conventions this branch's code actually shares. `mattttvaughn/chronicle` is upstream-of-
upstream: worth being aware of (its Android 14 refactor may be worth pulling into `germann/chronicle`
independently of anything here), but not the right target for *this* patch — this branch's diff
would not apply there; the two projects have diverged in package name, module layout, and Android
API baseline.

**Caveat, stated plainly:** this session could read `mattttvaughn/chronicle` (public, no access
restriction) but was refused access to `germann/chronicle` — it isn't in this session's repository
scope and cross-owner attachment isn't available once `dwebb128/chronicle` is already attached.
Everything below about `germann/chronicle` is therefore inferred from what this fork's own README
and `AGENT.md` say about it, not verified against its actual current code, activity, or contribution
norms. **Step 0 below exists to close that gap before any code work starts.**

## 2. Step 0: confirm the target and gauge interest — before writing a diff

Two things need to happen first, and neither is a code change:

1. **Get read access to `germann/chronicle` in a session that can attach it** (a fresh session with
   only that repo as the source, or an org that permits cross-owner attach) and confirm the basics
   this plan is currently guessing at:
   - Is it still active? What's its current default branch, module layout, `minSdk`/`compileSdk`,
     and how far has it diverged from the commit this fork branched from?
   - Does it have a `CONTRIBUTING.md`, issue templates, or a stated review process?
   - Is anyone already working on Wear OS support, or has it been discussed and rejected?

2. **Open an issue or discussion on `germann/chronicle` proposing Wear OS support before sending a
   PR.** This is a large, invasive change — a new module, a shared-code extraction that touches
   nearly every existing file, a different UI toolkit (Compose for Wear) alongside the existing
   Fragments/DataBinding phone app. A solo or small-team maintainer is far more likely to engage
   with "would you want this, and does this general shape work for you?" than with a 537-file PR
   arriving unannounced. Worth linking this fork's live branch as a working reference implementation
   rather than asking them to review it as a patch.

Do not proceed past this point without a maintainer signal. Everything in sections 3–6 assumes that
signal was positive; if it isn't, the honest fallback is that this fork continues on its own as an
independent "Chronicle Epilogue + Wear OS" project rather than a contribution.

## 3. Why the current branch can't go upstream as-is

Whatever `germann/chronicle` turns out to look like, this branch has three problems that exist
independent of its exact state:

- **It's not just Wear OS support.** `main` in this fork carries commits with no Wear connection at
  all — a Play Billing Library migration, login-screen UX fixes, an ANR fix, a version bump — mixed
  in with the Wear work through normal development. An upstream PR needs to isolate only what's
  relevant; the rest either doesn't apply (if `germann/chronicle` already has it) or is a separate,
  smaller PR of its own.
- **It carries this fork's identity.** Branding strings ("Chronicle Epilogue"), the
  `local.oss.chronicle` applicationId, links to chronicleapp.net and this fork's own Google Groups,
  and the `ASSETS-LICENSE`-covered logo/icon assets are all specific to being a downstream fork.
  None of that belongs in a PR to the project it's forked *from* — upstream is not "a fork of
  itself."
- **It's a single enormous diff.** The live PR touches 537 files. Even with a receptive maintainer,
  a change that size is nearly unreviewable in one piece and very likely to go stale waiting on
  feedback. Section 4 breaks it into a sequence that can each be reviewed and merged on their own.

## 4. Proposed sequence

Four stages, each mergeable independently, each building on the last. The ordering is deliberate:
earlier stages are smaller, lower-risk, and demonstrate good-faith contribution *before* asking a
maintainer to accept the large, opinionated addition (a whole new UI toolkit) in stage 3.

### Stage 1 — Extract `:core` as a pure refactor (no behavior change)

The single biggest enabler in this branch is the `:core` module: pulling the ~100 files common to
any Chronicle client (Plex API client, Room databases, the playback service, the DI boundary) out of
the app module and behind a `CoreComponent` interface that `Injector` holds rather than reading a
concrete `Application` subclass. This is valuable *on its own*, before Wear OS even enters the
picture — it's the thing that makes a second UI surface (watch, tablet, whatever) possible without
duplicating the data/playback layer, and it's reviewable purely as "does the phone app still work
identically," since it changes no behavior.

Submit this first, scoped to `germann/chronicle`'s existing phone-only structure: `:core` +
`:app`/`:mobile` (whatever they currently call it), zero user-visible change, full test parity.
This is the PR most likely to get a quick "yes" — it's mechanical, it's testable by "nothing looks
different," and it sets up everything after it.

### Stage 2 — Generic fixes found and fixed in this session, independent of Wear

Several fixes made while building the watch app are real correctness/security issues in the shared
data layer, not Wear-specific:

- **Cross-device progress sync was silently broken**, and a naive fix for it introduced a second
  silent-data-loss failure mode (clock skew between devices reverting progress to near-zero) —
  both now fixed and covered by tests in `MediaItemTrack.merge`.
- **Plex's `lastViewedAt` (seconds) was being compared against locally-written timestamps
  (milliseconds)** with no conversion, a thousand-fold unit mismatch that made several merge/
  rewind calculations silently wrong (`Audiobook.merge`, `getActiveTrack()`, auto-rewind duration).
- **A cached track file could be marked "downloaded" while only partially written** (interrupted
  Fetch download), which surfaced as a stuck seek or a source error during playback with no
  indication the download was the cause.
- **The "book finished" auto-detection fired on every one-second tick** for the entire final two
  minutes of a book, calling the Plex API and resetting progress every second instead of once, with
  no sanity bound on the duration it was computing against.
- **Cleartext HTTP was permitted to any host**, not just the local-network/`.plex.direct` hosts that
  legitimately need it; **auth tokens were stored in plain `SharedPreferences`** instead of the
  Keystore-backed `CredentialManager`; **download-cancel broadcasts were implicit**, receivable by
  any app on the device.

These apply to `germann/chronicle`'s phone app exactly as written, need no Wear-specific context to
review, and are the kind of contribution that builds trust ahead of the larger ask in stage 3. Submit
as one or two focused PRs (security fixes together; the progress-sync fixes together, since they
touch the same function and tell one story).

### Stage 3 — The `:wear` module itself

The actual feature: a new, additive module depending on `:core` (built in stage 1), using Compose
for Wear OS — `ScalingLazyColumn`-based screens sized for a small round display, rotary-crown input,
the plex.tv/link sign-in flow (no browser needed on a watch), and a standalone playback service so
the watch needs no paired phone once signed in. Framed correctly, this stage touches no existing
phone code at all — everything it needs from `:core` already exists after stage 1.

This is the stage most likely to draw real design discussion (a maintainer may have opinions on nav
structure, on what phone features have no watch equivalent and should be dropped rather than
squeezed in, on Compose-for-Wear vs. some other approach) — which is exactly why stages 1 and 2 go
first: smaller wins first, then the conversation that actually needs back-and-forth.

### Stage 4 — Docs

`INSTALLING.md`/`EMULATOR-UBUNTU.md`-equivalent updates for `germann/chronicle`'s actual doc
structure and conventions (which this plan hasn't seen), scoped to whichever of stages 1–3 they
support. Small, low-risk, goes in alongside whichever code stage it documents rather than as its own
PR.

## 5. Technical prep checklist (do this once `germann/chronicle` access exists)

For each stage above, before opening the PR:

- [ ] Rebase the relevant slice onto `germann/chronicle`'s actual current default branch — not this
      fork's `main`, which has already diverged with unrelated commits (see §3) and, per §1, may
      itself be behind `germann/chronicle`'s tip by now.
- [ ] Strip every Chronicle-Epilogue-specific string, link, and asset: `local.oss.chronicle` →
      whatever `germann/chronicle` uses, "Chronicle Epilogue" branding → theirs, chronicleapp.net /
      this fork's Google Groups links → removed, `ASSETS-LICENSE`-covered logo/icon assets →
      excluded entirely (that license is explicitly "All Rights Reserved," separate from the GPLv3
      code, and not this fork's to redistribute upstream).
  - [ ] Package rename (`local.oss.chronicle` → the target package) is mechanical but touches
        every file; do it as a dedicated commit within each stage's PR, separate from the
        functional diff, so a reviewer can skip straight past it.
- [ ] Re-run `ktlintCheck`/`ktlintFormat` against whatever ktlint config `germann/chronicle` ships
      (this fork inherited its ktlint setup and git pre-commit hook from upstream conventions, per
      `mattttvaughn/chronicle`'s own `README.md`, so it's likely close, but confirm rather than
      assume).
- [ ] Confirm CI compatibility — `germann/chronicle` may run different Gradle/AGP/JDK versions than
      this fork's `compileSdk 36`/JDK 17; match theirs rather than forcing an unrelated toolchain
      bump into a feature PR.
- [ ] Full test suite green against their structure, not just this fork's `:core`/`:wear`/`:mobile`
      split — module names and package paths will differ once rebased.

## 6. Licensing

Both this fork and `mattttvaughn/chronicle` are GPLv3 — license-compatible with contributing code
back, assuming `germann/chronicle` is the same (verify in step 0; nothing here should be assumed).
The one explicit exception is `ASSETS-LICENSE`: the Chronicle Epilogue logo and branding graphics are
"All Rights Reserved" and must not be included in any upstream PR — use placeholder or upstream's own
existing assets for anything the Wear UI needs (app icon, splash) instead.

## 7. Open risks, stated plainly

- **`germann/chronicle`'s actual current state is unverified.** This entire plan is built on what
  this fork's own docs say about its parent, not on having read the parent. It could have moved
  further than expected, could already have Wear work in progress, could have different priorities
  entirely, or could be inactive. Step 0 exists specifically to find this out before any of the later
  stages are worth starting.
- **A maintainer may reject the Compose-for-Wear approach**, or want a different module boundary
  than `:core`/`:wear`/`:mobile`, or prefer the watch app be a client of the phone app (a Wear
  *companion*, not standalone) — which this fork deliberately did not build, since "standalone,
  no phone required" was this fork's own design goal, not necessarily upstream's. Don't assume stage
  3 will be accepted as-is even if stages 1–2 land cleanly.
- **This fork's `main` is not the right branch to rebase from without checking first** — it already
  contains post-fork-point changes unrelated to Wear (billing, login UX, an ANR fix) that need to be
  separated from what actually goes upstream, not carried along by default.
