# ShutUp home screen design QA

The initial design and later revisions are recorded below. The latest revision is the integrated hero action and neutral card palette at the end of this report; it supersedes the initial two-button layout and subsequent red hero palette.

## Target and evidence

- Selected visual: option 1, revised only to add the requested actual FOR_SYSTEM value below the large state heading.
- Source: `app/build/home-design/selected.png` (853 × 1844 px; design viewport 390 × 844 dp).
- Implementation: native Jetpack Compose `HomeScreen`, running on emulator-5580. No web prototype or physical-device audio changes were involved.
- Emulator: 1080 × 2538 px, density 443 dpi. App-owned comparison crop removes 132 px above and 66 px below; normalized width 390 px, resulting height 845 px. The one-pixel height difference is density rounding. Android system bars are excluded, not recreated in app content.
- Matched state: Japanese, light theme, Shizuku ready, current FOR_SYSTEM=11, device default=11, idle, usage notes collapsed. Test fixture states are isolated from the audio backend.
- Final screenshot: `app/build/home-design/final-ja/enforced.png`.
- Full comparison: `app/build/home-design/comparison-latest.png`.
- Focused heading/numeric-label comparison: `app/build/home-design/comparison-state-latest.png`.
- Additional captures: `home-ja-dark.png`, `home-en-enforced.png`, `home-en-released.png`, `home-large-enforced.png`, `home-large-released.png` in the same folder.
- Actual cold-start screen, without fixture data: `app/build/home-design/home-live.png`. Shizuku is not running in this emulator; the actual screen correctly shows an unknown value and the Shizuku action.

## Comparison history

1. Initial comparison (`comparison-first.png`) found [P2] state/action typography smaller than the target, insufficient top breathing room, and a missing green connection-state cue. Adjusted heading/action sizes, top spacing, and connected-state colors. The second comparison confirmed the fixes.
2. Second comparison (`comparison-final.png`) found [P2] the lower edge of the usage disclosure slightly exceeding the target viewport. Reduced status-row vertical padding from 12 dp to 8 dp. The verified comparison showed all normal-size Japanese controls within the viewport.
3. Additional state comparison found [P2] a device-default-specific note used the platform default while the rest of the fixture used the supplied default. The UI now derives that note from the same default snapshot as its status and restore action. Added an assertion that releasing an enforced-default device does not show the already-default note. Fresh final English and large-font captures confirm that the incorrect note is absent.
4. Final comparison (`comparison-latest.png` and `comparison-state-latest.png`) confirms the required composition, large state heading, small actual numeric value, two action buttons, grouped status, restart note, and collapsible usage notes. No actionable P0/P1/P2 findings remain.

## Required fidelity surfaces

- **Fonts/typography:** existing Android SansSerif/Roboto/Noto fallback, bold 48 sp state text, 19 sp hero label, readable 16 sp body, smaller monospace numeric label. Natural two-line English state heading and scaled body wrapping stay legible. No cropped text or overlapping controls was found.
- **Spacing/layout:** 16 dp page margins, 24 dp hero padding, 28 dp surfaces, 60/56 dp minimum action heights, grouped status rows and divider. Normal Japanese layout fits; longer English and large-font content remains vertically scrollable with the toolbar available.
- **Colors/tokens:** existing Material 3 Expressive dynamic light/dark scheme; error container for enforced and unknown states, primary container for other states, primary action, neutral supporting surfaces, distinct disabled controls, and a green connected-state cue. Shizuku not-running status uses an error-colored warning bubble and status text. Foregrounds use the corresponding on-container tokens. Contrast checked visually in light/dark captures.
- **Image/icon quality:** the hero now uses official Material Symbols Rounded Photo Camera while enforced and No Photography while released (fill 1, weight 400, optical size 48). Standard Material icons remain elsewhere; individual official vector assets avoid a large icon dependency. No custom icon approximations, placeholder assets, or device-frame artwork were introduced. License attribution is included in the existing OSS screen.
- **Copy/content:** state describes enforcement rather than guaranteed silence. FOR_SYSTEM=11, 0, and ? are distinguished. Restart behavior and full existing usage/privacy/uninstall notes remain available. Japanese and English resource keys match.

## Functional verification

