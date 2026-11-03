package com.soluplay.core

import com.soluplay.core.api.ChannelsEnvelope
import com.soluplay.core.api.SoluPlayJson
import com.soluplay.core.browse.SeedCatalogue
import com.soluplay.core.browse.railsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The catalogue baked into the APK is the last resort, so it has to be a real
 * guide and not a shape that only looks like one.
 */
class SeedCatalogueTest {

    @Test
    fun `the baked catalogue is a real guide`() {
        val envelope = SoluPlayJson.json.decodeFromString(
            ChannelsEnvelope.serializer(),
            SeedCatalogue.unwrap(seedFile().readText()),
        )

        assertTrue(envelope.success)
        assertTrue(envelope.channels.size > 100)
        assertEquals(envelope.channels.size, envelope.count)
    }

    @Test
    fun `every baked channel can be shown`() {
        val envelope = SoluPlayJson.json.decodeFromString(
            ChannelsEnvelope.serializer(),
            SeedCatalogue.unwrap(seedFile().readText()),
        )

        envelope.channels.forEach { channel ->
            assertTrue("a channel has no name", channel.name.isNotBlank())
            assertTrue("a channel has no id", channel.id.isNotBlank())
        }
    }

    @Test
    fun `the baked catalogue makes rails in the published order`() {
        val envelope = SoluPlayJson.json.decodeFromString(
            ChannelsEnvelope.serializer(),
            SeedCatalogue.unwrap(seedFile().readText()),
        )

        val rails = railsOf(envelope.channels)

        assertTrue(rails.isNotEmpty())
        assertEquals(rails.sumOf { it.channels.size }, envelope.channels.size)

        val firsts = rails.map { it.channels.minOf { channel -> channel.priorityOrder } }
        assertEquals(firsts.sorted(), firsts)
    }

    @Test
    fun `the baked catalogue carries no stream ladder`() {
        val text = seedFile().readText()

        assertFalse("the seed still carries a mirror address", text.contains("m3u8"))
        assertFalse("the seed still carries a token", text.contains("token="))
        assertFalse("the seed still carries a session", text.contains("nimblesessionid"))

        val envelope = SoluPlayJson.json.decodeFromString(
            ChannelsEnvelope.serializer(),
            SeedCatalogue.unwrap(text),
        )

        envelope.channels.forEach { channel ->
            assertNull("a baked channel carries a mirror", channel.primaryStream)
            assertEquals("a baked channel counts mirrors", 0, channel.activeStreamCount)
        }
    }

    /** Finds the baked seed by walking up from the working directory. */
    private fun seedFile(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/catalogue-channels.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("could not find the baked seed above ${System.getProperty("user.dir")}")
    }
}
