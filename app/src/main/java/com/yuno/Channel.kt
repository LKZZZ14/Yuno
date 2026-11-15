package com.yuno

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Opens a channel page from anywhere (player channel row, card avatars). Provided by the shell. */
val LocalChannelOpener = staticCompositionLocalOf<(String) -> Unit> { {} }

/** The channel's id when its address already says it (/channel/UC…), so its page can load alongside NewPipe's details. */
private fun channelIdIn(url: String): String? =
    url.substringAfter("/channel/", "").takeWhile { it != '/' && it != '?' }.takeIf { it.startsWith("UC") && it.length == 24 }

/**
 * A channel page (GitHub #53, Derek picked A "Underline tabs" in docs/design/channel-page.html):
 * the profile, then YouTube's tabs — Home (the creator's shelves), Videos (Latest / Popular /
 * Oldest), Shorts (played in the Shorts pager) and Playlists — each loaded the first time it's shown.
 */
@Stable
class ChannelState(val url: String, private val scope: CoroutineScope) {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    /** The page's Home tab (tabs, featured video, shelves); null until loaded or if it couldn't be read. */
    var home by mutableStateOf<ChannelHome?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var subscribed by mutableStateOf<Boolean?>(null)
        private set
    /** The tab picked; [current] is the one shown (Home first, as on YouTube — Derek). */
    private var tab by mutableStateOf(ChannelTab.Home)
    /** The page's scroll, kept here so that back from one of its shorts the page is where it was. */
    val list = LazyListState()

    val videos = FeedState(scope) { token -> filled(InnerTube.channelVideos(channelId(), token)) }
    val shorts = FeedState(scope) { token -> InnerTube.channelShorts(channelId(), tabParams(ChannelTab.Shorts), token) }
    var playlists by mutableStateOf<List<PlaylistRef>>(emptyList())
        private set
    var playlistsLoading by mutableStateOf(false)
        private set
    var playlistsError by mutableStateOf<String?>(null)
        private set
    private var playlistsNext: String? = null
    private var playlistsLoaded = false
    private var playlistsJob: Job? = null
    val playlistsHaveMore get() = playlistsNext != null
    val playlistsEmpty get() = playlistsLoaded && !playlistsLoading && playlistsError == null && playlists.isEmpty()
    /** The Playlists tab has asked at least once (before that it shows its spinner). */
    val playlistsTried get() = playlistsLoaded

    /** Home's shelves as shown: Shorts shelves only while Shorts are on. */
    val shelves: List<ChannelShelf>
        get() = home?.shelves.orEmpty().filter { ShortsSetting.enabled || it.shorts.isEmpty() }

    /**
     * The tabs this channel has, in YouTube's order: Home only with something on it, Shorts only
     * while Shorts are on. Without the page (it couldn't be read), the uploads alone, as before.
     */
    val tabs: List<ChannelTab>
        get() {
            val h = home ?: return listOf(ChannelTab.Videos)
            val shown = ChannelTab.entries.filter { t ->
                when (t) {
                    ChannelTab.Home -> h.featured != null || shelves.isNotEmpty()
                    ChannelTab.Shorts -> ShortsSetting.enabled && t in h.tabs
                    else -> t in h.tabs
                }
            }
            return shown.ifEmpty { listOf(ChannelTab.Videos) }
        }

    val current: ChannelTab get() = tabs.let { t -> tab.takeIf { it in t } ?: t.first() }

    init { load() }

    private fun channelId() = details?.id ?: error("channel not loaded")
    private fun tabParams(t: ChannelTab) = home?.tabs?.get(t) ?: error("no ${t.title} tab")

    /** The uploads list doesn't repeat the channel on every item; fill it in. */
    private fun fill(v: VideoItem): VideoItem {
        val d = details ?: return v
        return v.copy(channel = v.channel.ifBlank { d.name }, avatar = v.avatar ?: d.avatar, channelUrl = v.channelUrl ?: d.url)
    }

    private fun filled(page: FeedPage) = page.copy(videos = page.videos.map(::fill))

