package com.soluplay.core.update

/**
 * What the app has been told about a build it does not have yet.
 *
 * The manifest carries more than one build — a phone build and a television
 * build — so the offer is already the one this device should take.
 */
data class UpdateOffer(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseNotes: String,
    val mandatory: Boolean,
    val minSupportedVersionCode: Int,
    val rolloutPercent: Int,
)

/**
 * What the policy says about one offer on one device.
 *
 * The offer is kept even when the device is outside the staged share or is
 * already on that build, because the caller may still want to show nothing
 * about it — the decision, not the offer, is what the screen acts on.
 */
data class UpdateDecision(
    val offer: UpdateOffer? = null,
    val block: BlockReason? = null,
) {
    /** True when the app must not play until this build is taken. */
    val blocksPlayback: Boolean get() = block != null
}

/**
 * The rules about taking an update, and about refusing to play without one.
 *
 * They are here and not in the screen because they are decisions, not words:
 * the same rules have to hold on a phone and on a television, and they have to
 * be provable without a device.
 */
object UpdatePolicy {

    private fun isSecureUrl(url: String): Boolean = try {
        com.soluplay.core.api.UrlConnectionTransport.requireSecureUrl(url)
        true
    } catch (_: java.io.IOException) {
        false
    }

    /**
     * How long a mandatory update may be ignored before it stops playback.
     *
     * A mandatory update that stopped the app the minute it appeared would be
     * read as a broken app, so the device is given a day to take it and is
     * told, on that day, that it is being asked to.
     */
    const val MANDATORY_GRACE_MS = 24 * 60 * 60 * 1000L

    /**
     * The offer for this device, or null when the manifest has nothing to say
     * to it.
     *
     * The variant for the surface wins over the top of the manifest: the same
     * release number is two different files on a phone and on a television.
     */
    fun offerOf(
        manifest: com.soluplay.core.model.UpdateManifest,
        surface: String,
    ): UpdateOffer? {
        val variant = manifest.variants[surface]
        val url = variant?.url?.takeIf { it.isNotBlank() } ?: manifest.assetUrl
        if (!isSecureUrl(url)) return null
        if (manifest.versionCode <= 0) return null

        return UpdateOffer(
            versionCode = manifest.versionCode,
            versionName = manifest.versionName,
            url = url,
            sha256 = variant?.sha256?.takeIf { it.isNotBlank() } ?: manifest.sha256,
            sizeBytes = variant?.sizeBytes?.takeIf { it > 0 } ?: manifest.sizeBytes,
            releaseNotes = manifest.releaseNotes,
            mandatory = manifest.mandatory,
            minSupportedVersionCode = manifest.minSupportedVersionCode,
            rolloutPercent = manifest.rolloutPercent,
        )
    }

    /** True when the build on offer is newer than the one that is running. */
    fun isNewer(offer: UpdateOffer, currentVersionCode: Int): Boolean =
        offer.versionCode > currentVersionCode

    /**
     * True when this install is in the share of installs the release is for.
     *
     * The share is a hundred buckets and every install lands in one of them by
     * its own id, so the same device is always in the same bucket and a
     * release that grows from ten percent to fifty reaches exactly the
     * devices that were already in it.
     */
    fun inRollout(percent: Int, installId: String): Boolean {
        if (percent >= 100) return true
        if (percent <= 0) return false
        return bucketOf(installId) <= percent
    }

    /**
     * The bucket an install lands in, from one to a hundred.
     *
     * The hash is the one every language agrees on, so the same install id
     * lands in the same bucket here and on the machine that published the
     * manifest.
     */
    fun bucketOf(installId: String): Int {
        var hash = -0x7ee3623bL
        for (character in installId) {
            hash = hash xor character.code.toLong()
            hash *= 16777619
        }
        return (((hash and 0x7fffffffL) % 100L) + 1L).toInt()
    }

    /**
     * True when this device is too old to keep playing.
     *
     * The manifest names the oldest build that may still play, so a release
     * can retire the builds it has broken.
     */
    fun mustUpdateToPlay(currentVersionCode: Int, minSupportedVersionCode: Int): Boolean =
        minSupportedVersionCode > 0 && currentVersionCode < minSupportedVersionCode

    /**
     * True when playback must stop.
     *
     * Two things stop it: a build that has been retired, and a mandatory
     * update that has been on the device for longer than the grace.
     */
    fun blocksPlayback(
        offer: UpdateOffer,
        currentVersionCode: Int,
        now: Long,
        firstSeenAt: Long,
    ): Boolean {
        if (mustUpdateToPlay(currentVersionCode, offer.minSupportedVersionCode)) return true
        if (!offer.mandatory) return false
        if (firstSeenAt <= 0L) return false
        return now >= firstSeenAt + MANDATORY_GRACE_MS
    }

    /**
     * What to do with one offer on one device.
     *
     * An offer that is not newer, or that this install is not in the share of,
     * is nothing at all — the app carries on as it is. An offer that is
     * refused the app is told about only when the manifest has retired this
     * build or a mandatory update has outlasted its grace.
     */
    fun decide(
        offer: UpdateOffer?,
        currentVersionCode: Int,
        installId: String,
        firstSeenAt: Long,
        now: Long,
    ): UpdateDecision {
        if (offer == null || !isSecureUrl(offer.url)) return UpdateDecision()
        if (!isNewer(offer, currentVersionCode)) return UpdateDecision()
        if (!inRollout(offer.rolloutPercent, installId)) return UpdateDecision()

        if (mustUpdateToPlay(currentVersionCode, offer.minSupportedVersionCode)) {
            return UpdateDecision(offer, BlockReason.RETIRED)
        }
        if (offer.mandatory && firstSeenAt > 0L && now >= firstSeenAt + MANDATORY_GRACE_MS) {
            return UpdateDecision(offer, BlockReason.MANDATORY)
        }
        return UpdateDecision(offer)
    }
}
