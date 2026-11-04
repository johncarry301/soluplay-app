package com.soluplay.core.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soluplay.core.browse.CatalogueRepository
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
        val session = machine.beginSession(channelId, channelName, logoUrl)
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
            publish()
        }
    }

    /** The viewer left the watch screen. */
    fun close() {
        apply(machine.stop())
        publish()
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
        publish()
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