    fun load() {
        loading = true
        error = null
        scope.launch {
            // Signed out, the page loads alongside NewPipe's details when the address has the id. Signed
            // in it waits for the channel's name and picture, which go to Bells with its bell.
            val early = channelIdIn(url)?.takeIf { !Account.signedIn }?.let { id -> async { runCatching { InnerTube.channelHome(id, null, null) } } }
            runCatching { YouTube.channel(url) }
                .onSuccess { d ->
                    val page = early?.await() ?: runCatching { InnerTube.channelHome(d.id, d.name, d.avatar) }
                    page.exceptionOrNull()?.let { if (it is CancellationException) throw it; Log.w("Yuno", "channel: couldn't read ${d.id}'s page; uploads only", it) }
                    // Profile and tabs land together, so the page never shifts as the tabs arrive.
                    details = d
                    home = page.getOrNull()?.let { h ->
                        h.copy(featured = h.featured?.let(::fill), shelves = h.shelves.map { s -> s.copy(videos = s.videos.map(::fill)) })
                    }
                    subscribed = home?.subscribed
                }
                .onFailure { if (it is CancellationException) throw it; error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun show(t: ChannelTab) { tab = t }

    /** What [t] shows, loaded the first time it's shown. */
    fun ensureLoaded(t: ChannelTab) {
        if (details == null) return
        when (t) {
            ChannelTab.Videos -> videos.ensureLoaded()
            ChannelTab.Shorts -> shorts.ensureLoaded()
            ChannelTab.Playlists -> if (!playlistsLoaded) loadPlaylists(more = false)
            ChannelTab.Home -> Unit
        }
    }

    fun loadMore() {
        when (current) {
            ChannelTab.Videos -> videos.loadMore()
            ChannelTab.Shorts -> shorts.loadMore()
            ChannelTab.Playlists -> loadPlaylists(more = true)
            ChannelTab.Home -> Unit
        }
    }

    fun loadPlaylists(more: Boolean) {
        val token = if (more) playlistsNext ?: return else null
        if (playlistsLoading) return
        playlistsLoaded = true
        playlistsLoading = true
        playlistsError = null
        playlistsJob?.cancel()
        playlistsJob = scope.launch {
            runCatching { InnerTube.channelPlaylists(channelId(), tabParams(ChannelTab.Playlists), token) }
                .onSuccess { p ->
                    playlists = if (token == null) p.playlists else (playlists + p.playlists).distinctBy { it.id }
                    playlistsNext = p.next
                }
                .onFailure { if (it is CancellationException) throw it; playlistsError = YouTube.friendlyError(it) }
            playlistsLoading = false
        }
    }

    /** The Shorts tab's shorts for the pager, from [start]; it pages on through the channel by itself. */
    fun shortsFrom(start: Int): ShortsList {
        val id = channelId()
        val params = tabParams(ChannelTab.Shorts)
        return ShortsList(shorts.videos, start, shorts.nextToken) { token -> InnerTube.channelShorts(id, params, token) }
    }

    fun toggleSubscribe(onError: (String) -> Unit) {
        val id = details?.id ?: return
        val before = subscribed ?: return
        subscribed = !before
        scope.launch {
            runCatching { InnerTube.setSubscribed(id, !before) }
                .onSuccess { if (before) Bells.forget(id) }
                .onFailure { subscribed = before; onError(str(R.string.ui_subscription_error)) }
        }
    }
}

private const val ProfileKey = "header"
private const val TabsKey = "tabs"

@Composable
fun ChannelScreen(
    state: ChannelState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenShorts: (ShortsList) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onMessage: (String) -> Unit,
    /** The colour the shell's bottom fade should settle into. Null once this page is gone. */
    onFootColor: (Color?) -> Unit = {},
) {
    val d = state.details
    // The page is this colour from top to bottom, taken from the profile picture.
    val palette = rememberArtworkPalette(d?.avatar ?: d?.banner)
    SideEffect { onFootColor(palette.wash) }
    DisposableEffect(Unit) { onDispose { onFootColor(null) } }

    val scheme = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = scheme, typography = BitChordTypography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge.merge(OwnDirectionText)) {
            ChannelPage(state, contentPadding, palette, onOpen, onOpenShorts, onOpenPlaylist, onMessage)
        }
    }
}

@Composable
private fun ChannelPage(
    state: ChannelState,
    contentPadding: PaddingValues,
    palette: ArtworkPalette,
    onOpen: (VideoItem) -> Unit,
    onOpenShorts: (ShortsList) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onMessage: (String) -> Unit,
) {
    val columns = gridColumns()
    val list = state.list
    val barHeight = topBarHeight()
    val tabs = state.tabs
    val current = state.current
    val hasTabs = tabs.size > 1
    LaunchedEffect(state.details, current) { state.ensureLoaded(current) }
    val shownCount = when (current) {
        ChannelTab.Videos -> state.videos.videos.size
        ChannelTab.Shorts -> state.shorts.videos.size
        ChannelTab.Playlists -> state.playlists.size
        ChannelTab.Home -> 0
    }
    val nearEnd by remember {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, current, shownCount) { if (nearEnd && shownCount > 0) state.loadMore() }

    fun pick(t: ChannelTab) {
        if (t == current) return
        state.show(t)
    }
    val tabLabels = tabs.map { tabLabel(it) }
    val page = palette.wash

    Box(Modifier.fillMaxSize().background(page)) {
        val d = state.details
        when {
            d == null && state.error != null -> Box(Modifier.padding(top = barHeight)) {
                EmptyState(
                    Icons.Rounded.CloudOff, stringResource(R.string.ui_channel_error_title), state.error.orEmpty(),
                    action = { PillButton(stringResource(R.string.ui_try_again), state::load) },
                )
            }
            else -> LazyColumn(state = list, contentPadding = contentPadding.withTop(barHeight)) {
                if (d == null) {
                    item { ChannelSkeletons() }
                    items(3) { FeedSkeletons(1) }
                    return@LazyColumn
                }
                item(key = ProfileKey) { ChannelName(state, d, palette, onMessage) }
                item(key = "about") { ChannelAbout(d, palette) }
                if (hasTabs) item(key = TabsKey) {
                    UnderlineTabs(
                        tabLabels, tabs.indexOf(current), { pick(tabs[it]) }, TabsRowModifier,
                        selectedColor = palette.onBackgroundVariant, idleColor = palette.onBackgroundVariant,
                    )
                }
                when (current) {
                    ChannelTab.Home -> homeTab(state, columns, palette, onOpen, onOpenShorts, onOpenPlaylist, onLink = { link ->
                        when (link) {
                            is ShelfLink.Tab -> pick(link.tab)
                            is ShelfLink.Playlist -> onOpenPlaylist(link.ref)
                        }
                    })
                    ChannelTab.Videos -> {
                        val feed = state.videos
                        if (feed.chips.size > 1) item(key = "sorts") {
                            SortChips(feed.chips, feed.chip, palette) { feed.selectChip(it) }
                        }
                        videoCards(feed.videos, columns, onOpen, metaColor = palette.onBackgroundVariant)
                        feedTail(feed.loading || feed.hasMore || feed.waiting, feed.error.takeIf { feed.videos.isEmpty() }, feed::refresh,
                            empty = if (feed.loadedEmpty) R.string.ui_channel_no_videos else null, alone = feed.videos.isEmpty())
                    }
                    ChannelTab.Shorts -> {
                        val feed = state.shorts
                        val perRow = (columns * 2).coerceAtMost(6)
                        val rows = feed.videos.withIndex().chunked(perRow)
                        items(rows, key = { "shorts-" + it.first().value.id }) { row ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { (i, s) -> ShortTile(s, Modifier.weight(1f)) { onOpenShorts(state.shortsFrom(i)) } }
                                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        feedTail(feed.loading || feed.hasMore || feed.waiting, feed.error.takeIf { feed.videos.isEmpty() }, feed::refresh,
                            empty = if (feed.loadedEmpty) R.string.ui_channel_no_shorts else null, alone = feed.videos.isEmpty())
                    }
                    ChannelTab.Playlists -> {
                        items(state.playlists, key = { "playlist-" + it.id }) { p -> PlaylistRow(p, palette) { onOpenPlaylist(p) } }
                        feedTail(state.playlistsLoading || state.playlistsHaveMore || !state.playlistsTried, state.playlistsError.takeIf { state.playlists.isEmpty() },
                            { state.loadPlaylists(more = false) }, empty = if (state.playlistsEmpty) R.string.ui_channel_no_playlists else null,
                            alone = state.playlists.isEmpty())
                    }
                }
            }
        }
        ChannelTopFade(list, page, Modifier.align(Alignment.TopCenter))
    }
}

/** The artist page's side circles, and the gap between the pill and the bell. */
private val ArtistSideButton = 52.dp
private val ArtistActionGap = 12.dp
private const val LightFillAlpha = 0.07f
private const val CircleEdgeAlpha = 0.10f

/** The old wide banner was 5.6. This is a bit taller than the last step, with a tighter corner. */
private const val BannerAspect = 5.6f / 1.55f
private const val BannerRadius = 14
private val ProfileSize = 72

/**
 * Banner, then the profile picture beside the name, @handle and subscriber count
 * (those three share the picture's height, spaced evenly), then a full-width Subscribe pill.
 */
@Composable
private fun ChannelName(state: ChannelState, d: ChannelDetails, palette: ArtworkPalette, onMessage: (String) -> Unit) {
    val handle = state.home?.handle
    Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter)) {
        d.banner?.let {
            Thumbnail(it, Modifier.fillMaxWidth().aspectRatio(BannerAspect), radius = BannerRadius)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(d.avatar, d.name, size = ProfileSize)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).height(ProfileSize.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(
                    d.name,
                    style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 24.sp),
                    color = palette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!handle.isNullOrBlank()) {
                    Text(
                        handle,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 16.sp),
                        color = palette.onBackgroundVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (d.subscribers > 0) {
                    Text(
                        subscribersLine(d.subscribers),
                        style = MaterialTheme.typography.labelMedium.copy(lineHeight = 14.sp),
                        color = palette.onBackgroundVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        state.subscribed?.let { subscribed ->
            Spacer(Modifier.height(12.dp))
            SubscribeRow(state, d, subscribed, onMessage)
        }
    }
}

/** Subscribe fills the width. The bell sits at the end once subscribed. */
@Composable
private fun SubscribeRow(state: ChannelState, d: ChannelDetails, subscribed: Boolean, onMessage: (String) -> Unit) {
    val toggleBell = rememberBellToggle(onMessage)
    val channel = BellChannel(d.id, d.name, d.avatar)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ArtistActionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubscribeWord(subscribed, Modifier.weight(1f)) { state.toggleSubscribe(onMessage) }
        if (subscribed) {
            val bell = Bells.isOn(d.id)
            ArtistCircle(
                icon = if (bell) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                contentDescription = stringResource(if (bell) R.string.app_bell_on_description else R.string.app_bell_off_description),
                onClick = { toggleBell(channel) },
            )
        }
    }
}

/** The page colour over whatever scrolls under the status bar. Off until the list moves. */
@Composable
private fun ChannelTopFade(list: LazyListState, page: Color, modifier: Modifier = Modifier) {
    val brush = remember(page) {
        Brush.verticalGradient(
            colorStops = Array(16) { i ->
                val t = i / 15f
                t to page.copy(alpha = androidx.compose.animation.core.EaseInCubic.transform(1f - t))
            },
        )
    }
    val alpha by remember(list) {
        derivedStateOf {
            if (list.firstVisibleItemIndex > 0) return@derivedStateOf 1f
            val header = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat() ?: return@derivedStateOf 0f
            if (header <= 0f) return@derivedStateOf 0f
            (list.firstVisibleItemScrollOffset / header).coerceIn(0f, 1f)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer { this.alpha = alpha }
            .drawBehind { drawRect(brush) },
    )
}

/** The bio, full width and left aligned under the Subscribe pill. */
@Composable
private fun ChannelAbout(d: ChannelDetails, palette: ArtworkPalette) {
    if (d.description.isBlank()) return
    Text(
        d.description,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = palette.onBackgroundVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 12.dp),
    )
}

/** "Subscribe" / "Subscribed", in the artist page's glass pill. Words only. */
@Composable
private fun SubscribeWord(subscribed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(ArtistSideButton)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = LightFillAlpha), CircleShape)
            .liquidGlassRim(CircleShape)
            .border(1.dp, Color.White.copy(alpha = CircleEdgeAlpha), CircleShape)
            .pressable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (subscribed) R.string.app_subscribed else R.string.app_subscribe),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Small circular icon button — the artist page's side circles.
 * A light veil, the glass rim, and a hairline edge. From BitChord's CircleIconButton.
 */
