# Expense Tracker — Android app

A real, installable Android app for your monthly income/expense tracker. It wraps the
dashboard in a lightweight WebView, stores data on-device with `localStorage`, and now
optionally syncs to your own Firebase project once you sign in with Google.

## What's inside
- `app/src/main/assets/index.html` — the entire app (dashboard, category breakdown,
  "+" entry sheet, month navigation, settings, theme editor, backup export/import,
  account/sync) as one self-contained HTML/JS file.
- `MainActivity.kt` — loads that file into a full-screen WebView, plus a JS bridge that
  hands off to native Android features: the share sheet & file picker for backups, and
  Google Sign-In + Firestore for cloud sync.
- No ads, no analytics, no third-party code beyond Firebase/Google Sign-In itself.

## What's new in this build
- **Debug and release build variants**: CI now builds and uploads both — see
  `Expense Tracker-debug-apk` and `Expense Tracker-release-apk` under each Actions run's Artifacts.
  Both are signed with the same committed `debug.keystore` (see the signing note further
  down), so either installs fine by sideloading; release just has `debuggable=false` and
  no `-debug` suffix on the version name.
- **Firebase Analytics**: `firebase-analytics` is included for basic usage tracking
  (screen views, session counts) — no event code has been added beyond what Firebase
  collects automatically. If your Firebase project was created without Analytics enabled,
  turn it on under Project Settings → Integrations → Google Analytics, or events won't
  show up in the Firebase console.
- **Export as Excel**: Settings → Backup → "Export as Excel (.xlsx)". Generates a workbook
  with a **Summary** sheet (opening/income/expense/closing balance per month) plus one
  sheet per month with every transaction, and hands it to the share sheet like the other
  exports. Built with a small hand-written OOXML writer instead of a library like Apache
  POI, which has a history of Android compatibility problems (method-count bloat, JVM-only
  XML APIs) that aren't worth the risk here.
- **Loading state on Sync/Restore**: both buttons now show a spinner and disable
  themselves while the Firestore call is in flight, so it's clear something's happening
  on a slow connection instead of looking unresponsive.
