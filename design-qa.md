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
