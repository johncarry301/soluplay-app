package com.soluplay.core

import com.soluplay.core.api.SoluPlayJson
import com.soluplay.core.model.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The manifest this repository publishes must stay readable by the updater
 * that ships inside the app, so the test parses the real file rather than a
 * copy of it.
 */
class UpdateManifestTest {

    @Test
    fun `the published manifest parses into the updater model`() {
        val manifest = SoluPlayJson.json.decodeFromString(
            UpdateManifest.serializer(),
            repoFile("latest.json").readText(),
        )

        assertEquals("com.soluplay.app", manifest.appId)
        assertEquals(1, manifest.versionCode)
        assertEquals("0.1.0", manifest.versionName)
        assertEquals(21, manifest.android.minSdkVersion)
        assertEquals(34, manifest.android.targetSdkVersion)
        assertTrue(manifest.variants.containsKey("phone"))
        assertTrue(manifest.variants.containsKey("tv"))
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
}
