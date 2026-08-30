# Installing Chronicle Epilogue from source

This guide covers building and sideloading the app from this repository.

> **Important — there is only one app on this branch.**
> The `claude/chronicle-android-to-watch-uuur6z` branch (PR #1) *converts* Chronicle from a
> phone app into a standalone **Wear OS** app. It does not add a watch variant alongside the
> phone one — it replaces it. There are no product flavors, and the manifest declares
> `<uses-feature android:name="android.hardware.type.watch" android:required="true" />`,
> so this branch produces a watch APK and nothing else.
>
> If you want the **phone** build, you must build it from `main`, which is the last
> pre-conversion state of the app. Both paths are documented below.

| Target | Branch | `minSdk` | Notes |
| --- | --- | --- | --- |
| Wear OS (watch) | `claude/chronicle-android-to-watch-uuur6z` | 34 (Android 14) | Standalone; no companion phone app required |
| Android (phone) | `main` | 30 (Android 11) | Pre-conversion phone app |

---

## 1. One-time setup

### 1.1 JDK 17

The Gradle build targets Java 17 (`sourceCompatibility`/`targetCompatibility = VERSION_17`,
`jvmTarget = "17"`), and CI runs on `java-version: '17'`. A newer JDK will also run the build,
but 17 is the supported configuration.

```bash
java -version   # expect 17 (or newer)
```

### 1.2 Android SDK

You need the Android SDK with **platform 36** and **build-tools 36.0.0** (the project sets
`compileSdk = 36` / `targetSdk = 36`).

If you use Android Studio, install these via **Settings → Languages & Frameworks → Android SDK**.

To set the SDK up from the command line with no Android Studio:

```bash
# Download the command-line tools
mkdir -p "$HOME/android-sdk/cmdline-tools"
cd "$HOME/android-sdk"
curl -sSL -o cmdtools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdtools.zip -d cmdline-tools-tmp
mv cmdline-tools-tmp/cmdline-tools cmdline-tools/latest
rm -rf cmdtools.zip cmdline-tools-tmp

# Accept licences and install the packages the build needs
export ANDROID_HOME="$HOME/android-sdk"
yes | ./cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" --licenses
./cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

(On macOS substitute `commandlinetools-mac-*.zip`; on Windows use the `-win-` archive.)

### 1.3 Point Gradle at the SDK

From the repository root:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

`local.properties` is already in `.gitignore` — do not commit it. Setting the `ANDROID_HOME`
environment variable instead works equally well.

### 1.4 Clone the repository

```bash
git clone https://github.com/dwebb128/chronicle.git
cd chronicle
```

---

## 2. Wear OS build (this branch)

### 2.1 Build the APK

```bash
git checkout claude/chronicle-android-to-watch-uuur6z
./gradlew assembleDebug
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

For an optimised build use `./gradlew assembleRelease`. Release builds are signed with the
config in `keystore.properties` (see `keystore.properties.example`) or with the
`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` environment variables. If
neither is present, the release build falls back to the debug signing key.

### 2.2 Install onto a physical watch

The app is **standalone** (`com.google.android.wearable.standalone = true`), so it installs
directly onto the watch — you do not need a paired phone app.

**Step 1 — enable developer options on the watch.** On the watch, open
**Settings → System → About → Versions** and tap **Build number** seven times.

**Step 2 — enable debugging.** Go back to **Settings → Developer options** and turn on
**ADB debugging** and **Debug over Wi-Fi**. The Developer options screen shows the watch's IP
address once Wi-Fi debugging is active.

**Step 3 — connect and install.** With your computer on the same Wi-Fi network:

```bash
adb connect <watch-ip>:5555
adb devices                       # confirm the watch is listed
adb -s <watch-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Accept the **Always allow from this computer** prompt on the watch the first time.

`-r` reinstalls over an existing copy while keeping its data. If you hit
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` (usually a signing-key change between a Play Store copy
and your local build), uninstall first with `adb -s <watch-ip>:5555 uninstall local.oss.chronicle`.

Watches with a USB data dock can skip the Wi-Fi steps and use plain `adb install` over USB.

### 2.3 Install onto a Wear OS emulator

Create a **Wear OS** AVD running **API 34 or newer** — the branch sets `minSdk = 34`, so an
API 33 or older watch image will reject the APK with `INSTALL_FAILED_OLDER_SDK`. In Android
Studio: **Device Manager → Add a device → Wear OS**, then pick a system image of API 34+.

With the emulator running, `./gradlew installDebug` builds and installs in one step.

### 2.4 First run

The watch build authenticates with the **plex.tv/link** code flow rather than a browser
redirect. On first launch the watch shows a short code; enter it at
[plex.tv/link](https://plex.tv/link) on any browser, and the watch picks up the token once you
confirm. Then choose your Plex server and audiobook library on the watch.

---

## 3. Android phone build (from `main`)

This branch does **not** produce a phone APK. Build the pre-conversion app from `main`:

```bash
git checkout main
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`main` sets `minSdk = 30`, so any phone on Android 11 or newer will accept it.

Note that `main` and the Wear branch share the same `applicationId`
(`local.oss.chronicle`) and the same `versionCode`. A device can therefore hold only one of
them at a time, and swapping between the two on one device requires an uninstall first.

### Sideloading the watch APK onto a phone

`adb install` does not enforce `uses-feature` filtering the way the Play Store does, so the
watch APK can technically be pushed onto a phone. It is not worth doing: the entire UI is
built with Compose for Wear OS and is laid out for a small round screen. Use the `main` build
for phones.

---

## 4. Verifying your build

The checks CI runs (`.github/workflows/ci.yml`) are:

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK
```

Both are worth running locally before you file a change.

## 5. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `SDK location not found` | `local.properties` is missing or `ANDROID_HOME` is unset — see §1.3. |
| `Failed to find Platform SDK with path: platforms;android-36` | Install the platform: `sdkmanager "platforms;android-36"`. |
| `INSTALL_FAILED_OLDER_SDK` | The watch or emulator is below API 34. The Wear branch requires Android 14+. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A differently-signed copy is installed. `adb uninstall local.oss.chronicle`, then reinstall. |
| `adb connect` times out | The watch and computer are on different networks, or **Debug over Wi-Fi** turned itself off — it resets when the watch disconnects from Wi-Fi. |
| Watch not listed in `adb devices` | Re-accept the **Always allow from this computer** prompt on the watch. |
| Gradle runs out of memory | Raise `org.gradle.jvmargs` in `gradle.properties` (defaults to `-Xmx2g`). |
