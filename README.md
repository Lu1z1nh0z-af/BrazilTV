# Brazil TV v2 — polished Android TV IPTV player

Version 2 turns the basic player into a TV-style interface.

## Included

- Large-screen Android TV UI
- D-pad/remote navigation
- Search
- Category filtering from `group-title`
- Favorites persisted locally
- Last-channel memory
- Channel numbering
- Full-screen Media3 playback
- Dynamic refresh from the Brazil iptv-org M3U
- Graceful playlist/network error state
- Landscape-first presentation

Playlist:
https://iptv-org.github.io/iptv/countries/br.m3u

## Build

Open in Android Studio with JDK 17 and Android SDK 36:

    ./gradlew assembleDebug

APK:
    app/build/outputs/apk/debug/app-debug.apk

Install:
    adb install -r app/build/outputs/apk/debug/app-debug.apk

## Notes

The playlist is not bundled; it is fetched at runtime. Therefore channels and URLs can change without rebuilding the application.

The current v2 intentionally avoids a third-party image-loading library. Channel logos are parsed from `tvg-logo`, but the UI uses a safe fallback icon. A future v2.1 can add Coil image caching, EPG, program cards, automatic retries and a "channel zapping" mode.

This app is a player for the supplied public playlist. Use streams only where you have the appropriate rights.