- Final four Android UI tests passed (`ui-tests-final.txt`): release/restore callbacks and enabled-state transitions; Shizuku startup/permission actions; busy-state lockout; disclosure expansion/collapse.
- Final release/restore scenario also passed in English (`ui-tests-final-en.txt`) and at font_scale=1.5 (`ui-tests-final-large.txt`). Light/dark variants were captured and inspected.
- The actual main activity cold-started successfully; home-to-settings navigation was checked through the app. No app AndroidRuntime fatal exception was found.
- Seven JVM tests passed; final APK assembly and Lint passed with zero errors. Remaining warnings are unrelated to home interaction behavior.
- UI tests exercise isolated callbacks and states. Real Shizuku/audio-policy operations were not changed or exercised on a physical device during this UI task.

## Accepted minor differences

- Native dynamic-color fills replace the generated mock's faint decorative gradient/waves. The later user-requested state-specific Material Symbols and enforced error palette supersede the mock's hero icon and color. These approved changes remain sharp and readable in both themes.

## Implementation checklist

- [x] Selected layout implemented in the Android app.
- [x] Actual numeric value shown below the heading.
- [x] Ready, released, unknown, permission, and busy controls verified.
- [x] Usage disclosure and settings route verified.
- [x] Japanese/English, dark theme, and large-font captures inspected.
- [x] Post-fix visual comparison completed; no P0/P1/P2 findings remain.

final result: passed

## State icon and color update (2026-10-10)

- Enforced / FOR_SYSTEM=11: Photo Camera, errorContainer, onErrorContainer, and error icon tint.
- Released / FOR_SYSTEM=0: No Photography and the existing primary-container palette.
- Busy state retains the last known ready-state palette. A subsequent user request extends the error palette and Photo Camera symbol to unavailable/unknown state.
- Evidence: `app/build/home-symbols/preview.png`, with original screenshots under `light/` and `dark/`.
- The existing release/restore UI scenario passed in both themes (`ui-test-light.txt`, `ui-test-dark.txt`). Screenshots were inspected for correct symbols, color transitions, and readable text.
- Debug APK and Android test APK assembled successfully; Lint reported 0 errors (21 existing warnings).

## Unknown and Shizuku not-running color update (2026-10-10)

- Unknown hero state (including unavailable Shizuku, missing permission, or a null current value) now uses Photo Camera and the same error-container palette as enforced state.
- Shizuku not-running status uses errorContainer/onErrorContainer for its warning bubble and error for its detail text. The device-default status retains its normal palette.
- Evidence: `app/build/home-unknown/preview.png`, with not-running and permission screenshots under `light/` and `dark/`.
- The existing Shizuku startup/permission UI scenario passed in both themes (`ui-test-light.txt`, `ui-test-dark.txt`). The light/dark not-running captures were visually inspected for the requested colors and readable text.
- APK assembly and Lint passed; 0 errors and 21 existing warnings.

## Hero description update (2026-10-10)

- Japanese released heading is now `解除済み` (English: `Released`).
- Enforced description: `シャッター音が鳴る状態です`.
- Released description: `着信音モードに従います` (English: `Follows the ringer mode.`), updated per the user's latest wording request.
- `（デフォルト）` is appended to either stable description only when the ready current value matches the device default. The description itself follows the current enforcement state. Busy/unknown hints remain separate.
- Equivalent English descriptions and the localized default suffix are supplied.
- Evidence: `app/build/home-description/preview.png`. Japanese enforced/default and released/non-default descriptions were visually checked for the correct text and layout. Existing release/restore UI test passed (`ui-test.txt`); APK assembly and Lint passed with 0 errors and 21 existing warnings.
- The above captures predate the final released-description wording revision. That resource-only revision retains the same default-suffix behavior.

## Integrated action and neutral hero update (2026-10-10)

- Removed the separate release-effect sentence and its unused Japanese/English resources.
- Expanded the hero downward to contain a single full-width action. Enforced states offer release; released states offer restoring the device default. When release already matches the default, restoration is disabled.
- Unavailable Shizuku and missing permission retain their respective startup/permission actions inside the hero. A ready but unknown value offers refresh. Busy state disables the action and displays progress.
- Enforced/unknown heroes now use neutral surfaceContainerHigh/onSurface tokens and a neutral icon bubble. Released state keeps the primary-container palette and No Photography symbol. Shizuku not-running status retains its error cue.
- Existing release/restore, permission, busy, and disclosure UI tests were updated for the single-action layout. Added functional coverage for an already-released device default and refreshing an unknown ready state without changing audio policy.
- Evidence: `app/build/home-integrated/preview.png` contains light/dark enforced, released, and unknown states. `large-preview.png` and `english-preview.png` show font_scale=1.5 and English. Captures were visually inspected: the integrated action fits or wraps naturally inside the card; descriptions and state values remain readable; supporting sections remain scrollable.
- All six UI tests passed (`ui-tests.txt`). Dark startup/permission and release/restore scenarios passed (`ui-tests-dark.txt`); release/restore passed with large text and English (`ui-tests-large.txt`, `ui-tests-english.txt`).
- APK/test APK assembly and Lint passed, with 0 errors and 21 existing warnings. Audio backend behavior and default-value detection are unchanged. The latest revision has no outstanding actionable visual findings.
- Subsequent resource-only heading revision: enforced state now reads `鳴ります` (English: `Will play`). The integrated-layout captures above predate this wording update; the released heading remains `解除済み`.
- Subsequent toolbar spacing revision: removed the extra 20 dp top padding from the home TopAppBar, returning the app title and settings action to the standard inset position. Existing captures predate this spacing revision.

