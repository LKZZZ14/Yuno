package com.yuno

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Opening animation (Lucas, 2026-11-22), in the way of YouTube's: on a cold start the two pause
 * bars pop in like a pair of eyes, then the play button swims in from the left and the smile is
 * drawn behind it. About 1.6 s over black; a tap skips it. It plays once per process, never on a
 * rotation, and not at all when the system asks for no animations. The app is already being built
 * underneath, so it costs no waiting.
 */
object IntroState {
    var shown = false
}

@Composable
fun IntroHost(enabled: Boolean, content: @Composable () -> Unit) {
    var intro by remember { mutableStateOf(enabled && !IntroState.shown) }
    LaunchedEffect(Unit) { IntroState.shown = true }
    Box(Modifier.fillMaxSize()) {
        content()
        if (intro) LogoIntro(onDone = { intro = false })
    }
}

private const val LogoW = 632f
private const val LogoH = 300f
private val LogoGreen = Color(0xFF8CF01E)
private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private fun shape(vararg contours: FloatArray): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    for (c in contours) {
        moveTo(c[0], c[1])
        var i = 2
        while (i < c.size) {
            lineTo(c[i], c[i + 1])
            i += 2
        }
        close()
    }
}

@Composable
private fun LogoIntro(onDone: () -> Unit) {
    val reduced = rememberReducedMotion()
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) {
            onDone()
            return@LaunchedEffect
        }
        clock.animateTo(1.6f, tween(1600, easing = LinearEasing))
        onDone()
    }
    val barA = remember { shape(BarA) }
    val barB = remember { shape(BarB) }
    val swooshWhite = remember { shape(SwooshW0, SwooshW1, SwooshW2) }
    val swooshGreen = remember { shape(SwooshG0, SwooshG1) }
    val play = remember { shape(PlayShape) }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - ((clock.value - 1.3f) / 0.3f).coerceIn(0f, 1f) }
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onDone() } },
    ) {
        val t = clock.value
        val s = size.width * 0.62f / LogoW
        val ox = (size.width - LogoW * s) / 2f
        val oy = (size.height - LogoH * s) / 2f
        withTransform({
            translate(ox, oy)
            scale(s, s, Offset.Zero)
        }) {
            // The eyes: each bar pops in with a little overshoot.
            val a = Overshoot.transform(((t - 0.0f) / 0.3f).coerceIn(0f, 1f))
            val b = Overshoot.transform(((t - 0.12f) / 0.3f).coerceIn(0f, 1f))
            if (a > 0f) withTransform({ scale(a, a, Offset(235.5f, 71.5f)) }) { drawPath(barA, Color.White) }
            if (b > 0f) withTransform({ scale(b, b, Offset(382.5f, 71.5f)) }) { drawPath(barB, Color.White) }
            // The smile, drawn left to right as the play button comes in.
            val r = FastOutSlowInEasing.transform(((t - 0.4f) / 0.6f).coerceIn(0f, 1f))
            if (r > 0f) {
                clipRect(left = -20f, top = -20f, right = r * (LogoW + 20f), bottom = LogoH + 20f) {
                    drawPath(swooshWhite, Color.White)
                    drawPath(swooshGreen, LogoGreen)
                }
                val k = 1f - r
                withTransform({ translate(-k * 340f, -k * 35f) }) {
                    drawPath(play, LogoGreen, alpha = (r * 5f).coerceIn(0f, 1f))
                }
            }
        }
    }
}

