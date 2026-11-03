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
