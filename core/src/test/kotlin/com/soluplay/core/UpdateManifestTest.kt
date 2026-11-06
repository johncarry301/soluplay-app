package com.soluplay.core

import com.soluplay.core.api.SoluPlayJson
import com.soluplay.core.model.UpdateManifest
import com.soluplay.core.update.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The manifest this repository publishes must stay readable by the updater
 * that ships inside the app, so the test parses the real file rather than a
 * copy of it.
 *
 * The same document is read by three readers that were written separately:
 * the app, the site's release route and the download page. A field renamed in
 * one place and not the other is not a rename, it is a broken release, so the
 * shape the publishing job writes is proved here against the app's own model.
 */
class UpdateManifestTest {

    @Test
    fun `the published manifest parses into the updater model`() {
        val manifest = published()

        assertEquals("com.soluplay.app", manifest.appId)
        assertEquals(21, manifest.android.minSdkVersion)
        assertEquals(34, manifest.android.targetSdkVersion)
        assertTrue(manifest.variants.containsKey("phone"))
        assertTrue(manifest.variants.containsKey("tv"))
    }

    @Test
    fun `the published manifest names the build that is released`() {
        val manifest = published()

        assertTrue(manifest.versionCode > 0)
        assertTrue(manifest.versionName.isNotBlank())
        assertTrue(manifest.assetUrl.contains("/v${manifest.versionName}/"))
        assertTrue(manifest.sha256.matches(Regex("[0-9a-f]{64}")))
        assertNotNull(UpdatePolicy.offerOf(manifest, "phone"))
        assertNotNull(UpdatePolicy.offerOf(manifest, "tv"))
    }

    @Test
    fun `the source in this repository is never behind the manifest it publishes`() {
        // The manifest names the newest published build, so the build in this
        // repository is never older than it: a device already running this
        // source must not be offered a build it is.
        val manifest = published()
        val app = appVersion()

        assertTrue(manifest.versionCode <= app.first)
        assertTrue(app.second.isNotBlank())
    }

    @Test
    fun `the asset url is read whichever name the copy uses`() {
        val fromApi = SoluPlayJson.json.decodeFromString(
            UpdateManifest.serializer(),
            """{ "versionCode": 2, "apkUrl": "https://example.com/app.apk" }""",
        )
        val fromMirror = SoluPlayJson.json.decodeFromString(
            UpdateManifest.serializer(),
            """{ "versionCode": 2, "downloadUrl": "https://example.com/app.apk" }""",
        )

        assertEquals("https://example.com/app.apk", fromApi.assetUrl)
        assertEquals("https://example.com/app.apk", fromMirror.assetUrl)
    }

    @Test
    fun `the document the publishing job writes is the one the app reads`() {
        val manifest = read(PUBLISHED_BY_THE_JOB)

        assertEquals(2, manifest.versionCode)
        assertEquals("0.2.0", manifest.versionName)

        val phone = UpdatePolicy.offerOf(manifest, "phone")
        val tv = UpdatePolicy.offerOf(manifest, "tv")

        assertNotNull(phone)
        assertNotNull(tv)
        assertEquals(URL, phone?.url)
        assertEquals(SHA, phone?.sha256)
        assertEquals(SIZE, phone?.sizeBytes)
        assertEquals(URL, tv?.url)
        assertEquals(SHA, tv?.sha256)
        assertEquals(SIZE, tv?.sizeBytes)
    }

    @Test
    fun `when the television build is published on its own, that file is the one offered`() {
        val manifest = read(SPLIT)

        val tv = UpdatePolicy.offerOf(manifest, "tv")

        assertEquals(TV_URL, tv?.url)
        assertEquals(TV_SHA, tv?.sha256)
        assertEquals(TV_SIZE, tv?.sizeBytes)
    }

    @Test
    fun `a device on the older build is offered the new one and a device on it is not`() {
        val offer = UpdatePolicy.offerOf(read(PUBLISHED_BY_THE_JOB), "phone")

        val onTheOldBuild = UpdatePolicy.decide(
            offer,
            currentVersionCode = 1,
            installId = "install-1",
            firstSeenAt = 0L,
            now = 0L,
        )
        val onTheNewBuild = UpdatePolicy.decide(
            offer,
            currentVersionCode = 2,
            installId = "install-1",
            firstSeenAt = 0L,
            now = 0L,
        )

        assertNotNull(onTheOldBuild.offer)
        assertNull(onTheOldBuild.block)
        assertNull(onTheNewBuild.offer)
    }

