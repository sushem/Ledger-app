# Ledger — Android app

A real, installable Android app for your monthly income/expense tracker. It wraps the
same dashboard in a lightweight WebView, stores all data on-device with `localStorage`
(no internet permission, no server, nothing leaves the phone), and works fully offline.

## What's inside
- `app/src/main/assets/index.html` — the entire app (dashboard, category breakdown,
  "+" entry sheet, settings with add/remove category) as one self-contained HTML/JS file.
- `MainActivity.kt` — a thin wrapper that loads that file into a full-screen WebView.
- No external libraries, no CDN calls, no ads, no analytics.

## Get an APK without installing anything (GitHub Actions)

This project includes `.github/workflows/build-apk.yml`, which builds a debug APK
automatically on GitHub's servers. Steps:

1. Create a new **empty** repository on [github.com](https://github.com/new) — any name,
   public or private both work. Don't add a README/gitignore when creating it (keeps it empty).
2. Unzip this project on your computer, then from inside the `android-ledger-app` folder run:
   ```
   git init
   git add .
   git commit -m "Ledger app"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
3. On GitHub, open your repo → **Actions** tab. A workflow run called "Build APK" starts
   automatically (takes ~2–4 minutes).
4. Once it finishes (green check), click into that run → scroll to **Artifacts** →
   download **ledger-debug-apk**. It's a zip containing `app-debug.apk`.
5. Transfer that `.apk` to your phone (email it to yourself, Google Drive, USB, whatever's
   easiest) and open it there. Android will ask to allow "install unknown apps" for
   whichever app you used to open it — allow that once, then tap install.

No Android Studio, no SDK, nothing installed on your machine — GitHub's runners do the
actual compiling. You can re-trigger a build any time from the Actions tab via
"Run workflow" (the `workflow_dispatch` trigger), without needing a new push.

## Alternative: build locally in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (free) on your computer.
2. Open Android Studio → **Open** → select this `android-ledger-app` folder.
3. Let it sync (first sync downloads Gradle 8.7 automatically — needs internet just this once).
4. Plug in your Android phone via USB with **USB debugging** enabled (Settings → Developer
   options), or use an emulator.
5. Click the green **Run ▶** button. The app installs and opens as "Ledger" with its own
   icon — from then on it launches like any other app, fully offline.

### To get an APK you can share/sideload without a cable
Build menu → **Build Bundle(s) / APK(s)** → **Build APK(s)**. Android Studio will show a
"locate" link to the generated `app-debug.apk` — copy that onto your phone and open it
to install (you'll need to allow "install unknown apps" for whichever app you transfer it with).

## Editing the app
Everything about the app's look and behavior lives in one file:
`app/src/main/assets/index.html`. Change colors, categories, or layout there and re-run —
no need to touch the Kotlin code unless you want to add native Android features later
(notifications, widgets, biometric lock, etc.).

## Notes
- minSdk 26 (Android 8.0+), covers the large majority of phones in use today.
- Data is stored per-app in the WebView's local storage. Uninstalling the app clears it —
  there's no cloud backup built in. If you want that, the natural next step is swapping
  `localStorage` for a small SQLite/Room database, which would also make export/backup easier.
