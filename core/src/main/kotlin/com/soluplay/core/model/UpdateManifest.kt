package com.soluplay.core.model

import kotlinx.serialization.Serializable

/**
 * The update manifest.
 *
 * The same document is served twice: from the site (`/api/app/version`) and
 * from the GitHub Releases mirror (`latest.json` in this repository), so that
 * a site outage cannot stop an update. Both copies carry the same fields, and
 * [assetUrl] reads whichever name the copy happens to use.
 */
@Serializable
data class UpdateManifest(
    val schemaVersion: Int = 1,
    val appId: String = "",
    val versionCode: Int = 0,
    val versionName: String = "",
    val updatedAt: String = "",
    val releaseNotes: String = "",
    val apkUrl: String = "",
    val downloadUrl: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val mandatory: Boolean = false,
    val minSupportedVersionCode: Int = 0,
    val rolloutPercent: Int = 100,
    val android: AndroidInfo = AndroidInfo(),
    val variants: Map<String, ManifestVariant> = emptyMap(),
) {
    /** The release asset this manifest points at, whichever name it uses. */
    val assetUrl: String get() = apkUrl.ifBlank { downloadUrl }
}

@Serializable
data class AndroidInfo(
    val packageName: String = "",
    val minSdkVersion: Int = 21,
    val minAndroidVersion: String = "",
    val targetSdkVersion: Int = 34,
)

/** One downloadable build inside the manifest. */
@Serializable
data class ManifestVariant(
    val label: String = "",
    val url: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val minSdkVersion: Int = 21,
)