    /** The manifest as it is published in this repository. */
    private fun published(): UpdateManifest = read(repoFile("latest.json").readText())

    /** The version of the build in this repository, as the build file names it. */
    private fun appVersion(): Pair<Int, String> {
        val buildFile = repoFile("app/build.gradle.kts").readText()
        val code = Regex("versionCode\\s*=\\s*(\\d+)").find(buildFile)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("no versionCode in app/build.gradle.kts")
        val name = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(buildFile)?.groupValues?.get(1)
            ?: throw AssertionError("no versionName in app/build.gradle.kts")
        return code to name
    }

    private fun read(body: String): UpdateManifest =
        SoluPlayJson.json.decodeFromString(UpdateManifest.serializer(), body)

    /** Finds a repository file by walking up from the working directory. */
    private fun repoFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("could not find $name above ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val URL =
            "https://github.com/johncarry301/soluplay-app/releases/download/v0.2.0/SoluPlay.apk"
        const val TV_URL =
            "https://github.com/johncarry301/soluplay-app/releases/download/v0.2.0/SoluPlay-tv.apk"
        const val SHA = "1a2b3c4d5e6f70819a2b3c4d5e6f70819a2b3c4d5e6f70819a2b3c4d5e6f7081"
        const val TV_SHA = "2b3c4d5e6f70819a2b3c4d5e6f70819a2b3c4d5e6f70819a2b3c4d5e6f708192a"
        const val SIZE = 12345678L
        const val TV_SIZE = 87654321L

        /**
         * The document as the publishing job writes it: the same fields, the
         * same names, and both variants naming the one published file.
         */
        val PUBLISHED_BY_THE_JOB = """
            {
              "schemaVersion": 1,
              "appId": "com.soluplay.app",
              "versionCode": 2,
              "versionName": "0.2.0",
              "updatedAt": "2026-11-05T00:00:00Z",
              "releaseNotes": "SoluPlay 0.2.0",
              "apkUrl": "$URL",
              "downloadUrl": "$URL",
              "fileName": "SoluPlay.apk",
              "sizeBytes": $SIZE,
              "sha256": "$SHA",
              "mandatory": false,
              "minSupportedVersionCode": 0,
              "rolloutPercent": 100,
              "android": {
                "packageName": "com.soluplay.app",
                "minSdkVersion": 21,
                "minAndroidVersion": "5.0",
                "targetSdkVersion": 34
              },
              "variants": {
                "phone": {
                  "label": "Android Phone & Tablet",
                  "url": "$URL",
                  "fileName": "SoluPlay.apk",
                  "sizeBytes": $SIZE,
                  "sha256": "$SHA",
                  "minSdkVersion": 21
                },
                "tv": {
                  "label": "Android TV & TV Boxes",
                  "url": "$URL",
                  "fileName": "SoluPlay.apk",
                  "sizeBytes": $SIZE,
                  "sha256": "$SHA",
                  "minSdkVersion": 21
                }
              }
            }
        """.trimIndent()

        /** The same document once the television build is published on its own. */
        val SPLIT = """
            {
              "schemaVersion": 1,
              "appId": "com.soluplay.app",
              "versionCode": 2,
              "versionName": "0.2.0",
              "apkUrl": "$URL",
              "downloadUrl": "$URL",
              "fileName": "SoluPlay.apk",
              "sizeBytes": $SIZE,
              "sha256": "$SHA",
              "rolloutPercent": 100,
              "variants": {
                "phone": {
                  "url": "$URL",
                  "fileName": "SoluPlay.apk",
                  "sizeBytes": $SIZE,
                  "sha256": "$SHA"
                },
                "tv": {
                  "url": "$TV_URL",
                  "fileName": "SoluPlay-tv.apk",
                  "sizeBytes": $TV_SIZE,
                  "sha256": "$TV_SHA"
                }
              }
            }
        """.trimIndent()
    }
}
