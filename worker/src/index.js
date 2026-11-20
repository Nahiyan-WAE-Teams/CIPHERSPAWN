const YT = "https://www.googleapis.com/youtube/v3";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Content-Type": "application/json; charset=utf-8"
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    if (request.method !== "GET") return json({ error: "Method not allowed" }, 405, cors);
    if (!env.YOUTUBE_API_KEY) return json({ error: "Server API key is not configured." }, 503, cors);

    try {
      if (url.pathname === "/api/channel") {
        const input = url.searchParams.get("channel") || "";
        if (!input.trim()) return json({ error: "Provide a channel URL, @handle, or channel ID." }, 400, cors);
        const channel = await resolveChannel(input.trim(), env.YOUTUBE_API_KEY);
        const stats = channel.statistics || {};
        const snippet = channel.snippet || {};
        const result = {
          channelId: channel.id,
          title: snippet.title || "YouTube channel",
          handle: snippet.customUrl || null,
          subscriberCount: stats.hiddenSubscriberCount ? null : numberOrNull(stats.subscriberCount),
          videoCount: Number(stats.videoCount || 0),
          viewCount: Number(stats.viewCount || 0),
          uploadsPlaylistId: channel.contentDetails?.relatedPlaylists?.uploads || null,
          updatedAt: new Date().toISOString()
        };
        return cachedJson(request, result, 300, cors);
      }

      if (url.pathname === "/api/likes") {
        const channelId = url.searchParams.get("channelId") || "";
        const maxVideos = Math.min(5000, Math.max(1, Number(url.searchParams.get("maxVideos") || 500)));
        if (!/^UC[a-zA-Z0-9_-]{20,}$/.test(channelId)) return json({ error: "A valid channel ID is required." }, 400, cors);
        const result = await sumPublicLikes(channelId, maxVideos, env.YOUTUBE_API_KEY);
        return cachedJson(request, result, 3600, cors);
      }

      return json({ error: "Route not found." }, 404, cors);
    } catch (error) {
      const message = error?.message || "Unexpected API error.";
      const status = message.includes("quota") ? 429 : 502;
      return json({ error: message }, status, cors);
    }
  }
};

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers });
}
function numberOrNull(value) {
  return value === undefined || value === null ? null : Number(value);
}
function parseChannelInput(input) {
  const cleaned = input.trim();
  const id = cleaned.match(/(UC[a-zA-Z0-9_-]{20,})/);
  if (id) return { id: id[1] };
  const handleMatch = cleaned.match(/(?:youtube\.com\/)?@([a-zA-Z0-9._-]+)/i);
  if (handleMatch) return { handle: handleMatch[1] };
  if (/^@[a-zA-Z0-9._-]+$/.test(cleaned)) return { handle: cleaned.slice(1) };
  throw new Error("Use a YouTube channel URL, @handle, or UC channel ID.");
}
async function ytFetch(path, params, apiKey) {
  const query = new URLSearchParams({ ...params, key: apiKey });
  const response = await fetch(`${YT}/${path}?${query}`);
  const body = await response.json();
  if (!response.ok) {
    const reason = body?.error?.errors?.[0]?.reason || "";
    if (reason === "quotaExceeded" || reason === "dailyLimitExceeded") throw new Error("YouTube API quota exceeded. Try again later.");
    throw new Error(body?.error?.message || `YouTube API returned HTTP ${response.status}.`);
  }
  return body;
}
async function resolveChannel(input, apiKey) {
  const parsed = parseChannelInput(input);
  const params = parsed.id
    ? { part: "snippet,statistics,contentDetails", id: parsed.id }
    : { part: "snippet,statistics,contentDetails", forHandle: parsed.handle };
  const data = await ytFetch("channels", params, apiKey);
  if (!data.items?.length) throw new Error("No public channel matched that URL or handle.");
  return data.items[0];
}
async function sumPublicLikes(channelId, maxVideos, apiKey) {
  const channelData = await ytFetch("channels", { part: "contentDetails", id: channelId }, apiKey);
  const uploads = channelData.items?.[0]?.contentDetails?.relatedPlaylists?.uploads;
  if (!uploads) throw new Error("Could not find this channel's public uploads playlist.");
  let pageToken = "";
  let scannedVideos = 0;
  let likesTotal = 0;
  let complete = false;
  while (scannedVideos < maxVideos) {
    const page = await ytFetch("playlistItems", {
      part: "contentDetails", playlistId: uploads,
      maxResults: Math.min(50, maxVideos - scannedVideos),
      ...(pageToken ? { pageToken } : {})
    }, apiKey);
    const ids = (page.items || []).map(item => item.contentDetails?.videoId).filter(Boolean);
    if (ids.length) {
      const details = await ytFetch("videos", { part: "statistics", id: ids.join(",") }, apiKey);
      for (const video of details.items || []) {
        scannedVideos += 1;
        if (video.statistics?.likeCount !== undefined) likesTotal += Number(video.statistics.likeCount);
      }
    }
    pageToken = page.nextPageToken || "";
    if (!pageToken) { complete = true; break; }
    if (!ids.length) break;
  }
  return {
    channelId, likesTotal, scannedVideos, complete,
    maxVideos,
    note: complete ? "All videos in the public uploads playlist were scanned." : "Partial scan; the configured scan limit was reached or the API returned incomplete data.",
    updatedAt: new Date().toISOString()
  };
}
async function cachedJson(request, data, ttl, headers) {
  const cache = caches.default;
  const cacheKey = new Request(new URL(request.url).toString(), request);
  const hit = await cache.match(cacheKey);
  if (hit) {
    const response = new Response(hit.body, hit);
    Object.entries(headers).forEach(([k, v]) => response.headers.set(k, v));
    response.headers.set("X-Cipher-Cache", "HIT");
    return response;
  }
  const response = json(data, 200, { ...headers, "Cache-Control": `public, max-age=${ttl}`, "X-Cipher-Cache": "MISS" });
  await cache.put(cacheKey, response.clone());
  return response;
}
