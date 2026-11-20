# CIPHERSPAWN

A premium Android dashboard for tracking **public YouTube channel statistics**.

## Product goals
- Search by YouTube channel URL, @handle, or channel ID.
- Show public subscriber count, total public views, and video count.
- Calculate likes across public videos with a clear scanned/complete status.
- Refresh on demand, cache intelligently, and handle quota/network failures gracefully.
- Never attempt to reveal hidden subscriber counts or private analytics.

## Architecture
- `app/`: native Android app built with Kotlin and Jetpack Compose.
- `worker/`: Cloudflare Worker API proxy. The YouTube Data API key stays server-side.
- The app does not ship with a YouTube API key.

## Setup
1. Create a YouTube Data API v3 key in Google Cloud.
2. From `worker/`, run `npm install`.
3. Set the secret: `npx wrangler secret put YOUTUBE_API_KEY`.
4. Deploy: `npx wrangler deploy`.
5. Open the Android app and set the deployed Worker URL in Settings.
6. Build with Android Studio or `./gradlew assembleDebug`.

See [worker/README.md](worker/README.md) for backend setup.

## Important data notes
- YouTube may hide subscriber counts. The app reports only values returned publicly by YouTube.
- YouTube does not provide a single channel-wide public likes-total field. CIPHERSPAWN sums public like counts from videos it scans and labels the scan as complete or partial.
- Data is not truly real-time. Refresh speed and freshness depend on YouTube API updates, quota, and cache TTL.

## Current status
Initial app and backend foundation. Android builds and live API behavior still need verification in an Android SDK environment with a deployed Worker and valid API key.
