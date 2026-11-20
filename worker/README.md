# CIPHERSPAWN API Worker

The Worker keeps the YouTube Data API key out of the Android APK.

## Deploy
```bash
npm install
npx wrangler secret put YOUTUBE_API_KEY
npx wrangler deploy
```

Set the secret to a YouTube Data API v3 key. Enable **YouTube Data API v3** for its Google Cloud project. Paste the deployed `https://...` Worker URL into the app Settings.

## Routes
- `GET /api/channel?channel=UC...` — channel ID, YouTube @handle, or channel URL.
- `GET /api/likes?channelId=UC...&maxVideos=500` — sums public like counts for up to the requested number of videos (maximum 5,000). Response includes scanned count and whether the scan reached the end.

The Worker caches successful responses briefly to reduce quota use. YouTube Data API quota still applies; scanning a large channel can take many API calls. Use a dedicated API project and monitor quota in Google Cloud.

Do not expose the Worker secret, commit API keys, or use this API to infer hidden subscriber counts.
