# Automated Screenshot Generation

Chronicle is now a Wear OS app. Play Store listing screenshots must be captured on a Wear OS
watch or watch emulator (Pixel Watch 4 / Wear OS 6, matching this app's `minSdk`/`targetSdk`).

## Manual Screenshots

```bash
# 1. Capture raw screenshots on a watch/watch emulator
# 2. Save to images/screenshots/
# 3. Run the generation script
./scripts/generate-playstore-graphics.sh
```

**See** [`scripts/README.md`](README.md) for detailed instructions.

> **Note:** This repo cannot install an Android/Wear OS emulator system image (Google's Maven is
> blocked in this environment), so the exact watch-emulator capture steps below have not been
> verified end to end here. In outline: start a Wear OS emulator (or connect a physical watch)
> with USB/ADB debugging enabled, install the debug APK, navigate to each screen manually, and
> use `adb exec-out screencap -p > screenshot.png` (or Android Studio's screenshot tool) to
> capture each one.

## Removed: automated Fastlane Screengrab flow

An earlier version of this project used Fastlane Screengrab (`Screengrabfile`) plus an Espresso
test (`ScreenshotTest.kt`) to script screenshot capture on a phone. Both were removed as part of
the Wear OS conversion: Espresso and the `screengrab` dependency are gone, and the old test
navigated phone-only screens (Home, Search, tabs) that no longer exist in the Compose-for-Wear-OS
UI. There is currently no automated screenshot pipeline — screenshots must be captured manually
per the section above. Re-introducing an automated flow for Wear (e.g. via `UiAutomator` against
the new Compose screens) is a reasonable follow-up but is not attempted here, since it cannot be
verified without a working Wear OS emulator.

## Additional Resources

- [Wear OS screenshot guidelines](https://developer.android.com/training/wearables/apps/creating-app#screenshots)
- [Play Store Screenshot Guidelines](https://support.google.com/googleplay/android-developer/answer/9866151)
