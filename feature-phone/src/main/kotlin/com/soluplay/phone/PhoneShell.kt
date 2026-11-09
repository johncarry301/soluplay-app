package com.soluplay.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.app.R
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.CatalogueOrigin
import com.soluplay.core.browse.allChannelsInOrder
import com.soluplay.core.favourites.FavouritesViewModel
import com.soluplay.core.fixtures.FixtureOption
import com.soluplay.core.fixtures.FixtureTarget
import com.soluplay.core.fixtures.fixtureTarget
import com.soluplay.core.fixtures.playableChoices
import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.HeaderBackground
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.OutageBanner
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.PanelMid
import com.soluplay.core.ui.Slate400
import com.soluplay.core.ui.Slate800
import com.soluplay.core.ui.SourceBadge
import com.soluplay.core.ui.UiLanguage
import com.soluplay.core.ui.UiLanguageStore
import com.soluplay.core.ui.outageMessage

/** The four places the phone can be. */
private enum class PhoneTab { HOME, MATCHES, SEARCH, SAVED }

/**
 * The handset app.
 *
 * Four places sit under one top bar: the guide, the match board, the search
 * and the viewer's own list. A channel opens the watch screen, which takes the
 * whole screen — the picture is the screen, and the words sit over it.
 *
 * The guide is the server's and is never re-ranked here. The list of
 * favourites is the viewer's own and lives on the device.
 */
@Composable
fun PhoneShell(
    viewModel: BrowseViewModel,
    playback: PlaybackViewModel,
    host: Media3PlaybackHost,
    favourites: FavouritesViewModel,
    language: UiLanguageStore,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val saved by favourites.snapshot.collectAsStateWithLifecycle()
    val spoken by language.language.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(PhoneTab.HOME) }
    var watching by remember { mutableStateOf<Channel?>(null) }
    // The match's own broadcast address, when the card being watched was opened
    // through it; null means the picture is the channel's own ladder.
    var matchLink by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<Fixture?>(null) }

    val marked = remember(saved.items) { saved.items.map { it.channelId }.toSet() }
    val channels = remember(snapshot.channels) { allChannelsInOrder(snapshot.channels) }

    /** Opens a catalogue channel, letting go of any match link that was playing. */
    fun watch(channel: Channel) {
        matchLink = null
        watching = channel
    }

    /**
     * Opens the thing the viewer picked on a card.
     *
     * A channel is opened by its id, as before. The match link is played on the
     * card's first openable channel as its identity, so the screen still names
     * the channel carrying the match; a card whose channels are all gone uses
     * the match itself as that identity.
     */
    fun play(fixture: Fixture, option: FixtureOption) {
        when (option) {
            is FixtureOption.Channel -> watch(
                Channel(
                    id = option.choice.channelId,
                    name = option.choice.name,
                    logo = option.choice.logoUrl,
                    category = option.choice.category,
                )
            )

            is FixtureOption.MatchLink -> {
                val identity = playableChoices(fixture).firstOrNull()
                watching = if (identity != null) {
                    Channel(
                        id = identity.channelId,
                        name = identity.name,
                        logo = identity.logoUrl,
                        category = identity.category,
                    )
                } else {
                    Channel(
                        id = "fixture:${fixture.id}",
                        name = fixture.title.ifBlank { "${fixture.teamA} vs ${fixture.teamB}" },
                        category = fixture.sport,
                    )
                }
                matchLink = option.url
            }
        }
    }

    /**
     * Where a tap on a match card goes.
     *
     * One option opens it, several are offered, and a card with nothing
     * openable is said so instead of opening nothing.
     */
    fun openFixture(fixture: Fixture) {
        when (val target = fixtureTarget(fixture)) {
            is FixtureTarget.Open -> play(fixture, target.option)
            else -> sheet = fixture
        }
    }

    val onAir = watching
    if (onAir != null) {
        PhoneWatchScreen(
            channel = onAir,
            channels = channels,
            directUrl = matchLink,
            host = host,
            playback = playback,
            marked = onAir.id in marked,
            onToggleFavourite = { favourites.toggle(onAir) },
            onOpenChannel = { watch(it) },
            onClose = {
                watching = null
                matchLink = null
            },
        )
        return
    }

    // The choice sheet is modal: the back button puts it away first.
    BackHandler(enabled = sheet != null) { sheet = null }

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                PhoneTopBar(
                    origin = snapshot.origin,
                    loading = snapshot.loading,
                    language = spoken,
                    onRefresh = viewModel::refresh,
                    onToggleLanguage = language::toggle,
                )

                if (snapshot.outage) {
                    OutageBanner(
                        message = outageMessage(snapshot),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (tab) {
                        PhoneTab.HOME -> PhoneHomeScreen(
                            snapshot = snapshot,
                            onOpen = { watch(it) },
                            onPickFixture = ::openFixture,
                        )

                        PhoneTab.MATCHES -> PhoneBoardScreen(
                            fixtures = snapshot.fixtures,
                            onPickFixture = ::openFixture,
                        )

                        PhoneTab.SEARCH -> PhoneSearchScreen(
                            channels = snapshot.channels,
                            marked = marked,
                            onToggleFavourite = favourites::toggle,
                            onOpen = { watch(it) },
                        )

                        PhoneTab.SAVED -> PhoneFavouritesScreen(
                            snapshot = saved,
                            onRemove = { favourite ->
                                val channel = Channel(
                                    id = favourite.channelId,
                                    name = favourite.name,
                                    logo = favourite.logo,
                                    category = favourite.category,
                                )
                                favourites.toggle(channel)
                            },
                            onOpen = { favourite ->
                                watching = Channel(
                                    id = favourite.channelId,
                                    name = favourite.name,
                                    logo = favourite.logo,
                                    category = favourite.category,
                                )
                            },
                        )
                    }
                }

                PhoneBottomBar(tab = tab, onPick = { tab = it })
            }

            val open = sheet
            if (open != null) {
                PhoneChoiceSheet(
                    fixture = open,
                    onPick = { option ->
                        sheet = null
                        play(open, option)
                    },
                    onDismiss = { sheet = null },
                )
            }
        }
    }
}

