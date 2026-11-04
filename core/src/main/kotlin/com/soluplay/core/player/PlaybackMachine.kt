package com.soluplay.core.player

import com.soluplay.core.ladder.Ladder
import com.soluplay.core.model.ChannelDetail
import com.soluplay.core.model.StreamLink

/**
 * The playback rules, as one small machine.
 *
 * It knows three things: which mirrors are being tried at this moment, whether
 * one of them has proved a picture, and whether the picture is still moving.
 * Everything else — what a mirror is, how to play one — belongs to the surface.
 *
 * The machine has no clock of its own and no player of its own: the time comes
 * from an injected clock and the playing is done by whoever owns the player.
 * That is what lets the whole ladder, every failure and every deadline be
 * tested without a device and without a network.
 *
 * The deadlines are the web player's own, so the app fails over at the same
 * moment the site does and for the same reasons.
 *
 * The mirrors are raced, not walked one at a time. The first published mirrors
 * are tuned together and the first of them to put a picture on the screen
 * keeps it; the others are let go of at once. The published order is never
 * changed — the race only decides how many of the published mirrors are in
 * flight at the same time, and when the whole batch has failed the walk goes
 * on to the next mirrors in the order the site published them.
 *
 * Two numbers keep late answers from changing what is on screen. The session
 * says which channel the screen is on, so a ladder that arrives for a channel
 * the viewer has already left is refused. The attempt says which try at
 * playing an event belongs to, so a picture, a stall or an error from a
 * mirror that has already been given up cannot be mistaken for the one being
 * waited for.
 */
