package com.soluplay.app.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soluplay.core.update.ApkDownloader
import com.soluplay.core.update.DownloadRefusal
import com.soluplay.core.update.DownloadResult
import com.soluplay.core.update.UpdateManifestClient
import com.soluplay.core.update.UpdateOffer
import com.soluplay.core.update.UpdatePolicy
import com.soluplay.core.update.UpdateState
import com.soluplay.core.update.offerOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The updater, from the launch of the app to the install of a new build.
 *
 * The rules about whether an update is offered, and whether the app may keep
 * playing without it, are in the policy and are not decided here. What lives
 * here is the part that needs a device: the asking, the downloading, the
 * fingerprint and the handover to the system.
 */
class UpdateViewModel(
    private val manifestClient: UpdateManifestClient,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val store: UpdateStore,
    private val downloadDirectory: File,
    private val surface: String,
    private val currentVersionCode: Int,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The build that was downloaded and verified, kept until it is installed. */
    private var downloaded: File? = null

    init {
        viewModelScope.launch {
            InstallEvents.results.collect { result ->
                val offer = _state.value.offerOrNull() ?: return@collect
                if (result.succeeded) return@collect
                _state.value = UpdateState.Failed(offer, DownloadRefusal.FAILED)
            }
        }
    }

    /**
     * Asks whether there is a newer build.
     *
     * The manifest is not asked for on every resume: a device that is opened
     * ten times a day must not fetch ten times a day. [force] is for the
     * person asking for it.
     */
    fun check(force: Boolean = false) {
        if (!force) {
            val last = store.lastCheckAt()
            if (last > 0L && now() - last < CHECK_INTERVAL_MS) return
        }

        viewModelScope.launch {
            _state.value = UpdateState.Checking
            store.setLastCheckAt(now())

            val offer = manifestClient.offer(surface)
            val decision = UpdatePolicy.decide(
                offer = offer,
                currentVersionCode = currentVersionCode,
                installId = store.installId(),
                firstSeenAt = firstSeenOf(offer),
                now = now(),
            )

            _state.value = when {
                decision.block != null && decision.offer != null ->
                    UpdateState.Blocked(decision.offer, decision.block)

                decision.offer != null -> UpdateState.Offered(decision.offer)
                else -> UpdateState.Idle
            }
        }
    }

    /**
     * The moment this build was first offered to this device.
     *
     * It is only written down for an offer this device is actually being
     * given: a build that is newer but sits outside the staged share has not
     * been asked of this device, so its grace has not started.
     */
    private fun firstSeenOf(offer: UpdateOffer?): Long {
        if (offer == null) return 0L
        if (!UpdatePolicy.isNewer(offer, currentVersionCode)) return 0L
        if (!UpdatePolicy.inRollout(offer.rolloutPercent, store.installId())) return 0L
        return store.firstSeenAt(offer.versionCode, now())
    }

    /**
     * Takes the build.
     *
     * One press downloads it, checks its fingerprint and hands it to the
     * system's installer. A device that does not allow this app to install
     * packages is asked for that permission instead, and the build that was
     * already downloaded is kept for the second press.
     */
    fun install() {
        val offer = _state.value.offerOrNull() ?: return

        viewModelScope.launch {
            val file = downloaded?.takeIf { it.exists() } ?: download(offer) ?: return@launch
            downloaded = file

            if (!installer.canInstall()) {
                _state.value = UpdateState.PermissionNeeded(offer, file.path)
                return@launch
            }

            val started = installer.install(file)
            _state.value = if (started) {
                UpdateState.Installing(offer, file.path)
            } else {
                UpdateState.Failed(offer, DownloadRefusal.FAILED)
            }
        }
    }

    /** The download, with the fingerprint checked before the file is kept. */
    private suspend fun download(offer: UpdateOffer): File? {
        _state.value = UpdateState.Downloading(offer, 0, offer.sizeBytes)

        if (!downloadDirectory.isDirectory) downloadDirectory.mkdirs()
        val target = File(downloadDirectory, fileNameOf(offer))
        val result = downloader.download(offer.url, target, offer.sha256) { bytes ->
            _state.value = UpdateState.Downloading(offer, bytes, offer.sizeBytes)
        }

        return when (result) {
            is DownloadResult.Verified -> result.file
            is DownloadResult.Refused -> {
                _state.value = UpdateState.Failed(offer, result.reason)
                null
            }
        }
    }

    /** The file name the build is kept under. */
    private fun fileNameOf(offer: UpdateOffer): String {
        val name = offer.url.substringAfterLast('/').substringBefore('?')
        return if (name.endsWith(".apk")) name else "soluplay-${offer.versionCode}.apk"
    }

    /**
     * Puts the sheet away.
     *
     * A build that has been retired, or a mandatory update that has outlasted
     * its grace, cannot be put away: the app does not carry on playing.
     */
    fun dismiss() {
        val current = _state.value
        if (current is UpdateState.Blocked) return
        _state.value = UpdateState.Idle
    }

    /** True when the app may not play until a new build is taken. */
    fun blocksPlayback(): Boolean = _state.value is UpdateState.Blocked

    companion object {
        /** How often the manifest is asked for. */
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}

/** The view model is built from the app's own graph, not by reflection. */
class UpdateViewModelFactory(
    private val manifestClient: UpdateManifestClient,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val store: UpdateStore,
    private val downloadDirectory: File,
    private val surface: String,
    private val currentVersionCode: Int,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UpdateViewModel::class.java)) {
            return UpdateViewModel(
                manifestClient = manifestClient,
                downloader = downloader,
                installer = installer,
                store = store,
                downloadDirectory = downloadDirectory,
                surface = surface,
                currentVersionCode = currentVersionCode,
            ) as T
        }
        throw IllegalArgumentException("Unknown view model ${modelClass.name}")
    }
}
