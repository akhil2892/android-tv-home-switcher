# Architecture

The package remains `dev.local.tvhomeselector`, version `0.2-local-adb`. The normal TV app-list entry is `SwitchActivity`; the app has no HOME filter.

| Class | Responsibility |
| --- | --- |
| `SwitchActivity` | Remote-friendly switching UI, confirmation, progress and last operation result. |
| `SwitchEngine` | Identity/user gates, selected launcher validation, package switching, resolution/control checks and stock recovery. |
| `LocalAdb` | ADB client connected only to `127.0.0.1:5555`; one connection per requested operation. |
| `AdbWire` | Bounded transport packets, checksum validation and shell-v2 stdout/stderr/exit parsing. |
| `AdbIdentity` | TV-generated private RSA identity, saved in app-private files and reused across updates/restart. |
| `MainActivity`, `HomePolicy` | Original public-API discovery, launch-once and system Settings tools. |

The `INTERNET` permission is used for the fixed local socket. There is no external host option, ADB server, listener, service, boot receiver, device-owner provisioning, hidden API, reflection or accessibility interception. Exported activities ignore intent extras as shell input; the UI accepts no arbitrary command text.

The user approves the app's distinct debugging key in Android's system prompt. The laptop key is never imported. The app keeps the latest command outputs/exit results in private `last-switch.log`, without key material. Debug builds allow the owner to retrieve that log through `run-as` for diagnosis. Backup is disabled.

The profile in `app/src/main/assets/verified-tv.json` records the observed fingerprint, user, original stock component, independent Sony controls and disabled-package baseline. It contains no unique hardware serial or LAN address. It is specific to the tested configuration. Changing a fingerprint alone is not a valid compatibility port.

Custom mode first validates and opens the replacement, then disables only the recorded stock package and sets the full Home component. Stock mode restores the package's original default-enabled state. Both verify effective HOME, independent controls and the reviewed disabled-package set. Detected failures after mutation attempt fresh-connection stock recovery. Power loss, process termination or loss of debugging can prevent automatic recovery; laptop recovery remains necessary in those cases.

No background code reapplies selection at boot. Android's package state and preferred Home supply persistence. The app key and the existing ADB service both returned successfully after the tested restart.

## Protocol references

- [Android 9 ADB protocol](https://github.com/aosp-mirror/platform_system_core/blob/android-9.0.0_r61/adb/protocol.txt)
- [Authentication signing](https://github.com/aosp-mirror/platform_system_core/blob/android-9.0.0_r61/adb/adb_auth_host.cpp)
- [Public-key encoding](https://github.com/aosp-mirror/platform_system_core/blob/android-9.0.0_r61/libcrypto_utils/android_pubkey.c)
- [Shell-v2 framing](https://github.com/aosp-mirror/platform_system_core/blob/android-9.0.0_r61/adb/shell_service.h)
- [Android ADB](https://developer.android.com/tools/adb)

The Java implementation is original protocol code, not a copied AOSP source distribution. SHA-1 AUTH signatures and the approval dialog's MD5 fingerprint are requirements of this Android 9 ADB protocol, not artifact integrity choices.
