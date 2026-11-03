package com.soluplay.core.browse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The catalogue baked into the APK.
 *
 * The seed file carries the body inside a `payload` field so the moment it
 * was baked travels with it; the catalogue is the body under it. A file that
 * carries the body directly is accepted too.
 */
object SeedCatalogue {

    private val json = Json { ignoreUnknownKeys = true }

    fun unwrap(body: String): String {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val payload = (element as? JsonObject)?.get("payload") ?: return body
        return payload.toString()
    }
}
