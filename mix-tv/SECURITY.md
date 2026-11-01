# Security design and review checklist

This project uses native Android APIs and zero external runtime libraries.

Android manifest:
- No <uses-permission> declarations (no INTERNET, ACCESSIBILITY, WRITE_SETTINGS, PACKAGE_USAGE_STATS).
- Declares HOME only as a selectable handler; no command ever makes it the default automatically.
- No receivers, background services, shell commands, network communication, tracking or debloat scripts.
- Only shared preferences store selected theme and favorite package IDs locally.

Test on a Sony KD-55X9000H before default Home:
[ ] App opens from Sony stock Home
[ ] All Apps loads; D-pad navigation, OK and Back work
[ ] Long OK pin/unpin and move survive app restart
[ ] Netflix/Disney+/YouTube open and Back returns
[ ] Settings shortcut opens
[ ] Input picker works OR graceful fallback message
[ ] PS5 HDMI picture and sound unaffected
[ ] Volume hold, eARC, CEC and soundbar still work
[ ] Removing MIX TV returns to Sony Home

Build artifact check:
- Inspect AndroidManifest.xml and read this checklist.
- Use `aapt dump permissions app-debug.apk`: should show no permissions.
- Do not sideload a third-party repackaging of this app.
