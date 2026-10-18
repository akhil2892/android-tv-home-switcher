# Compatibility and validation

## Current support

The recorded profile is for Sony BRAVIA Android 9, build 672201, using the Google stock launcher. Other firmware is refused by the app's identity check. The existing package-baseline gate may also refuse another TV with different disabled packages, even if its firmware matches.

The original requirement was to keep stock Home enabled. On the tested firmware, preference writes saved successfully but stock priority 2 still won over the custom launcher's priority 0. Both Sony Home/default-app Settings intents led to an `EmptyStubActivity` that immediately finished. Component-only state changes were rejected by the Android 9 shell restriction. No component was disabled.

The working whole-package method was tested only after explicit permission to disable the inactive stock launcher and after proving its restoration commands. It leaves Sony Settings and input services enabled. The stock launcher's own Google Inputs panel is unavailable in custom mode; the separate Sony Inputs path was tested.

## Recorded device tests

These tests were completed in the original local investigation on 18 October 2026. Raw captures are retained privately, not included in the public repository.

- Explicit custom launcher launch and D-pad app opening.
- YouTube picture/audio, volume/mute, Back and remote Home checks reported passed.
- Sony Settings and remote Inputs checks reported passed.
- Laptop-driven stock/custom round trips, with actual HOME resolution and package state checked.
- On-TV stock/custom selection using the physical remote; user confirmed both Home-button results.
- Standby/wake reported passed.
- Two coordinated full restarts, with changed boot IDs, boot completion and custom Home resolution/foreground checked.
- First restart followed by user-confirmed physical Home, Settings and Inputs checks.
- Second restart followed by successful app connection using its saved authorization without another prompt.
- Stock package restoration and physical Home return tested before leaving custom mode active.

No particular HDMI/tuner source was identified in the broad pass reply. Every provider's protected playback, casting, voice features, all stock-specific screens, months of endurance, firmware updates and future launchers were not tested. No universal or lifetime guarantee is implied.

## Automated checks

The tested application built using Gradle 8.13 / AGP 8.13.1 / JBR 21. APK signature verification passed. Six policy checks and 39 protocol/crypto/switch checks passed. Lint analysis contained no error/fatal incidents; full report generation was blocked by the local Java sandbox writable-directory check and is not claimed to pass.

The portable repository build was also run with its new build/check scripts. It passed all 45 checks and lint analysis, and produced the same APK SHA-256 as the device-tested build: `C4630A0BB2C23044D1AED39CFEA51F724C98BA632170A54228826744E3404975`. APKs and signing keys are kept out of Git.

Regression checks cover fragmented packets, checksums, payload limits, shell exit status, non-CRT key reload, token signing without double hashing, late packets for a closed stream, firmware/user/target/control gates, failed preference/resolution handling and stock restoration.

## Before supporting another TV

First inspect its actual firmware, active user, Home resolution, launcher components and system selection screens. Prefer a working system selection path over package disabling. Discover independent Settings/Inputs access and prove recovery before changing launcher state. Then test physical remote, playback, standby and restart behavior on that TV.

The broader compatibility work has not begun in this repository's initial snapshot.
