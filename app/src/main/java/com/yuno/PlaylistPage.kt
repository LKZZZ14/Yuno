package com.yuno

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuno.icons.SfIcons

/** Shuffle beside the release page's Play pill. From BitChord's [RELEASE_SIDE_BUTTON]. */
private val ReleaseSideButton = 50.dp
private val ReleaseActionGap = 14.dp

/** The cover's corners, the same radius as a video thumbnail. */
private val CoverShape = RoundedCornerShape(FeedVideoThumbRadius.dp)

/** The inset the header text and the action pills share. */
private val HeaderGutter = PageGutter + 8.dp

private const val LightFillAlpha = 0.07f
private const val CircleEdgeAlpha = 0.10f

/**
 * A playlist, laid out as BitChord's release page: a 16:9 cover the width of the page
 * inside the side padding, on the artwork's own colour, then the title, credit and
 * count, then Shuffle and Play. The rows under that are this app's videos, list or grid.
 */
@Composable
fun PlaylistScreen(
    playlist: PlaylistRef,
    feed: FeedState,
    contentPadding: PaddingValues,
    grid: Boolean,
    onOpen: (List<VideoItem>, Int) -> Unit,
    onShuffle: (List<VideoItem>) -> Unit,
    channelAvatarSeed: Map<String, String?> = emptyMap(),
    onFootColor: (Color?) -> Unit = {},
) {
    LaunchedEffect(playlist.id) { feed.ensureLoaded() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(feed.videos, channelAvatarSeed) {
        ChannelAvatars.resolve(scope, feed.videos, channelAvatarSeed)
    }
    val art = playlist.thumbnail ?: feed.videos.firstOrNull()?.thumbnail
    val palette = rememberArtworkPalette(art)
    DisposableEffect(Unit) {
        onDispose { onFootColor(null) }
    }
    androidx.compose.runtime.SideEffect { onFootColor(palette.background) }
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = scheme, typography = BitChordTypography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge.merge(OwnDirectionText)) {
            PlaylistBody(playlist, feed, contentPadding, grid, palette, onOpen, onShuffle)
        }
    }
}

@Composable
private fun PlaylistBody(
    playlist: PlaylistRef,
    feed: FeedState,
    contentPadding: PaddingValues,
    grid: Boolean,
    palette: ArtworkPalette,
    onOpen: (List<VideoItem>, Int) -> Unit,
    onShuffle: (List<VideoItem>) -> Unit,
) {
    val videos = feed.videos
    val listState = rememberLazyListState()
    val columns = gridColumns()
    val credit = videos.firstOrNull { it.channel.isNotBlank() }?.channel.orEmpty()
    val channelUrl = videos.firstOrNull { it.channel == credit && it.channelUrl != null }?.channelUrl
    Box(Modifier.fillMaxSize().background(palette.background)) {
        PullToRefresh(
            enabled = true,
            refreshing = feed.refreshing,
            onRefresh = feed::refresh,
            topPadding = topBarHeight(),
        ) {
            LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                ) {
                    item(key = "header") {
                        ReleaseHeader(
                            title = playlist.title,
                            art = playlist.thumbnail,
                            credit = credit,
                            channelUrl = channelUrl,
                            meta = playlistMeta(playlist.subtitle, credit, videos.size, feed.hasMore),
                            palette = palette,
                            showActions = videos.isNotEmpty(),
                            onPlay = { onOpen(videos, 0) },
                            onShuffle = { onShuffle(videos) },
                        )
                    }
                    when {
                        feed.error != null && videos.isEmpty() -> item {
                            EmptyState(
                                icon = Icons.Rounded.CloudOff,
                                title = stringResource(R.string.ui_feed_error_title),
                                body = feed.error.orEmpty(),
                                action = { PillButton(stringResource(R.string.ui_try_again), feed::refresh) },
                            )
                        }
                        videos.isEmpty() && (feed.loading || feed.waiting) -> item {
                            ChannelTabSkeleton(if (grid) ChannelBone.VideoCard else ChannelBone.VideoRow, page = true)
                        }
                        videos.isEmpty() -> item {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.ui_playlist_empty_body),
                                    color = palette.onBackgroundVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        else -> {
                            playlistVideos(videos, columns, grid, palette) { index -> onOpen(videos, index) }
                            if (feed.loading || feed.hasMore) item {
                                ChannelTabSkeleton(if (grid) ChannelBone.VideoCard else ChannelBone.VideoRow, page = false)
                            }
                        }
                    }
                }
        }
        PlaylistLoadMore(listState, videos.isNotEmpty()) { feed.loadMore() }
    }
}

