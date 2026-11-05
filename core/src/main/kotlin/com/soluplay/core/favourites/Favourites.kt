package com.soluplay.core.favourites

import com.soluplay.core.api.ApiConfig
import com.soluplay.core.api.HttpTransport
import com.soluplay.core.api.JsonPoster
import com.soluplay.core.model.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom

/**
 * One channel the viewer marked.
 *
 * The name and the logo are kept beside the id because the list has to be
 * readable before the guide is: a favourite whose channel is not in today's
 * catalogue is still a favourite, and it is still named.
 */
@Serializable
data class Favourite(
    val channelId: String = "",
    val name: String = "",
    val logo: String = "",
    val category: String = "",
    /** When the viewer marked it, in epoch milliseconds. */
    val addedAt: Long = 0L,
)

/**
 * The list as it is kept on the device, with the key it syncs under.
 *
 * The key is kept beside the list because the key *is* the list for the
 * server: two devices that carry the same key carry the same favourites.
 */
@Serializable
data class StoredFavourites(
    val key: String = "",
    val items: List<Favourite> = emptyList(),
)

/** The list on the device. */
interface FavouriteStore {
    fun read(): StoredFavourites
    fun write(stored: StoredFavourites)
}

/**
 * The list on the server, under a key the viewer carries.
 *
 * A null answer is not an empty list: it is a server that could not be asked,
 * and the two are told apart because a list that cannot be read must not empty
 * the list the device already has.
 */
interface FavouriteSync {
    /** The ids the server holds under this key, or null when it could not be asked. */
    suspend fun read(key: String): List<String>?

    /** Puts the ids on the server. False when the server could not be reached. */
    suspend fun write(key: String, ids: List<String>): Boolean
}

/**
 * The key a list syncs under.
 *
 * The app has no accounts and never will, so the key *is* the identity: it is
 * written on the device, shown to the viewer, and typed into the other device
 * that is to carry the same list. It is short enough to read aloud and long
 * enough that a guess is not worth making.
 *
 * The alphabet leaves out the letters and digits that are read wrong over the
 * phone — I, O, zero and one are not in it.
 */
object SyncKey {

    /** How many characters a key has. */
    const val LENGTH = 8

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** True when this is a key the app could have written. */
    fun isValid(key: String): Boolean {
        val text = key.trim().uppercase()
        if (text.length != LENGTH) return false
        return text.all { character -> ALPHABET.contains(character) }
    }

    /** The key as it is written down, or an empty string when it is not one. */
    fun normalise(key: String): String {
        val text = key.trim().uppercase()
        return if (isValid(text)) text else ""
    }

