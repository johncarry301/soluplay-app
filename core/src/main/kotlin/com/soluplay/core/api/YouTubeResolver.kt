package com.soluplay.core.api

import com.soluplay.core.player.StreamKinds
import java.util.concurrent.ConcurrentHashMap

/**
 * Posts a JSON body and returns the answer.
 *
 * It is separate from [HttpTransport] because the site takes the one body
 * that is posted to it through a POST, and the existing fake in the tests
 * answers GETs only — so the two are kept apart rather than the fake being
 * made to answer both.
 */
interface JsonPoster {
    /** Returns the body, or throws. A status outside 2xx is a failure. */
    suspend fun postJson(url: String, body: String): String
}

/**
 * The answer to "which video is this YouTube channel showing right now?".
 *
 * Most YouTube mirrors name the video they are playing in the address
 * itself, and those need nothing from the network. The ones that name only a
 * channel cannot be answered by the app — the video that is live changes over
 * time, and only the site knows — so the site is asked, through the route it
 * already publishes.
 *
 * The answer is kept for the life of the process: a channel that is live does
 * not change its video while the viewer is watching it.
 */
class YouTubeResolver(
    private val baseUrls: List<String> = listOf(ApiConfig.PRIMARY_BASE_URL) + ApiConfig.BACKUP_BASE_URLS,
    private val poster: JsonPoster,
) {

    /** The video id each channel-style address was found to be showing. */
    private val resolved = ConcurrentHashMap<String, String>()

    /**
     * The video id to play, or null when it cannot be found.
     *
     * The address is read first: an address that names a video is answered
     * without a round trip, exactly as the web player answers it.
     */
    suspend fun videoId(url: String): String? {
        StreamKinds.youTubeVideoId(url)?.let { return it }
        resolved[url]?.let { return it }

        for (base in baseUrls) {
            val answer = ask(base, url) ?: continue
            resolved[url] = answer
            return answer
        }
        return null
    }

    /** Asks one of the sites, and reads the answer if it gave one. */
    private suspend fun ask(base: String, url: String): String? {
        val body = runCatching {
            poster.postJson("$base${ApiConfig.YOUTUBE_RESOLVE_PATH}", envelopeFor(url))
        }.getOrNull() ?: return null

        val answer = runCatching {
            SoluPlayJson.json.decodeFromString(YouTubeResolveEnvelope.serializer(), body)
        }.getOrNull() ?: return null

        if (!answer.success) return null
        return answer.videoId.takeIf { it.isNotBlank() }
    }

    /**
     * The body the route is asked with.
     *
     * The address is put into JSON by hand, so a quote or a backslash in it
     * cannot break the body.
     */
    private fun envelopeFor(url: String): String =
        """{"url":"${url.escapedForJson()}"}"""

    private fun String.escapedForJson(): String = buildString {
        this@escapedForJson.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}