class PlaybackMachine(
    private val now: () -> Long = System::currentTimeMillis,
    private val firstFrameDeadlineMs: Long = PlaybackRules.FIRST_FRAME_DEADLINE_MS,
    private val youtubeFirstFrameDeadlineMs: Long = PlaybackRules.YOUTUBE_FIRST_FRAME_DEADLINE_MS,
    private val stallGraceMs: Long = PlaybackRules.STALL_GRACE_MS,
    private val transientRecoveryMs: Long = PlaybackRules.TRANSIENT_RECOVERY_MS,
    private val raceSize: Int = PlaybackRules.RACE_SIZE,
) {

    /**
     * One mirror in the race.
     *
     * A candidate is a published mirror, the try at playing it, and everything
     * the machine has to know about that try: its own attempt number, the
     * deadline it has to prove a picture by, and how many second chances it
     * has already had.
     */
    private class Candidate(
        val link: StreamLink,
        /** Where it sits in the published order, counting from one. */
        val rank: Int,
        var attempt: Long,
        /** What kind of source this is, and so how it is played. */
        val kind: StreamKind,
        /** When this mirror has to have proved a picture by. */
        var deadlineAt: Long?,
        var secondChances: Int = 0,
        /** True once this mirror has put a picture on the screen. */
        var proved: Boolean = false,
    )

    /** A published mirror that is waiting for its turn. */
    private class Published(val link: StreamLink, val rank: Int)

    private var ladder: Ladder = Ladder(emptyList())
    private var mirrorCount = 0

    private var phase: PlaybackPhase = PlaybackPhase.IDLE
    private var failure: FailoverReason? = null

    /** The player's own reason for the last failure, when it gave one. */
    private var failureDetail = ""

    /** When the mirror on the screen has to have proved a picture again by. */
    private var deadlineAt: Long? = null

    /** When the picture was last seen to stop moving. */
    private var stallSince: Long? = null

    /** The clock of the player as it was last seen. */
    private var lastPosition: Long? = null

    private var channelId = ""
    private var channelName = ""
    private var logoUrl = ""
    private var category = ""

    /** The channel the screen is on. */
    private var currentSession = 0L

    /** The try at playing the machine is waiting on. */
    private var attempt = 0L
    private var attempts = 0L

    /** The mirrors in flight, in the order the site published them. */
    private val candidates = mutableListOf<Candidate>()

    /**
     * The mirrors that lost a race and are waiting for another turn.
     *
     * A mirror that lost the race was not proved bad — it was simply slower
     * than the mirror that won. So it keeps its place in the published order
     * and is raced again if the picture on the screen dies. A mirror that
     * failed is not kept: a mirror that has already been given up is given up.
     */
    private val reserve = mutableListOf<Published>()

    /** The candidate that proved a picture, if one has. */
    private var winner: Candidate? = null

    /** The try at playing the machine is waiting on. */
    val currentAttempt: Long get() = winner?.attempt ?: attempt

    /** What the screen has to say. */
    val state: PlaybackState
        get() = PlaybackState(
            phase = phase,
            channelId = channelId,
            channelName = channelName,
            logoUrl = logoUrl,
            category = category,
            mirror = winner?.rank ?: candidates.firstOrNull()?.rank ?: 0,
            mirrorCount = mirrorCount,
            racing = candidates.count { !it.proved },
            failure = failure,
            detail = failureDetail,
        )

    /**
     * A channel was asked for and its ladder is on its way.
     *
     * Nothing is playing yet and nothing is claimed to be: the screen says the
     * ladder is being fetched. The session number this returns is what the
     * ladder has to be handed back with, so a ladder that arrives late for a
     * channel the viewer has already left is refused instead of played.
     */
    fun beginSession(channelId: String, channelName: String, logoUrl: String): Long {
        currentSession += 1
        resetPlayback()
        this.channelId = channelId
        this.channelName = channelName
        this.logoUrl = logoUrl
        phase = PlaybackPhase.LOADING
        return currentSession
    }

    /** True while the given session is the one the machine is on. */
    fun isCurrentSession(session: Long): Boolean = session == currentSession

    /**
     * The ladder arrived, and the race begins.
     *
     * The list is handed to the ladder as it came: the server ranked these
     * mirrors and the app races them in that order without re-ranking them.
     *
     * A ladder for a session that is over is refused: it belongs to a channel
     * the viewer has already left, and playing it would put the wrong channel
     * on the screen.
     */
    fun start(detail: ChannelDetail, session: Long): List<PlaybackCommand> {
        if (session != currentSession) return emptyList()

        resetPlayback()
        channelId = detail.id
        channelName = detail.name.ifBlank { channelName }
        logoUrl = detail.logo.ifBlank { logoUrl }
        category = detail.category
        mirrorCount = detail.streams.size

        if (detail.streams.isEmpty()) {
            phase = PlaybackPhase.EXHAUSTED
            failure = FailoverReason.NO_MIRROR
            return listOf(PlaybackCommand.Stop)
        }

        ladder = Ladder(detail.streams)
        return startBatch()
    }

    /**
     * The ladder could not be read, so there is nothing to play.
     *
     * This is never dressed up as an outage of the feed: the app says it could
     * not read the channel, and stops.
     */
    fun fail(reason: FailoverReason) {
        phase = PlaybackPhase.FAILED
        failure = reason
        deadlineAt = null
        stallSince = null
    }

    /**
     * A picture is on screen: that mirror has proved itself.
     *
     * The first mirror of the race to send a picture keeps it, and the others
     * are let go of at once. A picture that arrives from a mirror that has
     * already been given up changes nothing.
     */
    fun onFirstFrame(attempt: Long): List<PlaybackCommand> {
        val candidate = candidates.firstOrNull { it.attempt == attempt } ?: return emptyList()

        if (candidate.proved) {
            // The picture of the mirror that was on air came back.
            if (phase == PlaybackPhase.RECOVERING) {
                phase = PlaybackPhase.ON_AIR
                deadlineAt = null
                stallSince = null
            }
            return emptyList()
        }

        if (phase != PlaybackPhase.TUNING) return emptyList()

        // This mirror won the race. The others are let go of at once: a mirror
        // that lost must not keep a decoder, a buffer or a surface of its own.
        candidate.proved = true
        winner = candidate
        val losers = candidates.filter { it !== candidate }
        candidates.clear()
        candidates.add(candidate)

        // The winner's first-frame deadline is spent. If the picture later
        // dies and the mirror has to prove itself again, it is given the
        // budget of a first frame then, and not the one that has passed.
        candidate.deadlineAt = null

        // A mirror that lost the race was not proved bad, so it keeps its
        // place in the published order for another turn.
        reserve.addAll(0, losers.map { Published(it.link, it.rank) })

        phase = PlaybackPhase.ON_AIR
        deadlineAt = null
        stallSince = null
        lastPosition = null

        val commands = mutableListOf<PlaybackCommand>()
        losers.forEach { commands += PlaybackCommand.Cancel(it.attempt) }
        commands += PlaybackCommand.Select(attempt)
        return commands
    }

    /**
     * The player is waiting for data while a picture was up.
     *
     * The frozen picture stays on the screen: the feed gets the grace window
     * to come back before it is called stalled.
     */
    fun onStall(attempt: Long): List<PlaybackCommand> {
        val candidate = candidates.firstOrNull { it.attempt == attempt } ?: return emptyList()
        if (candidate !== winner) return emptyList()
        if (phase != PlaybackPhase.ON_AIR) return emptyList()
        if (stallSince == null) stallSince = now()
        return emptyList()
    }

    /** The player is running again after a stall. */
    fun onResumed(attempt: Long): List<PlaybackCommand> {
        val candidate = candidates.firstOrNull { it.attempt == attempt } ?: return emptyList()
        if (candidate !== winner) return emptyList()
        if (phase != PlaybackPhase.RECOVERING) return emptyList()
        phase = PlaybackPhase.ON_AIR
        deadlineAt = null
        stallSince = null
        return emptyList()
    }

    /**
     * The player could not play this mirror.
     *
     * A failure of the network is not a dead mirror: the mirror is given
     * another try at the same published address, which is what the web player
     * does when it re-reads a manifest. A failure that is not a network
     * failure means the mirror itself is broken: it leaves the race, and the
     * mirrors still in flight go on.
     */
    fun onError(fault: PlaybackFault, attempt: Long, detail: String = ""): List<PlaybackCommand> {
        val candidate = candidates.firstOrNull { it.attempt == attempt } ?: return emptyList()
        if (phase == PlaybackPhase.IDLE ||
            phase == PlaybackPhase.EXHAUSTED ||
            phase == PlaybackPhase.FAILED
        ) {
            return emptyList()
        }

        if (fault == PlaybackFault.TRANSIENT) {
            return secondChance(candidate)
        }

        // The mirror is broken. It leaves the race; the others go on.
        candidates.remove(candidate)
        failure = FailoverReason.PLAYER_ERROR
        if (detail.isNotBlank()) failureDetail = detail

        val commands = mutableListOf<PlaybackCommand>()
        commands += PlaybackCommand.Cancel(candidate.attempt)
        if (candidates.isEmpty()) {
            commands += advance(failure ?: FailoverReason.PLAYER_ERROR)
        }
        return commands
    }

    /**
     * The clock of the player, as it was last read.
     *
     * A clock that stops moving is a stalled feed, and a clock that moves is
     * the one proof that the picture on the screen is a live one and not the
     * last frame of a dead carrier.
     */
    fun onPosition(positionMs: Long): List<PlaybackCommand> {
        if (winner == null) return emptyList()
        if (phase != PlaybackPhase.ON_AIR && phase != PlaybackPhase.RECOVERING) return emptyList()

        val previous = lastPosition
        lastPosition = positionMs

        if (previous == null || positionMs != previous) {
            if (stallSince != null) {
                stallSince = null
                if (phase == PlaybackPhase.RECOVERING) {
                    phase = PlaybackPhase.ON_AIR
                    deadlineAt = null
                }
            }
            return emptyList()
        }

        if (stallSince == null) stallSince = now()
        return emptyList()
    }

    /**
     * Time has passed: the deadlines are checked.
     *
     * A mirror that has had its full budget and has still not proved a
     * picture leaves the race; when the whole batch is gone the walk moves on
     * to the next mirrors. A picture that has been frozen through the grace
     * window gets the same budget once more on the same mirror, and is only
     * given up after that.
     */
    fun onTick(): List<PlaybackCommand> {
        val moment = now()

        return when (phase) {
            PlaybackPhase.TUNING -> {
                val commands = mutableListOf<PlaybackCommand>()
                val spent = candidates.filter { candidate ->
                    val deadline = candidate.deadlineAt
                    !candidate.proved && deadline != null && moment >= deadline
                }
                spent.forEach { candidate ->
                    commands += PlaybackCommand.Cancel(candidate.attempt)
                    candidates.remove(candidate)
                    failure = FailoverReason.NO_PICTURE
                    failureDetail = detailFor(candidate.kind)
                }
                if (candidates.isEmpty()) {
                    commands += advance(failure ?: FailoverReason.NO_PICTURE)
                }
                commands
            }

            PlaybackPhase.ON_AIR -> {
                val stalled = stallSince
                if (stalled != null && moment - stalled >= stallGraceMs) {
                    phase = PlaybackPhase.RECOVERING
                    deadlineAt = moment + deadlineFor(winner?.kind ?: StreamKind.HLS)
                }
                emptyList()
            }

            PlaybackPhase.RECOVERING -> {
                val deadline = deadlineAt
                if (deadline != null && moment >= deadline) {
                    advance(FailoverReason.FROZEN)
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }
    }

    /** Stop playing and forget the walk. */
    fun stop(): List<PlaybackCommand> {
        currentSession += 1
        val commands = mutableListOf<PlaybackCommand>()
        candidates.forEach { commands += PlaybackCommand.Cancel(it.attempt) }
        resetPlayback()
        phase = PlaybackPhase.IDLE
        commands += PlaybackCommand.Stop
        return commands
    }

    /**
     * Puts the next published mirrors in flight, up to the size of the race.
     *
     * The mirrors are taken from the ladder in the order the site published
     * them. When fewer are left than the race holds, the ones that are left
     * are tried together.
     */
    private fun startBatch(): List<PlaybackCommand> {
        val batch = mutableListOf<Candidate>()
        while (batch.size < raceSize) {
            val published = nextPublished() ?: break
            val kind = StreamKinds.kindOf(published.link.url)
            batch += Candidate(
                link = published.link,
                rank = published.rank,
                attempt = nextAttempt(),
                kind = kind,
                deadlineAt = now() + deadlineFor(kind),
            )
        }

        if (batch.isEmpty()) {
            phase = PlaybackPhase.EXHAUSTED
            deadlineAt = null
            stallSince = null
            lastPosition = null
            return listOf(PlaybackCommand.Stop)
        }

        candidates.clear()
        candidates.addAll(batch)
        winner = null
        phase = PlaybackPhase.TUNING
        return batch.map { PlaybackCommand.Prepare(it.link.url, it.attempt, it.kind) }
    }

    /**
     * The next mirror to try, in the order the site published it.
     *
     * A mirror that lost an earlier race comes first, because it was
     * published before the ones that have not been tried yet.
     */
    private fun nextPublished(): Published? {
        if (reserve.isNotEmpty()) return reserve.removeAt(0)
        val link = ladder.next() ?: return null
        return Published(link, ladder.position)
    }

    /**
     * The same published mirror is worth another try.
     *
     * This is the web player's own behaviour: a network failure is re-read
     * from the same address before that mirror is given up, so a healthy
     * mirror is not retired over a hiccup. The ladder is not re-ranked — the
     * mirror that is tried again is the one the server published in this
     * place, and the walk moves on to the next one only when the second
     * chances are used up.
     */
    private fun secondChance(candidate: Candidate): List<PlaybackCommand> {
        val url = candidate.link.url

        // A mirror that had already proved itself has to prove itself again,
        // and gets the first-frame budget for that.
        val given = candidate.deadlineAt
        val budget = given ?: (now() + deadlineFor(candidate.kind)).also { candidate.deadlineAt = it }

        if (candidate.secondChances < PlaybackRules.TRANSIENT_RETRIES) {
            candidate.secondChances += 1
            // The mirror keeps the budget it was given: the web player does not
            // re-arm the deadline when it re-reads the manifest either.
        } else {
            // The web's recovery window: one more try, and three seconds to
            // show a picture in it — but never past the budget the mirror
            // already had.
            val window = now() + transientRecoveryMs
            if (budget > window) candidate.deadlineAt = window
        }

        // The try that is over is let go of, and the mirror is tried again by
        // a player of its own.
        val commands = mutableListOf<PlaybackCommand>()
        commands += PlaybackCommand.Cancel(candidate.attempt)
        candidate.attempt = nextAttempt()
        candidate.proved = false
        phase = PlaybackPhase.TUNING
        stallSince = null
        lastPosition = null
        commands += PlaybackCommand.Prepare(url, candidate.attempt, candidate.kind)
        return commands
    }

    /**
     * Steps to the next published mirrors, or gives up when there are none.
     *
     * The reason is kept, so the screen can say why the picture changed hands
     * instead of going quiet.
     */
    private fun advance(reason: FailoverReason): List<PlaybackCommand> {
        failure = reason

        val commands = mutableListOf<PlaybackCommand>()
        candidates.forEach { commands += PlaybackCommand.Cancel(it.attempt) }
        candidates.clear()
        winner = null
        deadlineAt = null
        stallSince = null
        lastPosition = null
        phase = PlaybackPhase.TUNING
        commands += startBatch()
        return commands
    }

    /**
     * How long a mirror of this kind has to prove a picture.
     *
     * The web player's own ten seconds for the feeds the app fetches itself,
     * and a longer one for YouTube, whose player has to load a page and its
     * own advertising before anything is on the screen.
     */
    private fun deadlineFor(kind: StreamKind): Long = when (kind) {
        StreamKind.YOUTUBE -> youtubeFirstFrameDeadlineMs
        StreamKind.HLS, StreamKind.MPEG_TS -> firstFrameDeadlineMs
    }

    /**
     * The reason a mirror of this kind gave for never showing a picture.
     *
     * A YouTube player that stays black has said nothing at all, so the kind
     * of player that was waited on is named instead.
     */
    private fun detailFor(kind: StreamKind): String = when (kind) {
        StreamKind.YOUTUBE -> PlaybackDetails.YOUTUBE_NO_PICTURE
        StreamKind.HLS, StreamKind.MPEG_TS -> ""
    }

    /**
     * Tunes an address and marks it as the try being waited on.
     *
     * Every try at playing gets its own number, so an event the player reports
     * later can be matched to the try it belongs to.
     */
    private fun nextAttempt(): Long {
        attempts += 1
        attempt = attempts
        return attempt
    }

    private fun resetPlayback() {
        ladder = Ladder(emptyList())
        mirrorCount = 0
        failure = null
        failureDetail = ""
        deadlineAt = null
        stallSince = null
        lastPosition = null
        candidates.clear()
        reserve.clear()
        winner = null
        // Everything reported from here on belongs to a playback that is over,
        // so the attempt those answers carry is no longer the one waited for.
        attempts += 1
        attempt = attempts
    }
}