## Screen transition animation update (2026-10-10)

- Home, settings, about, and OSS-license screens now use a short horizontal slide (one fifth of the viewport width) plus fade. Forward navigation enters from the right; returning reverses the direction. Slide duration is 300 ms, with eased movement and shorter opacity transitions.
- Animated content renders its own target screen and wraps each route with the existing saveable-state holder. Scroll position, home disclosure state, and saved screen-local state survive navigation.
- A themed full-size surface and viewport clipping keep the transition within the app window; initial launch shows the current screen directly.
- Both final navigation UI tests passed (`app/build/navigation-design/ui-tests-final.txt`): the full settings/about/licenses round trip, toolbar/system back, mid-transition rendering, returning during an active transition, and reopening a screen afterward. About scroll position and home disclosure state were retained.
- Evidence: `app/build/navigation-design/preview.png`, plus `settings.png`, `licenses.png`, and `interrupted-return.png`. Mid-transition samples and settled settings screen were visually inspected.
- Debug APK/test APK assembly and Lint passed with 0 errors and 21 existing warnings. The actual main activity also cold-started successfully.

## Supplied SVG app icon (2026-10-10)

- Preserved the supplied SVG in `artwork/shutup-icon.svg`. The two foreground paths, relative proportions, and #E3E3E3 fill are unchanged; nested transforms are combined into Android vector groups.
- Separated the black background from the foreground. The adaptive icon uses the foreground alone as its monochrome layer, so themed icons retain the camera and mute silhouette.
- Uniformly scaled and centered the foreground so every path control point lies within a 32 dp radius of the 108 dp layer center, leaving a margin inside the adaptive-icon safe zone. Verified the original and converted path data match.
- The about screen uses the same foreground and black background, with the 108 dp layer cropped to the launcher's central 72 dp viewport inside its circle.
- Visually inspected circle, rounded-square, themed-color, and 48 px previews in `app/build/icon-review/adaptive-preview.png`. Themed colors in this preview are illustrative; the launcher chooses the actual themed colors.
- Installed the debug APK on the dedicated Pixel 6a emulator. Visually inspected the real launcher app drawer (`launcher.png`) and the app information screen (`about.png`): both show the new icon clearly without clipping. Debug APK assembly and `git diff --check` passed.
- Subsequent size adjustment: reduced both foreground shapes by 10% about the layer center, to a 28.8 dp radius. Their proportions and relative placement are retained. The shared foreground updates the launcher, monochrome layer, and about screen together. The real emulator captures above predate this size adjustment; the adaptive preview is regenerated for the reduced size.

## Unenforced device-default wording (2026-10-10)

- A confirmed current value of FORCE_NONE on a device whose default is FORCE_NONE now shows `強制なし` / `Not enforced`, with a description explaining that the device does not enforce the shutter sound by default.
- A confirmed released value on a device whose default enforces the shutter sound retains `解除済み` / `Released` and the ringer-mode description. Unknown and busy states retain their existing wording. Default markers and action behavior are unchanged.
- Extended the existing home UI tests to distinguish these two cases and confirm that restoring an already-matching device default stays disabled. All six home UI tests passed (`app/build/home-default-label/ui-tests.txt`). Debug APK and test APK assembly passed after correcting a duplicate resource definition.
- Visually inspected `default-not-enforced.png` and `released.png` in `app/build/home-default-label`: both headings and descriptions fit the hero, and the default-device notice remains visible in the scrollable layout.

## Before-uninstall launcher shortcut (2026-10-10)

