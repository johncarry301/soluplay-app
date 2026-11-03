package com.soluplay.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A catalogue row as the public listing publishes it: the pinned channels
 * only, each with the mirror the server picked for it.
 */
@Serializable
data class Channel(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val logo: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val priorityOrder: Int = 99,
    val activeStreamCount: Int = 0,
    val primaryStream: StreamLink? = null,
)

/**
 * A channel with its mirror list, as the detail route publishes it.
 *
 * [streams] is the ladder in published order. The app never sorts it.
 */
@Serializable
data class ChannelDetail(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val logo: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val priorityOrder: Int = 99,
    val streams: List<StreamLink> = emptyList(),
)
