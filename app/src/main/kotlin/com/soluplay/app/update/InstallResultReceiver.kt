package com.soluplay.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The system's answer about the install.
 *
 * The installer answers on its own, to a receiver the app put in its
 * manifest, so the answer arrives even while the app is in the background.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        InstallEvents.publish(intent)
    }

    companion object {
        /** The action the installer is answered on. */
        const val ACTION = "com.soluplay.app.INSTALL_RESULT"
    }
}

/** What the system said about the install. */
data class InstallResult(val status: Int, val message: String) {
    val succeeded: Boolean get() = status == PackageInstaller.STATUS_SUCCESS
}

/**
 * The results of the installs this process started.
 *
 * The receiver is made by the system, so the results cannot be handed to the
 * view model directly — they are published here and read by whoever is
 * listening.
 */
object InstallEvents {

    private val _results = MutableSharedFlow<InstallResult>(extraBufferCapacity = 8)

    val results: SharedFlow<InstallResult> = _results

    fun publish(intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        _results.tryEmit(InstallResult(status, message))
    }
}
