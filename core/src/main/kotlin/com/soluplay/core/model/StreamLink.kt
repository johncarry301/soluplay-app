package com.soluplay.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One mirror of a channel, exactly as the site publishes it.
 *
 * The order of a channel's streams is the ladder: the server ranked the
 * mirrors and the app walks them in that order without re-ranking them.
 */
@Serializable
data class StreamLink(
    @SerialName("_id") val id: String = "",
    val url: String = "",
    val priority: Int = 0,
    val status: String = "active",
    val latency: Int = 0,
    val failedAttempts: Int = 0,
)
