package com.soluplay.core

import com.soluplay.core.fixtures.FixtureOption
import com.soluplay.core.fixtures.FixtureTarget
import com.soluplay.core.fixtures.fixtureTarget
import com.soluplay.core.fixtures.playableChoices
import com.soluplay.core.model.Fixture
import com.soluplay.core.model.FixtureChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a tap on a match card goes.
 *
 * The board publishes the channels a card can be watched on and the state of
 * each one. The app opens a channel, not a web route: the route the board
 * publishes is the site's own, and the app has a channel id and a ladder of
 * its own to fetch.
 */
class FixtureChoicesTest {

    private fun choice(
        id: String,
        state: String = "ready",
        source: String = "direct",
    ) = FixtureChoice(
        channelId = id,
        sourceType = source,
        name = "Channel $id",
        category = "Sports",
        logoUrl = "",
        watchPath = "/watch/$id",
        state = state,
    )

    private fun fixture(vararg choices: FixtureChoice) = Fixture(
        id = "f1",
        title = "One v Two",
        teamA = "One",
        teamB = "Two",
        choices = choices.toList(),
    )

    @Test
    fun `a card with one openable channel opens it`() {
        val target = fixtureTarget(fixture(choice("a1")))

        assertTrue(target is FixtureTarget.Open)
        val option = (target as FixtureTarget.Open).option
        assertTrue(option is FixtureOption.Channel)
        assertEquals("a1", (option as FixtureOption.Channel).choice.channelId)
    }

    @Test
    fun `a card with several channels asks which one`() {
        val target = fixtureTarget(fixture(choice("a1"), choice("a2")))

        assertTrue(target is FixtureTarget.Choose)
        assertEquals(2, (target as FixtureTarget.Choose).options.size)
    }

    @Test
    fun `a card with nothing openable says so`() {
        val target = fixtureTarget(fixture())

        assertEquals(FixtureTarget.None, target)
    }

    @Test
    fun `a channel the catalogue no longer shows is not offered`() {
        val target = fixtureTarget(fixture(choice("a1", state = "delisted")))

        assertEquals(FixtureTarget.None, target)
    }

    @Test
    fun `a channel of another tier is not offered by the app`() {
        val target = fixtureTarget(fixture(choice("a1", source = "secured")))

        assertEquals(FixtureTarget.None, target)
    }

    @Test
    fun `only the channels that can be opened are offered`() {
        val open = playableChoices(
            fixture(
                choice("a1"),
                choice("a2", state = "delisted"),
                choice("a3", source = "secured"),
                choice("a4"),
            ),
        )

        assertEquals(listOf("a1", "a4"), open.map { it.channelId })
    }

    @Test
    fun `a channel and the match link together make the card ask`() {
        val target = fixtureTarget(
            fixture(choice("a1")).copy(directUrl = "https://cdn.example/live.m3u8"),
        )

        assertTrue(target is FixtureTarget.Choose)
        val options = (target as FixtureTarget.Choose).options
        assertEquals(2, options.size)
        assertTrue(options[0] is FixtureOption.Channel)
        assertTrue(options[1] is FixtureOption.MatchLink)
        assertEquals(
            "https://cdn.example/live.m3u8",
            (options[1] as FixtureOption.MatchLink).url,
        )
    }

    @Test
    fun `a match link on its own opens the link`() {
        val target = fixtureTarget(
            fixture().copy(directUrl = "https://cdn.example/live.m3u8"),
        )

        assertTrue(target is FixtureTarget.Open)
        val option = (target as FixtureTarget.Open).option
        assertEquals(
            "https://cdn.example/live.m3u8",
            (option as FixtureOption.MatchLink).url,
        )
    }

    @Test
    fun `a blank match link is not an option`() {
        val target = fixtureTarget(fixture(choice("a1")).copy(directUrl = "   "))

        assertTrue(target is FixtureTarget.Open)
        assertTrue((target as FixtureTarget.Open).option is FixtureOption.Channel)
    }
}
