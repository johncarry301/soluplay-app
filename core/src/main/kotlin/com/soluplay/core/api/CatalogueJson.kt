package com.soluplay.core.api

import com.soluplay.core.model.Channel
import com.soluplay.core.model.ChannelDetail
import com.soluplay.core.model.Fixture
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The envelopes the public routes answer with.
 *
 * `success` is false on a failure, and a failure is not an empty list — the
 * app must never read a failed catalogue as "no channels".
 */
@Serializable
data class ChannelsEnvelope(
    val success: Boolean = false,
    val count: Int = 0,
    val channels: List<Channel> = emptyList(),
)

@Serializable
data class ChannelDetailEnvelope(
    val success: Boolean = false,
    val playable: Boolean = false,
    val channel: ChannelDetail? = null,
)

@Serializable
data class FixturesEnvelope(
    val success: Boolean = false,
    val count: Int = 0,
    val fixtures: List<Fixture> = emptyList(),
)

@Serializable
data class YouTubeResolveEnvelope(
    val success: Boolean = false,
    val videoId: String = "",
    val error: String = "",
)

object SoluPlayJson {
    /**
     * The site's payloads carry fields the app does not model yet, and some of
     * them are null where the app expects a value — so unknown keys are ignored
     * and a null becomes the default.
     */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
}

/**
 * What a body the routes answered with means.
 *
 * One decoder for the whole app: the client uses it to decide whether an
 * address answered with something usable, and the repository uses it to decide
 * what a body is worth. A body that cannot be read as a guide is never read as
 * an empty guide.
 */
object CatalogueBody {

    /**
     * The channels a body carries, or null when it is not a usable catalogue.
     *
     * A route that failed and a route with no channels are the same thing
     * here: not a guide.
     */
    fun channels(body: String?): List<Channel>? {
        if (body.isNullOrBlank()) return null
        val envelope = runCatching {
            SoluPlayJson.json.decodeFromString(ChannelsEnvelope.serializer(), body)
        }.getOrNull() ?: return null

        if (!envelope.success) return null
        if (envelope.channels.isEmpty()) return null
        return envelope.channels
    }

    /**
     * The fixtures a body carries, or null when the route failed.
     *
     * An empty list is a board with no matches on it — the board is empty
     * most of the time — and that is a good answer, not a failure.
     */
    fun fixtures(body: String?): List<Fixture>? {
        if (body.isNullOrBlank()) return null
        val envelope = runCatching {
            SoluPlayJson.json.decodeFromString(FixturesEnvelope.serializer(), body)
        }.getOrNull() ?: return null

        if (!envelope.success) return null
        return envelope.fixtures
    }

    /**
     * The channel and its mirror list, or null when the route did not give one.
     *
     * The mirror list is the ladder the player walks, already ranked by the
     * server. A channel with no usable mirror is still a channel, so its
     * answer is kept with an empty ladder rather than thrown away.
     */
    fun channel(body: String?): ChannelDetail? {
        if (body.isNullOrBlank()) return null
        val envelope = runCatching {
            SoluPlayJson.json.decodeFromString(ChannelDetailEnvelope.serializer(), body)
        }.getOrNull() ?: return null

        if (!envelope.success) return null
        return envelope.channel
    }
}
