package com.yuno

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Chapters in fullscreen, YouTube's way (Lucas, 2026-11-21): a panel down the right edge, over the
 * video. The video keeps its size and keeps playing; the panel is dark but see-through, enough to
 * read the chapters and still see the picture. Opened by tapping the chapter's name beside the time
 * (VideoContent). It closes with Close, a tap on the video beside it, or Back.
 */
@Composable
fun BoxScope.ChaptersPanel(state: PlayerState, onClose: () -> Unit) {
    val chapters = state.details?.chapters.orEmpty()
    // The next video may have none: nothing to show then.
    LaunchedEffect(chapters.isEmpty()) { if (chapters.isEmpty()) onClose() }
    if (chapters.isEmpty()) return
    val current by remember(chapters) {
        derivedStateOf { chapters.indexOfLast { it.startMs <= state.positionMs }.coerceAtLeast(0) }
    }
    val boards = state.details?.storyboards.orEmpty()
    val sheets = rememberStoryboardSheets(state)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    val reduced = rememberReducedMotion()
    val slide = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, tween(220)) }

    Box(Modifier.matchParentSize().zIndex(20f)) {
        // A tap on the video beside the panel closes it.
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { onClose() } })
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .widthIn(min = 260.dp, max = 400.dp)
                .graphicsLayer { translationX = slide.value * size.width }
                .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
                .background(Color.Black.copy(alpha = 0.74f))
                // Taps on the panel itself do not reach the scrim.
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(top = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.chapters_title),
                        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                    )
                    Text(
                        stringResource(R.string.chapters_position, current + 1, chapters.size),
                        color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    )
                }
                Text(
                    stringResource(R.string.ui_close),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier
                        .pressable(onClick = onClose)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    PanelRow(chapter, index == current, boards, sheets, state.durationMs) {
                        state.seekTo(chapter.startMs)
                        if (!ChaptersStayOpen.on) onClose()
                    }
                }
            }
        }
    }
}

/** One chapter of [ChaptersPanel]: the frame it starts at with its time on it, then its title. */
@Composable
private fun PanelRow(
    chapter: Chapter,
    playing: Boolean,
    boards: List<Storyboard>,
    sheets: StoryboardSheets,
    durationMs: Long,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (playing) 0.24f else 0.08f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            if (boards.isNotEmpty()) StoryboardThumb(sheets, boards, chapter.startMs, durationMs, Modifier.matchParentSize())
            Text(
                formatDuration(chapter.startMs),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                chapter.title.ifBlank { formatDuration(chapter.startMs) },
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            if (playing) {
                Text(
                    stringResource(R.string.chapters_now_playing),
                    color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * Under the player (portrait), like YouTube's chapters row: the chapter playing, with its frame and
 * "3 of 12". It opens the chapters list. Nothing for a video without chapters, or a live one.
 */
@Composable
fun ChaptersCard(player: PlayerState, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val chapters = player.details?.chapters.orEmpty()
    if (chapters.isEmpty() || player.details?.live == true) return
    val current by remember(chapters) {
        derivedStateOf { chapters.indexOfLast { it.startMs <= player.positionMs }.coerceAtLeast(0) }
    }
    val boards = player.details?.storyboards.orEmpty()
    val sheets = rememberStoryboardSheets(player)
    val chapter = chapters[current.coerceIn(0, chapters.lastIndex)]
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onOpen)
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainerLow)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (boards.isNotEmpty()) {
            StoryboardThumb(
                sheets, boards, chapter.startMs, player.durationMs,
                Modifier.width(88.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(c.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.chapters_title) + " · " + stringResource(R.string.chapters_position, current + 1, chapters.size),
                color = c.onSurfaceVariant, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            )
            Text(
                chapter.title.ifBlank { formatDuration(chapter.startMs) },
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null, tint = c.onSurfaceVariant)
    }
}
