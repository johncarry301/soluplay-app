package com.soluplay.core.cache

import java.io.File

/**
 * The last good copy of the guide, kept on the device.
 *
 * It is what the app shows when the site cannot be reached, so it is written
 * as the raw bodies the routes answered with — the same bytes the cache would
 * have parsed — and read back the same way.
 */
interface CatalogueCache {
    fun read(): CachedCatalogue?

    fun write(channelsJson: String, fixturesJson: String?)
}

/** One saved copy, with the moment it was taken. */
data class CachedCatalogue(
    val channelsJson: String,
    val fixturesJson: String?,
    val savedAt: Long,
)

/**
 * The cache on disk, under the app's private files directory.
 *
 * The bodies are written as they arrived, so the cache cannot drift from what
 * the server published; only the timestamp is added.
 */
class FileCatalogueCache(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
) : CatalogueCache {

    override fun read(): CachedCatalogue? {
        val channels = File(directory, CHANNELS_FILE)
        if (!channels.isFile) return null

        val body = channels.readText()
        if (body.isBlank()) return null

        val savedAt = File(directory, SAVED_AT_FILE)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
            ?: 0L

        val fixtures = File(directory, FIXTURES_FILE)
            .takeIf { it.isFile }
            ?.readText()
            ?.takeIf { it.isNotBlank() }

        return CachedCatalogue(body, fixtures, savedAt)
    }

    override fun write(channelsJson: String, fixturesJson: String?) {
        directory.mkdirs()
        // A copy that is half written is worse than no copy at all: the app
        // would read it as no guide and fall through to the baked one. So the
        // new copy is written beside the old one and moved into its place,
        // and the copy that is already there is never the one being damaged.
        writeAtomically(File(directory, CHANNELS_FILE), channelsJson)
        if (fixturesJson != null) {
            writeAtomically(File(directory, FIXTURES_FILE), fixturesJson)
        } else {
            // A board that could not be read leaves no board behind: one saved
            // on another day would be shown as if it were today's.
            File(directory, FIXTURES_FILE).delete()
        }
        writeAtomically(File(directory, SAVED_AT_FILE), now().toString())
    }

    /**
     * Writes a copy so that the one already in place is whole until the new
     * one is.
     */
    private fun writeAtomically(target: File, body: String) {
        val temporary = File(target.parentFile, "${target.name}.new")
        temporary.writeText(body)
        if (temporary.renameTo(target)) return

        // Windows will not rename over a file that is there, so the old copy
        // is moved aside first and the new one takes its place.
        if (target.exists() && target.delete() && temporary.renameTo(target)) return

        target.writeText(body)
        temporary.delete()
    }

    companion object {
        const val CHANNELS_FILE = "catalogue-channels.json"
        const val FIXTURES_FILE = "catalogue-fixtures.json"
        const val SAVED_AT_FILE = "catalogue-saved-at.txt"
    }
}
