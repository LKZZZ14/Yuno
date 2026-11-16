package com.yuno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yuno.icons.SfIcons

/** Newest history rows drawn before "Show all". From Flow's `SEARCH_HISTORY_LIMIT`. */
private const val HistoryShown = 8

/**
 * Search, as Flow's screen: history and suggestions together while typing, then the type chips
 * over the results. The field stays in the shared top bar.
 */
@Composable
fun SearchScreen(
    state: SearchState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onOpenShorts: (ShortsList) -> Unit,
    scrollToTop: Int = 0,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    SideEffect {
        state.onLink = { target ->
            when (target) {
                is YoutubeTarget.Video -> onOpen(stubVideo(target.id))
                is YoutubeTarget.Short -> onOpenShorts(ShortsList(listOf(stubVideo(target.id)), 0))
                is YoutubeTarget.Channel -> onOpenChannel(target.url)
                is YoutubeTarget.Playlist -> onOpenPlaylist(PlaylistRef(target.id, target.id, null, null))
            }
        }
    }
    LaunchedEffect(state.showingResults) {
        if (state.showingResults) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    // The bar and the chips float. The list fills the screen and scrolls behind them,
    // so the page colour is not a slab under the sticky chrome.
    val underBar = topBarHeight() + 8.dp
    val underChips = topBarHeight() + 6.dp + 40.dp + 12.dp
    val watched = WatchHistory.videos.map { it.id }.toSet()
    val shown = remember(state.hits, state.filter.watchState, watched) {
        state.hits.watchFiltered(state.filter.watchState, watched)
    }
    val topInset = if (state.showingResults) underChips else underBar
    val inset = PaddingValues(top = topInset, bottom = contentPadding.calculateBottomPadding())
    Box(Modifier.fillMaxSize().overlayTouchBlock()) {
        when {
            !state.showingResults -> SearchSuggestions(
                query = state.query,
                history = SearchHistory.matching(state.query),
                suggestions = state.suggestions,
                contentPadding = inset,
                onSubmit = { text ->
                    state.updateQuery(text)
                    dismissAndSearch(state, focus, keyboard)
                },
                onFill = state::updateQuery,
            )
            state.error != null && shown.isEmpty() -> EmptyState(
                icon = SfIcons.CloudOff,
                title = stringResource(R.string.ui_search_error_title),
                body = state.error.orEmpty(),
                action = { PillButton(stringResource(R.string.ui_try_again), state::submit) },
            )
            state.submitted.isEmpty() -> EmptyLine(stringResource(R.string.ui_search_intro_body))
            shown.isEmpty() && !state.loading -> EmptyLine(stringResource(R.string.ui_search_no_match, state.submitted))
            state.filter.contentType == ContentType.SHORTS -> SearchShorts(
                videos = shown.filterIsInstance<SearchHit.Video>().map { it.item },
                loading = state.loading && shown.isEmpty(),
                contentPadding = inset,
                onOpen = onOpenShorts,
                onMore = state::loadMore,
                hasMore = state.hasMore,
            )
            else -> SearchResults(
                hits = shown,
                loading = state.loading && shown.isEmpty(),
                contentPadding = inset,
                scrollToTop = scrollToTop,
                generation = state.generation,
                onOpen = onOpen,
                onOpenChannel = onOpenChannel,
                onOpenPlaylist = onOpenPlaylist,
                onOpenShorts = onOpenShorts,
                onMore = state::loadMore,
                hasMore = state.hasMore,
            )
        }
    }
    if (state.filtersOpen) {
        SearchFilterDialog(
            filter = state.filter,
            shortsEnabled = ShortsSetting.enabled,
            onApply = {
                state.filtersOpen = false
                state.updateFilter(it)
            },
            onDismiss = { state.filtersOpen = false },
        )
    }
}

/** Flow's `SearchSuggestionsPanel`: history first, then what YouTube suggests. */
@Composable
private fun SearchSuggestions(
    query: String,
    history: List<String>,
    suggestions: List<String>,
    contentPadding: PaddingValues,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll || query.isNotBlank()) history else history.take(HistoryShown)
    if (query.isBlank() && history.isEmpty() && suggestions.isEmpty()) {
        EmptyLine(stringResource(R.string.ui_search_intro_body))
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        if (history.isNotEmpty()) {
            item(key = "history:header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = PageGutter, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.ui_recent_searches),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.ui_clear_all)) }
                }
            }
            items(shown, key = { "history:$it" }) { item ->
                SuggestionRow(
                    text = item,
                    query = query,
                    icon = SfIcons.History,
                    trailing = SfIcons.Close,
                    trailingDescription = stringResource(R.string.ui_remove_search),
                    onClick = { onSubmit(item) },
                    onTrailing = { SearchHistory.remove(item) },
                )
            }
            if (!showAll && query.isBlank() && history.size > shown.size) {
                item(key = "history:more") {
                    TextButton(onClick = { showAll = true }, modifier = Modifier.padding(start = 4.dp)) {
                        Text(stringResource(R.string.ui_search_show_all))
                    }
                }
            }
        }
        items(suggestions, key = { "suggestion:$it" }) { suggestion ->
            SuggestionRow(
                text = suggestion,
                query = query,
                icon = SfIcons.Search,
                trailing = Icons.Rounded.NorthWest,
                trailingDescription = stringResource(R.string.ui_search_fill),
                onClick = { onSubmit(suggestion) },
                onTrailing = { onFill(suggestion) },
            )
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.ui_search_clear_title)) },
            text = { Text(stringResource(R.string.ui_search_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    SearchHistory.clear()
                }) { Text(stringResource(R.string.ui_search_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }
}

/** Flow's `FlowSuggestionRow`: the part not yet typed is bold, and the corner button fills or removes. */
@Composable
private fun SuggestionRow(
    text: String,
    query: String,
    icon: ImageVector,
    trailing: ImageVector,
    trailingDescription: String,
    onClick: () -> Unit,
    onTrailing: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).pressable(onClick = onClick).padding(start = PageGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                emphasiseCompletion(text, query),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(44.dp).pressable(onClick = onTrailing), contentAlignment = Alignment.Center) {
            Icon(trailing, contentDescription = trailingDescription, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** The type chips above the results, in the order YouTube shows them. From Flow's `SearchFilterBar`. */
@Composable
internal fun SearchFilterBar(
    filter: SearchFilter,
    shortsEnabled: Boolean,
    onFilterChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val types = remember(shortsEnabled) { ChipOrder.filterNot { it == ContentType.SHORTS && !shortsEnabled } }
    val videoFilters = filter.contentType.supportsVideoFilters()
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = PageGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(types, key = { it.name }) { type ->
            SearchChromeChip(stringResource(type.label()), filter.contentType == type) { onFilterChange(filter.copy(contentType = type)) }
        }
        if (videoFilters) {
            item(key = "recent") {
                val recent = filter.uploadDate == UploadDate.THIS_WEEK
                SearchChromeChip(stringResource(R.string.ui_search_recent), recent) {
                    onFilterChange(filter.copy(uploadDate = if (recent) UploadDate.ANY else UploadDate.THIS_WEEK))
                }
            }
            item(key = "unwatched") {
                val on = filter.watchState == WatchState.UNWATCHED
                SearchChromeChip(stringResource(R.string.ui_search_unwatched), on) {
                    onFilterChange(filter.copy(watchState = if (on) WatchState.ANY else WatchState.UNWATCHED))
                }
            }
            item(key = "watched") {
                val on = filter.watchState == WatchState.WATCHED
                SearchChromeChip(stringResource(R.string.ui_search_watched), on) {
                    onFilterChange(filter.copy(watchState = if (on) WatchState.ANY else WatchState.WATCHED))
                }
            }
        }
    }
}

/**
 * The search field's material: liquid glass while that is on, the same frost when it is off.
 * Drawn from the top chrome. Glass inside the page recording has nothing behind it to sample.
 */
@Composable
private fun SearchChromeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val ink = if (glass) glassContentColor() else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .height(40.dp)
            .floatingChrome(CircleShape)
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(label, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
    )
}

@Composable
private fun SearchResults(
    hits: List<SearchHit>,
    loading: Boolean,
    contentPadding: PaddingValues,
    scrollToTop: Int,
    generation: Int,
    onOpen: (VideoItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onOpenShorts: (ShortsList) -> Unit,
    onMore: () -> Unit,
    hasMore: Boolean,
) {
    val list = rememberScrollToTop(scrollToTop)
    LaunchedEffect(generation) { list.scrollToItem(0) }
    LoadMore(list, hits.isNotEmpty() && hasMore, onMore)
    LazyColumn(state = list, contentPadding = contentPadding) {
        if (loading) items(4) { FeedSkeletons(1) }
        items(hits.size, key = { hits[it].resultKey() }) { index ->
            when (val hit = hits[index]) {
                is SearchHit.Video -> if (hit.short) {
                    // A short that arrived inside an ordinary search opens with the shorts player.
                    VideoCard(hit.item, onClick = { onOpenShorts(ShortsList(listOf(hit.item), 0)) })
                } else VideoCard(hit.item, onClick = { onOpen(hit.item) })
                is SearchHit.Channel -> SearchChannelRow(hit) { onOpenChannel("https://www.youtube.com/channel/${hit.ref.id}") }
                is SearchHit.Playlist -> SearchPlaylistRow(hit.ref) { onOpenPlaylist(hit.ref) }
            }
        }
        if (hasMore && hits.isNotEmpty()) item { FeedSkeletons(1) }
    }
}

@Composable
private fun SearchShorts(
    videos: List<VideoItem>,
    loading: Boolean,
    contentPadding: PaddingValues,
    onOpen: (ShortsList) -> Unit,
    onMore: () -> Unit,
    hasMore: Boolean,
) {
    val list = rememberLazyGridState()
    val nearEnd by remember(list) {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, videos.isNotEmpty(), hasMore) { if (nearEnd && videos.isNotEmpty() && hasMore) onMore() }
    if (loading) {
        Box(Modifier.padding(top = contentPadding.calculateTopPadding())) { FeedSkeletons(4) }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        state = list,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = PageGutter),
    ) {
        gridItems(videos, key = { it.id }) { video ->
            val index = videos.indexOf(video)
            Column(Modifier.pressable(pressedScale = 0.97f) { onOpen(ShortsList(videos, index)) }) {
                Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(9f / 16f), radius = 16)
                Spacer(Modifier.height(8.dp))
                Text(video.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SearchChannelRow(hit: SearchHit.Channel, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = PageGutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(hit.ref.avatar, hit.ref.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.ref.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            hit.meta?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        }
    }
}

@Composable
private fun SearchPlaylistRow(playlist: PlaylistRef, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = PageGutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(playlist.thumbnail, Modifier.width(142.dp).aspectRatio(16f / 9f), radius = 12)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            playlist.subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        }
    }
}

/** Flow's filter dialog: each group is a menu, and features are chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterDialog(
    filter: SearchFilter,
    shortsEnabled: Boolean,
    onApply: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filter) { mutableStateOf(filter) }
    val videoFilters = draft.contentType.supportsVideoFilters()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_search_filters)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterMenu(stringResource(R.string.ui_search_type), stringResource(draft.contentType.label()), ChipOrder.filterNot { it == ContentType.SHORTS && !shortsEnabled }, { stringResource(it.label()) }) {
                    draft = draft.copy(contentType = it)
                }
                if (videoFilters) {
                    FilterMenu(stringResource(R.string.ui_search_duration), stringResource(draft.duration.label()), Duration.entries, { stringResource(it.label()) }) {
                        draft = draft.copy(duration = it)
                    }
                    FilterMenu(stringResource(R.string.ui_search_upload_date), stringResource(draft.uploadDate.label()), UploadDate.entries, { stringResource(it.label()) }) {
                        draft = draft.copy(uploadDate = it)
                    }
                    FilterMenu(stringResource(R.string.ui_search_watched), stringResource(draft.watchState.label()), WatchState.entries, { stringResource(it.label()) }) {
                        draft = draft.copy(watchState = it)
                    }
                }
                FilterMenu(stringResource(R.string.ui_search_sort), stringResource(draft.sortType.label()), SortType.entries, { stringResource(it.label()) }) {
                    draft = draft.copy(sortType = it)
                }
                if (videoFilters) {
                    HorizontalDivider()
                    Text(stringResource(R.string.ui_search_features), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchFeature.entries.forEach { feature ->
                            val selected = feature in draft.features
                            SearchChip(stringResource(feature.label()), selected) {
                                draft = draft.copy(features = if (selected) draft.features - feature else draft.features + feature)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text(stringResource(R.string.ui_search_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
private fun <T> FilterMenu(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().pressable { expanded = true }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { expanded = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun LoadMore(list: LazyListState, ready: Boolean, onMore: () -> Unit) {
    val nearEnd by remember(list) {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, ready) { if (nearEnd && ready) onMore() }
}

private fun emphasiseCompletion(text: String, query: String): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || !text.startsWith(trimmed, ignoreCase = true)) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, trimmed.length))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(trimmed.length)) }
    }
}

private fun List<SearchHit>.watchFiltered(state: WatchState, watched: Set<String>): List<SearchHit> = when (state) {
    WatchState.ANY -> this
    WatchState.WATCHED -> filter { it !is SearchHit.Video || it.item.id in watched }
    WatchState.UNWATCHED -> filter { it !is SearchHit.Video || it.item.id !in watched }
}

private fun SearchHit.resultKey(): String = when (this) {
    is SearchHit.Video -> "v:${item.id}"
    is SearchHit.Channel -> "c:${ref.id}"
    is SearchHit.Playlist -> "p:${ref.id}"
}

private fun stubVideo(id: String) = VideoItem(id, "", "", null, null, null, 0, -1, null)

private val ChipOrder = listOf(
    ContentType.ALL,
    ContentType.SHORTS,
    ContentType.VIDEOS,
    ContentType.LIVE,
    ContentType.CHANNELS,
    ContentType.PLAYLISTS,
    ContentType.MOVIES,
)

@Composable
private fun ContentType.label(): Int = when (this) {
    ContentType.ALL -> R.string.ui_search_all
    ContentType.VIDEOS -> R.string.ui_videos
    ContentType.SHORTS -> R.string.ui_shorts
    ContentType.CHANNELS -> R.string.ui_search_channels
    ContentType.PLAYLISTS -> R.string.ui_playlists
    ContentType.MOVIES -> R.string.ui_search_movies
    ContentType.LIVE -> R.string.ui_search_live
}

@Composable
private fun Duration.label(): Int = when (this) {
    Duration.ANY -> R.string.ui_search_any
    Duration.UNDER_3_MINUTES -> R.string.ui_search_under_3
    Duration.THREE_TO_20_MINUTES -> R.string.ui_search_3_to_20
    Duration.OVER_20_MINUTES -> R.string.ui_search_over_20
}

@Composable
private fun UploadDate.label(): Int = when (this) {
    UploadDate.ANY -> R.string.ui_search_any
    UploadDate.LAST_HOUR -> R.string.ui_search_last_hour
    UploadDate.TODAY -> R.string.ui_search_today
    UploadDate.THIS_WEEK -> R.string.ui_search_this_week
    UploadDate.THIS_MONTH -> R.string.ui_search_this_month
    UploadDate.THIS_YEAR -> R.string.ui_search_this_year
}

@Composable
private fun WatchState.label(): Int = when (this) {
    WatchState.ANY -> R.string.ui_search_any
    WatchState.UNWATCHED -> R.string.ui_search_unwatched
    WatchState.WATCHED -> R.string.ui_search_watched
}

@Composable
private fun SortType.label(): Int = when (this) {
    SortType.RELEVANCE -> R.string.ui_search_relevance
    SortType.VIEW_COUNT -> R.string.ui_search_view_count
}

@Composable
private fun SearchFeature.label(): Int = when (this) {
    SearchFeature.HD -> R.string.ui_search_hd
    SearchFeature.FOUR_K -> R.string.ui_search_4k
    SearchFeature.HDR -> R.string.ui_search_hdr
    SearchFeature.SUBTITLES -> R.string.ui_search_subtitles
    SearchFeature.CREATIVE_COMMONS -> R.string.ui_search_creative_commons
    SearchFeature.THREE_SIXTY -> R.string.ui_search_360
    SearchFeature.VR180 -> R.string.ui_search_vr180
    SearchFeature.THREE_D -> R.string.ui_search_3d
    SearchFeature.LOCATION -> R.string.ui_search_location
    SearchFeature.PURCHASED -> R.string.ui_search_purchased
}

private fun dismissAndSearch(
    state: SearchState,
    focus: FocusManager,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
) {
    focus.clearFocus(force = true)
    keyboard?.hide()
    state.submit()
}
