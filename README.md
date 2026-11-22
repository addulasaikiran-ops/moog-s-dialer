# MOOG'S DIALER

An iPhone-inspired Android dialer experience built with Kotlin and Jetpack Compose.

## Current implementation

- Light, iOS-inspired visual language with rounded surfaces and a blue accent
- Five destinations: Favorites, Recents, Contacts, Keypad, and Settings
- Number entry with delete and system dialer handoff through `ACTION_DIAL`
- On-device Contacts Provider integration behind an explicit runtime permission
- T9-style contact matching by name and phone number
- Local favorites saved on the device with SharedPreferences
- No account, backend, or cloud contact synchronization

## Technology

- Kotlin
- Jetpack Compose + Material 3
- AndroidX ViewModel and Kotlin Coroutines
- Android Contacts Provider
- Android SDK 35 / min SDK 26

## Open the project

1. Clone this repository.
2. Open the root folder in Android Studio.
3. Install Android SDK Platform 35 if prompted.
4. Allow Gradle sync to complete.
5. Run the `app` configuration when we reach the testing milestone.

## Build

After Android Studio has generated/synced the Gradle wrapper:

```bash
./gradlew assembleDebug
```

Windows PowerShell:

```powershell
.\\gradlew.bat assembleDebug
```

## Development roadmap

- [x] Branded Compose application shell
- [x] iPhone-inspired light interface and five-tab navigation
- [x] Keypad with number entry, delete, and system dialer handoff
- [x] Contacts Provider repository and runtime permission rationale
- [x] T9-style matching for contact names and phone numbers
- [x] Device-local favorites persistence
- [x] Contact detail screen with all saved phone numbers and type labels
- [x] Grouped contact list entries with one row per person
- [x] Recent calls backed by Android CallLog, gated by default Phone role and call-log permission
- [ ] Default Phone role and Android Telecom integration
- [ ] In-call UI through `InCallService`
- [ ] Dual-SIM behavior and carrier/device edge cases
- [ ] Accessibility, automated checks, device QA, and release build

## Platform and privacy notes

The current app can open Android's system dialer using `ACTION_DIAL`; it is not yet a full replacement Phone app. A true default dialer must meet Android's dialer-role requirements and implement the Telecom `InCallService` contract before requesting `RoleManager.ROLE_DIALER`.

The app requests `READ_CONTACTS` only when the user chooses to connect their address book. Contact data is read on-device. Favorites are stored locally. Call-log access is intentionally deferred until the app is eligible for the Phone role and the user-facing permission flow is ready.

Emergency calling and carrier-managed behavior remain under Android and the carrier's control.
