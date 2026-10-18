# tv-vibehack integration patch

Upstream: https://github.com/Gathin23/tv-vibehack

Reviewed base commit: `9ef40c1de85b757cbf75dbea174a24b99a0e50a3`.

`tv-vibehack-local.patch` preserves the separate launcher changes used in the device investigation. It adds a TV app-list entry, blocks the legacy combined installers, removes unverified vendor assumptions from active configuration and applies the documented build/font/lint compatibility fixes. It does not contain Home Switcher itself.

Apply to a separate clean checkout of the pinned upstream commit:

```shell
git checkout 9ef40c1de85b757cbf75dbea174a24b99a0e50a3
git apply --check /path/to/tv-vibehack-local.patch
git apply /path/to/tv-vibehack-local.patch
```

The upstream disable/restore/debloat lists are not approved instructions for this TV. Read the patch before building; keep installation, Home switching and recovery separate. This repository does not vendor the upstream launcher, its font binaries or debug APKs.

The patch's local Sony notes record the earlier investigation stage, before the later explicit authorization for whole-stock-package switching. The current working method is documented in the main README and recovery guide.

Upstream MIT and font notices are retained under `third_party/`.