/**
 * The bar at the top: who the app is, which copy of the guide is on the
 * screen, and the two things the viewer can ask for.
 *
 * It is the site's own bar: the same dark band, the same hairline under it,
 * and the same small pills on the right.
 */
@Composable
private fun PhoneTopBar(
    origin: CatalogueOrigin,
    loading: Boolean,
    language: UiLanguage,
    onRefresh: () -> Unit,
    onToggleLanguage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.soluplay_logo),
                contentDescription = "SoluPlay",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(28.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            SourceBadge(origin = origin, fontSize = 11.sp)
            Spacer(modifier = Modifier.weight(1f))
            ChromePill(
                text = if (language == UiLanguage.BENGALI) {
                    Copy.englishToggle()
                } else {
                    Copy.bengaliToggle()
                },
                onClick = onToggleLanguage,
            )
            Spacer(modifier = Modifier.width(8.dp))
            ChromePill(
                text = if (loading) Copy.refreshing() else Copy.refresh(),
                enabled = !loading,
                onClick = onRefresh,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Slate800.copy(alpha = 0.8f)),
        )
    }
}

/** One of the small pills in the top bar. */
@Composable
private fun ChromePill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = text,
        color = Slate400,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(PanelMid)
            .border(1.dp, Slate800, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * The four places, along the bottom.
 *
 * The bar is always there, so the viewer is never lost: the place they are in
 * is the one that is lit.
 */
@Composable
private fun PhoneBottomBar(tab: PhoneTab, onPick: (PhoneTab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Slate800.copy(alpha = 0.8f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomItem(PhoneTab.HOME, Icons.Filled.Home, Copy.home(), tab, onPick)
            BottomItem(PhoneTab.MATCHES, Icons.Filled.PlayArrow, Copy.matchesTab(), tab, onPick)
            BottomItem(PhoneTab.SEARCH, Icons.Filled.Search, Copy.searchTab(), tab, onPick)
            BottomItem(PhoneTab.SAVED, Icons.Filled.Star, Copy.savedTab(), tab, onPick)
        }
    }
}

@Composable
private fun BottomItem(
    item: PhoneTab,
    icon: ImageVector,
    label: String,
    current: PhoneTab,
    onPick: (PhoneTab) -> Unit,
) {
    val selected = item == current
    val tint = if (selected) Accent else Muted

    Column(
        modifier = Modifier
            .clickable { onPick(item) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
