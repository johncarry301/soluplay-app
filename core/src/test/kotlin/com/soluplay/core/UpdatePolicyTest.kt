package com.soluplay.core

import com.soluplay.core.model.ManifestVariant
import com.soluplay.core.model.UpdateManifest
import com.soluplay.core.update.BlockReason
import com.soluplay.core.update.UpdateOffer
import com.soluplay.core.update.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules about taking an update.
 *
 * These are decisions, not words, so they are proved here without a device:
 * which build is offered, which installs it is offered to, and when the app
 * stops playing until it is taken.
 */
class UpdatePolicyTest {

    private fun offer(
        versionCode: Int = 2,
        mandatory: Boolean = false,
        minSupportedVersionCode: Int = 0,
        rolloutPercent: Int = 100,
        url: String = "https://example.com/soluplay.apk",
    ) = UpdateOffer(
        versionCode = versionCode,
        versionName = "0.2.0",
        url = url,
        sha256 = "a".repeat(64),
        sizeBytes = 1024,
        releaseNotes = "The first release.",
        mandatory = mandatory,
        minSupportedVersionCode = minSupportedVersionCode,
        rolloutPercent = rolloutPercent,
    )

    @Test
    fun `the build for the surface is the one offered`() {
        val manifest = UpdateManifest(
            versionCode = 2,
            versionName = "0.2.0",
            apkUrl = "https://example.com/phone.apk",
            sha256 = "b".repeat(64),
            variants = mapOf(
                "phone" to ManifestVariant(
                    label = "phone",
                    url = "https://example.com/phone-v2.apk",
                    sha256 = "c".repeat(64),
                    sizeBytes = 2048,
                ),
            ),
        )

        val phone = UpdatePolicy.offerOf(manifest, "phone")
        val tv = UpdatePolicy.offerOf(manifest, "tv")

        assertEquals("https://example.com/phone-v2.apk", phone?.url)
        assertEquals("c".repeat(64), phone?.sha256)
        assertEquals(2048L, phone?.sizeBytes)
        assertEquals("https://example.com/phone.apk", tv?.url)
    }

    @Test
    fun `a manifest with nothing to offer is not an offer`() {
        val manifest = UpdateManifest(versionCode = 0, apkUrl = "https://example.com/app.apk")

        assertNull(UpdatePolicy.offerOf(manifest, "phone"))
    }

    @Test
    fun `a manifest with no address is not an offer`() {
        val manifest = UpdateManifest(versionCode = 2, versionName = "0.2.0")

        assertNull(UpdatePolicy.offerOf(manifest, "phone"))
    }

    @Test
    fun `insecure update offers are refused for fallback and variants`() {
        for (url in listOf("http://example.com/app.apk", "https://user:pass@example.com/app.apk")) {
            assertNull(UpdatePolicy.offerOf(UpdateManifest(versionCode = 2, apkUrl = url), "phone"))
            assertNull(UpdatePolicy.offerOf(UpdateManifest(
                versionCode = 2,
                apkUrl = "https://example.com/safe.apk",
                variants = mapOf("phone" to ManifestVariant(label = "phone", url = url)),
            ), "phone"))
            assertNull(UpdatePolicy.decide(offer(url = url), 1, "install", 0, 0).offer)
        }
    }

    @Test
    fun `only a newer build is taken`() {
        assertTrue(UpdatePolicy.isNewer(offer(versionCode = 2), currentVersionCode = 1))
        assertFalse(UpdatePolicy.isNewer(offer(versionCode = 1), currentVersionCode = 1))
        assertFalse(UpdatePolicy.isNewer(offer(versionCode = 1), currentVersionCode = 2))
    }

    @Test
    fun `an install lands in the same bucket every time`() {
        val first = UpdatePolicy.bucketOf("install-abc")

        assertEquals(first, UpdatePolicy.bucketOf("install-abc"))
        assertTrue(first in 1..100)
    }

    @Test
    fun `a full release reaches every install and an empty one reaches none`() {
        assertTrue(UpdatePolicy.inRollout(100, "any-install"))
        assertFalse(UpdatePolicy.inRollout(0, "any-install"))
    }

