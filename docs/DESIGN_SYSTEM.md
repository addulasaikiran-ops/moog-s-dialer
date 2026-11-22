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