/** The videos under the sleeve. List is the channel row; grid is the home card. */
private fun androidx.compose.foundation.lazy.LazyListScope.playlistVideos(
    videos: List<VideoItem>,
    columns: Int,
    grid: Boolean,
    palette: ArtworkPalette,
    onOpen: (Int) -> Unit,
) {
    if (!grid) {
        items(videos.size, key = { videos[it].id }) { index ->
            ChannelVideoRow(videos[index], palette) { onOpen(index) }
        }
        return
    }
    if (columns <= 1) {
        items(videos.size, key = { videos[it].id }) { index ->
            ChannelFeedVideo(videos[index], palette) { onOpen(index) }
        }
        return
    }
    val rows = videos.chunked(columns)
    items(rows.size, key = { rows[it].first().id }) { index ->
        val row = rows[index]
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
            row.forEach { video ->
                val at = videos.indexOf(video)
                Box(Modifier.weight(1f)) { ChannelFeedVideo(video, palette) { onOpen(at) } }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PlaylistLoadMore(list: LazyListState, ready: Boolean, onMore: () -> Unit) {
    val nearEnd by remember(list) {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, ready) { if (nearEnd && ready) onMore() }
}

@Composable
private fun playlistMeta(subtitle: String?, credit: String, count: Int, more: Boolean): String {
    val given = subtitle?.takeIf { it.isNotBlank() && it != credit }
    if (given != null) return given
    if (count > 0 && !more) return pluralStringResource(R.plurals.ui_playlist_videos, count, count)
    return ""
}

/**
 * BitChord's release header: a 16:9 cover the width of the page, inside the side
 * padding, then the title, credit and meta, then Shuffle and a labelled Play pill.
 */
@Composable
private fun ReleaseHeader(
    title: String,
    art: String?,
    credit: String,
    channelUrl: String?,
    meta: String,
    palette: ArtworkPalette,
    showActions: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val openChannel = LocalChannelOpener.current
    Column(
        Modifier.fillMaxWidth().padding(top = topBarHeight(), bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(horizontal = PageGutter)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CoverShape)
                .background(palette.elevated)
                .thumbnailHairline(CoverShape),
        ) {
            if (art == null) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Icon(SfIcons.PlaylistPlay, contentDescription = null, tint = palette.onBackgroundVariant, modifier = Modifier.size(48.dp))
                }
            } else {
                Thumbnail(art, Modifier.matchParentSize(), radius = FeedVideoThumbRadius)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = HeaderGutter),
        )
        if (credit.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = credit,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 19.sp),
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = HeaderGutter)
                    .then(
                        if (channelUrl == null) Modifier
                        else Modifier.clip(RoundedCornerShape(6.dp)).pressable { openChannel(channelUrl) },
                    ),
            )
        }
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HeaderGutter),
            )
        }
        if (showActions) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter),
                horizontalArrangement = Arrangement.spacedBy(ReleaseActionGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReleaseActionPill(
                    icon = SfIcons.PlayFill,
                    label = stringResource(R.string.ui_play),
                    filled = true,
                    onClick = onPlay,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                ReleaseActionPill(
                    icon = SfIcons.Shuffle,
                    label = stringResource(R.string.ui_shuffle),
                    filled = false,
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

/** Play and Shuffle, each half the row. Play is the white pill; Shuffle wears the glass. From BitChord's ActionPill. */
@Composable
private fun ReleaseActionPill(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (filled) Color.Black else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .height(ReleaseSideButton)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(Color.White)
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = LightFillAlpha), CircleShape)
                        .liquidGlassRim(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = CircleEdgeAlpha), CircleShape)
                },
            )
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
    }
}
