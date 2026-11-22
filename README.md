# MOOG'S DIALER

A thoughtfully designed Android dialer experience built with Kotlin and Jetpack Compose.

> Current milestone: polished app foundation and interactive UI. Device integration and default-dialer qualification are intentionally separate milestones.

## Product direction

- Distinctive graphite / warm-white / electric-lime visual identity
- Keypad-first calling flow
- Dedicated Recents, Contacts, Favorites, and Settings destinations
- Search and number entry
- Accessible touch targets and clear empty states
- Native Android handoff for dialing

## Stack

- Kotlin
- Jetpack Compose + Material 3
- AndroidX Activity
- Android SDK 35 / min SDK 26
- MVVM-friendly package structure

## Open the project

1. Clone this repository.
2. Open the root folder in Android Studio.
3. Install Android SDK Platform 35 if prompted.
4. Sync Gradle.
5. Run the `app` configuration.

## Build

After Android Studio has generated/synced the Gradle wrapper:

```bash
./gradlew assembleDebug
```

Windows PowerShell:

```powershell
.\\gradlew.bat assembleDebug
```

## Milestones

- [x] Branded Compose app shell and keypad
- [x] Main navigation and responsive content surfaces
- [x] Number entry, delete, clear, and system dialer handoff
- [x] Search-ready Contacts / Recents / Favorites surfaces
- [x] Theme and settings surface
- [ ] Real Contacts Provider integration and permission UX
- [ ] Real CallLog integration and permission UX
- [ ] T9 matching and contact suggestions
- [ ] Default Phone role eligibility and Telecom integration
- [ ] In-call UI via InCallService and notification/foreground-service requirements
- [ ] Dual-SIM behavior, accessibility audit, and device QA

## Important platform note

A complete replacement phone app must satisfy Android's dialer role requirements, including handling `ACTION_DIAL` and implementing the Telecom `InCallService` contract. This UI foundation currently opens the system dialer with `ACTION_DIAL`; it does not claim to replace the system phone app yet.

Permissions for contacts and call history will be requested only when those live integrations are implemented. Emergency calls and carrier-specific behavior remain under Android/system control.