@Composable
private fun ArtistCircle(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(ArtistSideButton)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = LightFillAlpha), CircleShape)
            .liquidGlassRim(CircleShape)
            .border(1.dp, Color.White.copy(alpha = CircleEdgeAlpha), CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(ArtistSideButton * 0.44f),
        )
    }
}

/** The tabs row's spacing: a little air above, the bar's room below. */
private val TabsRowModifier = Modifier.padding(top = 10.dp, bottom = 6.dp)

@Composable
private fun tabLabel(t: ChannelTab) = when (t) {
    ChannelTab.Home -> stringResource(R.string.app_tab_home)
    ChannelTab.Videos -> stringResource(R.string.ui_videos)
    ChannelTab.Shorts -> stringResource(R.string.app_tab_shorts)
    ChannelTab.Playlists -> stringResource(R.string.ui_playlists)
}

/** Home: the featured video, then the creator's shelves, each a sideways row with See all. */
private fun LazyListScope.homeTab(
    state: ChannelState,
    columns: Int,
    palette: ArtworkPalette,
    onOpen: (VideoItem) -> Unit,
    onOpenShorts: (ShortsList) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onLink: (ShelfLink) -> Unit,
) {
    state.home?.featured?.let { videoCards(listOf(it), columns, onOpen, metaColor = palette.onBackgroundVariant) }
    state.shelves.forEachIndexed { i, shelf ->
        item(key = "shelf-$i") { ShelfRow(shelf, palette, onOpen, onOpenShorts, onOpenPlaylist, onLink) }
    }
}

