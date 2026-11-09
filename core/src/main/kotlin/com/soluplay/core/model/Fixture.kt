package com.soluplay.core.model

import kotlinx.serialization.Serializable

/**
 * One channel a match card can be watched on, with the answer to "can I open
 * it right now?".
 */
@Serializable
data class FixtureChoice(
    val channelId: String = "",
    val sourceType: String = "direct",
    val name: String = "",
    val category: String = "",
    val logoUrl: String = "",
    val watchPath: String? = null,
    val state: String = "ready",
)

/** A match card as the public board publishes it. */
@Serializable
data class Fixture(
    val id: String = "",
    val title: String = "",
    val sport: String = "",
    val teamA: String = "",
    val teamB: String = "",
    val teamALogo: String = "",
    val teamBLogo: String = "",
    val competition: String = "",
    val startsAt: String = "",
    val durationMinutes: Int = 0,
    val phase: String = "",
    /**
     * The broadcast address the admin entered for the match itself, or empty.
     * It is played beside the linked channels, as one more thing the card can
     * open — the board already checked that it is an HTTPS link.
     */
    val directUrl: String = "",
    val choices: List<FixtureChoice> = emptyList(),
    val staleReferences: Int = 0,
    val watchPath: String? = null,
)