    @Test
    fun `a staged release reaches only the installs in its share`() {
        val inside = (1..500).map { "install-$it" }.filter {
            UpdatePolicy.bucketOf(it) <= 10
        }
        val outside = (1..500).map { "install-$it" }.filter {
            UpdatePolicy.bucketOf(it) > 10
        }

        assertTrue(inside.isNotEmpty())
        assertTrue(outside.isNotEmpty())
        assertTrue(inside.all { UpdatePolicy.inRollout(10, it) })
        assertTrue(outside.none { UpdatePolicy.inRollout(10, it) })
    }

    @Test
    fun `a release that grows reaches the installs it already had`() {
        val ids = (1..200).map { "install-$it" }
        val reachedAtTen = ids.filter { UpdatePolicy.inRollout(10, it) }

        val reachedAtFifty = ids.filter { UpdatePolicy.inRollout(50, it) }

        assertTrue(reachedAtTen.all { it in reachedAtFifty })
    }

    @Test
    fun `a retired build may not keep playing`() {
        assertTrue(UpdatePolicy.mustUpdateToPlay(currentVersionCode = 1, minSupportedVersionCode = 2))
        assertFalse(UpdatePolicy.mustUpdateToPlay(currentVersionCode = 2, minSupportedVersionCode = 2))
        assertFalse(UpdatePolicy.mustUpdateToPlay(currentVersionCode = 1, minSupportedVersionCode = 0))
    }

    @Test
    fun `a mandatory update is given a day before it stops the app`() {
        val offered = offer(mandatory = true)
        val firstSeen = 1_000_000L

        val inside = UpdatePolicy.decide(
            offer = offered,
            currentVersionCode = 1,
            installId = "install-1",
            firstSeenAt = firstSeen,
            now = firstSeen + UpdatePolicy.MANDATORY_GRACE_MS - 1,
        )
        val outside = UpdatePolicy.decide(
            offer = offered,
            currentVersionCode = 1,
            installId = "install-1",
            firstSeenAt = firstSeen,
            now = firstSeen + UpdatePolicy.MANDATORY_GRACE_MS,
        )

        assertNotNull(inside.offer)
        assertNull(inside.block)
        assertFalse(inside.blocksPlayback)
        assertEquals(BlockReason.MANDATORY, outside.block)
        assertTrue(outside.blocksPlayback)
    }

    @Test
    fun `an optional update never stops the app`() {
        val decision = UpdatePolicy.decide(
            offer = offer(),
            currentVersionCode = 1,
            installId = "install-1",
            firstSeenAt = 0L,
            now = Long.MAX_VALUE,
        )

        assertNotNull(decision.offer)
        assertNull(decision.block)
    }

    @Test
    fun `a retired build stops the app at once`() {
        val decision = UpdatePolicy.decide(
            offer = offer(minSupportedVersionCode = 2),
            currentVersionCode = 1,
            installId = "install-1",
            firstSeenAt = 0L,
            now = 0L,
        )

        assertEquals(BlockReason.RETIRED, decision.block)
        assertTrue(decision.blocksPlayback)
    }

    @Test
    fun `nothing at all is nothing to offer`() {
        val decision = UpdatePolicy.decide(
            offer = null,
            currentVersionCode = 1,
            installId = "install-1",
            firstSeenAt = 0L,
            now = 0L,
        )

        assertNull(decision.offer)
        assertNull(decision.block)
    }

    @Test
    fun `the build that is running is not offered to itself`() {
        val decision = UpdatePolicy.decide(
            offer = offer(versionCode = 1),
            currentVersionCode = 1,
            installId = "install-1",
            firstSeenAt = 0L,
            now = 0L,
        )

        assertNull(decision.offer)
    }

    @Test
    fun `an install outside the share is offered nothing`() {
        val outside = (1..500).map { "install-$it" }.first { UpdatePolicy.bucketOf(it) > 1 }

        val decision = UpdatePolicy.decide(
            offer = offer(rolloutPercent = 1),
            currentVersionCode = 1,
            installId = outside,
            firstSeenAt = 0L,
            now = 0L,
        )

        assertNull(decision.offer)
        assertFalse(decision.blocksPlayback)
    }
}
