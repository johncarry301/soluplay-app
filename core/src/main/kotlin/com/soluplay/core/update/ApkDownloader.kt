package com.soluplay.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest

/** Why a download did not become an installable file. */
enum class DownloadRefusal {
    /** The manifest carries no fingerprint, so there is nothing to check the file against. */
    NO_FINGERPRINT,

    /** The bytes that arrived are not the bytes the manifest named. */
    MISMATCH,

    /** The file could not be fetched or written. */
    FAILED,
}

/** What a download came to. */
sealed class DownloadResult {
    /** The file is on disk and its fingerprint is the one the manifest named. */
    data class Verified(val file: File, val bytes: Long, val sha256: String) : DownloadResult()

    /** The file was not made, or was not made good. */
    data class Refused(val reason: DownloadRefusal) : DownloadResult()
}

/**
 * Fetches a build and checks it before anything can install it.
 *
 * A file is only ever moved into place under its own name after its fingerprint
 * matches the one the manifest named: a half-written or substituted file is
 * deleted rather than left where an installer might find it.
 *
 * The stream is opened through a function so that a test can hand it a body
 * without a server, and so that the download itself can be proved here rather
 * than only on a device.
 */
class ApkDownloader(
    private val open: (String) -> InputStream = { URL(it).openStream() },
) {

    suspend fun download(
        url: String,
        target: File,
        expectedSha256: String,
        onProgress: (downloadedBytes: Long) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        val expected = expectedSha256.trim().lowercase()
        if (!expected.matches(FINGERPRINT)) {
            return@withContext DownloadResult.Refused(DownloadRefusal.NO_FINGERPRINT)
        }

        val part = File(target.parentFile, target.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L

        try {
            open(url).use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        total += read
                        onProgress(total)
                    }
                }
            }
        } catch (error: Exception) {
            part.delete()
            return@withContext DownloadResult.Refused(DownloadRefusal.FAILED)
        }

        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        if (actual != expected) {
            part.delete()
            return@withContext DownloadResult.Refused(DownloadRefusal.MISMATCH)
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.delete()
            return@withContext DownloadResult.Refused(DownloadRefusal.FAILED)
        }

        DownloadResult.Verified(target, total, actual)
    }

    private companion object {
        const val BUFFER = 64 * 1024
        val FINGERPRINT = Regex("^[0-9a-f]{64}$")
    }
}
