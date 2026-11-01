# Free Music — Android music player

Ek chhota, pura chalne wala Android music player jo chaar sources se chalta hai:
**Audius** (bina key), **Internet Archive** (bina key), **Jamendo** (free key) aur
**Spotify** (aapka Client ID + Premium). App khulte hi album artwork ka grid dikhta hai.
Kotlin + Jetpack Compose + Media3 (ExoPlayer).

> **Legal note:** commercial catalog (Bollywood / Hollywood) kisi bhi app se free nahi
> chalaya ja sakta — uske liye licensed platform chahiye. Is app mein Audius aur Jamendo
> wahi music dete hain jo artists ne khud upload kiya ya CC license ke saath share kiya.
> Spotify source official Web API use karta hai, aur gaana Spotify app ke andar bajta hai.

## Screenshots

| Browse | Player | Jamendo key |
|---|---|---|
| ![Browse](docs/screenshot-home.png) | ![Player](docs/screenshot-player.png) | ![Setup](docs/screenshot-setup.png) |

_Note: ye UI previews hain jo app ke actual Compose theme (colours, type scale, layout)
se banaye gaye hain — real device screenshots nahi, kyunki build environment mein
emulator nahi tha._

## Download

Prebuilt APK: [**FreeMusic-1.3.apk**](https://github.com/technicalnihilist-lgtm/free-music-player-android/releases/latest)

Install karne ke liye phone par "Install unknown apps" allow karna padega.
Debug key se signed hai — apna release build banane ke liye neeche dekhein.

## Music sources

| Source | Key chahiye? | Audio kahan se | Catalog |
|---|---|---|---|
| **Audius** (default) | Nahi — bas `app_name` string | Seedha app mein (ExoPlayer) | Open catalog, artists ke apne uploads |
| **Internet Archive** | Nahi | Seedha app mein (ExoPlayer) | Public domain + Creative Commons audio, live concerts, 78rpm, netlabels |
| **Jamendo** | Haan, free Client ID | Seedha app mein (ExoPlayer) | 600,000+ Creative Commons tracks |
| **Spotify** | Haan, Client ID + **Premium** | Spotify app ke andar | Poora licensed catalog |

### Audius

Read API bina credentials ke chalta hai: har request mein sirf `app_name` bhejna hota hai.
`GET /tracks/{id}/stream` asli audio deti hai un tracks ke liye jinme `is_streamable`
true hai. Audius kai discovery nodes par chalta hai, is liye app 4 known hosts order se
try karta hai.

### Jamendo

[devportal.jamendo.com](https://devportal.jamendo.com/) par free account → naya app →
**Client ID** copy karein → app ke settings icon se paste karein. Read-only plan turant
milta hai.

### Spotify — setup (ek baar)

1. [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard) par app banayein
2. **Redirect URI** add karein: `freemusic://callback` (exactly, bina slash ke)
3. **Client ID** copy karein — Client **secret ki zarurat nahi hai**, use kabhi na daalein
4. App mein Spotify chip chunein, Client ID paste karein, "Login with Spotify" dabayein

Kaam karne ke liye: phone mein **Spotify app installed** ho, aur us account par
**Premium** ho. Ek baar Spotify app mein koi gaana baja lein taaki wo "active device"
ban jaye — phir is app se track choose karne par wahi bajega.

**Kaise kaam karta hai:** login Authorization Code + PKCE flow se hota hai (client secret
ke bina, jo mobile apps ke liye Spotify ka recommended flow hai). Uske baad app Web API
se search karti hai aur `PUT /me/player/play` se Spotify ko bolti hai ki track bajaye.
Audio app ke andar kabhi nahi aata — is liye background playback, seek bar aur mini
player Spotify source par laagu nahi hote; wo controls Spotify app mein hain.

## Features

- Chaar sources: Audius (default, keyless), Internet Archive, Jamendo, Spotify
- App khulte hi **album artwork ka 2-column grid** — tap karke bajayein
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
  MainActivity.kt              entry point, notifications, Spotify redirect handling
  data/Audius.kt               Audius API (no key) + node failover
  data/InternetArchive.kt      archive.org search + lazy MP3 resolution
  data/Jamendo.kt              Jamendo API + JSON parsing
  data/SpotifyAuth.kt          Authorization Code + PKCE login (no client secret)
  data/SpotifyApi.kt           Web API search + play-on-device
  data/Prefs.kt                API keys and Spotify tokens
  player/PlaybackService.kt    Media3 background service + notification
  player/PlayerViewModel.kt    MediaController + UI state flow
  ui/App.kt                    Compose UI (browse, source switcher, players)
  ui/PlayerIcons.kt            hand-built vector icons (keeps APK small)
  ui/theme/Theme.kt            dark colour scheme
```

## Changelog

**v1.3** — Home screen ab album artwork ka 2-column grid hai, app khulte hi load hota
hai. Internet Archive source add hua (keyless, public domain + CC audio); uske MP3 URL
per item lazily resolve hote hain, is liye list turant dikhti hai.

**v1.2** — Spotify source: PKCE login, catalog search, play on active Spotify device
(Premium + Spotify app required). Note: the official `spotify-auth` 2.1.0 AAR was not
used — it has no PKCE support and its implicit-grant path was sunset on 27 Nov 2025, so
the documented PKCE flow is implemented directly.

**v1.1** — Audius source added (keyless, default). Jamendo optional; source switcher chips.

**v1.0** — Pehla working build: Jamendo streaming, search, genres, background playback.

## License

Code MIT. Music apne respective platform ke terms ke under aata hai — Audius par artist
ke apne uploads, Jamendo par CC licenses, Spotify par Spotify ke terms.
