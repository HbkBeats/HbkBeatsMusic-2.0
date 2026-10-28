HbkBeats Music - Android music + video player (Kotlin, Jetpack Compose, libVLC)

BUILD THE APK
1. Install Android Studio (free) and open this folder (HbkBeatsMusic).
2. Let Gradle sync finish (needs internet the first time). Accept any prompt to update Gradle/plugins.
3. Debug APK: Build > Build Bundle(s) / APK(s) > Build APK(s).
   File appears in app/build/outputs/apk/debug/app-debug.apk
4. Copy to your phone and install (allow "install unknown apps").
5. Release APK: Build > Generate Signed Bundle / APK > APK, create a keystore, choose release.

PRIVATE SPACE
- Tap the 3-dot menu on any song/video: "Make private" (file) or "Make folder ... private".
- Private items vanish from the Music and Videos tabs and appear only in the Private tab.
- First time in the Private tab you create a 4-8 digit PIN.
- The space locks when you leave the app; private playback stops. Screenshots are blocked in it.
- NOTE: items are hidden inside this app only. Other apps (Gallery, file manager) can still see the files.
- If you forget the PIN, clear the app's data (this also clears the hidden list).

BUILD ON GITHUB (no Android Studio needed)
1. Create a new GitHub repository and upload everything inside this folder to its root
   (settings.gradle.kts must be at the top level, and the .github/workflows/build.yml file must be included).
2. Open the repo's Actions tab. The "Build APK" workflow runs on every push (or press Run workflow).
3. When it finishes (about 5-10 minutes), open the run and download "HbkBeatsMusic-debug-apk" under Artifacts.
4. Unzip it, copy app-debug.apk to your phone and install.

NEW IN THIS VERSION
- Home: spinning vinyl (or the song's cover art if tagged), recently played, your songs, videos.
- Bottom navigation: Home, Search, Library, Private. Tap the mini player for the full Now Playing screen.
- Now Playing: swipe up on the art or the bottom bar to pull up lyrics (synced .lrc lines highlight and scroll).
  Lyrics: looked up online from LRCLIB (needs internet, cached afterwards), or paste your own with "Edit / paste".
  Private songs are never looked up online.
- Search: songs, artists, videos and folders.
- Equalizer (Home or Now Playing icons): 24 presets incl. Reggae, Afrobeats, Amapiano, Dancehall, Drill, Hip-Hop,
  R&B, Trap, Afro House, Kwaito, Gospel + 10-band custom sliders and preamp.
- Effects: Chorus, Flanger, Reverb, Stereo widen, Compressor, Vocal remover. Press "Apply effects" (restarts audio engine briefly).
- Skins & colours (palette icon on Home): 8 skins + 8 accent colours.
