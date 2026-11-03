package com.soluplay.core

import com.soluplay.core.cache.FileCatalogueCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The copy the device keeps when the site cannot be reached.
 */
class CatalogueCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val channels = """{ "success": true, "count": 1, "channels": [] }"""
    private val fixtures = """{ "success": true, "count": 0, "fixtures": [] }"""

    @Test
    fun `the saved copy comes back as it was written`() {
        val cache = FileCatalogueCache(folder.newFolder("catalogue"), now = { 1_700_000_000_000 })

        cache.write(channels, fixtures)
        val saved = cache.read()

        assertEquals(channels, saved?.channelsJson)
        assertEquals(fixtures, saved?.fixturesJson)
        assertEquals(1_700_000_000_000, saved?.savedAt)
    }

    @Test
    fun `a device with no saved copy reads nothing`() {
        val cache = FileCatalogueCache(folder.newFolder("catalogue"))

        assertNull(cache.read())
    }

    @Test
    fun `the saved copy survives a new instance over the same folder`() {
        val directory = folder.newFolder("catalogue")
        FileCatalogueCache(directory).write(channels, null)

        val saved = FileCatalogueCache(directory).read()

        assertEquals(channels, saved?.channelsJson)
        assertTrue(saved?.fixturesJson == null)
    }

    @Test
    fun `a board that could not be read leaves no board behind`() {
        val directory = folder.newFolder("catalogue")
        val cache = FileCatalogueCache(directory)

        cache.write(channels, fixtures)
        cache.write(channels, null)

        assertNull(cache.read()?.fixturesJson)
    }

    @Test
    fun `an empty board is kept as an empty board`() {
        val directory = folder.newFolder("catalogue")
        val cache = FileCatalogueCache(directory)

        cache.write(channels, """{ "success": true, "count": 0, "fixtures": [] }""")

        assertEquals("""{ "success": true, "count": 0, "fixtures": [] }""", cache.read()?.fixturesJson)
    }
}
