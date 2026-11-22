# MOOG Design System

## Phase 1 foundation

The shared Compose theme lives in `ui/theme/MoogTheme.kt`.

- Appearance preference is persisted as `system`, `light`, or `dark`; default is System.
- Light and dark semantic palettes use the iPhone-inspired values in the UI redesign brief.
- Typography tokens establish a 34sp large title, 28sp headline, 17sp body, 15sp subhead, 13sp footnote, and 12sp caption floor.
- `MoogTheme(forceDark = true)` is available for the immersive in-call surface.

Reusable stateless components live in `ui/components/MoogComponents.kt`: grouped sections, list rows, search bar, segmented control, switch, avatar, circle button, empty state, navigation bar, tab bar, and confirmation sheet.

## Behavior constraints

The theme and components are presentation-only. They must not change Android Telecom behavior, call routing, preferred SIM selection, call-log permission gating, DTMF, call waiting, conference controls, blocklist decisions, or voicemail provider behavior.

## Verification

This phase has not been built, linted, unit-tested, or device-tested. Those checks remain intentionally deferred until explicitly authorized.

## Phase 2 — App shell foundation

- Extracted the Android entry point and outgoing-call handoff into the thin `MainActivity.kt`.
- Moved the main Compose app shell and current tab/screen composables into `ui/screens/DialerScreens.kt`.
- Replaced the repeated MOOG brand header and custom bottom row with the shared five-item `MoogTabBar`.
- Corrected the Keypad tab icon to a dialpad grid icon.
- Kept Settings as the fifth tab so every existing feature remains reachable without a disruptive navigation change.
- Added system Back handling for the in-app contact profile; standalone Spam Protection and carrier voicemail screens continue to use Android Activity back navigation.
- The tab bar now applies navigation-bar insets.

This is the first navigation extraction. Further per-screen file separation, Settings sub-pages, and full navigation-stack consolidation remain subsequent work.