- Added a static launcher shortcut, `削除前に戻す`, with an adaptive restore icon. It opens a Material 3 reminder titled `削除しようとしていますか？`, explaining that uninstalling does not restore the audio setting.
- Opening the shortcut never changes audio policy. The user must tap the restore action; unavailable Shizuku, missing authorization, unknown state, and busy operations receive the appropriate prerequisite action. A confirmed matching default displays that restoration is unnecessary.
- The reminder preserves its open/dismissed state across activity recreation and reopens for a new shortcut intent. It is independent of the launcher's system uninstall action.
- All three instrumentation tests passed (`app/build/uninstall-shortcut/ui-tests.txt`), covering explicit restoration, confirmed default, Shizuku prerequisite actions, error feedback, the published shortcut intent, cold/warm activity launches, and recreation. Restoration UI tests use simulated state and do not change device audio policy.
- Debug APK/test APK assembly and Lint passed (0 errors, 20 warnings). The first integration-test attempt used the instrumentation context's English strings for a Japanese per-app locale; the final test reads the activity's localized resources.
- The dedicated emulator experienced low-memory and response delays during background package activity. The final instrumentation run passed after it settled; the launcher shortcut was also verified directly in Pixel Launcher (`shortcut.png`).
- Tapped the actual Pixel Launcher shortcut and visually inspected the Japanese reminder (`reminder.png`). The title wraps without clipping, the uninstall warning and Shizuku guidance fit, and both action buttons remain visible.
- Subsequent confirmed-default revision: the dialog now says `アンインストールできます` and `現在の設定は既定値です。このままアンインストールして大丈夫です。`, with only a Close button. The restoration warning is hidden in this state, including after successful restoration is confirmed. Unknown, unavailable, and busy states retain the prerequisite guidance. Both reminder UI tests passed (`ui-tests-ready.txt`), verifying the reassurance title/body and absence of the restoration warning and button. Debug APK/test APK assembly and `git diff --check` passed; the captures above predate this wording revision.

## Automatic update checks and prompts (2026-10-10)

- Adapted NITTC Scheduler's repository, cached results, eight-hour successful-check interval, and per-version notification dismissal. MainActivity checks on resume; no work is scheduled while the app is closed. Manual checks bypass the interval and share the same update repository.
- Added a home banner and badged update action. Closing the banner keeps the action available for the session; ignoring a version persists across restarts, while manual checks can still open it. Update prompts only open on user action, and update controls are hidden during audio-policy operations and the uninstall reminder.
- The checker reads up to 100 published releases, selects the highest eligible version, and includes intermediate release notes. Stable receives full releases, Beta receives Beta/full releases, and IntDev also receives development releases. Numbered suffixes such as Beta2/Beta10 compare numerically. Unknown release tags and drafts are skipped.
- Added an update-details screen with selectable release notes, expandable intermediate notes, and download/cancel/install controls. A missing APK falls back to the release page. Downloaded files use a narrowly scoped FileProvider, repository URLs, optional GitHub SHA-256 digests, and package/version/signing-certificate validation before opening Android's installer. Installation permission is requested through Android settings only after the user taps the action.
- All 17 JVM tests passed, including cached/offline behavior, manual refresh, ignore/check races, promotion and channel filtering, version ordering, interval/version changes, and URL validation. All seven instrumentation tests passed (`app/build/update-design/ui-tests-final.txt`), covering the update journey, real preference persistence and removal after installation, release-response parsing, APK package/signature validation, existing navigation, and the uninstall shortcut.
- Visually inspected Japanese banner, prompt, details, and simulated download progress captures under `app/build/update-design/japanese-normal/`. The short details toolbar title and removal of a duplicate release-page action were applied afterward.
- All four update instrumentation tests also passed with Japanese and font_scale=1.5 on the final APK (`ui-tests-large.txt`). Visually inspected the final large-text prompt and details captures under `app/build/update-design/update-design/`: buttons remain accessible, the shorter toolbar title fits, long notes scroll, and the missing-APK fallback has one release-page action.
- Debug APK/test APK assembly, JVM tests, and Lint passed (`build-final.txt`), with 0 errors and 24 warnings (four additional optional KTX-style suggestions). Initial parallel validation caused resource pressure and System UI response delays in the emulator; final builds ran sequentially and clean Japanese captures replaced those first captures.
- The real public GitHub API returned HTTP 200 with zero releases. Update-available screens use fixtures; downloading and installing a published update APK has not been tested because none is published yet. Release notes currently display as selectable text rather than importing Scheduler's Android View Markdown renderer.
- Launched the real main activity on the final APK and verified that its automatic network check saved `last_check_version=1.0.0-IntDev` and a successful-check timestamp in the production update preferences. With no published release, no update banner is shown (`startup.png`).

