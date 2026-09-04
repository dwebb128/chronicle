# Running the phone and watch apps in emulators on Ubuntu

A start-to-finish setup for running both Chronicle apps in Android emulators on Ubuntu, without
Android Studio. Everything here was checked against **Ubuntu 24.04 LTS**; the only differences on
22.04 are called out where they matter.

| App | Module | Emulator you need |
| --- | --- | --- |
| Phone | `:mobile` | A Pixel AVD, API 30 or newer |
| Watch | `:app` | A Wear OS AVD, **API 34 or newer** |

The watch app sets `minSdk = 34`, so an older watch image rejects its APK with
`INSTALL_FAILED_OLDER_SDK`. The phone app sets `minSdk = 30`.

> **Note on "Pixel Watch".** The command-line tools ship no `pixel_watch` device profile — the Wear
> profiles are the generic `wearos_small_round`, `wearos_large_round`, `wearos_rect` and
> `wearos_square`. A Pixel Watch is a small round watch, so `wearos_small_round` is the right
> starting point; [§7](#7-matching-a-real-pixel-watch) shows how to match a specific model's
> resolution. Android Studio's Device Manager carries its own, longer device list and does offer
> named Pixel Watch profiles.

---

## 1. Check your hardware can do this

The emulator images are x86_64, so they need hardware virtualisation (KVM). Without it the emulator
either refuses to start or falls back to an interpreter that is far too slow to use.

```bash
sudo apt update
sudo apt install -y cpu-checker
kvm-ok
```

You want `KVM acceleration can be used`. If you instead get `KVM acceleration can NOT be used`,
virtualisation is off in your firmware — reboot into BIOS/UEFI and enable **Intel VT-x** or
**AMD-V** (often listed as SVM Mode).

This will not work inside most VMs or containers unless nested virtualisation is explicitly enabled
by the host. `ls /dev/kvm` failing is the quick tell.

## 2. Enable KVM for your user

```bash
sudo apt install -y qemu-system-x86 libvirt-daemon-system
sudo adduser "$USER" kvm
```

Then **log out and back in** — group membership is only picked up on a new login session. Confirm:

```bash
groups | grep -q kvm && echo "kvm group OK"
ls -l /dev/kvm          # should be group 'kvm', mode crw-rw----
```

Two things worth knowing:

- On Ubuntu 24.04 the package is **`qemu-system-x86`**. The old `qemu-kvm` name no longer exists and
  `apt install qemu-kvm` fails — a lot of older guides still tell you to install it.
- The Android emulator **bundles its own QEMU**, so strictly it only needs `/dev/kvm` access, not
  the distro's QEMU. Installing `qemu-system-x86` is still the simplest way to pull in the KVM
  plumbing and is harmless.

## 3. Install the JDK and the libraries the emulator needs

```bash
sudo apt install -y openjdk-17-jdk curl unzip
```

The Gradle build targets Java 17, which is what CI uses.

The emulator ships its own Qt, protobuf and glib, so the list of genuinely external libraries is
short. On a desktop install most are already present; on a minimal or server install they are not:

```bash
sudo apt install -y \
  libpulse0 libnss3 libnspr4 libx11-6 libxcb1 libxext6 libxi6 \
  libice6 libsm6 libxkbfile1 libdrm2 libexpat1 libgcrypt20 \
  libpng16-16t64 libgl1 libgbm1 libxcb-cursor0
```

Ubuntu 24.04 renamed a number of these with a `t64` suffix (the 64-bit-`time_t` transition), so
`libpng16-16` is now **`libpng16-16t64`** and `libasound2` is **`libasound2t64`**. On 22.04 use the
unsuffixed names.

## 4. Install the Android SDK

Skip to §5 if you already have an SDK — just make sure `emulator` and `platform-tools` are in it.

```bash
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME"

curl -sSLo cmdtools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdtools.zip -d tmp
mv tmp/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
rm -rf cmdtools.zip tmp
```

Put the tools on your `PATH` — add this to `~/.bashrc` so it survives a new shell:

```bash
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

Accept the licences and install the packages:

```bash
yes | sdkmanager --licenses > /dev/null
sdkmanager "platform-tools" "emulator" "platforms;android-36" "build-tools;36.0.0"
```

`platforms;android-36` and `build-tools;36.0.0` are what the project builds against
(`compileSdk = 36`).

## 5. Install the system images

```bash
# Phone — API 36 matches targetSdk. Anything from API 30 up will run the app.
sdkmanager "system-images;android-36;google_apis;x86_64"

# Watch — API 34 is the lowest the watch app accepts.
sdkmanager "system-images;android-34;android-wear;x86_64"
```

Each is roughly 1–1.5 GB.

Use `google_apis` (not `google_apis_playstore`) for the phone unless you specifically want the Play
Store on the device — the Play Store images are locked down and cannot be rooted with `adb root`.

Newer watch images exist (`system-images;android-36;android-wear-signed;x86_64`). They carry the
`android-wear-signed` tag rather than `android-wear`, which does not match the tag on the stock Wear
device profiles, so `avdmanager` may object when pairing the two. API 34 is the combination these
instructions were checked with; if you want a newer one, create it through Android Studio's Device
Manager instead.

Confirm what you have:

```bash
sdkmanager --list_installed
```

## 6. Create the two virtual devices

```bash
avdmanager create avd -n chronicle-phone \
  -k "system-images;android-36;google_apis;x86_64" -d pixel_7

avdmanager create avd -n chronicle-watch \
  -k "system-images;android-34;android-wear;x86_64" -d wearos_small_round
```

Answer `no` when asked whether you want to create a custom hardware profile.

`pixel_7` is the newest Pixel phone profile the command-line tools carry; `avdmanager list device`
shows the rest. The profile only sets screen size, density and RAM — it does not have to match the
phone you own.

Check they exist:

```bash
avdmanager list avd
```

## 7. Matching a real Pixel Watch

The generic `wearos_small_round` profile is close to a Pixel Watch but not identical. To match your
model exactly, edit the AVD's config after creating it:

```bash
nano ~/.android/avd/chronicle-watch.avd/config.ini
```

Set the three display keys to your watch's real values, for example:

```ini
hw.lcd.width=384
hw.lcd.height=384
hw.lcd.density=320
```

Look up the resolution and density for your specific model — they differ between Pixel Watch
generations and between the 41 mm and 45 mm sizes — and confirm before relying on the numbers above.
Getting this right matters for this app in particular: the watch UI is laid out for a small round
screen, and a wrong density is the quickest way to see a layout problem that is not really there.

## 8. Boot an emulator

Each `emulator` call runs in the foreground, so background it or use a second terminal:

```bash
emulator -avd chronicle-phone &
emulator -avd chronicle-watch &
```

Flags worth knowing:

| Flag | What it does |
| --- | --- |
| `-no-snapshot-load` | Cold boot, ignoring the saved snapshot. First thing to try when an AVD misbehaves. |
| `-wipe-data` | Factory reset — back to a clean install with no accounts. |
| `-gpu host` | Use the real GPU. Fastest, but unhappy with some Nvidia proprietary drivers. (`auto` is the default.) |
| `-gpu swiftshader` | Software rendering. Slower, but works everywhere — use it if the window is black or the emulator crashes on start. `swangle` and `software` are the other software modes; run `emulator -help-gpu` for the list your version accepts. |
| `-no-audio` | Skip audio entirely. Avoids PulseAudio problems if you are not testing playback. |
| `-no-window` | Headless. Fine for installing and reading `logcat`, useless for looking at the UI. |

**On Wayland** (the default GNOME session on Ubuntu 22.04 and 24.04) the emulator runs through
XWayland. If the window never appears or dies immediately, force the X11 backend:

```bash
QT_QPA_PLATFORM=xcb emulator -avd chronicle-phone
```

Wait for boot to finish, then check `adb` can see them:

```bash
adb wait-for-device
adb devices
```

You should get something like:

```
emulator-5554   device
emulator-5556   device
```

## 9. Build and install the apps

From the repository root:

```bash
./gradlew :mobile:installDebug   # phone
./gradlew :app:installDebug      # watch
```

`installDebug` builds and installs in one step.

With **both** emulators running, `adb` cannot guess which one you mean and the install fails.
`installDebug` honours `ANDROID_SERIAL`, so name the target:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :mobile:installDebug
ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug
```

Both apps share the applicationId `local.oss.chronicle`, so **one device can only hold one of
them** — that is why they go on separate emulators. Installing the phone app over the watch app on
the same device replaces it.

Launch without touching the UI (both apps use the same Activity class):

```bash
adb -s emulator-5554 shell am start -n local.oss.chronicle/.application.MainActivity
```

Follow the logs:

```bash
adb -s emulator-5554 logcat --pid=$(adb -s emulator-5554 shell pidof -s local.oss.chronicle)
```

## 10. Signing in, and reaching your Plex server

Both apps sign in with the **plex.tv/link short code**, so no browser is needed on the emulator:
read the code off the emulator screen and enter it at [plex.tv/link](https://plex.tv/link) in your
own browser. The two emulators hold separate sessions, so sign in on each.

For the server itself, the address depends on where Plex is running:

| Plex server location | Address to use in the app |
| --- | --- |
| Same machine as the emulator | `http://10.0.2.2:32400` |
| Another machine on your LAN | Its normal LAN address, e.g. `http://192.168.1.20:32400` |

`10.0.2.2` is the emulator's alias for the host loopback — **`localhost` inside the emulator is the
emulator itself**, not your machine, and is the single most common reason a local server appears
unreachable.

Plex account sign-in needs working internet in the emulator, which it inherits from the host. If DNS
fails inside the emulator but works on the host, restart it with an explicit resolver:

```bash
emulator -avd chronicle-phone -dns-server 8.8.8.8
```

## 11. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `/dev/kvm device permission denied` | Not in the `kvm` group, or you have not logged out and back in since `adduser`. See §2. |
| `KVM acceleration can NOT be used` | Virtualisation disabled in BIOS/UEFI, or you are inside a VM without nested virtualisation. |
| `Package qemu-kvm has no installation candidate` | Renamed on 24.04 — install `qemu-system-x86`. |
| `libpulse.so.0: cannot open shared object file` | `sudo apt install libpulse0`, or run with `-no-audio`. |
| Unable to locate package `libpng16-16` / `libasound2` | 24.04 `t64` renames — use `libpng16-16t64` / `libasound2t64`. |
| Emulator window never appears on Wayland | Force X11: `QT_QPA_PLATFORM=xcb emulator -avd …`. |
| Black screen, or a crash on start | `-gpu swiftshader`. Common with Nvidia proprietary drivers. |
| `INSTALL_FAILED_OLDER_SDK` | Installing the watch app on an API < 34 image. Recreate the AVD on API 34+. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A differently-signed copy is installed. `adb uninstall local.oss.chronicle`, then reinstall. |
| `adb: more than one device/emulator` | Both emulators running — set `ANDROID_SERIAL`, see §9. |
| App installs but the server is unreachable | Using `localhost` instead of `10.0.2.2`. See §10. |
| AVD boots to a blank or broken state | `emulator -avd <name> -no-snapshot-load`, then `-wipe-data` if that does not help. |
| `SDK location not found` from Gradle | Set `ANDROID_HOME`, or write `sdk.dir=$HOME/android-sdk` into `local.properties`. |

## 12. Removing it all

```bash
avdmanager delete avd -n chronicle-phone
avdmanager delete avd -n chronicle-watch
rm -rf "$HOME/android-sdk" "$HOME/.android"
```

`~/.android` holds the AVD disk images, which are the bulk of the space.