- **Google Sign-In + cloud sync**: Settings → Account → "Sign in with Google". Once
  signed in you get "Sync to cloud" (push what's on this phone to Firestore) and
  "Restore from cloud" (pull it back down — handy on a new phone). Sync is manual/explicit
  by design, same as the existing backup export/import, so nothing overwrites silently.
- Firestore uses two separate top-level collections, related only by `uid` (no nesting):
  - `users/{uid}` — profile: `uid`, `displayName`, `email`, `updatedAt`. Written once at
    sign-in.
  - `Expense TrackerData/{uid}` — your budget data: `uid`, `Expense TrackerData` (the same JSON blob used
    for local backups), `updatedAt`. Written whenever you tap "Sync to cloud".
  Each collection is keyed by the same Firebase Auth `uid`, so joining them back together
  (if you ever query them from outside the app) is a simple document-ID lookup, not a
  nested read.

*(Multi-month tracking, backup export/import, and the color-wheel theme editor from
earlier builds are all still here — see git history for their write-ups.)*

---

## Firebase setup (do this once, before your first build)

The app won't build without a `google-services.json` from your own Firebase project —
the file wires up your project's specific keys, and the google-services Gradle plugin
reads it at build time. None of this costs anything at this scale (Spark/free plan).

1. **Create the project** — go to [console.firebase.google.com](https://console.firebase.google.com),
   **Add project**, name it whatever you like (e.g. "Expense Tracker"), Google Analytics is optional
   and can be skipped.

2. **Register the Android app** — in the project, click the Android icon ("Add app").
   - Android package name: `com.sushem.Expense Tracker` (must match exactly)
   - App nickname: anything, e.g. "Expense Tracker"
   - Debug signing certificate SHA-1: use this value — it comes from the `debug.keystore`
     already committed in this repo, so it's stable across every rebuild (local or CI):
     ```
     65:56:2B:02:A8:B2:73:21:6D:E6:5F:33:11:1F:12:32:61:DD:11:A7
     ```
   - Download the generated **`google-services.json`** and place it at `app/google-services.json`
     in this project (same folder as `app/build.gradle.kts`).
   - Commit that file and push it — the google-services plugin needs it present at build
     time, including in GitHub Actions. It contains your project's public config (API key,
     project ID etc.), not a secret credential; access is controlled by the Firestore rules
     below plus the package name + SHA-1 check, which is standard practice for Android apps.

3. **Turn on Google Sign-In** — in the Firebase console: **Build → Authentication →
   Get started → Sign-in method → Google → Enable**. Pick a support email and save.

4. **Turn on Firestore** — **Build → Firestore Database → Create database**. Choose a
   location close to you, and start in **production mode** (we'll set rules next either way).

5. **Lock down the security rules** — Firestore → **Rules**, replace the contents with:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
       match /Expense TrackerData/{userId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```
   This makes sure a signed-in user can only ever read or write their *own* documents in
   either collection — nobody else's profile or budget data is reachable, even though
   everyone shares the same database. Click **Publish**.

That's it — steps 1–5 only need to happen once. From here on, building the app (via
GitHub Actions or Android Studio, see below) picks up `google-services.json` automatically.

> Already did this setup before this update? The rules above changed (a new `Expense TrackerData`
> collection was added) — go back to Firestore → Rules and re-publish with the version
> shown here, or "Sync to cloud" will fail with a permission error.

---

## Get an APK without installing anything (GitHub Actions)

This project includes `.github/workflows/build-apk.yml`, which builds a debug APK
automatically on GitHub's servers. Steps:

1. Create a new **empty** repository on [github.com](https://github.com/new) (or use your
   existing `Expense Tracker-app` repo). Don't add a README/gitignore when creating a new one.
2. Unzip this project on your computer, make sure `app/google-services.json` from the
   Firebase steps above is in place, then from inside the `android-Expense Tracker-app` folder run:
   ```
   git init
   git add .
   git commit -m "Expense Tracker app with Firebase sync"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
   (If you're updating your existing repo instead, just copy these files over your local
   clone, `git add -A`, commit, and push as usual.)
3. On GitHub, open your repo → **Actions** tab. A workflow run called "Build APK" starts
   automatically (takes ~2–4 minutes).
4. Once it finishes (green check), click into that run → scroll to **Artifacts** →
   download **Expense Tracker-debug-apk**. It's a zip containing `app-debug.apk`.
5. Transfer that `.apk` to your phone and open it there. Android will ask to allow
   "install unknown apps" for whichever app you used to open it — allow that once, then
   tap install.

You can re-trigger a build any time from the Actions tab via "Run workflow", without
needing a new push.

## Alternative: build locally in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. Open Android Studio → **Open** → select this `android-Expense Tracker-app` folder.
3. Make sure `app/google-services.json` is present (Firebase setup above).
4. Let it sync (first sync downloads Gradle 8.7 — needs internet just this once).
5. Plug in your phone via USB with **USB debugging** enabled, or use an emulator, and
   click **Run ▶**.

### To get a shareable APK
Build menu → **Build Bundle(s) / APK(s)** → **Build APK(s)** → use the "locate" link for
`app-debug.apk`.

## Editing the app
Everything about the app's look and behavior lives in one file:
`app/src/main/assets/index.html`. Native features (sign-in, file sharing, Firestore) live
in `MainActivity.kt`. Change the HTML file for UI/behavior tweaks; touch the Kotlin file
only if you're adding new native capabilities.

## Notes
- minSdk 26 (Android 8.0+).
- Local data lives in the WebView's `localStorage` regardless of sign-in state — signing
  in and syncing is opt-in, not required to use the app.
- `app/debug.keystore` is committed on purpose and now signs **both** the debug and
  release build types, keeping the SHA-1 registered with Firebase valid for either one.
  This is a well-known, non-secret debug-style key (standard Android debug keystore
  convention) — fine for sideloading to your own devices, but if you ever publish this
  to the Play Store, generate a proper private release key instead and register its
  SHA-1 with Firebase too (Firebase supports multiple fingerprints per app).
