package com.soluplay.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The system's answer about the install.
 *
 * The installer answers on its own, to a receiver the app put in its
 * manifest, so the answer arrives even while the app is in the background.
 *
 * One of the answers is not an outcome at all: it is the system asking the
 * person to confirm the install, and it carries the confirmation screen as an
 * intent. Opening that intent is the app's job — a receiver that only reads
 * the status leaves the person with a downloaded build and no dialog, which
 * is exactly what this one used to do.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = confirmationOf(intent)
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(confirmation) }.isSuccess) return
            }
        }

        InstallEvents.publish(intent)
    }

    /** The confirmation screen the system sent with its answer. */
    private fun confirmationOf(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
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
