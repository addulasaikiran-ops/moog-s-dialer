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
- Moved the main Compose app shell into `ui/screens/DialerScreens.kt` and extracted Keypad, Contacts, Favorites, Contact Detail, Recents, and Settings into separate `ui/screens/*Screen.kt` files.
- Replaced the repeated MOOG brand header and custom bottom row with the shared five-item `MoogTabBar`.
- Corrected the Keypad tab icon to a dialpad grid icon.
- Kept Settings as the fifth tab so every existing feature remains reachable without a disruptive navigation change.
- Added system Back handling for the in-app contact profile; standalone Spam Protection and carrier voicemail screens continue to use Android Activity back navigation.
- The tab bar now applies navigation-bar insets.

This is the first navigation extraction. Settings sub-pages and full navigation-stack consolidation remain subsequent work.

## Phase 3 — Screen redesign progress

- Keypad: removed the redundant label; added responsive number sizing, T9 suggestions above the number, system Add Number intent, 78dp tactile keys, pressed-state feedback, haptics, long-press 0 for `+`, and a green call action with adjacent delete.
- Favorites: rows call on tap and expose a separate contact-information action.
- Contacts: added alphabetical sticky sections, a right-edge index scrubber with haptic feedback, a contact count, and asynchronous cached thumbnail loading from existing `PhoneContact.photoUri`.
- Recents: All/Missed segmented filtering, Incoming/Outgoing overflow filters, consecutive-call collapsing in newest-first order, and direction-specific call glyphs. Per-row Block/Unblock buttons were removed; the local blocklist remains manageable in Spam Protection and from the incoming-call action.
- Settings: added persisted System / Light / Dark appearance selection.
- In-call: uses the dark palette, resolves a contact name only when contact permission is already granted, shows the Telecom connection timer, and groups controls into consistent three-column rows.

This screen work is still in progress. Call-detail navigation, full Settings sub-pages, swipe actions, the complete in-call redesign, and accessibility review remain subsequent work.

## Phase 4 — Settings and call-detail completion

- Rebuilt Settings as a grouped, large-title page with Appearance, Calling, Incoming Calls, Calling Features, and About sections.
- Replaced the SIM dropdown with a bottom-sheet account picker, including the Android system-default option and selected-state feedback.
- Made default Phone role, SIM permission, notification permission, and full-screen intent actions explicit and actionable.
- Connected Recents row selection to the existing Call Detail screen. The detail page exposes call type, relative date, duration, SIM label, callback, message, and local block/unblock actions.
- Incoming-call UI now presents Answer and Decline as the primary actions; Block caller and decline is a secondary action. Resolved contact names also retain the phone number as supporting context.



## Phase 5 — MOOG identity and appearance

- Replaced the iOS blue accent with the default **Analog Amber** palette, inspired by classic synthesizer control panels. The alternative **Signal Teal** token set is defined alongside it for a future brand choice; the current UI defaults to Analog Amber.
- Preserved the existing semantic token structure and kept green for calling and red for destructive actions. The call green is now darker in both themes so its white call glyph has sufficient contrast.
- Updated the light/dark surfaces to a warm studio-neutral palette and applied Android's rounded system sans-serif to large display typography and keypad digits.
- Added a persisted **Material You** appearance mode beside System, Light, and Dark. On Android 12+, it uses the device's dynamic light/dark color scheme; on older Android versions it falls back to MOOG's selected light/dark palette.
- Existing `MoogColors.blue` reads remain source-compatible through an alias to the new signature accent; new components should use `MoogColors.accent`.
- Presentation-only scope: no Telecom, call routing, SIM selection, permission gating, call-log, DTMF, call-control, blocklist, or voicemail-provider behavior was changed.

Verification for Phase 5: build, lint, automated tests, and physical-device contrast/appearance checks have not been run. On device, verify System/Light/Dark/Material You persistence, wallpaper-derived colors on Android 12+, fallback on older Android, keypad digit/title typography, and readable call-button glyph contrast in both light and dark themes.
