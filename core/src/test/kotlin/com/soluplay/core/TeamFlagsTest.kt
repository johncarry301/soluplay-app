package com.soluplay.core

import com.soluplay.core.fixtures.TeamFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Team recognition on the match card.
 *
 * The cases that matter are the ones an admin would be embarrassed by if they
 * broke: a flag on the wrong country, a flag on a club, and a title that does
 * not split.
 */
class TeamFlagsTest {

    @Test
    fun `a national team is recognised by its full name`() {
        assertEquals("bd", TeamFlags.resolveTeamCountry("Bangladesh"))
        assertEquals("in", TeamFlags.resolveTeamCountry("India"))
        assertEquals("br", TeamFlags.resolveTeamCountry("Brazil"))
        assertEquals("ar", TeamFlags.resolveTeamCountry("Argentina"))
        assertEquals("za", TeamFlags.resolveTeamCountry("South Africa"))
        assertEquals("nz", TeamFlags.resolveTeamCountry("New Zealand"))
    }

    @Test
    fun `a national team is recognised by its common abbreviation`() {
        assertEquals("bd", TeamFlags.resolveTeamCountry("BAN"))
        assertEquals("in", TeamFlags.resolveTeamCountry("IND"))
        assertEquals("pk", TeamFlags.resolveTeamCountry("PAK"))
        assertEquals("gb-eng", TeamFlags.resolveTeamCountry("ENG"))
        assertEquals("au", TeamFlags.resolveTeamCountry("AUS"))
        assertEquals("wi", TeamFlags.resolveTeamCountry("WI"))
    }

    @Test
    fun `the name is read the way an admin would type it`() {
        assertEquals("bd", TeamFlags.resolveTeamCountry("bangladesh"))
        assertEquals("bd", TeamFlags.resolveTeamCountry("  Bangladesh  "))
        assertEquals("bd", TeamFlags.resolveTeamCountry("BANGLADESH"))
        assertEquals("ci", TeamFlags.resolveTeamCountry("Côte d'Ivoire"))
        assertEquals("za", TeamFlags.resolveTeamCountry("South-Africa"))
        assertEquals("us", TeamFlags.resolveTeamCountry("U.S.A."))
    }

    @Test
    fun `a qualifier does not change which country a team is from`() {
        assertEquals("bd", TeamFlags.resolveTeamCountry("Bangladesh Women"))
        assertEquals("in", TeamFlags.resolveTeamCountry("India U19"))
        assertEquals("gb-eng", TeamFlags.resolveTeamCountry("England XI"))
    }

    @Test
    fun `the four home nations keep their own flags`() {
        assertEquals("gb-eng", TeamFlags.resolveTeamCountry("England"))
        assertEquals("gb-sct", TeamFlags.resolveTeamCountry("Scotland"))
        assertEquals("gb-wls", TeamFlags.resolveTeamCountry("Wales"))
        assertEquals("gb-nir", TeamFlags.resolveTeamCountry("Northern Ireland"))
    }

    @Test
    fun `SA is South Africa in a cricket card and KSA is Saudi Arabia`() {
        assertEquals("za", TeamFlags.resolveTeamCountry("SA"))
        assertEquals("sa", TeamFlags.resolveTeamCountry("KSA"))
        assertEquals("sa", TeamFlags.resolveTeamCountry("Saudi Arabia"))
    }

    @Test
    fun `a name that is not a national team is not given a flag`() {
        assertNull(TeamFlags.resolveTeamCountry("Abahani Limited"))
        assertNull(TeamFlags.resolveTeamCountry(""))
        assertNull(TeamFlags.resolveTeamCountry("   "))
        assertNull(TeamFlags.resolveTeamCountry("???"))
    }

    @Test
    fun `the flag is drawn from the country the team belongs to`() {
        assertEquals("https://flagcdn.com/w80/bd.png", TeamFlags.teamFlagUrl("Bangladesh"))
        assertEquals("https://flagcdn.com/w80/bd.png", TeamFlags.teamFlagUrl("BAN"))
        assertEquals("https://flagcdn.com/w80/gb-eng.png", TeamFlags.teamFlagUrl("England"))
        assertNull(TeamFlags.teamFlagUrl("Abahani Limited"))
    }

    @Test
    fun `a normalised name keeps its words and loses its punctuation`() {
        assertEquals("new zealand", TeamFlags.normalizeTeamName("New Zealand"))
        assertEquals("south africa", TeamFlags.normalizeTeamName("South-Africa"))
        assertEquals("usa", TeamFlags.normalizeTeamName("U.S.A."))
    }

    @Test
    fun `a title splits into the two teams it names`() {
        assertEquals(
            "Bangladesh" to "India",
            TeamFlags.parseMatchTitle("Bangladesh vs India"),
        )
        assertEquals(
            "Brazil" to "Argentina",
            TeamFlags.parseMatchTitle("Brazil vs Argentina"),
        )
        assertEquals("BAN" to "IND", TeamFlags.parseMatchTitle("BAN vs IND"))
        assertEquals(
            "Bangladesh" to "India",
            TeamFlags.parseMatchTitle("Bangladesh v India"),
        )
        assertEquals(
            "Bangladesh" to "India",
            TeamFlags.parseMatchTitle("Bangladesh - India"),
        )
    }

    @Test
    fun `a title that does not name two teams is left alone`() {
        assertNull(TeamFlags.parseMatchTitle("Bangladesh"))
        assertNull(TeamFlags.parseMatchTitle(""))
        assertNull(TeamFlags.parseMatchTitle("Bangladesh vs India vs Pakistan"))
        assertNull(TeamFlags.parseMatchTitle("vs India"))
    }
}
