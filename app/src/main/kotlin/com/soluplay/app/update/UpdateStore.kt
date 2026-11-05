package com.soluplay.app.update

import android.content.Context
import java.util.UUID

/**
 * What the device remembers about updates.
 *
 * Three things have to survive a restart: the id that decides which share of
 * a staged release this install is in, the moment a build was first seen (a
 * mandatory update is given a day from that moment, not from the moment it
 * was read), and the moment the manifest was last looked at (so a device that
 * is opened ten times a day does not ask ten times a day).
 */
class UpdateStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /**
     * The id of this install.
     *
     * It is made once and never changes, so a release that grows from ten
     * percent to fifty reaches the devices that were already in it.
     */
    fun installId(): String {
        val existing = prefs.getString(KEY_INSTALL_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, created).apply()
        return created
    }

    /**
     * When this build was first offered to this device.
     *
     * The first time it is asked for, the moment is written down; after that
     * the same moment is read back, so the grace a mandatory update is given
     * is the same grace on every run.
     */
    fun firstSeenAt(versionCode: Int, now: Long = System.currentTimeMillis()): Long {
        val key = "$KEY_FIRST_SEEN$versionCode"
        val existing = prefs.getLong(key, 0L)
        if (existing > 0L) return existing

        prefs.edit().putLong(key, now).apply()
        return now
    }

    /** When the manifest was last asked for. */
    fun lastCheckAt(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    fun setLastCheckAt(at: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK, at).apply()
    }

    private companion object {
        const val NAME = "soluplay-update"
        const val KEY_INSTALL_ID = "install-id"
        const val KEY_FIRST_SEEN = "first-seen-"
        const val KEY_LAST_CHECK = "last-check"
    }
}
