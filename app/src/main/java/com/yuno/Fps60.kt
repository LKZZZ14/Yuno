package com.yuno

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 60 FPS (Lucas, 2026-11-24). On by default: a video that has a 60 fps version of the height being
 * played plays that one (StreamSelection), and the quality lists mark those heights, e.g. "1080p60".
 * Off, the 30 fps version is used instead, which takes less data and battery. A change counts from
 * the next video that is opened.
 */
object Fps60 {
    private const val KEY = "prefer_60fps"
    private var prefs: android.content.SharedPreferences? = null

    var prefer by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        prefer = prefs?.getBoolean(KEY, true) != false
    }

    fun choose(on: Boolean) {
        prefer = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
    }

    /** "1080p", or "1080p60" when [info] offers a 60 fps stream at [height]. */
    fun label(height: Int, details: VideoDetails): String = if (height in details.fps60) "${height}p60" else "${height}p"
}
