package com.soluplay.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Channel logos: fetched once, kept small, and never fetched twice at once.
 *
 * A phone shows a hundred logos at a time and scrolls through a hundred more,
 * so three things matter: the picture that is kept in memory has to be small,
 * the picture that is fetched has to be fetched only once, and the phone must
 * not open a hundred connections at the same time. A logo that is fetched
 * once is kept on the device as well, so the next run of the app does not ask
 * the network for it again.
 */
object LogoLoader {

    /**
     * The longest edge a kept logo may have.
     *
     * The biggest a logo is ever drawn is the television tile, and a picture
     * twice as big as that is memory spent on nothing.
     */
    private const val MAX_EDGE_PX = 320

    /** How much the kept logos may cost at once. */
    private const val MEMORY_BUDGET_BYTES = 24L * 1024 * 1024

    /** How many logos may be fetched at the same time. */
    private const val MAX_PARALLEL_FETCHES = 4

    private val memory = LinkedHashMap<String, ImageBitmap>(32, 0.75f, true)
    private var memoryBytes = 0L

    private val inFlight = HashMap<String, CompletableDeferred<ImageBitmap?>>()
    private val gate = Semaphore(MAX_PARALLEL_FETCHES)

    private var diskDirectory: File? = null

    /**
     * Where the copies of the logos are kept between runs.
     *
     * The folder is the app's own cache, so the system can empty it when the
     * device is short of room and the app can live without it.
     */
    fun useDiskCache(directory: File) {
        diskDirectory = directory
    }

    suspend fun load(url: String): ImageBitmap? {
        if (url.isBlank()) return null

        kept(url)?.let { return it }

        val mine = CompletableDeferred<ImageBitmap?>()
        val waiting = synchronized(inFlight) {
            val existing = inFlight[url]
            if (existing != null) {
                existing
            } else {
                inFlight[url] = mine
                null
            }
        }

        // Somebody else is already fetching this one, so this caller waits for
        // their answer rather than opening a second connection.
        if (waiting != null) return waiting.await()

        var answer: ImageBitmap? = null
        try {
            answer = fetch(url)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            answer = null
        } finally {
            // The waiters are let go of first: a fetch that was cancelled
            // must not leave anyone waiting for an answer that will not come.
            mine.complete(answer)
            synchronized(inFlight) { inFlight.remove(url) }
        }
        return answer
    }

    /** The logo that is already at hand, in memory or on the device. */
    private suspend fun kept(url: String): ImageBitmap? {
        synchronized(memory) {
            memory[url]?.let { return it }
        }

        val file = diskFile(url) ?: return null
        if (!file.isFile) return null

        val bytes = withContext(Dispatchers.IO) {
            runCatching { file.readBytes() }.getOrNull()
        } ?: return null

        val image = decode(bytes) ?: return null
        return remember(url, image)
    }

    /** Fetches the logo and keeps a copy of it on the device. */
    private suspend fun fetch(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val bytes = gate.withPermit {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "image/*")
                    // Some of the logo hosts refuse a request that does not
                    // say who is asking — Wikimedia answers 403 to a client
                    // that names no one. A browser's name is what they expect.
                    setRequestProperty("User-Agent", LOGO_USER_AGENT)
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    connection.inputStream.use { it.readBytes() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        } ?: return@withContext null

        writeToDisk(url, bytes)
        val image = decode(bytes) ?: return@withContext null
        remember(url, image)
    }

    /**
     * Decodes the picture no bigger than it is ever drawn.
     *
     * The size is read first and the picture is then sampled down, so a large
     * logo is never turned into a large picture in memory first.
     */
    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = LogoSupport.sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    /** Keeps the logo in memory, and forgets the oldest ones when there are too many. */
    private fun remember(url: String, image: ImageBitmap): ImageBitmap {
        synchronized(memory) {
            memory.remove(url)?.let { memoryBytes -= sizeOf(it) }
            memory[url] = image
            memoryBytes += sizeOf(image)

            val iterator = memory.entries.iterator()
            while (memoryBytes > MEMORY_BUDGET_BYTES && iterator.hasNext()) {
                val eldest = iterator.next()
                memoryBytes -= sizeOf(eldest.value)
                iterator.remove()
            }
        }
        return image
    }

    private fun sizeOf(image: ImageBitmap): Long =
        image.width.toLong() * image.height.toLong() * BYTES_PER_PIXEL

    /** The copy of the logo that is kept on the device. */
    private fun diskFile(url: String): File? {
        val directory = diskDirectory ?: return null
        if (!directory.isDirectory && !directory.mkdirs()) return null
        return File(directory, nameOf(url))
    }

    private fun writeToDisk(url: String, bytes: ByteArray) {
        val file = diskFile(url) ?: return
        runCatching { file.writeBytes(bytes) }
    }

    /** The name the logo is kept under: the address itself, hashed. */
    private fun nameOf(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) } + ".img"
    }

    private const val BYTES_PER_PIXEL = 4L

    /**
     * The name the logos are asked with.
     *
     * A logo is a web resource, and the hosts that carry them answer a browser.
     */
    const val LOGO_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36 SoluPlayApp/0.1.0"
}
