# Cozy Beach

Four little friends around a campfire on a low-poly beach, made as an animated wallpaper (Three.js).

## Android live wallpaper app

**Download:** https://github.com/TheProbyte/cozy-beach/releases/download/latest/CozyBeach.apk

1. Open the download on your phone. Android asks to allow installs from your browser once; allow it, then **Install**.
2. Open **Cozy Beach** and tap **Set as wallpaper**, then **Set wallpaper** (home screen, lock screen or both).
3. In the app, tap the beach to open the ⚙ settings. The wallpaper uses the same settings. On the wallpaper itself, three quick taps also show the gear.

4. Optional: tap **♪ Show my music on the sign** so Bo holds up what you're playing. This needs notification access, but the app only reads the song title and artist and nothing leaves the phone. If Android calls it a restricted setting, go to App info → ⋮ → Allow restricted settings first.

Phone touches: frame rate up to 120 fps (⚙ → Phone → Frame rate, 60 by default), it follows your clock, tilting the phone shifts the view a little, the night sky is darker (OLED battery saving), and the ⚙ **Phone** tab frames the scene (zoom, pan, palm trees).

No ads. The page and 3D engine are inside the app, so it works offline; only the live weather uses the internet.
It runs a battery-saving mode: lighter graphics, 24–30 frames a second (15 on low battery), and it stops drawing whenever the wallpaper is out of sight.

Each push to `main` builds a new APK (see `.github/workflows/build.yml`). Installing a newer APK updates the app in place.

## Windows (Lively Wallpaper)

**Download:** https://github.com/TheProbyte/cozy-beach/releases/download/latest/CozyBeach-Lively.zip

Drag the zip onto Lively Wallpaper (or **+** → pick the file). It needs the internet the first time it starts, to load the 3D engine.

## Surprises

Every few minutes something unexpected happens. What, is up to you to find out. ⚙ → Extras turns them off or changes how often they come.

## In a browser

- https://theprobyte.github.io/cozy-beach/
- Phone wallpaper mode: https://theprobyte.github.io/cozy-beach/?wallpaper

The live weather comes from Open-Meteo for Fresno, California.
