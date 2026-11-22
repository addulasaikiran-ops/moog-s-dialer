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
- [x] Display the Telecom account label (SIM/carrier) associated with each call when Android exposes it
- [x] Respect available audio routes when toggling speaker; avoid forcing earpiece when Bluetooth or wired audio is active
- [x] Leave outgoing SIM/account selection to Android Telecom so the system can honor the user's preferred SIM or show its account chooser
- [x] In-app preferred-SIM picker with System default / Ask every time and active SIM account choices
- [x] Outgoing calls pass the selected Telecom PhoneAccountHandle when a preferred SIM is saved
- [x] Recent-call rows map CallLog phone-account IDs to active SIM/carrier labels
### In-call DTMF keypad

During an active or held call, the in-call screen exposes a keypad toggle with the standard 0–9, `*`, and `#` keys. Each tap asks Android Telecom to play the corresponding DTMF tone and automatically stops it after a short pulse; starting another digit stops the previous pulse first. The keypad is hidden during ringing/dialing/connecting states, and any active tone is stopped when the keypad is hidden or the call is removed. IVR recognition and tone duration can vary by carrier, network, and remote phone system, so this feature still needs real-call verification.

### Advanced call management

The in-call screen keeps the established foreground call visible when a second call rings and shows a separate Call waiting card with Answer and Decline actions. Answering is delegated to Android Telecom and the underlying ConnectionService; whether the existing call is held, and whether call waiting is available at all, depends on device, SIM, carrier provisioning, and current call capabilities. When Telecom reports an active call and a held call, MOOG'S DIALER exposes Swap only when both calls report hold support. A Merge control appears only when one call lists the other as conferenceable; the app then requests the conference through Telecom. Conference participant count is shown when Telecom exposes child calls. Unsupported actions are hidden rather than simulated. These flows still require physical-device and carrier validation.

### Smart Recents

The Recents screen provides All, Missed, Incoming, and Outgoing filters plus local search across the cached contact name and phone number in the device call log. Matching history is grouped under the contact name when Android supplies one, otherwise under the phone number, with newest-first ordering preserved. Tap a row or its phone action to start a callback through the existing dial flow; the message action opens the system SMS composer addressed to that number. Call history remains on-device and is shown only when MOOG'S DIALER is the default Phone app and call-log permission is granted. Search is limited to the most recent records loaded by the call-log repository; it is not a full-device or cloud search.

### Rich contact profiles

Opening a contact shows a dedicated profile with distinct saved phone numbers and their Android-provided type labels. Each number has separate Call and Message actions so users can choose the correct work, mobile, or home line instead of relying on an arbitrary default. The profile also matches the locally available CallLog entries against every saved number for that contact and shows recent incoming, outgoing, missed, or rejected calls with the SIM label where available. Contact history is available only under the existing default Phone app and call-log permission requirements. Phone-number matching normalizes punctuation and compares the final ten digits to accommodate common country-code formatting; this is a convenience match, not a globally unique identity guarantee. Contact and call data remain on-device.

### User-controlled spam protection

The Spam protection page stores a user-managed blocklist only in app-private SharedPreferences on this device. Users can add and unblock numbers, and can block and decline a currently ringing number from the incoming-call screen. When MOOG'S DIALER is the active InCallService, an incoming call is automatically rejected only if its number matches an entry the user explicitly blocked. Recents and call UI may label a matching number as **On your blocklist**; this is a local list match, not a network reputation result or live caller-identification service. Private/withheld numbers cannot be matched to a saved number. Android Telecom, OEM, carrier, and default-dialer eligibility determine whether automatic rejection is delivered.

### Stabilisation and release readiness
- [ ] Accessibility pass: TalkBack descriptions and traversal, minimum 48dp touch targets, scalable text, contrast, focus states, and clear permission/error messaging
- [ ] Automated CI: GitHub Actions workflow for Gradle validation, Android lint, unit tests, and debug APK assembly
- [ ] Unit tests for T9 matching, SIM account-key mapping, stale preferred-SIM handling, and call-log label fallbacks
- [ ] Carrier/device QA across physical dual-SIM and eSIM configurations
- [ ] Validate call waiting, hold/swap, conference support, VoLTE/VoWiFi, roaming, and DSDS/DSDA behaviour where supported
- [ ] Verify incoming-call presentation, notification/full-screen-intent behaviour, Bluetooth, wired headsets, audio-route changes, and DTMF navigation through real IVR menus
- [ ] Release readiness: versioning, release signing configuration, privacy/permission review, crash-free smoke test, and release notes

### Planned product upgrades (not implemented yet)
- [x] In-call DTMF keypad for IVR and automated phone menus
- [x] Advanced call management: second incoming call, call waiting, hold/swap, and conference controls where Android Telecom and the carrier support them
- [x] Smart Recents: missed/incoming/outgoing filters, call-history search, contact grouping, and quick callback/message actions
- [x] Rich contact profiles: contact-specific call history, quick actions, and improved multi-number handling
- [x] User-controlled spam protection: local blocklist, block/unblock controls, and clear suspected-spam indicators; no unsupported claims of live caller identification
- [ ] Optional local call insights and duplicate-contact assistance, with privacy-first on-device processing

## Dual-SIM and device compatibility notes

