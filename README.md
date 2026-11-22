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
- [x] Default Phone role request flow and Android Telecom InCallService registration
- [x] Incoming and ongoing call UI through `InCallService` with answer, decline, end, mute, speaker, and hold controls
- [ ] Dual-SIM behavior and carrier/device edge cases
- [ ] Accessibility, automated checks, device QA, and release build

## Platform and privacy notes

MOOG'S DIALER declares an Android Telecom `InCallService` with incoming and ongoing call UI. The Settings screen offers a user-initiated default Phone app request (RoleManager on Android 10+, legacy Telecom chooser on earlier supported versions). If the role is granted, outgoing calls are sent through `TelecomManager.placeCall`; otherwise the app hands off to the system dialer with `ACTION_DIAL`.

The app requests `READ_CONTACTS` only when the user chooses to connect their address book. Contact data is read on-device and favorites are stored locally. The Recents screen now has a CallLog repository and UI, but it reads nothing unless Android reports that MOOG'S DIALER holds `ROLE_DIALER` and `READ_CALL_LOG` is granted. The Phone role and call-log flow still need device verification. Android 13+ notification permission and Android 14+ full-screen-intent access affect how prominently incoming calls can be surfaced. Ringtone playback remains managed by the platform.

Emergency calling and carrier-managed behavior remain under Android and the carrier's control.
