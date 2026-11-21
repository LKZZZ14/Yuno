package com.yuno

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Autoplay (Lucas, 2026-11-20). Two switches in Settings › Playback, both on by default:
 *
 * - [next]: when the last video of the queue ends, the first suggested video starts by itself,
 *   as on YouTube.
 * - [musicMix]: when a song starts with nothing queued after it, YouTube's own Mix for that
 *   song ("RD" + video id) is queued behind it, so music keeps playing like on YouTube.
 */
object AutoPlay {
    private const val KEY_NEXT = "autoplay_next"
    private const val KEY_MIX = "autoplay_music_mix"
    private var prefs: android.content.SharedPreferences? = null

    var next by mutableStateOf(true)
        private set
    var musicMix by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        next = prefs?.getBoolean(KEY_NEXT, true) != false
        musicMix = prefs?.getBoolean(KEY_MIX, true) != false
    }

    fun chooseNext(on: Boolean) {
        next = on
        prefs?.edit()?.putBoolean(KEY_NEXT, on)?.apply()
    }

    fun chooseMusicMix(on: Boolean) {
        musicMix = on
        prefs?.edit()?.putBoolean(KEY_MIX, on)?.apply()
    }
}

/**
 * What the app knows about the Watch later playlist this session. [ids] are the videos last
 * listed on its page (so a long-press can offer "Remove from Watch later"); [removed] are the
 * ones taken out since, which the page hides without reloading.
 */
object WatchLaterList {
    var ids by mutableStateOf<Set<String>>(emptySet())
    var removed by mutableStateOf<Set<String>>(emptySet())

    /** [id] was taken out of Watch later: the page hides it at once, and it is no longer "saved". */
    fun remove(id: String) {
        removed = removed + id
        ids = ids - id
    }

    /** [id] was put into Watch later (from the player, the held menu or the Save sheet). */
    fun added(id: String) {
        removed = removed - id
        ids = ids + id
    }

    /** The Watch later page listed [videos]: those are the ones a long-press can offer to remove. */
    fun listed(videos: List<VideoItem>) {
        ids = (ids + videos.map { it.id }) - removed
    }

    /** Signed out: forget everything about the account's Watch later. */
    fun clear() {
        ids = emptySet()
        removed = emptySet()
    }
}

/**
 * The title over "More videos" under the player, with YouTube's Autoplay switch beside it: on,
 * the first suggestion starts by itself when the queue ends ([AutoPlay.next]). It is the same
 * setting as in Settings › Playback.
 */
@Composable
fun AutoPlayHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(stringResource(R.string.ui_more_videos), Modifier.weight(1f))
        Text(
            stringResource(R.string.ui_autoplay),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        IosSwitch(
            checked = AutoPlay.next,
            onCheckedChange = { AutoPlay.chooseNext(it) },
            modifier = Modifier.padding(end = PageGutter - 12.dp, top = 12.dp),
        )
    }
}
