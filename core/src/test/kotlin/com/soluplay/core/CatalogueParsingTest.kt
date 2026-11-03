package com.soluplay.core

import com.soluplay.core.api.ChannelDetailEnvelope
import com.soluplay.core.api.ChannelsEnvelope
import com.soluplay.core.api.SoluPlayJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue contract: the shapes the public routes answer with and the
 * order they answer in.
 */
class CatalogueParsingTest {

    private val catalogueJson = """
        {
          "success": true,
          "count": 2,
          "channels": [
            {
              "_id": "abc123",
              "name": "Sports One",
              "logo": "https://example.com/one.png",
              "category": "Sports",
              "tags": [],
              "isPinned": true,
              "priorityOrder": 1,
              "activeStreamCount": 2,
              "primaryStream": {
                "_id": "s1",
                "url": "https://example.com/one.m3u8",
                "priority": 1,
                "status": "active",
                "latency": 120,
                "failedAttempts": 0
              }
            },
            {
              "_id": "def456",
              "name": "Sports Two",
              "logo": "",
              "category": "Sports",
              "tags": ["hd"],
              "isPinned": true,
              "priorityOrder": 2,
              "activeStreamCount": 1,
              "primaryStream": null
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `the catalogue parses into channels with their published mirror`() {
        val envelope = SoluPlayJson.json
            .decodeFromString(ChannelsEnvelope.serializer(), catalogueJson)

        assertTrue(envelope.success)
        assertEquals(2, envelope.count)
        assertEquals("Sports One", envelope.channels[0].name)
        assertEquals("abc123", envelope.channels[0].id)
        assertEquals(2, envelope.channels[0].activeStreamCount)
        assertEquals("https://example.com/one.m3u8", envelope.channels[0].primaryStream?.url)
    }

    @Test
    fun `a channel with no usable mirror is still listed, without one`() {
        val envelope = SoluPlayJson.json
            .decodeFromString(ChannelsEnvelope.serializer(), catalogueJson)

        assertEquals(null, envelope.channels[1].primaryStream)
    }

    @Test
    fun `the detail route keeps the mirror ladder in published order`() {
        val detailJson = """
            {
              "success": true,
              "playable": true,
              "channel": {
                "_id": "abc123",
                "name": "Sports One",
                "streams": [
                  { "_id": "s1", "url": "https://example.com/first.m3u8", "priority": 1 },
                  { "_id": "s2", "url": "https://example.com/second.m3u8", "priority": 2 }
                ]
              }
            }
        """.trimIndent()

        val envelope = SoluPlayJson.json
            .decodeFromString(ChannelDetailEnvelope.serializer(), detailJson)

        assertTrue(envelope.playable)
        assertEquals(
            listOf("https://example.com/first.m3u8", "https://example.com/second.m3u8"),
            envelope.channel?.streams?.map { it.url },
        )
    }

    @Test
    fun `a failed catalogue is not an empty catalogue`() {
        val failureJson = """{ "success": false, "error": "Catalogue source unavailable" }"""

        val envelope = SoluPlayJson.json
            .decodeFromString(ChannelsEnvelope.serializer(), failureJson)

        assertEquals(false, envelope.success)
        assertEquals(0, envelope.channels.size)
    }
}