/**
 * The list's end for a tab: a spinner while there's more, the error with Try again when nothing
 * loaded, or the tab's own empty note ([empty]). No keys (rule 8: a keyed trailing loader pins the list).
 * While the tab has nothing yet ([alone]) it takes the page's height, so switching to an empty
 * tab does not pull the list back up to the profile.
 */
private fun LazyListScope.feedTail(more: Boolean, error: String?, retry: () -> Unit, empty: Int?, alone: Boolean) {
    when {
        error != null -> item {
            Box(if (alone) Modifier.fillParentMaxHeight() else Modifier) { EmptyState(
                icon = Icons.Rounded.CloudOff,
                title = stringResource(R.string.ui_feed_error_title),
                body = error,
                action = { PillButton(stringResource(R.string.ui_try_again), retry) },
            ) }
        }
        empty != null -> item {
            Box(if (alone) Modifier.fillParentMaxHeight() else Modifier) {
                EmptyState(icon = Icons.Rounded.VideoLibrary, title = stringResource(R.string.ui_playlist_empty_title), body = stringResource(empty))
            }
        }
        more -> item { if (alone) FeedSkeletons() else FeedSkeletons(1) }
    }
}

/**
 * The Videos tab's filters (Latest, Popular, Oldest, and whatever else YouTube sends):
 * chips in a row that scrolls. The chosen one's fill is the artist-line secondary.
 */
