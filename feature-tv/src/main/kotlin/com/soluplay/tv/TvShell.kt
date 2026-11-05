package com.soluplay.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.CatalogueOrigin
import com.soluplay.core.favourites.FavouritesViewModel
import com.soluplay.core.fixtures.FixtureTarget
import com.soluplay.core.fixtures.fixtureTarget
import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.SourceBadge
import com.soluplay.core.ui.UiLanguage
import com.soluplay.core.ui.UiLanguageStore

/** The four places the television can be. */
enum class TvTab { GUIDE, MATCHES, SEARCH, SAVED }

/** Where the language switch sits in the bar. */
private const val CHROME_LANGUAGE = 4

/** Where the refresh sits in the bar. */
private const val CHROME_REFRESH = 5

/** How many things the bar holds. */
const val CHROME_ITEMS = 6

/**
 * The bar at the top of every screen.
 *
 * It holds the four places, the language switch and the refresh, and it is
 * the first row of the focus on every screen — so the remote can always walk
 * back to it and the viewer is never lost.
 *
 * The bar is drawn by the screen itself, so that the focus and the row it
 * belongs to are always the same thing.
 */
class TvChrome(
    val origin: CatalogueOrigin,
    val loading: Boolean,
    val language: UiLanguage,
    val tab: TvTab,
    /** One focus for each thing in the bar. */
    val requesters: List<FocusRequester>,
    val onPickTab: (TvTab) -> Unit,
    val onToggleLanguage: () -> Unit,
    val onRefresh: () -> Unit,
) {

    /** What the Enter key does on the thing that is focused in the bar. */
    fun press(index: Int) {
        when (index) {
            in 0..3 -> onPickTab(TvTab.entries[index])
            CHROME_LANGUAGE -> onToggleLanguage()
            CHROME_REFRESH -> onRefresh()
        }
    }
}

/**
 * The television.
 *
 * Four places sit under one bar: the guide, the match board, the search and
 * the viewer's own list. A channel opens the watch screen, which takes the
 * whole screen — the picture is the screen, and the words sit over it.
 *
 * The guide is the server's and is never re-ranked here. The list of
 * favourites is the viewer's own and lives on the device.
 *
 * While the guide is being browsed, the channel the remote is standing on
 * plays in the corner: the sound carries on, and the viewer sees what the
 * channel is before opening it.
 */
@Composable
fun TvShell(
    viewModel: BrowseViewModel,
    playback: PlaybackViewModel,
    host: Media3PlaybackHost,
    previewHost: Media3PlaybackHost,
    previewPlayback: PlaybackViewModel,
    favourites: FavouritesViewModel,
    language: UiLanguageStore,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val saved by favourites.snapshot.collectAsStateWithLifecycle()
    val spoken by language.language.collectAsStateWithLifecycle()
    val previewState by previewPlayback.state.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(TvTab.GUIDE) }
    var watching by remember { mutableStateOf<Channel?>(null) }
    var sheet by remember { mutableStateOf<Fixture?>(null) }
    var previewChannel by remember { mutableStateOf<Channel?>(null) }

    val marked = remember(saved.items) { saved.items.map { it.channelId }.toSet() }
    val chromeRequesters = remember { List(CHROME_ITEMS) { FocusRequester() } }
    val scope = rememberCoroutineScope()

    // The choice sheet is closed by the back key, the same way it is closed
    // on a phone.
    BackHandler(enabled = sheet != null) { sheet = null }

    val chrome = TvChrome(
        origin = snapshot.origin,
        loading = snapshot.loading,
        language = spoken,
        tab = tab,
        requesters = chromeRequesters,
        onPickTab = { tab = it },
        onToggleLanguage = language::toggle,
        onRefresh = viewModel::refresh,
    )

    /**
     * Where a tap on a match card goes.
     *
     * One channel opens it, several are offered, and a card with nothing
     * openable is said so instead of opening nothing.
     */
    fun openFixture(fixture: Fixture) {
        when (val target = fixtureTarget(fixture)) {
            is FixtureTarget.Open -> watching = Channel(
                id = target.choice.channelId,
                name = target.choice.name,
                logo = target.choice.logoUrl,
                category = target.choice.category,
            )

            else -> sheet = fixture
        }
    }

    // The preview plays only while the guide is being browsed.
    LaunchedEffect(tab) {
        if (tab != TvTab.GUIDE) previewChannel = null
    }

    // The preview is a player of its own, so it is told what to play here.
    LaunchedEffect(previewChannel?.id) {
        val channel = previewChannel
        if (channel == null) {
            previewPlayback.close()
            return@LaunchedEffect
        }
        previewPlayback.open(channel.id, channel.name, channel.logo)
    }

    // The bar keeps the focus when the place on the screen changes.
    LaunchedEffect(tab) {
        chromeRequesters.getOrNull(tab.ordinal)?.askForFocus(scope)
    }

    val onAir = watching
    if (onAir != null) {
        // The watch screen has the picture to itself: the corner preview is
        // let go of, so the two never talk over each other.
        LaunchedEffect(onAir.id) { previewChannel = null }

        TvWatchScreen(
            channel = onAir,
            host = host,
            playback = playback,
            marked = onAir.id in marked,
            onToggleFavourite = { favourites.toggle(onAir) },
            onClose = { watching = null },
        )
        return
    }

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // The choice sheet is modal: the arrows belong to the
                    // sheet until it is closed.
                    if (sheet == null) return@onPreviewKeyEvent false
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    TvTab.GUIDE -> TvGuideScreen(
                        snapshot = snapshot,
                        chrome = chrome,
                        marked = marked,
                        onWatch = { watching = it },
                        onPickFixture = ::openFixture,
                        onPreview = { previewChannel = it },
                    )

                    TvTab.MATCHES -> TvBoardScreen(
                        fixtures = snapshot.fixtures,
                        chrome = chrome,
                        onPickFixture = ::openFixture,
                    )

                    TvTab.SEARCH -> TvSearchScreen(
                        channels = snapshot.channels,
                        chrome = chrome,
                        marked = marked,
                        onOpen = { watching = it },
                    )

                    TvTab.SAVED -> TvFavouritesScreen(
                        snapshot = saved,
                        chrome = chrome,
                        onOpen = { favourite ->
                            watching = Channel(
                                id = favourite.channelId,
                                name = favourite.name,
                                logo = favourite.logo,
                                category = favourite.category,
                            )
                        },
                        onRemove = { favourite ->
                            favourites.toggle(
                                Channel(
                                    id = favourite.channelId,
                                    name = favourite.name,
                                    logo = favourite.logo,
                                    category = favourite.category,
                                ),
                            )
                        },
                        onPull = favourites::pull,
                        onPush = favourites::push,
                        onAdopt = { code -> favourites.adopt(code) },
                    )
                }
            }

            val preview = previewChannel
            if (preview != null && tab == TvTab.GUIDE) {
                TvCornerPreview(
                    host = previewHost,
                    state = previewState,
                    channel = preview,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 48.dp, bottom = 32.dp),
                )
            }

            val open = sheet
            if (open != null) {
                TvChoiceSheet(
                    fixture = open,
                    onPick = { choice ->
                        sheet = null
                        watching = Channel(
                            id = choice.channelId,
                            name = choice.name,
                            logo = choice.logoUrl,
                            category = choice.category,
                        )
                    },
                    onDismiss = { sheet = null },
                )
            }
        }
    }
}