## Animated home status shapes (2026-10-10)

- The hero uses Google Material Symbols Rounded: Error for enforced audio, Check Circle for released audio (including a non-enforcing device default), and Help for an unknown/unavailable state. Official vector assets retain their Apache 2.0 attribution in source comments.
- MaterialShapes.SoftBurst surrounds enforced and unknown states; MaterialShapes.Cookie9Sided surrounds released states. A single Morph interpolates between the shapes over 700 ms and continues smoothly when interrupted/reversed. Card, text, icon, and shape colors animate together; symbols crossfade over 220 ms.
- Only the background shape rotates, completing one revolution in 30 seconds. Animation state is read during drawing/layer updates so stable-state rotation does not recompose the whole home screen each frame. The symbol stays upright and the existing 92 dp layout footprint is preserved.
- Debug APK/test APK assembly and Lint passed (0 errors). Eight instrumentation tests passed on the dedicated emulator, including rendered-image checks for an intermediate morph, continuity on reversal, and rotating outline with an unchanged central symbol. Existing tests cover release/restore, busy state, unknown state, non-enforcing defaults, prerequisites, and usage notes.
- Two additional dark-theme instrumentation tests passed. Visually inspected enforced, released, and unknown captures in both light and dark modes under `app/build/home-expressive/`; the new outlines and symbols fit without overlapping text. Tests use simulated UI state and never change actual device audio policy.

## IntDev update developer options

- Settings exposes two independent, persisted switches only for IntDev builds. Version-comparison bypass offers the most recently published non-draft release, including equal/older versions and unversioned tags. Interval bypass checks on each launch/resume regardless of the eight-hour interval; it does not schedule checks while closed. Both default to off.
- Channel gating uses the installed APK's package metadata. Non-IntDev builds hide the section, ignore saved enabled values, and refuse writes. Switching comparison bypass off immediately removes ineligible cached results; results finishing during a policy change are revalidated before publication and manual-check return.
- Debug APK/test APK assembly, all 22 JVM tests, and Lint passed (0 errors). All eight instrumentation tests passed on the dedicated emulator (`app/build/developer-updates/ui-tests.txt`), covering independent persistence, cached results, publication ordering, non-version tags, non-IntDev gating, settings navigation, and the existing update journey and APK validation.
- Visually inspected the Japanese settings capture (`app/build/developer-updates/captures/settings.png`): both descriptions wrap without clipping, and the switches and existing settings remain visible. Tests use isolated preferences and release fixtures; no release was published and no physical device was used.

## Optional automatic maintenance of released audio policy

- Added a Material 3 settings section in Japanese and English. The persisted enabled switch stays on after manual restoration; a separate persisted armed flag pauses automatic release. The helper text distinguishes off, enabled, and manually paused states without asserting that an unknown policy is released.
- An Application-owned Shizuku connection/permission listener and unique hourly WorkManager periodic work share one coordinator. All manual routes use that coordinator, including the QS tile, notification restoration, home, and before-uninstall reminder. Only a known value of 11 is automatically written to 0; existing 0 and unknown values require no automatic write.
- Settings are recorded before asynchronous work. A shared Mutex, intent lock, independent automatic/manual generations, and cancellation of the automatic child prevent stale checks from overriding restoration or OFF. A stopped Worker awaits connection cleanup; its callback never performs detached audio-policy writes. An OFF toggle does not cancel a pending manual restoration.
- Debug APK/test APK assembly, all 45 JVM tests (23 new maintenance tests), and Lint passed (0 errors, 31 warnings). All 27 instrumentation tests passed on the dedicated emulator (`app/build/auto-maintain/ui-tests.txt`), covering new settings, actual unique scheduling/cancellation, production Worker instantiation, stopped-Worker cleanup, temporary Shizuku-listener cleanup, and existing home/navigation/uninstall/update/developer settings.
- Two additional settings tests passed in English at font_scale=1.5. Visually inspected enabled/paused captures under `app/build/auto-maintain/captures-ja/` and `captures-en-large/`: descriptions wrap, switches remain accessible, and paused maintenance retains the ON presentation.
- The audio-policy tests use simulated connections and isolated preferences. No physical device audio policy was changed. Real Shizuku recovery, recovery after a physical reboot, and timing under Doze remain unverified. Implementation details, constraints, full changed-file list, and test scope are recorded in `docs/auto-maintain.md`; README describes the user-visible behavior.
