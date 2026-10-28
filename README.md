# YTXTube

A lightweight YouTube and YouTube Music client with a modern Liquid Glass UI, ad-free playback, endless personalised Shorts, synced Apple Music-style lyrics, and high-quality downloads.

## Features

- Liquid Glass interface across the player, Shorts and music mini player
- Ad-free YouTube and YouTube Music playback
- Personalised Shorts feed with a one-tap fresh batch and loading animation
- Synced lyrics with an Apple Music-style animated lyric view
- High-quality video and audio downloads
- Search, channels, playlists and community posts
- Background playback, picture-in-picture and Android TV / D-pad support
- Built-in update checker: new GitHub releases are detected automatically and can be installed from inside the app

## Download

[Download the latest YTXTube.apk](https://github.com/Koustubh12345/YTXTube/releases/latest/download/YTXTube.apk)

Version 1.1.0 · Android 7.0+ · one APK for phones, tablets and Android TV.

## Build

```bash
./gradlew :app:assembleRelease
```

The release APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Updates

The app reads the latest GitHub release at launch. When a newer `versionName` is published, a liquid-glass dialog offers **Later** or **Download & install**; the APK downloads from the release asset and opens the system installer.

Licensed under GPLv3. See [LICENSE](LICENSE).
