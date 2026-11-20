# CIPHERSPAWN

A native Android application for tracking **public YouTube channel statistics**.

## Product capabilities
- Search by YouTube channel URL, @handle, or channel ID.
- Display public subscriber count, total views, and video count.
- Sum individual public video like counts as a separate, explicitly labelled scan.
- Save channels locally with Room database persistence.
- Record historical snapshots and chart view-count movement.
- Optional background refresh of saved channels about every six hours.
- Cache API responses at the edge and apply per-IP request rate limits.

## Architecture
- `app/`: Kotlin + Jetpack Compose Android app.
- Room: persistent saved channels and historical snapshots.
- WorkManager: opt-in periodic refresh.
- `worker/`: Cloudflare Worker API proxy for YouTube Data API v3.
- YouTube API key stays on the server and is never embedded in the APK.

## Backend setup
See [worker/README.md](worker/README.md). You need a Google Cloud project with YouTube Data API v3 enabled, a server-side API key, and a deployed Cloudflare Worker.

## Data accuracy
- YouTube may hide subscriber counts. CIPHERSPAWN displays hidden counts as hidden; it does not estimate or reveal them.
- YouTube does not provide an official channel-wide public likes total. CIPHERSPAWN sums individual video like counts returned by the API.
- Likes scanning is separate from the initial channel lookup. The app reports how many videos were checked and whether the scan was partial.
- Data is not truly real-time. Freshness depends on YouTube's API, cache TTL, quota, and background scheduling.
- Historical charts begin collecting from the first successful lookup/refresh; older history cannot be reconstructed from current public totals.

## Build
Open the repository in Android Studio, use JDK 17, and build the `app` configuration. CI also runs Android unit tests and builds a debug APK on pushes to `main`.

## Current engineering status
This repository is under active implementation. CI results must pass before treating a commit as build-verified. A production release additionally requires a deployed backend, API-key configuration, real-channel tests, and device testing.
