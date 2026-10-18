# Recovery

The tested stock package is `com.google.android.tvlauncher`; the stock HOME component is `com.google.android.tvlauncher/.MainActivity`. Its recorded original enabled setting is default (0). The active user was observed as 0; the script checks it rather than assuming a different TV is equivalent.

## From the TV

Open Home Switcher, choose **Connect and check** if needed, then **Use stock Home** and confirm. Press the physical Home button afterward. This restores the stock launcher package and stock preference. It does not uninstall the custom launcher or clear app data.

## From an authorized laptop

Use official Android SDK Platform-Tools and an already authorized ADB key. Set `ADB_VENDOR_KEYS` to your existing key path if your laptop uses a project-specific identity; do not copy a private key into this repository. Use the known TV address, without scanning the network.

```powershell
./scripts/Restore-Stock.ps1 -Device 'YOUR_TV_IP:5555'
```

The script checks the recorded fingerprint and active user before restoring package state, saving stock Home, verifying resolution and opening it. Output and errors go to ignored `.local/` files. It supports `-WhatIf` for a preview and accepts `-Adb` if Platform-Tools is not on PATH. It never runs during a build.

The equivalent commands were device-tested in the original investigation, followed by a physical stock Home check. The repository's portable script is the same restore operation with an explicit device argument; it should not be described as tested on another TV.

```text
pm default-state --user 0 com.google.android.tvlauncher
cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT
am start -W --user 0 -n com.google.android.tvlauncher/.MainActivity
```

Independent controls on the recorded firmware:

```powershell
adb -s 'YOUR_TV_IP:5555' shell am start -n com.android.tv.settings/com.sony.dtv.settings.MainSettings
adb -s 'YOUR_TV_IP:5555' shell am start -n com.sony.dtv.tvx/.inputselect.InputSelectionActivity
```

The original baseline had an empty preferred record. The restore operation deliberately saves an explicit stock preference. Returning to that original empty record additionally requires stock App details → **Clear defaults**, an operation tested during the investigation. Do not choose Clear data or Uninstall updates.

Recovery depends on debugging being available, the device being reachable and a valid authorization. Clearing app data, uninstalling the switcher or revoking authorizations can require approving a key again. Keep the laptop recovery route available during testing.