@Composable
private fun SortChips(chips: List<FeedChip>, chosen: FeedChip?, palette: ArtworkPalette, onPick: (FeedChip) -> Unit) {
    val onInk = if (palette.onBackgroundVariant.luminance() > 0.5f) Color.Black else Color.White
    val reduced = rememberReducedMotion()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 11.dp, end = 11.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip ->
            val on = chosen?.title == chip.title || (chosen == null && chip.token == null)
            val bg by animateColorAsState(if (on) palette.onBackgroundVariant else palette.elevated, tween(if (reduced) 0 else 220), label = "sortBg")
            val fg by animateColorAsState(if (on) onInk else palette.onBackgroundVariant, tween(if (reduced) 0 else 220), label = "sortFg")
            val corner by animateDpAsState(if (on) 18.dp else 10.dp, if (reduced) tween(0) else spring(dampingRatio = 0.55f, stiffness = 500f), label = "sortCorner")
            Box(
                Modifier
                    .pressable(onClick = { if (!on) onPick(chip) })
                    .height(36.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(sortLabel(chip.title), color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** YouTube's English sort names in the app's language (others as sent). */
@Composable
private fun sortLabel(english: String) = when (english.lowercase()) {
    "latest" -> stringResource(R.string.ui_channel_sort_latest)
    "popular" -> stringResource(R.string.ui_channel_sort_popular)
    "oldest" -> stringResource(R.string.ui_channel_sort_oldest)
    else -> english
}

/** YouTube's own shelf names in the app's language; a creator's own titles stay as written. */
@Composable
private fun shelfTitle(english: String) = when (english.lowercase()) {
    "videos" -> stringResource(R.string.ui_videos)
    "shorts" -> stringResource(R.string.app_tab_shorts)
    "popular videos" -> stringResource(R.string.ui_channel_popular_videos)
    "featured channels" -> stringResource(R.string.ui_channel_featured_channels)
    "playlists", "created playlists" -> stringResource(R.string.ui_playlists)
    else -> english
}

@Composable
private fun ShelfRow(
    shelf: ChannelShelf,
    palette: ArtworkPalette,
    onOpen: (VideoItem) -> Unit,
    onOpenShorts: (ShortsList) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onLink: (ShelfLink) -> Unit,
) {
    val type = MaterialTheme.typography
    Column(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = PageGutter, end = PageGutter, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                shelfTitle(shelf.title), Modifier.weight(1f),
                style = type.headlineMedium, color = palette.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            shelf.link?.let { link ->
                Row(Modifier.pressable { onLink(link) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_see_all), style = type.titleSmall, color = palette.onBackgroundVariant)
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.onBackgroundVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                shelf.videos.isNotEmpty() -> items(shelf.videos, key = { it.id }) { v -> ShelfVideo(v, palette) { onOpen(v) } }
                shelf.shorts.isNotEmpty() -> itemsIndexed(shelf.shorts, key = { _, s -> s.id }) { i, s ->
                    ShortTile(s, Modifier.width(118.dp)) { onOpenShorts(ShortsList(shelf.shorts, i)) }
                }
                shelf.playlists.isNotEmpty() -> items(shelf.playlists, key = { it.id }) { p -> ShelfPlaylist(p, palette) { onOpenPlaylist(p) } }
                else -> items(shelf.channels, key = { it.id }) { ch -> ShelfChannel(ch, palette) }
            }
        }
    }
}

/** A video in a shelf: a 200 dp card, thumbnail over two lines of title and views · age. */
@Composable
private fun ShelfVideo(video: VideoItem, palette: ArtworkPalette, onClick: () -> Unit) {
    val type = MaterialTheme.typography
    val menu = LocalVideoMenu.current
    Column(
        Modifier
            .width(ShelfCardWidth)
            .prefetchOnPress(video.id)
            .pressable(pressedScale = 0.97f, onLongClick = { menu(video) }, onClick = onClick),
    ) {
        Box {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 16)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(7.dp), small = true)
            ResumeBar(video.id, Modifier.align(Alignment.BottomStart))
        }
        Text(
            video.title, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            style = type.titleMedium, color = palette.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        val meta = metaLine("", video.views, video.uploaded)
        if (meta.isNotEmpty()) Text(
            meta, Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = type.bodyMedium, color = palette.onBackgroundVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A short: its 9:16 frame with the title and views on a dark fade at the bottom. */
@Composable
private fun ShortTile(short: VideoItem, modifier: Modifier, onClick: () -> Unit) {
    val shade = remember { Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f)) }
    Box(modifier.aspectRatio(9f / 16f).pressable(pressedScale = 0.97f, onClick = onClick).clip(RoundedCornerShape(18.dp))) {
        Thumbnail(short.thumbnail, Modifier.fillMaxSize(), radius = 18)
        Box(Modifier.fillMaxSize().drawBehind { drawRect(shade) })
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(short.title, color = Color.White, fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (short.views >= 0) Text(viewsLine(short.views), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** How many videos a playlist holds, from YouTube's English "12 videos" (just the number, in Latin digits). */
private fun playlistCount(p: PlaylistRef): String? = p.subtitle?.let { Regex("\\d[\\d,]*").find(it)?.value?.replace(",", "") }

/** The playlist mark and its count, over the thumbnail's corner. */
@Composable
private fun PlaylistBadge(p: PlaylistRef, modifier: Modifier) {
    Row(
        modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.72f)).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        playlistCount(p)?.let {
            Spacer(Modifier.width(3.dp))
            Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShelfPlaylist(p: PlaylistRef, palette: ArtworkPalette, onClick: () -> Unit) {
    Column(Modifier.width(ShelfCardWidth).pressable(pressedScale = 0.97f, onClick = onClick)) {
        Box {
            Thumbnail(p.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 16)
            PlaylistBadge(p, Modifier.align(Alignment.BottomEnd).padding(7.dp))
        }
        Text(
            p.title, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            style = MaterialTheme.typography.titleMedium, color = palette.onBackground,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The Playlists tab's row: thumbnail with the count, the playlist's name beside it. */
@Composable
private fun PlaylistRow(p: PlaylistRef, palette: ArtworkPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.97f, onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(150.dp)) {
            Thumbnail(p.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 16)
            PlaylistBadge(p, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            p.title, Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium, color = palette.onBackground,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A channel the creator features: its picture and name; a tap opens its page. */
@Composable
private fun ShelfChannel(ch: ChannelRef, palette: ArtworkPalette) {
    val open = LocalChannelOpener.current
    Column(
        Modifier.width(76.dp).pressable { open("https://www.youtube.com/channel/${ch.id}") },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelAvatar(ch.avatar, ch.name, size = 64)
        Spacer(Modifier.height(6.dp))
        Text(
            ch.name,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onBackgroundVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ShelfCardWidth = 200.dp