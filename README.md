# Chronicle Epilogue - Audiobook Player for Plex

> **Unofficial fork.** This is an unofficial, community-maintained fork of
> [mattttvaughn/chronicle](https://github.com/mattttvaughn/chronicle) whose purpose is to **add Wear
> OS support** to the original Android app. It is not affiliated with, endorsed by, or supported by
> the original author or by Plex. The original project was unmaintained for a while; this fork
> continues development and adds a watch app alongside the phone one. We acknowledge and appreciate
> the excellent work done by the original developer,
> [@mattttvaughn](https://github.com/mattttvaughn). For the original, unmodified app, use upstream.

An audiobook player for Plex that runs on **both your phone and your watch**. Stream and download
audiobooks hosted on your Plex server — on an Android phone, or standalone on a Wear OS watch with
no phone required once it's set up.

| App | Gradle module | Runs on |
| --- | --- | --- |
| Phone | `:mobile` | Android 11+ (API 30) |
| Watch | `:app` | Wear OS 4+ (API 34), standalone |

Both are built from the shared `:core` module, which holds the Plex API client, the Room
databases and the playback service.

> **Note:** The Chronicle Epilogue app is being submitted to the Google Play Store! If you'd like to access the closed beta testing, you can request access by joining our [Google Group for alpha testing](https://groups.google.com/g/chronicle-app-alpha-testing)/[Google Group for beta testing](https://groups.google.com/g/chronicle-app-beta-testing).

### Features

 - Sync audiobook progress on device
 - Support for file formats: mp3, m4a, m4b
 - Adjustable playback speed
 - Auto-rewind
 - Sleep timer
 - Skip silent audio
 - Download books for playing any time, even when offline

### Screenshots

For more screenshots and information, visit [www.chronicleapp.net](https://www.chronicleapp.net)

> **Note:** The screenshots linked above show the phone app. The Wear OS UI is not pictured yet.

### Installing from source

See [docs/INSTALLING.md](docs/INSTALLING.md) for how to build both apps and sideload them onto a
Pixel phone and a Pixel Watch (or their emulators). On Ubuntu,
[docs/EMULATOR-UBUNTU.md](docs/EMULATOR-UBUNTU.md) sets up both emulators from scratch.

### Reporting Bugs

Found a bug? Please report it in the [GitHub Issues](https://github.com/germann/chronicle/issues) section. Your feedback helps improve the app for everyone!

### Useful Links

 - [Plex Audiobook Guide](https://github.com/seanap/Plex-Audiobook-Guide)
 - [Chronicle subreddit](https://www.reddit.com/r/ChronicleApp/)

### License

This project uses a dual licensing model:

- **Source Code**: Licensed under the [GNU General Public License v3.0](LICENSE)
- **Branding Assets**: The Chronicle Epilogue logo, app icons, and promotional graphics are
  [All Rights Reserved](ASSETS-LICENSE)

If you fork this project and intend to publish your app based on it, you must replace the branding assets with your own.
See [ASSETS-LICENSE](ASSETS-LICENSE) for details.

