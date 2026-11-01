# Free Music — Android music player

Ek chhota, pura chalne wala Android music player jo do free, keyless sources se chalta
hai: **Internet Archive** (archive.org) aur **Audius**. App khulte hi album artwork ka
grid dikhta hai. Kotlin + Jetpack Compose + Media3 (ExoPlayer).

> **Legal note:** commercial catalog (Bollywood / Hollywood) kisi bhi app se free nahi
> chalaya ja sakta — uske liye licensed platform chahiye. Ye app sirf wahi music bajata
> hai jo public domain hai ya artist ne khud CC license ke saath share kiya hai.

## Screenshots

| Browse | Player | 
|---|---|
| ![Browse](docs/screenshot-home.png) | ![Player](docs/screenshot-player.png) |

_Note: ye UI previews hain jo app ke actual Compose theme (colours, type scale, layout)
se banaye gaye hain — real device screenshots nahi, kyunki build environment mein
emulator nahi tha._

## Download

Prebuilt APK: [**FreeMusic-1.4.apk**](https://github.com/technicalnihilist-lgtm/free-music-player-android/releases/latest)

Install karne ke liye phone par "Install unknown apps" allow karna padega.
Debug key se signed hai — apna release build banane ke liye neeche dekhein.

## Music sources

Dono sources **bina kisi API key, bina signup, bina account** ke chalte hain.

| Source | Catalog |
|---|---|
| **Internet Archive** (default) | Public domain aur Creative Commons audio — live concert recordings, 78rpm records, netlabels, classical, old radio |
| **Audius** | Open catalog, artists ke apne uploads; trending aur search |

App khulte hi **Archive** load hota hai; upar ke chips se **Audius** par switch kar sakte
hain. Dono jagah 2-column artwork grid, tap karke bajayein.

**Archive ke baare mein:** listing sasti hai, par asli MP3 ka URL har item ke andar alag
hota hai. Is liye list turant dikhti hai aur audio tap karne par resolve hota hai
(`archive.org/metadata/{id}`), phir player ko diya jaata hai.

**Audius ke baare mein:** read API mein har request par sirf ek `app_name` string jaati
hai. `GET /tracks/{id}/stream` asli audio deti hai un tracks ke liye jinme
`is_streamable` true hai. Audius kai discovery nodes par chalta hai, is liye app 4 known
hosts order se try karta hai.

## Features

- App khulte hi **album artwork ka 2-column grid**
- Do free sources: Internet Archive aur Audius — dono keyless
- Search — gaana, artist ya album ke naam se
- Background playback + notification aur lock-screen controls (Media3 MediaSession)
- Play / pause, next / previous, seek bar, shuffle, repeat-all
- Headphones nikalne par auto-pause
- Mini player + full-screen player with album art

## Build

```bash
# JDK 17 + Android SDK 34 chahiye
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Ya Android Studio mein `File > Open` → sync → `Build > Build APK(s)`.

## Tech

| | |
|---|---|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose (Material 3), Compose BOM 2024.06.00 |
| Playback | AndroidX Media3 1.3.1 (ExoPlayer + MediaSessionService) |
| Networking | OkHttp 4.12.0, `org.json` |
| Images | Coil 2.6.0 |
| AGP / Gradle | 8.5.2 / 8.7 |
| minSdk / targetSdk | 24 (Android 7.0) / 34 |

## Structure

```
app/src/main/java/com/freemusic/player/
  MainActivity.kt              entry point, notification permission
  data/InternetArchive.kt      archive.org search + lazy MP3 resolution
  data/Audius.kt               Audius API (no key) + node failover
  player/PlaybackService.kt    Media3 background service + notification
  player/PlayerViewModel.kt    MediaController + UI state flow
  ui/App.kt                    Compose UI (artwork grid, mini player, player)
  ui/PlayerIcons.kt            hand-built vector icons (keeps APK small)
  ui/theme/Theme.kt            dark colour scheme
```

Jamendo aur Spotify ke files (`data/Jamendo.kt`, `data/SpotifyAuth.kt`,
`data/SpotifyApi.kt`, `data/Prefs.kt`) repo mein pade hain par UI se hata diye gaye hain
— v1.2/v1.3 releases mein wo chal rahe the.

## Changelog

**v1.4** — Sirf do keyless sources: Internet Archive (default) aur Audius. Jamendo aur
Spotify UI se hata diye (code repo mein hai). App khulte hi artwork grid.

**v1.3** — Artwork grid home screen; Internet Archive source add hua.

**v1.2** — Spotify source: PKCE login, catalog search, play on active Spotify device.

**v1.1** — Audius source added (keyless, default).

**v1.0** — Pehla working build: Jamendo streaming, search, genres, background playback.

## License

Code MIT. Music apne respective platform ke terms ke under aata hai — Internet Archive
par public domain / CC items, Audius par artist ke apne uploads.
