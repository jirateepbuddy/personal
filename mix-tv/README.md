# MIX TV — Ambient Dock (Android TV)

A small **native, offline Android TV launcher** designed for Sony X9000H and other Android TVs. tvOS-inspired visuals without copying Apple assets.

## Features
- Ambient Dock with installed app icons, remote focus animations and three dark themes.
- All Apps, Favorite pin/unpin and reordering (long-press OK).
- Native Settings shortcut, optional HDMI menu with Sony remote fallback.
- LEANBACK_LAUNCHER (can launch as ordinary app) + HOME (only if user explicitly selects).
- Offline. **No Internet permission, analytics, accessibility service, root or debloat scripts.**
- Does **not** disable or uninstall stock Home or Sony services.

## Build APK
Requires Android Studio/Android SDK 35 and JDK 17. From this folder:

```sh
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

The GitHub Actions workflow `Build MIX TV APK` builds the same APK on the `mix-tv-launcher` branch and uploads an installable APK artifact.

## Install safely
1. Sideload the APK: `adb install app/build/outputs/apk/debug/app-debug.apk` (from computer connected to TV, or use a trusted file manager).
2. On Sony TV go to Apps and **open MIX TV as a normal app first**.
3. Test D-pad navigation, app launches, long-press OK, HDMI Inputs, Sony Settings, PS5, Netflix, Disney+ and soundbar volume via remote.
4. Only if all works, use `HOME DEFAULT` to attempt selection in Android Home settings. Some Sony firmware will not expose this setting. Do not disable Sony Home to force it.

## Rollback
- If used as normal app: simply press Sony Home to leave it.
- If made default: select the original launcher in Android Home settings, or uninstall MIX TV.
- ADB uninstall: `adb uninstall tv.mix.launcher`. This cannot uninstall any Sony package.
- No system configuration is modified by installation itself.

## Important limitations
- HDMI switching varies by Sony firmware; if its intent isn't supported, use the remote Input key.
- Some Sony firmwares block changing the Home launcher. Our app will still launch from the app list.
- Netflix/Disney+ app visibility depends on installed package and Android package visibility rules.
- GitHub Actions debug builds use ephemeral debug signing keys; updates from a later CI run may require uninstalling the previous app (which resets favorites).
- This is an installable **personal test build**, not a factory-tested commercial release. The Sony KD-55X9000H itself must be tested before declaring full compatibility.
