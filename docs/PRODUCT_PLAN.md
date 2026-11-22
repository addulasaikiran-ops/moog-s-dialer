# MOOG'S DIALER — Product Plan

## Product principles

1. Native first: use Android Telecom, Contacts Provider, and CallLog APIs rather than simulating phone behavior.
2. Honest UI: never display fabricated contacts or call records.
3. Privacy by design: request sensitive permissions only at the point of use and explain why.
4. Accessible: large touch targets, content descriptions, readable contrast, and system font scaling.
5. Resilient: handle missing permissions, no SIM, no network, and unavailable carrier features gracefully.

## Experience map

- Keypad: enter, edit, clear, and hand a number to Android's system dialer.
- Recents: missed, incoming, and outgoing call records with filters and detail actions.
- Contacts: searchable local contacts, contact details, and call/message shortcuts.
- Favorites: pinned contacts and one-tap access.
- Settings: appearance, haptics, privacy, default-phone status, and app information.

## Delivery sequence

### Foundation
- Branded Compose shell, navigation, keypad, theme, empty states.

### Device data
- Contacts permission flow and Contacts Provider repository.
- Call log permission flow and CallLog repository.
- Search normalization and T9 matching.
- Favorites persistence with Room.

### Phone role
- ACTION_DIAL entry points and role request flow.
- Telecom InCallService implementation and call UI.
- Notification/channel and foreground service compliance.
- Incoming/outgoing state handling and disconnect causes.

### Quality
- Dual-SIM selection where supported by platform APIs.
- Accessibility and localization.
- Unit/UI tests, permission-denial tests, emulator and physical-device QA.
- Release signing and APK/AAB build.

## Out of scope until explicitly designed

- Recording calls.
- Uploading contacts or call history to a server.
- Bypassing Android role, permission, or carrier restrictions.
