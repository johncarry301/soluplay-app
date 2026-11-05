package com.soluplay.core

import com.soluplay.core.favourites.Favourite
import com.soluplay.core.favourites.FavouriteStore
import com.soluplay.core.favourites.FavouritesRepository
import com.soluplay.core.favourites.FavouritesSnapshot
import com.soluplay.core.favourites.StoredFavourites
import com.soluplay.core.favourites.SyncKey
import com.soluplay.core.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The favourites list and the key it travels under.
 *
 * The list is kept on the device and copied to the server, and the two rules
 * that matter are that a list is never lost because the network was down, and
 * that a key that is not one is refused rather than written.
 */
class FavouritesTest {

    /** A store that keeps what it is given, in memory. */
    private class MemoryStore(var stored: StoredFavourites = StoredFavourites()) : FavouriteStore {
        override fun read(): StoredFavourites = stored
        override fun write(stored: StoredFavourites) {
            this.stored = stored
        }
    }

    /** A server that answers with what it was given, or with nothing. */
    private class Server(
        var ids: List<String>? = null,
        var reachable: Boolean = true,
    ) : com.soluplay.core.favourites.FavouriteSync {
        val written = mutableListOf<Pair<String, List<String>>>()

        override suspend fun read(key: String): List<String>? {
            if (!reachable) return null
            return ids
        }

        override suspend fun write(key: String, ids: List<String>): Boolean {
            if (!reachable) return false
            written += key to ids
            this.ids = ids
            return true
        }
    }

    private fun channel(id: String, name: String = "Channel $id") = Channel(
        id = id,
        name = name,
        logo = "https://logos.test/$id.png",
        category = "Sports",
    )

    @Test
    fun `a key is eight characters of the alphabet that is read right`() {
        val key = SyncKey.random()

        assertEquals(SyncKey.LENGTH, key.length)
        assertTrue(SyncKey.isValid(key))
        assertFalse(key.contains("I"))
        assertFalse(key.contains("O"))
        assertFalse(key.contains("0"))
        assertFalse(key.contains("1"))
    }

    @Test
    fun `two keys are not the same key`() {
        assertNotEquals(SyncKey.random(), SyncKey.random())
    }

    @Test
    fun `a key is read in either case and with the spaces around it`() {
        assertEquals("ABCDEFGH", SyncKey.normalise(" abcdefgh "))
        assertTrue(SyncKey.isValid("abcdefgh"))
    }

    @Test
    fun `a key that is not one is refused`() {
        assertFalse(SyncKey.isValid("short"))
        assertFalse(SyncKey.isValid("ABCDEFG!"))
        assertEquals("", SyncKey.normalise("ABCDEFG!"))
    }

    @Test
    fun `a device that has never synced is given a key of its own`() {
        val store = MemoryStore()
        val repository = FavouritesRepository(store, Server())

        val snapshot = repository.load()

        assertTrue(snapshot.key.isNotBlank())
        assertEquals(snapshot.key, store.stored.key)
    }

    @Test
    fun `marking a channel puts it on the list and the list is written at once`() {
        val store = MemoryStore()
        val repository = FavouritesRepository(store, Server())

        val snapshot = repository.toggle(channel("c1", "T Sports"))

        assertEquals(1, snapshot.items.size)
        assertEquals("c1", snapshot.items.first().channelId)
        assertEquals("T Sports", snapshot.items.first().name)
        assertEquals(1, store.stored.items.size)
    }

    @Test
    fun `marking a channel that is on the list takes it off`() {
        val store = MemoryStore()
        val repository = FavouritesRepository(store, Server())

        repository.toggle(channel("c1"))
        val snapshot = repository.toggle(channel("c1"))

        assertTrue(snapshot.items.isEmpty())
        assertTrue(store.stored.items.isEmpty())
    }

    @Test
    fun `the list is kept when the server cannot be asked`() {
        val store = MemoryStore()
        val repository = FavouritesRepository(store, Server(ids = null, reachable = false))
        repository.toggle(channel("c1"))

        val snapshot = kotlinx.coroutines.runBlocking { repository.pull() }

        assertEquals(1, snapshot.items.size)
        assertFalse(snapshot.synced)
    }

    @Test
    fun `the server's list is the truth when it answers`() {
        val store = MemoryStore()
        val repository = FavouritesRepository(store, Server(ids = listOf("c1", "c2")))
        repository.toggle(channel("c1"))

        val snapshot = kotlinx.coroutines.runBlocking { repository.pull() }

        assertEquals(listOf("c1", "c2"), snapshot.items.map { it.channelId })
        assertTrue(snapshot.synced)
    }

    @Test
    fun `a name the device knows is kept when the server holds only ids`() {
        val store = MemoryStore(StoredFavourites(key = "ABCDEFGH", items = listOf(Favourite("c1", "T Sports"))))
        val repository = FavouritesRepository(store, Server(ids = listOf("c1", "c9")))

        val snapshot = kotlinx.coroutines.runBlocking { repository.pull() }

        assertEquals("T Sports", snapshot.items.first { it.channelId == "c1" }.name)
        assertEquals("", snapshot.items.first { it.channelId == "c9" }.name)
    }

    @Test
    fun `the list is put on the server under the device's own key`() {
        val store = MemoryStore()
        val server = Server()
        val repository = FavouritesRepository(store, server)
        repository.toggle(channel("c1"))
        repository.toggle(channel("c2"))

        val snapshot = kotlinx.coroutines.runBlocking { repository.push() }

        assertTrue(snapshot.synced)
        assertEquals(listOf("c1", "c2"), server.written.single().second)
        assertEquals(snapshot.key, server.written.single().first)
    }

    @Test
    fun `a list that could not be pushed stays on the device`() {
        val store = MemoryStore()
        val repository = FavouritesRepository(store, Server(reachable = false))
        repository.toggle(channel("c1"))

        val snapshot = kotlinx.coroutines.runBlocking { repository.push() }

        assertFalse(snapshot.synced)
        assertEquals(1, store.stored.items.size)
    }

    @Test
    fun `another device's key is taken on, and a key that is not one is refused`() {
        val store = MemoryStore()
        val repository = FavouritesRepository(store, Server())

        assertNull(repository.adopt("not a key"))
        assertEquals(0, store.stored.key.length)

        val snapshot = repository.adopt("abcdefgh")

        assertEquals(FavouritesSnapshot::class, snapshot!!::class)
        assertEquals("ABCDEFGH", snapshot.key)
        assertEquals("ABCDEFGH", store.stored.key)
    }
}
