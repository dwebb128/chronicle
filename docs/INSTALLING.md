# Building and installing Chronicle Epilogue

This branch builds **two apps** from one source tree:

| App | Gradle module | APK | `minSdk` | Target device |
| --- | --- | --- | --- | --- |
| Phone | `:mobile` | `mobile/build/outputs/apk/debug/mobile-debug.apk` | 30 (Android 11) | Pixel phone |
| Watch | `:app` | `app/build/outputs/apk/debug/app-debug.apk` | 34 (Android 14) | Pixel Watch |

Both sit on `:core`, a library module holding the Plex API client, the Room databases and the
playback service. The watch app is standalone — once it is signed in it needs no phone.

> The module named `:app` is the **watch** app. It kept that name so the Play publishing config,
> signing paths and CI artifact paths did not have to change; `:mobile` is the phone app.

Both APKs share the applicationId `local.oss.chronicle`, which is what lets Play treat them as one
listing and deliver the right one to each form factor. It also means **a single device can only
hold one of them at a time** — installing the phone build over the watch build on the same device
replaces it.

---

## 1. One-time setup

### 1.1 JDK 17

The build targets Java 17 and CI runs on it. A newer JDK will also work, but 17 is the supported
configuration.

```bash
java -version   # expect 17 or newer
```

### 1.2 Android SDK

You need **platform 36** and **build-tools 36.0.0** (`compileSdk`/`targetSdk` are both 36).

With Android Studio, install them under **Settings → Languages & Frameworks → Android SDK**.
From the command line, with no Android Studio:

```bash
mkdir -p "$HOME/android-sdk/cmdline-tools"
cd "$HOME/android-sdk"
curl -sSL -o cmdtools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdtools.zip -d cmdline-tools-tmp
mv cmdline-tools-tmp/cmdline-tools cmdline-tools/latest
rm -rf cmdtools.zip cmdline-tools-tmp

export ANDROID_HOME="$HOME/android-sdk"
yes | ./cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" --licenses
./cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

(On macOS use `commandlinetools-mac-*.zip`; on Windows the `-win-` archive.)

### 1.3 Point Gradle at the SDK

From the repository root:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

`local.properties` is gitignored — do not commit it. Exporting `ANDROID_HOME` works just as well.

### 1.4 Clone

```bash
git clone https://github.com/dwebb128/chronicle.git
cd chronicle
```

---

## 2. Build

```bash
./gradlew assembleDebug          # both APKs
./gradlew :mobile:assembleDebug  # phone only
./gradlew :app:assembleDebug     # watch only
```

For optimised builds use `assembleRelease`. Release builds are signed from `keystore.properties`
(see `keystore.properties.example`) or from the `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS`
/ `KEY_PASSWORD` environment variables, falling back to the debug key if neither is present.

---

## 3. Install on a Pixel phone

**Step 1 — enable developer options.** On the phone, **Settings → About phone** and tap **Build
number** seven times.

**Step 2 — enable USB debugging.** **Settings → System → Developer options → USB debugging**.

**Step 3 — connect over USB and install.**

```bash
adb devices                       # accept the "Allow USB debugging" prompt on the phone
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

Or let Gradle do both steps: `./gradlew :mobile:installDebug`.

To install over Wi-Fi instead (Android 11+), pair once under **Developer options → Wireless
debugging → Pair device with pairing code**:

```bash
adb pair <phone-ip>:<pairing-port>    # enter the 6-digit code shown on the phone
adb connect <phone-ip>:<port>         # the port under "Wireless debugging", not the pairing one
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

---

## 4. Install on a Pixel Watch

The watch app is standalone, so it installs directly — no companion app needed. A Pixel Watch has
no USB data port, so this goes over Wi-Fi.

**Step 1 — enable developer options.** On the watch, **Settings → System → About → Versions**, then
tap **Build number** seven times.

**Step 2 — enable debugging.** **Settings → Developer options**, turn on **ADB debugging** and
**Debug over Wi-Fi**. The screen then shows the watch's IP address.

**Step 3 — connect and install.** With your computer on the same Wi-Fi network as the watch:

```bash
adb connect <watch-ip>:5555
adb devices                       # accept "Always allow from this computer" on the watch
adb -s <watch-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

If more than one device is attached, `-s <watch-ip>:5555` is what keeps the watch APK off your
phone — worth being explicit about, since both APKs share an applicationId.

Watches with a USB data dock can skip the Wi-Fi steps and use plain `adb install`.

---

## 5. Testing both at once

Because the two APKs share an applicationId, keep them on separate devices — a Pixel phone and a
Pixel Watch — rather than trying to hold both on one. With both connected:

```bash
adb devices
# List of devices attached
# 1A2B3C4D5E6F        device      <- phone
# 192.168.1.42:5555   device      <- watch

adb -s 1A2B3C4D5E6F      install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb -s 192.168.1.42:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Both apps sign in with the **plex.tv/link** short code: the app shows a code, you enter it at
[plex.tv/link](https://plex.tv/link) in any browser, and the app picks up the token once you
confirm. They hold separate sessions, so sign in on each device.

Watch the logs of either with:

```bash
adb -s <device> logcat --pid=$(adb -s <device> shell pidof -s local.oss.chronicle)
```

### Emulators

- **Phone:** any Pixel AVD on API 30+.
- **Watch:** a **Wear OS** AVD on **API 34 or newer** — an older watch image rejects the APK with
  `INSTALL_FAILED_OLDER_SDK`. In Android Studio: **Device Manager → Add a device → Wear OS**.

With one emulator running, `./gradlew :mobile:installDebug` or `:app:installDebug` builds and
installs in a single step.

---

## 6. Verifying a build

The checks CI runs (`.github/workflows/ci.yml`) are:

```bash
./gradlew testDebugUnitTest   # unit tests across :core, :app and :mobile
./gradlew assembleDebug       # both debug APKs
```

Both are worth running before filing a change.

---

## 7. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `SDK location not found` | `local.properties` missing or `ANDROID_HOME` unset — see §1.3. |
| `Failed to find Platform SDK with path: platforms;android-36` | `sdkmanager "platforms;android-36"`. |
| `INSTALL_FAILED_OLDER_SDK` | Device below the module's minSdk: 30 for the phone app, 34 for the watch app. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A differently-signed copy is installed — often the *other* app, since they share an applicationId. `adb uninstall local.oss.chronicle`, then reinstall. |
| The wrong app installed on a device | Both APKs share an applicationId; pass `-s <serial>` and check you named the right APK. |
| `adb connect` to the watch times out | Watch and computer on different networks, or **Debug over Wi-Fi** switched itself off — it resets whenever the watch drops off Wi-Fi. |
| Device missing from `adb devices` | Re-accept the authorisation prompt on the device. |
| Gradle runs out of memory | Raise `org.gradle.jvmargs` in `gradle.properties` (defaults to `-Xmx2g`). |