// The logo's shapes, traced from Lucas's drawing: x and y in a 632 x 300 box.
private val BarA = floatArrayOf(230.0f, 0.0f, 216.0f, 6.0f, 210.0f, 12.0f, 204.0f, 24.0f, 203.0f, 29.0f, 203.0f, 114.0f, 208.0f, 128.0f, 214.0f, 135.0f, 225.0f, 141.0f, 242.0f, 142.0f, 255.0f, 136.0f, 263.0f, 127.0f, 267.0f, 117.0f, 267.0f, 26.0f, 264.0f, 17.0f, 255.0f, 6.0f, 248.0f, 2.0f, 241.0f, 0.0f)
private val BarB = floatArrayOf(377.0f, 0.0f, 370.0f, 2.0f, 362.0f, 7.0f, 355.0f, 15.0f, 351.0f, 24.0f, 350.0f, 29.0f, 350.0f, 114.0f, 354.0f, 126.0f, 361.0f, 135.0f, 372.0f, 141.0f, 388.0f, 142.0f, 395.0f, 140.0f, 402.0f, 136.0f, 410.0f, 127.0f, 414.0f, 116.0f, 414.0f, 26.0f, 410.0f, 15.0f, 402.0f, 6.0f, 395.0f, 2.0f, 388.0f, 0.0f)
private val SwooshW0 = floatArrayOf(348.0f, 246.0f, 337.0f, 247.0f, 268.0f, 264.0f, 265.0f, 266.0f, 267.0f, 275.0f, 268.0f, 276.0f, 275.0f, 275.0f, 309.0f, 264.0f, 345.0f, 249.0f)
private val SwooshW1 = floatArrayOf(6.0f, 246.0f, 9.0f, 252.0f, 14.0f, 257.0f, 27.0f, 264.0f, 48.0f, 269.0f, 66.0f, 271.0f, 102.0f, 271.0f, 103.0f, 270.0f, 127.0f, 269.0f, 212.0f, 256.0f, 292.0f, 238.0f, 387.0f, 210.0f, 392.0f, 206.0f, 392.0f, 175.0f, 389.0f, 172.0f, 338.0f, 192.0f, 270.0f, 214.0f, 227.0f, 225.0f, 185.0f, 233.0f, 136.0f, 239.0f, 100.0f, 239.0f, 80.0f, 235.0f, 68.0f, 227.0f, 65.0f, 221.0f, 66.0f, 213.0f, 70.0f, 206.0f, 80.0f, 196.0f, 103.0f, 180.0f, 136.0f, 164.0f, 160.0f, 155.0f, 166.0f, 151.0f, 158.0f, 152.0f, 134.0f, 158.0f, 100.0f, 170.0f, 76.0f, 180.0f, 45.0f, 196.0f, 26.0f, 209.0f, 15.0f, 219.0f, 7.0f, 232.0f)
private val SwooshW2 = floatArrayOf(615.0f, 57.0f, 596.0f, 47.0f, 575.0f, 41.0f, 569.0f, 41.0f, 562.0f, 39.0f, 522.0f, 38.0f, 496.0f, 41.0f, 495.0f, 42.0f, 514.0f, 43.0f, 534.0f, 46.0f, 557.0f, 53.0f, 565.0f, 58.0f, 573.0f, 66.0f, 577.0f, 75.0f, 575.0f, 87.0f, 567.0f, 100.0f, 548.0f, 119.0f, 527.0f, 135.0f, 506.0f, 148.0f, 504.0f, 151.0f, 507.0f, 154.0f, 525.0f, 163.0f, 530.0f, 162.0f, 567.0f, 141.0f, 588.0f, 127.0f, 603.0f, 115.0f, 619.0f, 98.0f, 626.0f, 83.0f, 626.0f, 75.0f, 624.0f, 69.0f, 620.0f, 62.0f)
private val SwooshG0 = floatArrayOf(264.0f, 270.0f, 255.0f, 269.0f, 244.0f, 272.0f, 236.0f, 272.0f, 198.0f, 284.0f, 170.0f, 291.0f, 154.0f, 297.0f, 158.0f, 298.0f, 172.0f, 297.0f, 238.0f, 285.0f, 263.0f, 276.0f)
private val SwooshG1 = floatArrayOf(141.0f, 133.0f, 123.0f, 136.0f, 88.0f, 149.0f, 66.0f, 161.0f, 56.0f, 168.0f, 53.0f, 172.0f, 59.0f, 171.0f, 88.0f, 159.0f, 118.0f, 144.0f, 131.0f, 139.0f)
private val PlayShape = floatArrayOf(524.0f, 188.0f, 516.0f, 181.0f, 441.0f, 142.0f, 432.0f, 139.0f, 422.0f, 142.0f, 419.0f, 144.0f, 415.0f, 152.0f, 416.0f, 244.0f, 419.0f, 251.0f, 426.0f, 256.0f, 432.0f, 256.0f, 438.0f, 254.0f, 518.0f, 210.0f, 523.0f, 206.0f, 526.0f, 200.0f, 526.0f, 192.0f)
