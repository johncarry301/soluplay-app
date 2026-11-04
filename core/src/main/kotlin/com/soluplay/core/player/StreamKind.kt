package com.soluplay.core.player

/**
 * What kind of source a published mirror is.
 */
enum class StreamKind {

    /** An HLS playlist. */
    HLS,

    /** A raw MPEG-TS transport stream. */
    MPEG_TS,

    /** A YouTube video, played with YouTube's own player. */
    YOUTUBE,
}

/**
 * How a published mirror's address is read.
 *
 * The site publishes a URL and nothing else — no field says "this one is
 * YouTube" — so the app reads the kind out of the address, the same way the
 * web player does:
 *
 *  - a YouTube address is played with YouTube's own player,
 *  - a `.ts` path is a raw transport stream,
 *  - everything else is an HLS playlist.
 *
 * That last rule is the web player's own. It loads every other mirror with
 * hls.js, which reads what the address *returns* rather than what the address
 * is called, so a mirror published as `stream.php?id=…&e=.m3u8` is an HLS
 * playlist. Media3 cannot be told that by the name of the file — its own
 * extension table knows `m3u8`, `mpd`, `ism` and nothing else — so the kind is
 * decided here and the player is built to match.
 *
 * Nothing here reaches the network and nothing here is Android's, so the whole
 * rule can be tested without a device.
 */
object StreamKinds {

    /** The path of the relay that older builds stored a link behind. */
    const val STREAM_PROXY_PATH = "/api/stream"

    /**
     * The kind of source an address is.
     */
    fun kindOf(url: String): StreamKind = when {
        isYouTubeUrl(url) -> StreamKind.YOUTUBE
        isMpegTsUrl(url) -> StreamKind.MPEG_TS
        else -> StreamKind.HLS
    }

    /** True when the address points at YouTube. */
    fun isYouTubeUrl(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return host == "youtube.com" ||
            host == "m.youtube.com" ||
            host == "youtu.be" ||
            host == "music.youtube.com"
    }

    /**
     * True when the address is a raw MPEG-TS feed, or points at one.
     *
     * A link stored behind the old relay is read through to what it wraps: a
     * relay that wraps an HLS playlist is still HLS, and a relay that wraps a
     * `.ts` feed is still a transport stream.
     */
    fun isMpegTsUrl(url: String): Boolean {
        val parsed = parse(url) ?: return false
        val path = parsed.path.lowercase()
        if (path.endsWith(".ts")) return true

        val innerPath = parsed.query["url"]?.let { inner -> parse(inner)?.path?.lowercase() }
        if (path.endsWith(STREAM_PROXY_PATH)) {
            // A relay that wraps a playlist is still a playlist.
            if (innerPath != null && innerPath.endsWith(".m3u8")) return false
            return true
        }
        return innerPath != null && innerPath.endsWith(".ts")
    }

    /**
     * The address to load: a link stored behind the old relay is read back to
     * the origin it wrapped, and anything else is already a direct address.
     */
    fun toDirectStreamUrl(rawUrl: String): String {
        val parsed = parse(rawUrl) ?: return rawUrl
        if (!parsed.path.endsWith(STREAM_PROXY_PATH)) return rawUrl
        val inner = parsed.query["url"] ?: return rawUrl
        val scheme = parse(inner)?.scheme ?: return rawUrl
        return if (scheme == "http" || scheme == "https") inner else rawUrl
    }

    /**
     * The video id the address names, when it names one.
     *
     * A channel-style address (`/@handle/live`) names no video, because the
     * video that is live changes over time — those have to be resolved against
     * the site's own resolver.
     */
    fun youTubeVideoId(url: String): String? {
        if (!isYouTubeUrl(url)) return null
        val parsed = parse(url) ?: return null

        if (parsed.host == "youtu.be") {
            return parsed.path.split('/').firstOrNull { it.isNotEmpty() }
        }

        parsed.query["v"]?.takeIf { it.isNotBlank() }?.let { return it }

        val segments = parsed.path.split('/').filter { it.isNotEmpty() }
        listOf("live", "embed", "shorts").forEach { marker ->
            val index = segments.indexOf(marker)
            if (index != -1) {
                segments.getOrNull(index + 1)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    /** The host of an address, without a leading `www.`. */
    private fun hostOf(url: String): String? =
        parse(url)?.host?.removePrefix("www.")?.takeIf { it.isNotEmpty() }

    /** An address, taken apart. */
    private class ParsedUrl(
        val scheme: String,
        val host: String,
        val path: String,
        val query: Map<String, String>,
    )

    /**
     * A small address reader.
     *
     * `java.net.URI` cannot answer the two questions this asks — the host of
     * an address that may be relative, and one parameter out of a query — and
     * Android's own `Uri` is not available to a plain JVM test, so the parts
     * are read here.
     */
    private val ADDRESS = Regex(
        "^(?:([A-Za-z][A-Za-z0-9+.\\-]*):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#.*)?$"
    )

    private fun parse(raw: String): ParsedUrl? {
        val match = ADDRESS.matchEntire(raw.trim()) ?: return null
        val authority = match.groupValues[2]
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        return ParsedUrl(
            scheme = match.groupValues[1].lowercase(),
            host = host,
            path = match.groupValues[3],
            query = queryOf(match.groupValues[4]),
        )
    }

    private fun queryOf(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val values = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            values[decode(pair.substringBefore('='))] = decode(pair.substringAfter('=', ""))
        }
        return values
    }

    private fun decode(value: String): String = runCatching {
        java.net.URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)
}
