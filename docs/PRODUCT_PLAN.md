# MOOG'S DIALER — Product Plan

## Product direction

An original MOOG'S DIALER identity using an iPhone-inspired interaction language: calm light surfaces, clear hierarchy, rounded controls, familiar bottom navigation, and restrained blue accents. This is an Android app and will use Android platform APIs rather than attempting to reproduce iOS system behavior.

## Product principles

1. Native first: use Android Telecom, Contacts Provider, and CallLog APIs.
2. Honest UI: never fabricate contacts or call records.
3. Privacy by design: request sensitive permissions only when a feature needs them and explain why.
4. Accessible: large touch targets, content descriptions, readable contrast, and system font scaling.
5. Resilient: handle denied permissions, no SIM, no network, and unsupported carrier features gracefully.

## Experience map

- Favorites: device-local pinned contacts and one-tap dial.
- Recents: missed, incoming, and outgoing call records with filters and details.
- Contacts: on-device contacts, search, T9 matching, and contact details.
- Keypad: number entry, editing, T9 suggestions, and native dialer handoff.
- Settings: appearance, haptics, privacy, default-phone status, and app information.

## Delivery sequence

### Foundation — implemented
- Branded Compose shell and navigation.
- iPhone-inspired light visual system.
- Keypad and system dialer handoff.
- Contacts Provider repository and permission request.
- T9-style name/number matching.
- Local favorites persistence.

### Contacts completion
- Contact detail page and multiple phone numbers.
- Better contact photo presentation.
- Favorite management and search refinements.
- Permission-denial and empty-state refinements.

### Phone role and call data
- Meet Android dialer-role qualification.
- Implement Telecom InCallService and in-call UI.
- Request default Phone role with RoleManager.
- Read CallLog only after role eligibility and permission grant.
- Incoming/outgoing call state, audio route, hold, and disconnect handling.

### Quality and release
- Dual-SIM behavior where supported.
- Accessibility and localization.
- Unit/UI tests, permission-denial tests, emulator and physical-device QA.
- Release signing and APK/AAB build.

## Out of scope unless explicitly designed
- Call recording.
- Uploading contacts or call history to a server.
- Bypassing Android role, permission, or carrier restrictions.
