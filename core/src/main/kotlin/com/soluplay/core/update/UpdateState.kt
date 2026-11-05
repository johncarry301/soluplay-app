package com.soluplay.core.update

/**
 * What the updater is doing, in the words a screen can draw.
 *
 * The states are the same on a phone and on a television, so the sheet that
 * draws them is written once.
 */
sealed class UpdateState {

    /** Nothing is known about an update yet, or there is nothing to say. */
    data object Idle : UpdateState()

    /** The manifest is being fetched. */
    data object Checking : UpdateState()

    /** A newer build exists and the sheet is asking about it. */
    data class Offered(val offer: UpdateOffer) : UpdateState()

    /** The build is coming down. */
    data class Downloading(
        val offer: UpdateOffer,
        val downloaded: Long,
        val total: Long,
    ) : UpdateState()

    /** The build is here and its fingerprint matched. The install can start. */
    data class Ready(val offer: UpdateOffer, val filePath: String) : UpdateState()

    /** The system has the build and is asking the person to install it. */
    data class Installing(val offer: UpdateOffer, val filePath: String) : UpdateState()

    /** The device does not let this app install packages yet. */
    data class PermissionNeeded(val offer: UpdateOffer, val filePath: String) : UpdateState()

    /** The build did not come down. */
    data class Failed(val offer: UpdateOffer, val reason: DownloadRefusal) : UpdateState()

    /** This build may no longer play, and the screen says so instead. */
    data class Blocked(val offer: UpdateOffer, val reason: BlockReason) : UpdateState()
}

/** The offer a state carries, or null when the state carries none. */
fun UpdateState.offerOrNull(): UpdateOffer? = when (this) {
    is UpdateState.Idle -> null
    is UpdateState.Checking -> null
    is UpdateState.Offered -> offer
    is UpdateState.Downloading -> offer
    is UpdateState.Ready -> offer
    is UpdateState.Installing -> offer
    is UpdateState.PermissionNeeded -> offer
    is UpdateState.Failed -> offer
    is UpdateState.Blocked -> offer
}

/**
 * Why the app stops playing.
 *
 * The two are told apart because the viewer can act on one of them and not the
 * other: a retired build has to be replaced now, while a mandatory update has
 * been asked for before and is being asked for again.
 */
enum class BlockReason {
    /** The manifest names this build as retired. */
    RETIRED,

    /** A mandatory update has been on the device longer than the grace. */
    MANDATORY,
}
