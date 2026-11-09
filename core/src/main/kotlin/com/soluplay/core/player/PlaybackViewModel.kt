package com.soluplay.core.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soluplay.core.browse.CatalogueRepository
import com.soluplay.core.model.ChannelDetail
import com.soluplay.core.model.StreamLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the playback of one channel.
 *
 * It owns the machine and the player, reads the ladder from the site and keeps
 * the screen told what is happening. The screen never drives the walk: it
 * says which channel was picked and shows what comes back.
 */
class PlaybackViewModel(
    private val repository: CatalogueRepository,
    private val host: PlaybackHost,
    private val machine: PlaybackMachine = PlaybackMachine(),
) : ViewModel(), PlaybackListener {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** True while the viewer is holding the feed. */
    private var pausedByViewer = false

    /** True while the app is away and the feed is stopped for that reason. */
    private var pausedByBackground = false

    /**
     * The session whose ladder was read a second time.
     *
     * The ladder is read again when a channel runs out of mirrors, but only
     * once: a second reading that has nothing to play either is the answer,
     * and the screen says so.
     */
    private var refreshedSession = 0L

    init {
        host.listener = this
        viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                poll()
            }
        }
    }

    /**
     * Plays a channel, from the site's own ladder.
     *
     * The ladder is read live and from nowhere else: the saved copy and the
     * baked seed carry no stream addresses, and a stale ladder would walk a
     * viewer into a link that died days ago.
     *
     * The session is begun here, in the order the viewer asked for it, so a
     * ladder that arrives late for a channel they have already left belongs
     * to a session that is over and is dropped.
     */
    fun open(channelId: String, channelName: String, logoUrl: String) {
        // The channel that was on the screen is let go of at once. Its picture
        // and its sound go away before the next one is tuned, so the two are
        // never on the screen together and the old picture cannot sit on top
        // of the new one while it is being tuned.
        host.stop()

        val session = machine.beginSession(channelId, channelName, logoUrl)
        pausedByViewer = false
        pausedByBackground = false
        publish()

        viewModelScope.launch {
            val detail = repository.liveChannelDetail(channelId)
            if (!machine.isCurrentSession(session)) return@launch

            if (detail == null) {
                machine.fail(FailoverReason.LADDER_UNREADABLE)
                publish()
                return@launch
            }

            apply(machine.start(detail, session))
            rehold()
            publish()
        }
    }

    /**
     * Plays the match's own broadcast link.
     *
     * The board publishes one address for the match itself, already checked as
     * an HTTPS link, so there is no ladder to read and nothing to rank: the
     * link is the whole ladder, and the same machine walks it — a first-frame
     * proof, a stall watchdog and a place on the screen — exactly as it does a
     * catalogue channel.
     *
     * `channelId`, `channelName` and `logoUrl` are only the identity the screen
     * shows; nothing about them is read from the catalogue.
     */
    fun openDirect(channelId: String, url: String, channelName: String, logoUrl: String) {
        host.stop()

        val session = machine.beginSession(channelId, channelName, logoUrl)
        pausedByViewer = false
        pausedByBackground = false
        publish()

        val detail = ChannelDetail(
            id = channelId,
            name = channelName,
            logo = logoUrl,
            streams = listOf(
                StreamLink(
                    id = DIRECT_LINK_STREAM_ID,
                    url = url,
                    priority = 1,
                    status = "active",
                ),
            ),
        )
        apply(machine.start(detail, session))
        rehold()
        publish()
    }

    /**
     * Holds the feed again after a ladder was started.
     *
     * Starting the walk forgets who was holding the feed, so a viewer who has
     * put the phone down would get the sound back the moment the ladder
     * arrives. The hold is put back before the screen is told anything.
     */
    private fun rehold() {
        if (!pausedByViewer && !pausedByBackground) return
        machine.hold()
        host.pauseAll()
    }

    /** The viewer left the watch screen. */
    fun close() {
        apply(machine.stop())
        publish()
    }

    /**
     * The viewer asked for another mirror of this channel.
     *
     * The walk moves on to the next published mirror and the one on the
     * screen is given up. The published order is the server's and is not
     * changed by the request.
     */
    fun skipMirror() {
        apply(machine.skipToNextMirror())
        publish()
    }

    /**
     * The viewer held the feed, or let it run again.
     *
     * The picture stays on the screen while the feed is held: the picture is
     * the last thing a viewer has, and taking it away would be a worse answer
     * than a frozen picture.
     */
    fun setPaused(paused: Boolean) {
        pausedByViewer = paused
        applyPause()
        publish()
    }

    /**
     * The app was put away.
     *
     * The feed stops: a phone that is locked, or an app that is minimised,
     * must not keep playing a sound into the background. The picture is not
     * given up — the decoder is only paused — so the channel is where it was
     * when the viewer comes back.
     */
    fun enterBackground() {
        if (pausedByBackground) return
        pausedByBackground = true
        applyPause()
        publish()
    }

    /** The app came back to the front. */
    fun exitBackground() {
        if (!pausedByBackground) return
        pausedByBackground = false
        applyPause()
        publish()
    }

    /**
     * Holds or releases the feed, according to who is asking for it.
     *
     * The feed is held while the viewer is holding it and while the app is
     * away, and it runs again only when neither of them is holding it.
     */
    private fun applyPause() {
        if (pausedByViewer || pausedByBackground) {
            machine.hold()
            host.pauseAll()
        } else {
            machine.releaseHold()
            host.resumeAll()
        }
    }

    override fun onFirstFrame(attempt: Long) {
        viewModelScope.launch {
            apply(machine.onFirstFrame(attempt))
            publish()
        }
    }

    override fun onStall(attempt: Long) {
        viewModelScope.launch {
            apply(machine.onStall(attempt))
            publish()
        }
    }

    override fun onResumed(attempt: Long) {
        viewModelScope.launch {
            apply(machine.onResumed(attempt))
            publish()
        }
    }

    override fun onError(fault: PlaybackFault, attempt: Long, detail: String) {
        viewModelScope.launch {
            apply(machine.onError(fault, attempt, detail))
            publish()
        }
    }

    override fun onCleared() {
        host.listener = null
        host.release()
        super.onCleared()
    }

    /**
     * Reads the player and asks the machine whether anything is due.
     *
     * The clock of the player is the one proof that the picture on the screen
     * is live: a feed whose clock has stopped is a dead carrier, whatever the
     * picture still shows.
     *
     * Only the mirror that is on the screen is read. The mirrors still in the
     * race have no picture to speak of yet.
     */
    private fun poll() {
        val attempt = machine.currentAttempt
        if (host.isBuffering(attempt)) machine.onStall(attempt)
        machine.onPosition(host.currentPositionMs(attempt))
        apply(machine.onTick())
        refreshLadderIfNeeded()
        publish()
    }

    /**
     * The channel's list of mirrors is read again when there is nothing left
     * to play.
     *
     * A channel that was publishing a mirror a minute ago may be publishing it
     * again now, so the list is read once more and the walk is started again
     * with it — that is what the site's own page does when it refreshes. While
     * the list is on its way the screen says so rather than saying the
     * channel is dead, and if the second reading has nothing to play either,
     * the screen keeps the truth it has and says that no mirror is sending a
     * picture.
     */
    private fun refreshLadderIfNeeded() {
        val state = machine.state
        if (state.phase != PlaybackPhase.EXHAUSTED) return
        if (refreshedSession == machine.session) return
        refreshedSession = machine.session

        val channelId = state.channelId
        val session = machine.session

        machine.beginRefresh()

        viewModelScope.launch {
            val detail = repository.liveChannelDetail(channelId)
            if (!machine.isCurrentSession(session)) return@launch

            if (detail == null) {
                machine.fail(FailoverReason.LADDER_UNREADABLE)
                publish()
                return@launch
            }

            apply(machine.start(detail, session))
            rehold()
            publish()
        }
    }

    private fun apply(commands: List<PlaybackCommand>) {
        commands.forEach { command ->
            when (command) {
                is PlaybackCommand.Prepare -> host.prepare(command.url, command.attempt, command.kind)
                is PlaybackCommand.Cancel -> host.cancel(command.attempt)
                is PlaybackCommand.Select -> host.select(command.attempt)
                PlaybackCommand.Stop -> host.stop()
            }
        }
    }

    private fun publish() {
        _state.value = machine.state
    }

    private companion object {
        /** How often the deadlines are checked and the player's clock read. */
        const val POLL_INTERVAL_MS = 500L

        /** The one mirror a match link is played as; it matches no catalogue row. */
        const val DIRECT_LINK_STREAM_ID = "match-direct"
    }
}

/** The view model is built from the app's own graph, not by reflection. */
class PlaybackViewModelFactory(
    private val repository: CatalogueRepository,
    private val host: PlaybackHost,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaybackViewModel::class.java)) {
            return PlaybackViewModel(repository, host) as T
        }
        throw IllegalArgumentException("Unknown view model ${modelClass.name}")
    }
}
