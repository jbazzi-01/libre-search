package org.libre.search.ui.cards

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.libre.search.panels.Sky
import org.libre.search.panels.Weather
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SunYellow = Color(0xFFFDD663)
private val MoonBlue = Color(0xFF9CC0FA)
private val CloudWhite = Color(0xFFF1F3F4)
private val CloudGrey = Color(0xFFB8BCC2)
private val RainBlue = Color(0xFF7FB2F5)

/** Weather icon drawn in code (sun, moon, clouds, rain, snow, storm, fog). */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier) {
    val sky = Weather.sky(code)
    Canvas(modifier) {
        val s = size.minDimension
        when (sky) {
            Sky.CLEAR -> orb(isDay, Offset(s * 0.5f, s * 0.5f), s * 0.3f)
            Sky.PARTLY -> {
                orb(isDay, Offset(s * 0.38f, s * 0.38f), s * 0.24f)
                cloud(Offset(s * 0.56f, s * 0.64f), s * 0.62f, CloudWhite)
            }
            Sky.CLOUDY -> {
                if (code == 3 || code == 2) orb(isDay, Offset(s * 0.34f, s * 0.36f), s * 0.2f)
                cloud(Offset(s * 0.55f, s * 0.6f), s * 0.72f, CloudWhite)
            }
            Sky.FOG -> {
                cloud(Offset(s * 0.5f, s * 0.42f), s * 0.64f, CloudGrey)
                for (i in 0..2) {
                    val y = s * (0.66f + i * 0.1f)
                    drawLine(CloudGrey, Offset(s * 0.18f, y), Offset(s * 0.82f, y), s * 0.05f, StrokeCap.Round)
                }
            }
            Sky.DRIZZLE, Sky.RAIN -> {
                cloud(Offset(s * 0.5f, s * 0.42f), s * 0.7f, if (sky == Sky.RAIN) CloudGrey else CloudWhite)
                val n = if (sky == Sky.RAIN) 3 else 2
                for (i in 0 until n) {
                    val x = s * (0.34f + i * 0.16f)
                    drawLine(RainBlue, Offset(x, s * 0.7f), Offset(x - s * 0.05f, s * 0.86f), s * 0.06f, StrokeCap.Round)
                }
            }
            Sky.SNOW -> {
                cloud(Offset(s * 0.5f, s * 0.42f), s * 0.7f, CloudWhite)
                for (i in 0..2) drawCircle(Color.White, s * 0.045f, Offset(s * (0.33f + i * 0.17f), s * (0.78f + (i % 2) * 0.07f)))
            }
            Sky.STORM -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.72f, CloudGrey)
                val bolt = Path().apply {
                    moveTo(s * 0.52f, s * 0.58f); lineTo(s * 0.4f, s * 0.78f); lineTo(s * 0.5f, s * 0.78f)
                    lineTo(s * 0.44f, s * 0.95f); lineTo(s * 0.62f, s * 0.7f); lineTo(s * 0.52f, s * 0.7f); close()
                }
                drawPath(bolt, SunYellow)
            }
        }
    }
}

private fun DrawScope.orb(isDay: Boolean, center: Offset, r: Float) {
    if (isDay) {
        drawCircle(SunYellow, r, center)
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            val p1 = Offset(center.x + cos(a).toFloat() * r * 1.35f, center.y + sin(a).toFloat() * r * 1.35f)
            val p2 = Offset(center.x + cos(a).toFloat() * r * 1.7f, center.y + sin(a).toFloat() * r * 1.7f)
            drawLine(SunYellow, p1, p2, r * 0.22f, StrokeCap.Round)
        }
    } else {
        // Crescent moon: a circle with a smaller offset circle cut out.
        val path = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(center, r))
        }
        val cut = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + r * 0.55f, center.y - r * 0.35f), r * 0.9f))
        }
        val moon = Path().apply { op(path, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
        drawPath(moon, MoonBlue)
    }
}

private fun DrawScope.cloud(center: Offset, width: Float, color: Color) {
    val h = width * 0.42f
    val left = center.x - width / 2
    val top = center.y - h / 2
    drawRoundRect(color, Offset(left, top + h * 0.35f), Size(width, h * 0.65f), CornerRadius(h * 0.33f))
    drawCircle(color, h * 0.42f, Offset(left + width * 0.36f, top + h * 0.42f))
    drawCircle(color, h * 0.32f, Offset(left + width * 0.64f, top + h * 0.5f))
}

