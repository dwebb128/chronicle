# Building and installing Chronicle Epilogue

This branch builds **two apps** from one source tree:

| App | Gradle module | APK | `minSdk` | Target device |
| --- | --- | --- | --- | --- |
| Phone | `:mobile` | `mobile/build/outputs/apk/debug/mobile-debug.apk` | 30 (Android 11) | Pixel phone |
| Watch | `:wear` | `wear/build/outputs/apk/debug/wear-debug.apk` | 34 (Android 14) | Pixel Watch |

Both sit on `:core`, a library module holding the Plex API client, the Room databases and the
playback service. The watch app is standalone — once it is signed in it needs no phone.

> The modules are named for the form factor they target: `:wear` is the watch app, `:mobile` is the
> phone app. (`:wear` was called `:app` until recently — older notes and branches may still say so.)

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
./gradlew :wear:assembleDebug     # watch only
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
adb -s <watch-ip>:5555 install -r wear/build/outputs/apk/debug/wear-debug.apk
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
adb -s 192.168.1.42:5555 install -r wear/build/outputs/apk/debug/wear-debug.apk
```

Both apps sign in with the **plex.tv/link** short code: the app shows a code, you enter it at
[plex.tv/link](https://plex.tv/link) in any browser, and the app picks up the token once you
confirm. They hold separate sessions, so sign in on each device.

Watch the logs of either with:

```bash
adb -s <device> logcat --pid=$(adb -s <device> shell pidof -s local.oss.chronicle)
```

## 5a. Running in an emulator

> **On Ubuntu?** [`EMULATOR-UBUNTU.md`](EMULATOR-UBUNTU.md) is a start-to-finish setup for
> Ubuntu 22.04/24.04 — KVM, the exact apt packages, both AVDs and a troubleshooting table.

An emulator is the quickest way to try either app without owning the hardware. Both are x86_64
images, so they need **hardware virtualisation** — KVM on Linux, Hypervisor.framework on macOS,
WHPX/Hyper-V on Windows. On a machine without it (most CI runners, containers and VMs) the
emulator either refuses to start or falls back to software rendering that is too slow to be
usable. Check on Linux with `ls /dev/kvm`.

### From Android Studio

**Device Manager → Add a device**, then:

- **Phone:** any Pixel profile with a system image of **API 30 or newer**.
- **Watch:** the **Wear OS** category, with a system image of **API 34 or newer** — the watch app
  sets `minSdk = 34`, so an older image rejects the APK with `INSTALL_FAILED_OLDER_SDK`.

Pick the module from the run-configuration dropdown (`mobile` or `wear`) and press Run.

### From the command line

Install the emulator and a system image. These IDs are current:

```bash
export ANDROID_HOME="$HOME/android-sdk"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

# Phone — API 36 matches compileSdk/targetSdk; anything from API 30 up will run the app
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "emulator" \
  "system-images;android-36;google_apis;x86_64"

# Watch — API 34 is the lowest the watch app accepts
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "emulator" \
  "system-images;android-34;android-wear;x86_64"
```

On an Apple Silicon Mac substitute `arm64-v8a` for `x86_64` in both image IDs.

Create the virtual devices:

```bash
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"

"$AVDMANAGER" create avd -n chronicle-phone \
  -k "system-images;android-36;google_apis;x86_64" -d pixel_7

"$AVDMANAGER" create avd -n chronicle-watch \
  -k "system-images;android-34;android-wear;x86_64" -d wearos_small_round
```

Run `"$AVDMANAGER" list device` to see the other device profiles.

Boot one (each call blocks, so use a separate terminal or background it):

```bash
"$ANDROID_HOME/emulator/emulator" -avd chronicle-phone &
"$ANDROID_HOME/emulator/emulator" -avd chronicle-watch &
```

Useful flags: `-no-snapshot-load` for a cold boot, `-wipe-data` to reset to a clean install, and
`-no-window` for a headless run (fine for install and `logcat`, no use for looking at the UI).

Wait for the device to finish booting, then install:

```bash
adb wait-for-device
adb devices                      # emulator-5554, emulator-5556, ...

./gradlew :mobile:installDebug   # phone emulator
./gradlew :wear:installDebug      # watch emulator
```

`installDebug` builds and installs in one step. With **both** emulators running, `adb` cannot
guess which you mean, so name the target — `installDebug` reads `ANDROID_SERIAL`:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :mobile:installDebug
ANDROID_SERIAL=emulator-5556 ./gradlew :wear:installDebug
```

Launch without touching the UI:

```bash
adb -s emulator-5554 shell am start -n local.oss.chronicle/.application.MainActivity
```

### Signing in on an emulator

Both apps use the plex.tv/link code flow, which needs no browser on the device — read the code off
the emulator screen and enter it at [plex.tv/link](https://plex.tv/link) on your own machine.

The emulator reaches your host at **10.0.2.2**, not `localhost`. A Plex server running on the same
machine as the emulator is therefore `http://10.0.2.2:32400`, and a server elsewhere on your LAN
works by its normal address. Plex account sign-in needs the emulator to have working internet,
which it inherits from the host.

---

## 6. Verifying a build

The checks CI runs (`.github/workflows/ci.yml`) are:

```bash
./gradlew testDebugUnitTest   # unit tests across :core, :wear and :mobile
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