MOOG'S DIALER does not hard-code SIM slot numbers or infer a SIM from carrier names. Android Telecom exposes the call-capable SIM accounts. Settings lets the user choose a preferred outgoing account or return to **System default / Ask every time**. When MOOG'S DIALER is the default Phone app and the required permissions are granted, outgoing calls pass the selected `PhoneAccountHandle` to `TelecomManager.placeCall`. If MOOG'S DIALER is not the default Phone app, it hands the number to the system dialer and cannot force the selected line.

For incoming and ongoing calls, the app reads the call's `PhoneAccountHandle` and displays the associated Telecom `PhoneAccount` label when available. Some carriers and OEMs expose generic labels or no useful label; in those cases the UI falls back to “SIM” or “SIM not identified” rather than guessing SIM 1/SIM 2.

Speaker routing follows Android's currently available audio-route mask. If Bluetooth or a wired headset is active, turning speaker off returns to an available Bluetooth/wired route before falling back to the earpiece. If the device does not expose a speaker route, the speaker control is disabled. Android Telecom remains authoritative for call state, hold support, audio routing, carrier restrictions, emergency calls, roaming, VoLTE/VoWiFi, and DSDS/DSDA behavior.

Settings includes a preferred outgoing SIM selector backed by Android Telecom call-capable SIM phone accounts. The chosen account key is stored locally. If that account disappears, MOOG'S DIALER clears the stale preference and returns to Android's system default account selection. Selecting System default / Ask every time removes the account override. Incoming calls are never redirected by this preference.

Recents uses the CallLog phone-account component and account ID columns to associate each historical call with the matching active Telecom SIM label. Old calls from removed SIMs or OEMs that do not expose matching identifiers show a neutral unknown/unavailable label rather than a guessed SIM number. Reading the active SIM account list requires Android's phone-state and phone-number permissions because Telecom protects phone-account metadata. Users can grant both from Settings. Without them, calls still use Android's default routing and SIM labels may be unavailable.

Dual-SIM and carrier behaviour still requires physical-device testing. Use the following QA matrix and record device model, Android version, carrier, SIM/eSIM configuration, expected result, actual result, and screenshots/logs for each run:

| Scenario | What to verify |
| --- | --- |
| Outgoing SIM selection | Each active SIM can be selected; System default / Ask every time restores Android-managed selection |
| Incoming calls | Calls arriving on either line display and answer on the receiving line |
| Dual-SIM concurrency | DSDS/DSDA behaviour, data/voice concurrency, and one SIM busy while the other receives a call |
| SIM lifecycle | SIM removed, disabled, replaced, no service, and eSIM plus physical SIM |
| Carrier services | VoLTE, VoWiFi, call waiting, hold/swap, conference calling, and roaming where provisioned; confirm unsupported controls stay hidden |
| Audio routes | Earpiece, speaker, Bluetooth connect/disconnect, and wired headset during a call |
| OEM call experience | Incoming-call notifications, full-screen intent restrictions, lock-screen behaviour, and background restrictions |
| Permissions/default role | Grant, deny, revoke, and re-grant phone permissions; set and remove the default Phone role |

Do not mark carrier validation or device QA complete until each supported scenario has been exercised on real hardware and results have been recorded. Android, OEM, carrier, region, and subscription capabilities vary.

## Platform and privacy notes

MOOG'S DIALER declares an Android Telecom `InCallService` with incoming and ongoing call UI. The Settings screen offers a user-initiated default Phone app request (RoleManager on Android 10+, legacy Telecom chooser on earlier supported versions). If the role is granted, outgoing calls are sent through `TelecomManager.placeCall`; otherwise the app hands off to the system dialer with `ACTION_DIAL`.

The app requests `READ_CONTACTS` only when the user chooses to connect their address book. Contact data is read on-device and favorites are stored locally. The Recents screen has a CallLog repository and UI, but it reads nothing unless Android reports that MOOG'S DIALER holds `ROLE_DIALER` and `READ_CALL_LOG` is granted. The Phone role and call-log flow still need device verification. Android 13+ notification permission and Android 14+ full-screen-intent access affect how prominently incoming calls can be surfaced. Ringtone playback remains managed by the platform.

Emergency calling and carrier-managed behavior remain under Android and the carrier's control.


## Accessibility and quality gates

Accessibility is a release requirement, not a cosmetic follow-up. Verify all primary flows with TalkBack, keyboard/switch navigation where applicable, large font and display scaling, adequate text/background contrast, descriptive labels for icon-only controls, and touch targets of at least 48dp. Avoid relying on colour alone to communicate call state or errors.

Automated checks should run from GitHub Actions on pull requests and pushes to the main branch. The intended pipeline is Gradle validation, Android lint, unit tests, and debug APK assembly. Add instrumented tests only where they can run reliably on an Android emulator. A green CI run does not replace carrier or physical-device QA.

## Current verification status

The SIM preference and per-SIM Recents changes are merged into `main`. The in-call DTMF keypad is merged into `main`. Advanced call-management controls are merged into `main`. Smart Recents is merged into `main`. Rich contact profiles are merged into `main`. User-controlled spam protection is implemented on its feature branch and is awaiting review/merge. The stabilisation, accessibility, automated-check, carrier-validation, and device-QA items above are planned work unless explicitly checked off with recorded evidence. No build, automated test suite, or physical-device validation is claimed by this README.