/**
 * Original illustrated strip under the weather card: hills, sky that follows the time and weather,
 * and a small fox character who reacts to the conditions (campfire at night, umbrella in rain…).
 */
@Composable
fun WeatherScene(code: Int, isDay: Boolean, modifier: Modifier) {
    val sky = Weather.sky(code)
    val t = rememberInfiniteTransition(label = "scene")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val flicker by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "f")
    val stars = remember { List(28) { Offset(Random.nextFloat(), Random.nextFloat() * 0.55f) } }
    val drops = remember { List(40) { Offset(Random.nextFloat(), Random.nextFloat()) } }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val night = !isDay
        val grey = sky in listOf(Sky.CLOUDY, Sky.RAIN, Sky.DRIZZLE, Sky.STORM, Sky.FOG, Sky.SNOW)
        val top = when {
            night -> Color(0xFF16213E)
            grey -> Color(0xFF6B7A8F)
            else -> Color(0xFF6FB1F7)
        }
        val bottom = when {
            night -> Color(0xFF263B6A)
            grey -> Color(0xFF9AA8B8)
            else -> Color(0xFFB9DCFF)
        }
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        if (night && !grey) stars.forEach { drawCircle(Color.White.copy(alpha = 0.7f), 1.6f, Offset(it.x * w, it.y * h)) }
        if (!grey) orb(isDay, Offset(w * 0.82f, h * 0.28f), h * 0.12f)
        if (sky != Sky.CLEAR) {
            val cc = if (grey) Color(0xFFD5DAE0).copy(alpha = if (night) 0.35f else 0.8f) else Color.White.copy(alpha = 0.85f)
            cloud(Offset(w * (0.2f + phase * 0.02f), h * 0.22f), w * 0.22f, cc)
            cloud(Offset(w * (0.62f - phase * 0.02f), h * 0.16f), w * 0.18f, cc)
        }

        // Hills
        val farHill = if (night) Color(0xFF1E3358) else Color(0xFF6FA86B)
        val nearHill = if (night) Color(0xFF223D66) else Color(0xFF4F8F4B)
        val far = Path().apply {
            moveTo(0f, h * 0.7f)
            cubicTo(w * 0.25f, h * 0.5f, w * 0.5f, h * 0.75f, w * 0.75f, h * 0.58f)
            cubicTo(w * 0.88f, h * 0.5f, w, h * 0.6f, w, h * 0.62f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(far, farHill)
        val near = Path().apply {
            moveTo(0f, h * 0.84f)
            cubicTo(w * 0.3f, h * 0.72f, w * 0.6f, h * 0.9f, w, h * 0.78f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(near, nearHill)

        // Fox character
        val fx = w * 0.2f
        val fy = h * 0.8f
        val u = h * 0.07f
        val orange = Color(0xFFF08A3C)
        val cream = Color(0xFFFFE8CC)
        // tail
        val tail = Path().apply {
            moveTo(fx + u * 1.2f, fy)
            cubicTo(fx + u * 3.2f, fy - u * 0.4f, fx + u * 3.4f, fy - u * 2.4f, fx + u * 2.2f, fy - u * 2.6f)
            cubicTo(fx + u * 2.6f, fy - u * 1.4f, fx + u * 2f, fy - u * 0.6f, fx + u * 0.8f, fy - u * 0.4f)
            close()
        }
        drawPath(tail, orange)
        drawCircle(cream, u * 0.45f, Offset(fx + u * 2.25f, fy - u * 2.35f))
        // body and head
        drawOval(orange, Offset(fx - u * 1.1f, fy - u * 2.2f), Size(u * 2.3f, u * 2.4f))
        drawOval(cream, Offset(fx - u * 0.55f, fy - u * 1.7f), Size(u * 1.1f, u * 1.5f))
        drawCircle(orange, u * 1.05f, Offset(fx, fy - u * 3f))
        val earL = Path().apply { moveTo(fx - u * 0.95f, fy - u * 3.3f); lineTo(fx - u * 0.7f, fy - u * 4.5f); lineTo(fx - u * 0.15f, fy - u * 3.8f); close() }
        val earR = Path().apply { moveTo(fx + u * 0.95f, fy - u * 3.3f); lineTo(fx + u * 0.7f, fy - u * 4.5f); lineTo(fx + u * 0.15f, fy - u * 3.8f); close() }
        drawPath(earL, orange); drawPath(earR, orange)
        drawCircle(cream, u * 0.5f, Offset(fx, fy - u * 2.6f))
        drawCircle(Color(0xFF2B2B2B), u * 0.13f, Offset(fx, fy - u * 2.75f))
        val eyeY = fy - u * 3.2f
        if (night && sky != Sky.RAIN && sky != Sky.STORM) {
            // sleepy eyes by the fire
            drawLine(Color(0xFF2B2B2B), Offset(fx - u * 0.5f, eyeY), Offset(fx - u * 0.2f, eyeY), u * 0.12f, StrokeCap.Round)
            drawLine(Color(0xFF2B2B2B), Offset(fx + u * 0.2f, eyeY), Offset(fx + u * 0.5f, eyeY), u * 0.12f, StrokeCap.Round)
        } else {
            drawCircle(Color(0xFF2B2B2B), u * 0.14f, Offset(fx - u * 0.35f, eyeY))
            drawCircle(Color(0xFF2B2B2B), u * 0.14f, Offset(fx + u * 0.35f, eyeY))
        }

        when {
            sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.STORM -> {
                // umbrella
                val ux = fx - u * 0.2f
                drawLine(Color(0xFF5F6368), Offset(ux, fy - u * 1.2f), Offset(ux, fy - u * 5.2f), u * 0.15f)
                val canopy = Path().apply {
                    moveTo(ux - u * 2.2f, fy - u * 5f)
                    quadraticTo(ux, fy - u * 7.2f, ux + u * 2.2f, fy - u * 5f)
                    close()
                }
                drawPath(canopy, Color(0xFFE8453C))
            }
            sky == Sky.SNOW -> {
                drawRoundRect(Color(0xFF4A8AF4), Offset(fx - u * 0.9f, fy - u * 2.35f), Size(u * 1.8f, u * 0.4f), CornerRadius(u * 0.2f))
            }
            night -> {
                // campfire
                val cx = fx + u * 4.2f
                val cy = fy + u * 0.2f
                drawLine(Color(0xFF8D5A3B), Offset(cx - u * 0.9f, cy), Offset(cx + u * 0.9f, cy - u * 0.2f), u * 0.3f, StrokeCap.Round)
                drawLine(Color(0xFF7A4A2F), Offset(cx - u * 0.9f, cy - u * 0.2f), Offset(cx + u * 0.9f, cy), u * 0.3f, StrokeCap.Round)
                drawCircle(Color(0x55FDB54E), u * 2.2f * flicker, Offset(cx, cy - u * 0.8f))
                val flame = Path().apply {
                    moveTo(cx - u * 0.6f, cy - u * 0.2f)
                    quadraticTo(cx - u * 0.7f, cy - u * 1.4f * flicker, cx, cy - u * 2.2f * flicker)
                    quadraticTo(cx + u * 0.7f, cy - u * 1.4f * flicker, cx + u * 0.6f, cy - u * 0.2f)
                    close()
                }
                drawPath(flame, Color(0xFFFDB54E))
                drawCircle(Color(0xFFFFE08A), u * 0.35f, Offset(cx, cy - u * 0.6f))
            }
            else -> {
                // a small flower next to the fox on nice days
                val px = fx + u * 4f
                drawLine(Color(0xFF3AA757), Offset(px, fy + u * 0.4f), Offset(px, fy - u * 1.2f), u * 0.15f)
                for (i in 0 until 5) {
                    val a = i * 2 * Math.PI / 5
                    drawCircle(Color(0xFFF7A8C4), u * 0.3f, Offset(px + cos(a).toFloat() * u * 0.35f, fy - u * 1.4f + sin(a).toFloat() * u * 0.35f))
                }
                drawCircle(Color(0xFFFDD663), u * 0.22f, Offset(px, fy - u * 1.4f))
            }
        }

        // Precipitation layer
        if (sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.STORM) {
            drops.forEach { d ->
                val y = ((d.y + phase) % 1f) * h
                val x = d.x * w
                drawLine(RainBlue.copy(alpha = 0.8f), Offset(x, y), Offset(x - 3f, y + h * 0.06f), 2f, StrokeCap.Round)
            }
        } else if (sky == Sky.SNOW) {
            drops.forEach { d ->
                val y = ((d.y + phase * 0.5f) % 1f) * h
                drawCircle(Color.White, 2.5f, Offset(d.x * w + sin((phase + d.y) * 6.28f) * 4f, y))
            }
        } else if (sky == Sky.FOG) {
            drawRect(Color.White.copy(alpha = 0.25f))
        }
    }
}
