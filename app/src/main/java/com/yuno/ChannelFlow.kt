package com.yuno

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Latest / Popular / Oldest are one menu. Everything else (Members only, Public, …) is a chip. */
private val SortTitles = setOf("latest", "popular", "oldest")

/**
 * Flow's channel tab row: a 44dp scrollable row, a 2dp indicator, and a hairline under it.
 * Colours are the channel page's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelTabRow(
    selectedIndex: Int,
    tabs: List<String>,
    palette: ArtworkPalette,
    onTabSelected: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(palette.wash)) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 0.dp,
            containerColor = palette.wash,
            contentColor = palette.onBackground,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedIndex),
                    height = 2.dp,
                    color = palette.onBackgroundVariant,
                )
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedIndex == index
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.height(44.dp),
                    selectedContentColor = palette.onBackground,
                    unselectedContentColor = palette.onBackgroundVariant,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
        HorizontalDivider(color = palette.onBackgroundVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
    }
}

/**
 * Flow's filter bar. The sort (Latest, Popular, Oldest) opens a menu. The other filters are chips,
 * with a check on the one that's on.
 */
@Composable
internal fun ChannelFilterBar(
    chips: List<FeedChip>,
    chosen: FeedChip?,
    palette: ArtworkPalette,
    labelOf: @Composable (String) -> String,
    onPick: (FeedChip) -> Unit,
) {
    val sorts = chips.filter { it.title.lowercase() in SortTitles }
    val filters = chips.filter { it.title.lowercase() !in SortTitles }
    val chosenSort = sorts.firstOrNull { it.title == chosen?.title }
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(palette.wash),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sorts.isNotEmpty()) {
            item(key = "sort") {
                SortMenu(sorts, chosenSort, palette, labelOf, onPick)
            }
        }
        items(filters, key = { it.title }) { chip ->
            val on = chosen?.title == chip.title
            FlowStyleChip(labelOf(chip.title), on, palette) { onPick(chip) }
        }
    }
}

@Composable
private fun SortMenu(
    sorts: List<FeedChip>,
    chosen: FeedChip?,
    palette: ArtworkPalette,
    labelOf: @Composable (String) -> String,
    onPick: (FeedChip) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = chosen ?: sorts.firstOrNull { it.token == null } ?: sorts.first()
    Box {
        FlowStyleChip(labelOf(active.title), selected = chosen != null, palette = palette, dropdown = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sorts.forEach { chip ->
                val on = chip.title == active.title
                DropdownMenuItem(
                    text = { Text(labelOf(chip.title)) },
                    onClick = { open = false; onPick(chip) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun FlowStyleChip(label: String, selected: Boolean, palette: ArtworkPalette, dropdown: Boolean = false, onClick: () -> Unit) {
    val ink = if (palette.onBackgroundVariant.luminance() > 0.5f) Color.Black else Color.White
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected && !dropdown) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        trailingIcon = if (dropdown) {
            { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = palette.elevated,
            labelColor = palette.onBackgroundVariant,
            iconColor = palette.onBackgroundVariant,
            selectedContainerColor = palette.onBackgroundVariant,
            selectedLabelColor = ink,
            selectedLeadingIconColor = ink,
            selectedTrailingIconColor = ink,
        ),
    )
}

/**
 * Flow's About panel: the description, then one row per fact the channel actually has.
 */
@Composable
internal fun ChannelAboutPage(description: String, handle: String?, subscribers: String?, palette: ArtworkPalette) {
    val rows = listOfNotNull(
        handle?.let { Icons.Outlined.Person to it },
        subscribers?.let { Icons.Outlined.Person to it },
    )
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (description.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.channel_about_heading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.onBackground)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = palette.onBackground)
            }
        }
        if (rows.isNotEmpty()) {
            if (description.isNotBlank()) HorizontalDivider(color = palette.onBackgroundVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.channel_about_more), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.onBackground)
                rows.forEach { (icon, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = palette.onBackgroundVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium, color = palette.onBackground)
                    }
                }
            }
        }
    }
}

/** One community post, in Flow's order: author, text, a full-width picture, then like, share and comments. */
@Composable
internal fun ChannelPostCard(post: CommunityPost, palette: ArtworkPalette) {
    val context = LocalContext.current
    var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
    var overflows by rememberSaveable(post.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(post.avatar, post.author, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    post.author,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (post.ago.isNotBlank()) {
                    Text(post.ago, style = MaterialTheme.typography.bodySmall, color = palette.onBackgroundVariant)
                }
            }
        }
        if (post.text.isNotBlank()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SelectionContainer {
                    Text(
                        post.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onBackground,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { overflows = it.hasVisualOverflow },
                    )
                }
                if (!expanded && overflows) {
                    TextButton(onClick = { expanded = true }) {
                        Text(stringResource(R.string.channel_post_more), color = palette.onBackgroundVariant)
                    }
                }
            }
        }
        post.image?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 520.dp).background(palette.elevated),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ThumbUp, contentDescription = null, tint = palette.onBackgroundVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                post.likes.ifBlank { stringResource(R.string.player_like) },
                style = MaterialTheme.typography.labelLarge,
                color = palette.onBackgroundVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { shareLink(context, "https://www.youtube.com/post/${post.id}", post.author) }) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.ui_share), modifier = Modifier.size(20.dp), tint = palette.onBackgroundVariant)
            }
            if (post.comments.isNotBlank()) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.onBackgroundVariant)
                Spacer(Modifier.width(8.dp))
                Text(post.comments, style = MaterialTheme.typography.labelLarge, color = palette.onBackgroundVariant)
            }
        }
        HorizontalDivider(color = palette.onBackgroundVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
    }
}

/** A video on the channel, as a row: thumbnail on the left, title and the detail line. No channel name. */
@Composable
internal fun ChannelVideoRow(video: VideoItem, palette: ArtworkPalette, onClick: () -> Unit) {
    val menu = LocalVideoMenu.current
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp).prefetchOnPress(video.id)
            .pressable(pressedScale = 0.97f, onLongClick = { menu(video) }, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(168.dp)) {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 12, videoId = video.id)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true)
            ResumeBar(video.id, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, color = palette.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = metaLine("", video.views, video.uploaded)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(meta, style = MaterialTheme.typography.bodySmall, color = palette.onBackgroundVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
