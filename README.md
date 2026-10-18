# Android TV Home Switcher

Choose which launcher opens when you press the remote's Home button. Switch back to stock Home from the same app.

**Current scope: a tested Sony Android 9 implementation, not a universal Android TV app.** The firmware and recovery profile is checked before switching. Supporting more TVs is the next stage of this project.

## How it works

Sony's stock Home has a higher intent priority than an ordinary launcher on the tested firmware. Saving a different Home preference alone did not change the physical Home button's behavior.

Home Switcher uses an explicitly authorized connection to the TV's existing local ADB service. In custom mode it disables only the recorded stock launcher package, selects the chosen HOME activity and checks the result. Stock mode restores the stock package's original default-enabled state and its Home preference. The selected launcher is Android's actual Home; this app is not a proxy launcher.

- Remote-operated buttons with visible focus and a confirmation before switching.
- A separate **Connect and check** action that makes no launcher changes.
- Firmware, active-user, launcher, independent-control and package-state checks.
- Stock recovery after a detected failure during switching.
- Public-API launcher discovery and **Launch once** tools.
- No root, firmware changes, accessibility interception, helper APK or background service.

## Tested configuration

| Item | Result |
| --- | --- |
| TV | Sony KD-55X7500F; ADB platform model BRAVIA 4K GB ATV3 |
| Android | Android 9 / API 28, build 672201 |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |
| Custom Home | [tv-vibehack](https://github.com/Gathin23/tv-vibehack), locally patched |
| Switching | Stock → custom → stock → custom passed |
| Physical remote | Both on-TV switch actions and Home-button results confirmed |
| Controls and playback | User-reported Sony Settings, Inputs, YouTube picture/audio and remote checks passed |
| Persistence | Standby/wake and two full restarts passed; app authorization survived restart |

This does not establish compatibility with every TV, launcher, streaming provider or stock-launcher-specific feature. The stock launcher's own screens are unavailable while its package is disabled; the independent Sony Settings and Inputs paths were tested. See [compatibility and validation](docs/COMPATIBILITY.md).

## Use

1. Build and install the app on the tested configuration. Installation itself does not change Home.
2. Open **Home Switcher** from the TV's Apps list.
3. Choose **Connect and check**. Approve this app's debugging key on the TV and select **Always allow**.
4. Choose **Use stock Home** or **Use tv-vibehack**, then confirm **Switch**.
5. Press the physical Home button to verify the result.

Debugging must remain enabled and available when switching. No ongoing laptop connection is needed. The selected Home persists through the tested restarts without a running switcher service.

**Other installed Homes** discovers additional candidates. Test one through **Launcher tools → Launch once** before selecting it, then repeat the control, playback, physical remote and restart checks. Discovery alone is not a compatibility test.

## Build and checks

Requirements: JDK 17+ (tested with Android Studio JBR 21), Android SDK platform 36, build-tools 36.0.0, Gradle 8.13 via the checked-in wrapper. The distribution checksum is pinned. PowerShell helpers require PowerShell 7.

Open the repository in Android Studio, or use:

```powershell
./scripts/Build.ps1 -Jdk 'PATH_TO_JDK' -Sdk 'PATH_TO_ANDROID_SDK'
```

The build runs APK assembly, lint and the standalone Java checks. If the Windows sandbox prevents lint's report writer from checking writable directories, use the explicit analysis-only mode:

```powershell
./scripts/Build.ps1 -Jdk 'PATH_TO_JDK' -Sdk 'PATH_TO_ANDROID_SDK' -AnalysisOnly
```

That mode checks lint's definite/provisional XML for error/fatal incidents and retains warnings. It does not claim that the full lint report task passed. The app has 6 status-policy checks and 39 ADB protocol, crypto, key persistence, stream cleanup and switch/failure checks.

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Debug signing is for local testing. To update an existing installation signed with your own debug key, set `HOME_SWITCHER_DEBUG_KEYSTORE` to that key's absolute path before building; otherwise the normal Android debug key is used. No private key is included in this repository.

## Recovery

Keep a separately authorized laptop ADB connection available while testing. See [recovery](docs/RECOVERY.md) for the profile-checked restore script and independent Sony controls. Do not clear launcher data, uninstall stock Home or run upstream debloat/restore lists.

## Repository layout

- `app/`: Home Switcher and its recorded device profile.
- `checks/`: standalone Java checks.
- `scripts/`: build, checks and explicit recovery; build scripts never connect to a TV.
- `docs/`: architecture, compatibility, validation and recovery.
- `patches/`: the reviewed tv-vibehack patch, pinned to its upstream commit.
- `third_party/`: upstream and Gradle wrapper license notices.

Raw device logs, screenshots, LAN addresses, private keys, debug APKs, caches and the original investigation workspace are not published here.
