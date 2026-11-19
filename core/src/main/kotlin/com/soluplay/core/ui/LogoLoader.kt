package com.soluplay.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.soluplay.core.api.ApiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    private var appContext: Context? = null

    /**
     * Where the copies of the logos are kept between runs.
     *
     * The folder is the app's own cache, so the system can empty it when the
     * device is short of room and the app can live without it.
     */
    fun useDiskCache(directory: File) {
        diskDirectory = directory
    }

    /** The application, for the one logo kind only the web engine can draw. */
    fun useApplicationContext(context: Context) {
        appContext = context.applicationContext
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
        val (bytes, type) = fetchBytes(url) ?: return@withContext null

        if (isSvg(bytes, type)) {
            // A vector logo is one the picture decoder cannot read, so the
            // web engine draws it once and the drawing is what is kept.
            val image = rasterizeSvg(url) ?: return@withContext null
            writeToDisk(url, pngBytes(image))
            return@withContext remember(url, image)
        }

        writeToDisk(url, bytes)
        val image = decode(bytes) ?: return@withContext null
        remember(url, image)
    }

    /**
     * Asks for the picture, following every redirect and waiting out a host
     * that is counting requests.
     *
     * A redirect that changes protocol is followed by hand, because the
     * connection's own following stops at the protocol door. A host that
     * answers "too many" is asked again after a wait that doubles, the way a
     * browser's own retry behaves; a host that refuses outright is not asked
     * twice.
     */
    private suspend fun fetchBytes(url: String): Pair<ByteArray, String>? {
        var current = url
        var waitMs = 2_000L
        for (step in 0 until 8) {
            val answer = gate.withPermit {
                runCatching {
                    val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 10_000
                        instanceFollowRedirects = false
                        setRequestProperty("Accept", "image/*")
                        // Some of the logo hosts refuse a request that does not
                        // say who is asking — Wikimedia answers 403 to a client
                        // that names no one. A browser's name is what they expect.
                        setRequestProperty("User-Agent", LOGO_USER_AGENT)
                        // And some serve only the pages the logo stands on: a
                        // request naming the site is served, one naming nothing
                        // is turned away with 403.
                        setRequestProperty("Referer", ApiConfig.PRIMARY_BASE_URL + "/")
                    }
                    try {
                        val code = connection.responseCode
                        val type = connection.contentType ?: ""
                        val location = connection.getHeaderField("Location")
                        val bytes = if (code in 200..299) {
                            connection.inputStream.use { it.readBytes() }
                        } else {
                            null
                        }
                        Answer(code, type, location, bytes)
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            } ?: return null

            when {
                answer.code in 200..299 && answer.bytes != null ->
                    return answer.bytes to answer.type
                answer.code in 300..399 && answer.location != null ->
                    current = URL(URL(current), answer.location).toString()
                answer.code == 429 || answer.code == 503 -> {
                    delay(waitMs)
                    waitMs *= 2
                }
                else -> return null
            }
        }
        return null
    }

    /** One answer from a logo host, before it is judged. */
    private class Answer(
        val code: Int,
        val type: String,
        val location: String?,
        val bytes: ByteArray?,
    )

    /** Whether the picture is a vector one, by its type or by its own head. */
    private fun isSvg(bytes: ByteArray, type: String): Boolean {
        if (type.contains("svg", ignoreCase = true)) return true
        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8)
        return head.contains("<svg", ignoreCase = true)
    }

    /**
     * Draws a vector logo with the web engine, off the screen.
     *
     * The engine is given a page of exactly the size a kept logo may be, with
     * the logo fitted inside it, and what it draws is taken as the picture.
     * The page says when the logo has arrived by naming itself; until then,
     * or until the wait runs out, nothing is taken.
     */
    private suspend fun rasterizeSvg(url: String): ImageBitmap? {
        val context = appContext ?: return null
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val web = WebView(context)
                val handler = Handler(Looper.getMainLooper())
                var finished = false
                val finish: (ImageBitmap?) -> Unit = { result ->
                    if (!finished) {
                        finished = true
                        handler.removeCallbacksAndMessages(null)
                        web.destroy()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(result))
                        }
                    }
                }
                continuation.invokeOnCancellation { handler.post { finish(null) } }

                web.setBackgroundColor(Color.TRANSPARENT)
                web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                web.settings.javaScriptEnabled = true
                web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                web.settings.loadWithOverviewMode = true
                web.settings.useWideViewPort = true
                web.webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String?) {
                        if (title != READY_TITLE) return
                        val edge = MAX_EDGE_PX
                        val exact = View.MeasureSpec.makeMeasureSpec(edge, View.MeasureSpec.EXACTLY)
                        web.measure(exact, exact)
                        web.layout(0, 0, edge, edge)
                        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
                        web.draw(Canvas(bitmap))
                        finish(bitmap.asImageBitmap())
                    }
                }

                val html = "<html><head>" +
                    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                    "<style>html,body{margin:0;padding:0;width:100%;height:100%;background:transparent}" +
                    "img{width:100%;height:100%;object-fit:contain}</style></head>" +
                    "<body><img src=\"" + url + "\" onload=\"document.title='" + READY_TITLE + "'\">" +
                    "</body></html>"
                web.loadDataWithBaseURL(url, html, "text/html", "utf-8", null)
                handler.postDelayed({ finish(null) }, SVG_TIMEOUT_MS)
            }
        }
    }

    /** The drawing of a vector logo, kept as a picture every decoder reads. */
    private fun pngBytes(image: ImageBitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
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

    /** The name the vector page gives itself when its logo has arrived. */
    private const val READY_TITLE = "soluplay-logo-ready"

    /** How long the web engine is given to draw one vector logo. */
    private const val SVG_TIMEOUT_MS = 10_000L

    /**
     * The name the logos are asked with.
     *
     * A logo is a web resource, and the hosts that carry them answer a browser.
     */
    const val LOGO_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36 SoluPlayApp/0.1.0"
}
