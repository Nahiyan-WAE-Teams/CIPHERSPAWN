# CIPHERSPAWN API Worker

The Cloudflare Worker keeps the YouTube Data API key out of the Android APK.

## Deploy
1. Create a Google Cloud project and enable **YouTube Data API v3**.
2. Create an API key and restrict it to YouTube Data API v3.
3. In `worker/`, run:

```bash
npm install
npx wrangler login
npx wrangler secret put YOUTUBE_API_KEY
npx wrangler deploy
```

4. Paste the deployed HTTPS Worker URL into CIPHERSPAWN Settings.

Never commit the API key or place it in Android resources, source code, or build configuration.

## Routes
- `GET /api/health` — service health check.
- `GET /api/channel?channel=UC...` — public channel details; accepts channel ID, YouTube @handle, or channel URL.
- `GET /api/likes?channelId=UC...&maxVideos=100` — sums public like counts for up to the requested number of uploads. The response includes videos checked, videos with public like counts, and whether the upload playlist scan is complete.

## Reliability and limits
- Edge cache lookup happens before calling YouTube, so cache hits do not consume YouTube API quota.
- Channel details are cached for five minutes; likes scans are cached for one hour.
- Cloudflare Rate Limiting limits each client IP to 30 requests per minute.
- The Android app fetches core channel stats first and runs the likes scan separately.
- The Android app can refresh saved channels in the background about every six hours when the user enables it.
- YouTube Data API quota still applies. Likes scanning a large channel consumes more quota than a basic channel lookup.

## Data caveats
- Hidden subscriber counts remain hidden.
- Like counts may be unavailable for individual videos. The total is a sum of available public video counts, not an official channel-level metric.
- A partial scan is not presented as a complete total.
- Only current public API data is available; private analytics are never accessed.