/**
 * The bar at the top of the screen.
 *
 * The four places are pills with their own names, and the language switch and
 * the refresh are beside them. The place the viewer is in is lit, and the
 * one the remote is on has a ring around it.
 */
@Composable
fun TvTopBar(chrome: TvChrome) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SoluPlay",
            color = Accent,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(16.dp))
        SourceBadge(origin = chrome.origin, fontSize = 18.sp)
        Spacer(modifier = Modifier.weight(1f))

        TvTabPill(TvTab.GUIDE, Icons.Filled.Home, Copy.home(), chrome)
        TvTabPill(TvTab.MATCHES, Icons.Filled.PlayArrow, Copy.matchesTab(), chrome)
        TvTabPill(TvTab.SEARCH, Icons.Filled.Search, Copy.searchTab(), chrome)
        TvTabPill(TvTab.SAVED, Icons.Filled.Star, Copy.savedTab(), chrome)

        Spacer(modifier = Modifier.width(14.dp))

        TvPill(
            text = if (chrome.language == UiLanguage.BENGALI) Copy.englishToggle() else Copy.bengaliToggle(),
            requester = chrome.requesters.getOrNull(CHROME_LANGUAGE),
        )

        Spacer(modifier = Modifier.width(10.dp))

        TvPill(
            text = if (chrome.loading) Copy.refreshing() else Copy.refresh(),
            requester = chrome.requesters.getOrNull(CHROME_REFRESH),
        )
    }
}

/** One place in the bar, with the name of it. */
@Composable
private fun TvTabPill(tab: TvTab, icon: ImageVector, label: String, chrome: TvChrome) {
    val chosen = chrome.tab == tab
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(if (chosen) Accent else Panel)
            .then(
                chrome.requesters.getOrNull(tab.ordinal)
                    ?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
            .focusable()
            .onFocusChanged { }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (chosen) Background else Muted,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = if (chosen) Background else Muted,
            fontSize = 18.sp,
        )
    }
}

/**
 * One thing in the bar that is not a place.
 *
 * The ring is the focus: the remote shows where it is, so the viewer is
 * never guessing.
 */
@Composable
private fun TvPill(text: String, requester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }

    Text(
        text = text,
        color = if (focused) Background else Muted,
        fontSize = 18.sp,
        modifier = Modifier
            .then(if (requester == null) Modifier else Modifier.focusRequester(requester))
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Accent else Panel)
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
