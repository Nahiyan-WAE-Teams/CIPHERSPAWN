const YT = "https://www.googleapis.com/youtube/v3";
const CACHE = globalThis.caches?.default;
const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8" };

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const cors = {
      ...JSON_HEADERS,
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    };

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "GET") return json({ error: "Method not allowed." }, 405, cors);
    if (!env.YOUTUBE_API_KEY) return json({ error: "The YouTube API key is not configured on the server." }, 503, cors);
    if (env.API_RATE_LIMITER) {
      const clientIp = request.headers.get("CF-Connecting-IP") || "unknown";
      const { success } = await env.API_RATE_LIMITER.limit({ key: clientIp });
      if (!success) return json({ error: "Too many requests. Wait a minute and try again." }, 429, cors);
    }

    try {
      if (url.pathname === "/api/health") {
        return json({ service: "CIPHERSPAWN API", status: "ok", time: new Date().toISOString() }, 200, cors);
      }

      if (url.pathname === "/api/channel") {
        const input = (url.searchParams.get("channel") || "").trim();
        if (!input || input.length > 300) return json({ error: "Enter a valid YouTube channel URL, @handle, or channel ID." }, 400, cors);
        const cacheKey = new Request("https://cache.cipherspawn.invalid/channel/" + encodeURIComponent(input.toLowerCase()));
        const result = await cached(cacheKey, 300, async () => {
          const channel = await resolveChannel(input, env.YOUTUBE_API_KEY);
          const stats = channel.statistics || {};
          const snippet = channel.snippet || {};
          return {
            channelId: channel.id,
            title: snippet.title || "YouTube channel",
            description: snippet.description || "",
            thumbnailUrl: snippet.thumbnails?.high?.url || snippet.thumbnails?.medium?.url || snippet.thumbnails?.default?.url || null,
            handle: snippet.customUrl || null,
            subscriberCount: stats.hiddenSubscriberCount ? null : numberOrNull(stats.subscriberCount),
            subscriberCountHidden: Boolean(stats.hiddenSubscriberCount),
            videoCount: Number(stats.videoCount || 0),
            viewCount: Number(stats.viewCount || 0),
            uploadsPlaylistId: channel.contentDetails?.relatedPlaylists?.uploads || null,
            updatedAt: new Date().toISOString(),
            source: "YouTube Data API v3"
          };
        });
        return json(result.data, 200, { ...cors, "X-Cipher-Cache": result.hit ? "HIT" : "MISS" });
      }

      if (url.pathname === "/api/likes") {
        const channelId = url.searchParams.get("channelId") || "";
        const maxVideos = Math.min(5000, Math.max(1, Number(url.searchParams.get("maxVideos") || 500)));
        if (!/^UC[a-zA-Z0-9_-]{20,}$/.test(channelId)) return json({ error: "A valid YouTube channel ID is required." }, 400, cors);
        const cacheKey = new Request(`https://cache.cipherspawn.invalid/likes/${channelId}/${maxVideos}`);
        const result = await cached(cacheKey, 3600, () => sumPublicLikes(channelId, maxVideos, env.YOUTUBE_API_KEY));
        return json(result.data, 200, { ...cors, "X-Cipher-Cache": result.hit ? "HIT" : "MISS" });
      }

      return json({ error: "Endpoint not found." }, 404, cors);
    } catch (error) {
      const message = safeError(error);
      const status = message.toLowerCase().includes("quota") ? 429 :
        message.toLowerCase().includes("not found") ? 404 : 502;
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
function safeError(error) {
  const message = String(error?.message || "Unexpected API error.");
  return message.length > 300 ? message.slice(0, 300) : message;
}

// Cache lookup happens BEFORE calling YouTube. A cache hit therefore costs zero YouTube API quota.
async function cached(key, ttlSeconds, load) {
  // Node-based unit tests do not provide the Workers Cache API. Bypass cache there.
  if (!CACHE) return { data: await load(), hit: false };
  const hit = await CACHE.match(key);
  if (hit) return { data: await hit.json(), hit: true };
  const data = await load();
  const response = new Response(JSON.stringify(data), {
    headers: { ...JSON_HEADERS, "Cache-Control": `public, max-age=${ttlSeconds}` }
  });
  await CACHE.put(key, response);
  return { data, hit: false };
}

function parseChannelInput(input) {
  const cleaned = input.trim();
  const id = cleaned.match(/(?:^|\/)(UC[a-zA-Z0-9_-]{20,})(?:[/?#]|$)/);
  if (id) return { id: id[1] };
  const handle = cleaned.match(/(?:youtube\.com\/)?@([a-zA-Z0-9._-]+)/i);
  if (handle) return { handle: handle[1] };
  if (/^@[a-zA-Z0-9._-]+$/.test(cleaned)) return { handle: cleaned.slice(1) };
  throw new Error("Use a YouTube channel URL, @handle, or UC channel ID. Legacy custom URLs are not supported yet.");
}

async function ytFetch(path, params, apiKey) {
  const query = new URLSearchParams({ ...params, key: apiKey });
  const response = await fetch(`${YT}/${path}?${query}`);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const reason = body?.error?.errors?.[0]?.reason || "";
    if (reason === "quotaExceeded" || reason === "dailyLimitExceeded") {
      throw new Error("YouTube API quota exceeded. Cached data may still be available; try again later.");
    }
    if (response.status === 404 || reason === "channelNotFound") throw new Error("YouTube channel not found.");
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
  if (!uploads) throw new Error("This channel has no accessible public uploads playlist.");

  let pageToken = "";
  let videosListed = 0;
  let videosWithLikeCount = 0;
  let likesTotal = 0;
  let complete = false;

  while (videosListed < maxVideos) {
    const remaining = maxVideos - videosListed;
    const page = await ytFetch("playlistItems", {
      part: "contentDetails", playlistId: uploads,
      maxResults: Math.min(50, remaining),
      ...(pageToken ? { pageToken } : {})
    }, apiKey);
    const ids = (page.items || []).map(item => item.contentDetails?.videoId).filter(Boolean);
    if (ids.length) {
      const details = await ytFetch("videos", { part: "statistics", id: ids.join(",") }, apiKey);
      const byId = new Map((details.items || []).map(video => [video.id, video]));
      for (const id of ids) {
        videosListed += 1;
        const video = byId.get(id);
        if (video?.statistics?.likeCount !== undefined) {
          likesTotal += Number(video.statistics.likeCount);
          videosWithLikeCount += 1;
        }
      }
    }
    pageToken = page.nextPageToken || "";
    if (!pageToken) { complete = true; break; }
    if (!ids.length) break;
  }

  return {
    channelId,
    likesTotal,
    videosListed,
    videosWithLikeCount,
    videosWithoutPublicLikeCount: videosListed - videosWithLikeCount,
    complete,
    scanLimit: maxVideos,
    coverage: complete ? "All public uploads were checked; some videos may hide like counts." : "Partial scan; more public uploads remain.",
    updatedAt: new Date().toISOString(),
    source: "YouTube Data API v3"
  };
}

export { parseChannelInput };
