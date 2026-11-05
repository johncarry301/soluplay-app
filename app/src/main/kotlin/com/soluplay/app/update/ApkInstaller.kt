package com.soluplay.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The install of a build that has already been verified.
 *
 * The system does the installing, not the app: the file is written into a
 * session, the session is committed, and the person is asked by the system.
 * Nothing here is silent — an app that replaced itself without asking would
 * be an app nobody keeps.
 */
class ApkInstaller(private val context: Context) {

    /** True when the device lets this app install packages. */
    fun canInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** The screen where the owner gives that permission. */
    fun permissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Hands the file to the system's installer.
     *
     * True means the system took it and the person is being asked. False
     * means the session could not be opened and there is nothing on the
     * screen to answer.
     */
    suspend fun install(file: File): Boolean = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(file.length())

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("package", 0, file.length()).use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                commit(session, sessionId)
            }
            true
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            false
        }
    }

    /**
     * The commit, in the form the device understands.
     *
     * A device older than Oreo takes a plain intent; a newer one takes a
     * sender, because the system has to be able to answer later. The sender
     * has to be mutable from Android 12 on, or the system cannot add its own
     * extras to it.
     *
     * The old form was taken out of the platform in Android 10, so it is
     * called by name on the devices that still have it.
     */
    private fun commit(session: PackageInstaller.Session, sessionId: Int) {
        val intent = Intent(context, InstallResultReceiver::class.java).apply {
            action = InstallResultReceiver.ACTION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            session.commit(senderFor(sessionId, intent))
        } else {
            val legacy = session.javaClass.getMethod("commit", Intent::class.java)
            legacy.invoke(session, intent)
        }
    }

    private fun senderFor(sessionId: Int, intent: Intent): IntentSender {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }
}
