# CIPHERSPAWN

A native Android app for tracking **public YouTube channel statistics**.

## Features
- Search by channel URL, @handle, or channel ID.
- Display public subscriber count, total channel views, and video count.
- Sum public video likes in a separate scan of up to 100 uploads.
- Save channels locally with Room.
- Record historical snapshots and chart view-count movement.
- Optional six-hour background refresh.
- Each installation uses its own YouTube Data API v3 key. No shared backend or shared API quota.

## Architecture
- Native Kotlin + Jetpack Compose Android app.
- Direct HTTPS requests from the app to YouTube Data API v3.
- User API key stored in AndroidX EncryptedSharedPreferences (Android Keystore-backed).
- Room stores tracked channels and historical snapshots.
- WorkManager performs opt-in background refresh using the same local key.

## First launch
1. Open Settings.
2. Add your own Google Cloud API key.
3. Enable YouTube Data API v3 in the Google Cloud project that owns the key.
4. Restrict the key to YouTube Data API v3. Android application restrictions can also be configured for the release package and signing certificate.
5. Search a public channel.

Every user supplies their own key and consumes their own Google Cloud quota. Never share your API key in screenshots, chat, or public repositories. Keys stored on a device are encrypted at rest, but no client-side API key can be made impossible to extract from a device under its owner's control. Restrict the key in Google Cloud.

## Data accuracy
- Hidden subscriber counts remain hidden; the app does not estimate them.
- YouTube has no official channel-wide public likes total. CIPHERSPAWN sums individual video like counts.
- Likes scanning is separate and limited to 100 uploads per scan. It must be treated as a partial scan when more uploads remain.
- Data is not truly real-time; it reflects the latest response from YouTube Data API.
- Historical charts begin after the first successful lookup.

## Build
Open in Android Studio with JDK 17. CI builds the debug APK and runs tests on pushes to `main`.

The `worker/` directory contains the retired Cloudflare implementation and is no longer used by the Android app.