    /** A new key. */
    fun random(): String {
        val random = SecureRandom()
        val text = StringBuilder(LENGTH)
        repeat(LENGTH) { text.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return text.toString()
    }
}

/** What the favourites screen knows at any moment. */
data class FavouritesSnapshot(
    val items: List<Favourite> = emptyList(),
    /** The key this device syncs under. */
    val key: String = "",
    /** True when the last sync reached the server. */
    val synced: Boolean = false,
    /** True while a sync is in flight. */
    val syncing: Boolean = false,
    /**
     * True once the site has been asked for the list.
     *
     * Before the first ask there is nothing to report: a screen that said
     * "the site could not be reached" before anything was asked would be
     * telling the viewer about a failure that never happened.
     */
    val attempted: Boolean = false,
)

/**
 * The favourites, on the device and on the server.
 *
 * The device's copy is the one the screen shows, and it is written the moment
 * a channel is marked — a viewer who marks a channel on a train with no
 * signal must not lose it. The server's copy is the one that travels: it is
 * written when the screen asks for it, and read when the viewer asks for the
 * list of another device.
 *
 * The two are reconciled by last write, and the rule is stated plainly
 * because there is no account and no merge to fall back on: the copy that was
 * written last is the copy that is kept. The device that pushed last wins,
 * and the other device sees that list the next time it pulls.
 */
class FavouritesRepository(
    private val store: FavouriteStore,
    private val sync: FavouriteSync,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * The list as it is on the device, with the key it syncs under.
     *
     * A device that has never synced gets a key of its own on the first read,
     * so the list is never without one.
     */
    fun load(): FavouritesSnapshot {
        val stored = store.read()
        if (stored.key.isNotBlank()) {
            return FavouritesSnapshot(items = stored.items, key = stored.key)
        }

        val key = SyncKey.random()
        store.write(stored.copy(key = key))
        return FavouritesSnapshot(items = stored.items, key = key)
    }

    /**
     * Marks a channel, or unmarks it.
     *
     * The list is written to the device before the screen is told anything, so
     * the mark survives the app being closed and the server being down.
     */
    fun toggle(channel: Channel): FavouritesSnapshot {
        val stored = store.read()
        val items = stored.items.toMutableList()
        val existing = items.indexOfFirst { it.channelId == channel.id }

        if (existing >= 0) {
            items.removeAt(existing)
        } else {
            items += Favourite(
                channelId = channel.id,
                name = channel.name,
                logo = channel.logo,
                category = channel.category,
                addedAt = now(),
            )
        }

        val key = stored.key.ifBlank { SyncKey.random() }
        store.write(StoredFavourites(key = key, items = items))
        return FavouritesSnapshot(items = items, key = key)
    }

    /** True when this channel is on the list. */
    fun isFavourite(channelId: String): Boolean =
        store.read().items.any { it.channelId == channelId }

    /**
     * Asks the server for the list and keeps it if the server answers.
     *
     * A server that cannot be asked leaves the list on the device exactly as
     * it was: a list that is emptied because the network was down is worse
     * than a list that is a day old.
     */
    suspend fun pull(): FavouritesSnapshot {
        val stored = store.read()
        val key = stored.key.ifBlank { SyncKey.random() }

        val ids = sync.read(key) ?: return FavouritesSnapshot(
            items = stored.items,
            key = key,
            synced = false,
            attempted = true,
        )

        // The names the device already knows are kept: the server holds ids,
        // and a favourite is still named when the guide does not have it.
        val known = stored.items.associateBy { it.channelId }
        val items = ids.map { id ->
            known[id] ?: Favourite(channelId = id)
        }

        store.write(StoredFavourites(key = key, items = items))
        return FavouritesSnapshot(items = items, key = key, synced = true, attempted = true)
    }

    /**
     * Puts the list on the device onto the server.
     *
     * A server that cannot be reached is not a failure of the list: the list
     * stays on the device and is pushed again the next time.
     */
    suspend fun push(): FavouritesSnapshot {
        val stored = store.read()
        val key = stored.key.ifBlank { SyncKey.random() }
        if (stored.key.isBlank()) {
            store.write(stored.copy(key = key))
        }

        val written = sync.write(key, stored.items.map { it.channelId })
        return FavouritesSnapshot(
            items = stored.items,
            key = key,
            synced = written,
            attempted = true,
        )
    }

    /**
     * Carries another device's list on this one.
     *
     * The key is the list for the server, so a key that is not one is refused
     * and nothing is written. The list this device holds is kept until the
     * next pull, which is when the other device's list arrives.
     */
    fun adopt(key: String): FavouritesSnapshot? {
        val normalised = SyncKey.normalise(key)
        if (normalised.isBlank()) return null

        val stored = store.read()
        store.write(StoredFavourites(key = normalised, items = stored.items))
        return FavouritesSnapshot(items = stored.items, key = normalised)
    }
}

/**
 * The list on the device, in a file under the app's private directory.
 *
 * A file that cannot be read is an empty list rather than a crash: a
 * favourites list that was damaged must not take the app down with it.
 */
class FileFavouriteStore(
    private val directory: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FavouriteStore {

    override fun read(): StoredFavourites {
        val file = File(directory, FILE_NAME)
        if (!file.isFile) return StoredFavourites()

        val stored = runCatching {
            json.decodeFromString(StoredFavourites.serializer(), file.readText())
        }.getOrNull() ?: return StoredFavourites()

        return StoredFavourites(
            key = SyncKey.normalise(stored.key),
            items = stored.items.filter { it.channelId.isNotBlank() },
        )
    }

    override fun write(stored: StoredFavourites) {
        directory.mkdirs()
        val body = json.encodeToString(
            StoredFavourites.serializer(),
            StoredFavourites(key = stored.key, items = stored.items),
        )

        // The copy that is already there is never the one being damaged: the
        // new one is written beside it and moved into its place.
        val target = File(directory, FILE_NAME)
        val temporary = File(directory, "$FILE_NAME.new")
        temporary.writeText(body)
        if (!temporary.renameTo(target)) {
            if (target.exists()) target.delete()
            temporary.renameTo(target)
        }
    }

    private companion object {
        const val FILE_NAME = "favourites.json"
    }
}

/** The list as it is written on the server. */
@Serializable
data class FavouriteListBody(
    val success: Boolean = false,
    val ids: List<String> = emptyList(),
    val updatedAt: String = "",
)

/** The list as it is sent to the server. */
@Serializable
data class FavouriteListRequest(
    val key: String = "",
    val ids: List<String> = emptyList(),
)

/**
 * The list on the server.
 *
 * The site is asked first and the backups after it, exactly as the guide is
 * read: a domain that stops answering must not cost the viewer their list.
 *
 * An address that answers with something the app cannot read is passed over
 * rather than treated as an empty list — a list that is emptied because a
 * route answered with rubbish is a list the viewer did not ask to lose.
 */
class HttpFavouriteSync(
    private val reader: HttpTransport,
    private val poster: JsonPoster,
    private val baseUrls: List<String> = listOf(ApiConfig.PRIMARY_BASE_URL) + ApiConfig.BACKUP_BASE_URLS,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FavouriteSync {

    override suspend fun read(key: String): List<String>? {
        val normalised = SyncKey.normalise(key)
        if (normalised.isBlank()) return null

        for (address in baseUrls.filter { it.isNotBlank() }) {
            val body = runCatching {
                reader.get("${address.trimEnd('/')}${ApiConfig.FAVOURITES_PATH}?key=$normalised")
            }.getOrNull() ?: continue

            val answer = runCatching {
                json.decodeFromString(FavouriteListBody.serializer(), body)
            }.getOrNull() ?: continue

            return answer.ids.filter { it.isNotBlank() }
        }
        return null
    }

    override suspend fun write(key: String, ids: List<String>): Boolean {
        val normalised = SyncKey.normalise(key)
        if (normalised.isBlank()) return false

        val body = json.encodeToString(
            FavouriteListRequest.serializer(),
            FavouriteListRequest(key = normalised, ids = ids.filter { it.isNotBlank() }),
        )

        for (address in baseUrls.filter { it.isNotBlank() }) {
            val written = runCatching {
                poster.postJson("${address.trimEnd('/')}${ApiConfig.FAVOURITES_PATH}", body)
            }.getOrNull() ?: continue

            val answer = runCatching {
                json.decodeFromString(FavouriteListBody.serializer(), written)
            }.getOrNull()

            if (answer?.success == true) return true
        }
        return false
    }
}
