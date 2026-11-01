# Free Music — Android music player

Ek chhota, pura chalne wala Android music player jo **do legal, open music catalogs** se
stream karta hai: **Audius** (bina kisi API key ke) aur **Jamendo** (free key chahiye).
Kotlin + Jetpack Compose + Media3 (ExoPlayer).

> **Legal note:** duniya ke saare commercial gaane (Bollywood / Hollywood) kisi bhi app se
> free nahi chalaye ja sakte — wo catalog licensed hota hai aur uske liye Spotify/Apple
> jaisa platform ya licensing deal chahiye. Ye app wahi music bajata hai jo artists ne
> khud upload kiya hai ya CC license ke saath share kiya hai, is liye ye 100% legal hai.

## Screenshots

| Browse | Player | Jamendo key |
|---|---|---|
| ![Browse](docs/screenshot-home.png) | ![Player](docs/screenshot-player.png) | ![Setup](docs/screenshot-setup.png) |

_Note: ye UI previews hain jo app ke actual Compose theme (colours, type scale, layout)
se banaye gaye hain — real device screenshots nahi, kyunki build environment mein
emulator nahi tha._

## Download

Prebuilt APK: [**FreeMusic-1.1.apk**](https://github.com/technicalnihilist-lgtm/free-music-player-android/releases/latest)

Install karne ke liye phone par "Install unknown apps" allow karna padega.
Debug key se signed hai — apna release build banane ke liye neeche dekhein.

## Music sources

App khulte hi **Audius** par chalta hai — koi signup, koi key nahi. Upar ke chips se
aap **Jamendo** par switch kar sakte hain.

| Source | Key chahiye? | Catalog |
|---|---|---|
| **Audius** | Nahi — bas ek `app_name` string | Open catalog, artists ke apne uploads |
| **Jamendo** | Haan, free Client ID | 600,000+ Creative Commons tracks |

Audius ka read API bina credentials ke chalta hai: har request mein sirf `app_name`
bhejna hota hai, jo aap khud choose karte hain. `GET /tracks/{id}/stream` asli audio
deti hai (30-second preview nahi) un tracks ke liye jinme `is_streamable` true hai.
Audius kai discovery nodes par chalta hai, is liye app 4 known hosts ko order se try
karta hai.

Jamendo key ke liye: [devportal.jamendo.com](https://devportal.jamendo.com/) par free
account banayein → naya app banayein → **Client ID** copy karein → app ke settings icon
se paste karein. Read-only plan turant mil jata hai.

## Features

- Do sources: Audius (default, keyless) aur Jamendo
- Search — gaana, artist ya album ke naam se
- Genre filter — Pop, Rock, Electronic, Hip-Hop, Jazz, Classical, Ambient, Lounge, World
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
  data/Audius.kt               Audius API (no key) + node failover
  data/Jamendo.kt              Jamendo API + JSON parsing
  data/Prefs.kt                Jamendo client id storage
  player/PlaybackService.kt    Media3 background service + notification
  player/PlayerViewModel.kt    MediaController + UI state flow
  ui/App.kt                    Compose UI (browse, source switcher, players)
  ui/PlayerIcons.kt            hand-built vector icons (keeps APK small)
  ui/theme/Theme.kt            dark colour scheme
```

## Changelog

**v1.1** — Audius source added (keyless, default). Jamendo ab optional; key ki hard gate
hati, settings icon se key manage hoti hai. Source switcher chips.

**v1.0** — Pehla working build: Jamendo streaming, search, genres, background playback.

## License

Code MIT. Music apne respective platform ke terms ke under aata hai — Audius par artist
ke apne uploads, Jamendo par CC licenses (har track ka `license_ccurl` API response mein
milta hai).
