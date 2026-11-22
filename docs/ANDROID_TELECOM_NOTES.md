# Android Telecom Implementation Notes

A custom keypad is not enough to become the default Phone app.

Before requesting the system dialer role, the app must satisfy the current Android dialer-role contract, including:

- An activity that handles `android.intent.action.DIAL`.
- A properly declared `InCallService` with the required Telecom intent filter and metadata.
- Correct call UI behavior for incoming, active, held, and disconnected calls.
- Appropriate foreground execution, notifications, audio routing, and lifecycle handling.
- A role request using `RoleManager.ROLE_DIALER` on supported Android versions.

The exact requirements must be verified against the target Android SDK and Android version before implementation. Emergency calling and carrier-managed behavior remain system-controlled.

Do not request broad phone permissions prematurely. Add each permission with a user-facing rationale when the feature that needs it is ready.
