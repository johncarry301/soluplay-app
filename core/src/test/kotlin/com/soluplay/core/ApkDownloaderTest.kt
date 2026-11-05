package com.soluplay.core

import com.soluplay.core.update.ApkDownloader
import com.soluplay.core.update.DownloadRefusal
import com.soluplay.core.update.DownloadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * The download of a build, and the fingerprint it has to match.
 *
 * A build that is not what the manifest said it was must never reach the
 * installer, so the file is only kept when its fingerprint matches — and a
 * manifest that carries no fingerprint is refused outright rather than
 * trusted.
 */
class ApkDownloaderTest {

    private val payload = "this is the build".toByteArray()

    private val fingerprint: String =
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }

    private fun workspace(): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "soluplay-download-test")
        directory.mkdirs()
        return directory
    }

    @Test
    fun `a build whose fingerprint matches is kept`() = runBlocking {
        val target = File(workspace(), "matching.apk")
        val downloader = ApkDownloader { ByteArrayInputStream(payload) }

        val result = downloader.download("https://example.com/app.apk", target, fingerprint)

        assertTrue(result is DownloadResult.Verified)
        val verified = result as DownloadResult.Verified
        assertEquals(fingerprint, verified.sha256)
        assertEquals(payload.size.toLong(), verified.bytes)
        assertTrue(target.exists())
        assertTrue(target.readBytes().contentEquals(payload))
    }

    @Test
    fun `a build whose fingerprint does not match is thrown away`() = runBlocking {
        val target = File(workspace(), "mismatched.apk")
        val downloader = ApkDownloader { ByteArrayInputStream(payload) }

        val result = downloader.download("https://example.com/app.apk", target, "b".repeat(64))

        assertEquals(DownloadRefusal.MISMATCH, (result as DownloadResult.Refused).reason)
        assertFalse(target.exists())
    }

    @Test
    fun `a manifest with no fingerprint is refused`() = runBlocking {
        val target = File(workspace(), "no-fingerprint.apk")
        val downloader = ApkDownloader { ByteArrayInputStream(payload) }

        val result = downloader.download("https://example.com/app.apk", target, "")

        assertEquals(DownloadRefusal.NO_FINGERPRINT, (result as DownloadResult.Refused).reason)
        assertFalse(target.exists())
    }

    @Test
    fun `a fingerprint that is not a fingerprint is refused`() = runBlocking {
        val target = File(workspace(), "bad-fingerprint.apk")
        val downloader = ApkDownloader { ByteArrayInputStream(payload) }

        val result = downloader.download("https://example.com/app.apk", target, "not-a-fingerprint")

        assertEquals(DownloadRefusal.NO_FINGERPRINT, (result as DownloadResult.Refused).reason)
    }

    @Test
    fun `a download that fails is a failure, not an empty build`() = runBlocking {
        val target = File(workspace(), "failed.apk")
        val downloader = ApkDownloader { _: String ->
            object : InputStream() {
                override fun read(): Int = throw IOException("the connection dropped")
            }
        }

        val result = downloader.download("https://example.com/app.apk", target, fingerprint)

        assertEquals(DownloadRefusal.FAILED, (result as DownloadResult.Refused).reason)
        assertFalse(target.exists())
    }

    @Test
    fun `the progress is told about while the build comes down`() = runBlocking {
        val target = File(workspace(), "progress.apk")
        val seen = mutableListOf<Long>()
        val downloader = ApkDownloader { ByteArrayInputStream(payload) }

        downloader.download("https://example.com/app.apk", target, fingerprint) { bytes ->
            seen += bytes
        }

        assertTrue(seen.isNotEmpty())
        assertEquals(payload.size.toLong(), seen.last())
    }
}
