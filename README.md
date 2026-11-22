# MOOG'S DIALER

A custom Android phone dialer built with Kotlin and Jetpack Compose.

## Status

**Phase 1 foundation:** project structure, custom dark/lime theme, Compose dial-pad UI, and Android app configuration.

This is an Android Studio Gradle project. Native calling, default-dialer role, contacts, call history, and in-call UI are planned follow-up milestones.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3
- MVVM-ready package structure
- Android Telecom / Contacts Provider integration (subsequent milestones)

## Open in Android Studio

1. Clone this repository.
2. Open the repository root in Android Studio.
3. Allow Gradle sync to finish.
4. Run the `app` configuration on an Android emulator or device.

## Build

Use Android Studio's Gradle sync/build, or from a terminal:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

A Gradle wrapper is intentionally not committed in this starter; Android Studio can generate/update it for the installed Gradle version.

## Roadmap

- [x] Compose app foundation and branded dial-pad screen
- [ ] T9 contact search using Contacts Provider
- [ ] Permission rationale and contact picker/list
- [ ] Outgoing calls using ACTION_CALL / ACTION_DIAL as appropriate
- [ ] Implement ACTION_DIAL and InCallService requirements for default Phone role
- [ ] Call history with CallLog permissions and role eligibility
- [ ] Favorites and contact details
- [ ] Dual-SIM selection and testing
- [ ] Accessibility, privacy, and device testing

## Permissions and safety

The initial app does not request call-log, contacts, or phone permissions. Those will be added only with the relevant feature and runtime rationale. Being the default phone app requires implementing the Android Telecom contract and requesting the system dialer role; a custom keypad alone is not a complete replacement dialer.
