package com.soluplay.core.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Channel logos, fetched once and kept for the life of the process.
 *
 * The same few hundred logos repeat on every rail and on every scroll, so
 * they are fetched over plain HTTP and cached in memory — no disk copy and no
 * image library.
 */
object LogoLoader {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        cache[url]?.let { return it }
        val image = fetch(url) ?: return null
        cache[url] = image
        return image
    }

    private suspend fun fetch(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "image/*")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val bytes = connection.inputStream.use { it.readBytes() }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
