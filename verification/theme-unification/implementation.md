# Theme, transfer presentation and Android execution (2026-11-07)

The starting ShutUp commit was `d2aee9f9062517b5e0ac219392771dc3cee3d8b9`
on `main`, with a clean worktree. Goldy started at
`28d66246c410ebb7ab95f983a3a11de044ccb1b8` on `master`.
Start snapshots and protected source hashes are under
`build/theme-unification-20261107/` and `build/update-icon-20261107/` in the
respective repositories. ShutUp is intentionally uncommitted.

## Theme

Previously destination-specific wrappers applied Pyrite colors only to Settings
and About in light mode; Update retained a separate ShutUp ColorScheme. Transition
content could therefore change colors with the destination. `ShutterTheme` now
owns one published PyriteTheme (Pyrite `43557c8`) above every destination, dialog
and both transition contents. Pyrite supplies ColorScheme, typography, shapes and
motion. Home inherits the same theme; functional status colors are unchanged.
The unused old typography file and destination theme/local color exceptions were
removed. No Pyrite source was changed.

One `LocalUpdateCardStyle` provides `surfaceContainerLowest` in light mode and
`surfaceContainerHigh` in dark mode, with Pyrite's large shape. Elief's banner,
notes, history and inline actions consume it; the retained-install-request host
card now consumes the same style. Docked action areas retain Elief's surface
canvas semantics. Dialogs inherit the parent scheme. PyriteScreenTransition's
animation and navigation direction are retained.

## Published icon and progress

Goldy commits `32ec799d85f76f21954d4f487e691e9ddaa0bd02` and
`847d098cd1ddff7cc740d8189c25a1f854e23b28` add the exact browser_updated vector
geometry shared by ShutUp/Scheduler, attribution, and a locale-aware decimal
progress formatter. `elief_refresh.xml` and Gold core security sources were not
changed. The new final SHA is the sole public ShutUp dependency. Three icon
overrides and the unreferenced host vector were removed only after retrieving
and checking the new public AAR and passing independent public consumers.

The formatter uses decimal 1000-based units and two decimals for KB/MB and
percentages; unknown/nonpositive totals have no percentage and an indeterminate
bar. Incomplete transfers cannot round to 100.00%. JVM tests cover English,
Japanese, German, Long limits, negative values, overshoot and unknown totals.

## Permission and installer state

The host rechecks Gold's actual PackageManager-backed permission status at
Activity resume and before settings/prepare/launch. Resume (rather than start)
also covers returning from Settings while both activities remain visible in
multi-window mode. Missing permission exposes
one clearly named Settings action. Returning granted never reopens Settings or
launches an installer automatically. An unacquired APK offers Download; a
verified receipt or prepared durable request offers Install. An unpersisted
receipt lost to process death requires downloading and validating again.

The ledger preserves the candidate snapshot, permission intent, selected durable
request and claimed/handed-off IDs. Metadata cannot reconstruct VerifiedApk or
an install lease. An ambiguous prior handoff offers only the existing explicit
reopen confirmation. A claim is persisted before invoking the installer. URI
leases are released only through the existing explicit confirmation; download
completion and Activity/settings return never release them or imply install
success. Ready is published after transfer finalization so its first action is
actually accepted. Cancel finishes cancellation and file cleanup before enabling
a fresh download; stale notification tickets cannot cancel a later operation.

## Background execution

`UpdateDownloadViewModel` is now a facade over application state. Android owns
the actual transfer coroutine: API34+ uses a user-initiated JobScheduler job;
API28–33 uses a unique expedited WorkManager job with a dataSync foreground
service. Only explicit user operations schedule work. Notifications expose
progress, opening the update screen and cancellation, never installation.
The JobService is safe to load at minSdk28 and guards its API34 entry point.

Gold's public ApkDownloader, ApkInstaller and full byte/digest/package/version/
signature validation remain the only pipeline. Completion prepares a durable
request and shows Ready, even while backgrounded; it never starts an installer.
An OS interruption can be rescheduled while the process survives. After process
death, an interrupted running transfer is restored as paused with an explicit
full-restart action. Force-stop and OS restrictions can stop execution; continual
execution while closed is not promised.

Android's choice of UIDT for user-started long transfers and its foreground
WorkManager fallback are documented in the official guidance:
[UIDT](https://developer.android.com/develop/background-work/background-tasks/uidt),
[transfer options](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options),
[long-running workers and Android16 quotas](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

### Phase 3: durable Range resume

The subsequent Phase 3 implementation adds durable Gold checkpoints and strict
HTTP Range/If-Range handling, while retaining this theme/icon/permission/Android
execution architecture. Valid partials survive interruption; explicit cancel
discards only the selected checkpoint. Completion always performs whole-file
hashing and every existing APK/security check. Paused UI and notifications use
the saved cumulative byte count with the existing Elief formatter.

See [Phase 3 implementation](../phase3/implementation.md) and its results/report
for the exact public SHA, artifact/Consumer gates, process-resume evidence and
remaining verification limits. The results.json in this theme-unification
directory remains the historical result of the preceding phase.

## Reproduction

Use JDK21 and the installed Android SDK. Normal final builds use public JitPack
only; no included build or local Maven repository is in settings.gradle.kts.
`build-with-local-goldy.ps1` exists only for explicit development and restores
the exact original settings bytes in finally; it is not used for final results.

Build `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
:app:minifyReleaseWithR8 :app:lintDebug :app:lintRelease`. Production release
packaging still requires the real existing signing setup; no key was obtained
or copied and release signing was not replaced with debug signing.

Run `run-ui-matrix.ps1` for normal, narrow (320dp/font1.8) and activities-dark.
Run `run-transfer-ux.ps1` phases integration, permission, background, interruption
and leases against an explicitly selected disposable emulator. Permission,
interruption and lease stages must run in order, with force-stop between stages.
Do not run against a physical phone. Wait for APK generation to finish before
testing; the transfer runner snapshots and hashes both APKs before installation.

Final measured outcomes and remaining device coverage are recorded in results.json
and report.md alongside this file. Raw failures are retained under build, including
the original cancellation/Ready races, a build/install overlap, and the Lint API
annotation issue; they are not counted as passing results.
