# NXT IPTV

Simple, fast IPTV player for Android TV and phones, built for remote-control use.
*Android TV ve telefonlar için kumandayla kullanıma göre tasarlanmış, sade ve hızlı bir IPTV oynatıcı.*

## Features / Özellikler

- M3U / M3U8 playlists from a URL or a file (including USB drives); multiple playlists, last one remembered
- Category bar (Left/Right) + channel list + large live preview, fullscreen with Up/Down zapping
- Favorites (hold OK), search, jump to channel by number
- English and Turkish UI, black theme with orange accent
- Runs on Android 5.0+ (minSdk 21) — works on low-end TV boxes and Android-compatible TVs

## Remote control / Kumanda

| Key | Lists | Fullscreen |
|---|---|---|
| Up / Down | Move | Previous / next channel |
| Left / Right | Change category | Show info |
| OK | Play, then fullscreen | Back to lists |
| Hold OK | Toggle favorite | Toggle favorite |
| Back | Fullscreen | Back to lists |
| Hold Back | Exit (asks first) | Exit (asks first) |
| 0–9 | Jump to channel number | Jump to channel number |

## Build / Derleme

Requires JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it can be sideloaded directly.

## Notes

NXT IPTV is only a player: it ships no channels or content. A public
[iptv-org](https://github.com/iptv-org/iptv) Turkey playlist is added on first launch as an example.
