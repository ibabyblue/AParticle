package com.ibabyblue.aparticle.internal

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Frozen pre-optimization renderer used only to detect visual regressions. */
internal class CelebrationReferenceRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val destination = RectF()

    fun prepare() { atlas }

    fun draw(canvas: Canvas, frame: CelebrationSnapshot, density: Float) {
        val checkpoint = canvas.save()
        canvas.clipRect(0f, 0f, frame.width, frame.height)
        paint.shader = null
        paint.color = Color.BLACK
        paint.alpha = (frame.backdropOpacity * 255).roundToInt()
        if (frame.backdropOpacity > 0) canvas.drawRect(0f, 0f, frame.width, frame.height, paint)
        frame.elements.forEach { element ->
            if (element.opacity <= 0) return@forEach
            val opacity = element.opacity.coerceIn(0f, 1f)
            paint.shader = null
            paint.color = Color.WHITE
            paint.alpha = (opacity * 255).roundToInt()
            when (val geometry = element.geometry) {
                is CelebrationGeometry.Trail -> drawTrail(canvas, geometry, element.palette, opacity)
                is CelebrationGeometry.Glow -> {
                    val sprite = atlas.glows[element.palette.ordinal]
                    val dimension = sprite.dimension * geometry.radius / sprite.radius
                    destination.set(geometry.center.x - dimension / 2, geometry.center.y - dimension / 2,
                        geometry.center.x + dimension / 2, geometry.center.y + dimension / 2)
                    canvas.drawBitmap(sprite.image, null, destination, paint)
                }
                is CelebrationGeometry.Star -> {
                    val radiusIndex = ((geometry.radius / density).roundToInt() - 2).coerceIn(0, 4)
                    val sprite = atlas.stars[element.palette.ordinal * 10 + (if (geometry.fivePoint) 5 else 0) + radiusIndex]
                    val dimension = sprite.dimension * geometry.radius / sprite.radius
                    val saved = canvas.save()
                    canvas.translate(geometry.center.x, geometry.center.y)
                    canvas.rotate(geometry.rotation * 180f / PI.toFloat())
                    destination.set(-dimension / 2, -dimension / 2, dimension / 2, dimension / 2)
                    paint.alpha = (opacity * opacity * 255).roundToInt()
                    sprite.shadow?.let { canvas.drawBitmap(it, null, destination, paint) }
                    paint.alpha = (opacity * (2 - opacity) * 255).roundToInt()
                    canvas.drawBitmap(sprite.image, null, destination, paint)
                    canvas.restoreToCount(saved)
                    paint.alpha = (opacity * 255).roundToInt()
                    canvas.drawCircle(geometry.center.x, geometry.center.y, max(1.2f * density, geometry.radius * 0.16f), paint)
                }
            }
        }
        paint.shader = null
        canvas.restoreToCount(checkpoint)
    }

    private fun drawTrail(canvas: Canvas, trail: CelebrationGeometry.Trail, palette: CelebrationPalette, opacity: Float) {
        val points = trail.points
        if (points.size < 2 || points.first() == points.last()) return
        path.rewind()
        fun side(index: Int, sign: Float, move: Boolean) {
            val before = points[max(0, index - 1)]
            val after = points[minOf(points.lastIndex, index + 1)]
            val dx = after.x - before.x
            val dy = after.y - before.y
            val length = max(0.001f, hypot(dx, dy))
            val half = trail.width * 0.5f * (index.toFloat() / points.lastIndex).pow(1.4f) * sign
            val x = points[index].x - dy / length * half
            val y = points[index].y + dx / length * half
            if (move) path.moveTo(x, y) else path.lineTo(x, y)
        }
        points.indices.forEach { side(it, 1f, it == 0) }
        for (index in points.lastIndex downTo 0) side(index, -1f, false)
        path.close()
        paint.alpha = 255
        paint.shader = LinearGradient(points.first().x, points.first().y, points.last().x, points.last().y,
            intArrayOf(palette.edge.withMultipliedAlpha(0f), palette.edge.withMultipliedAlpha(opacity * 0.24f),
                palette.edge.withMultipliedAlpha(opacity * 0.85f), palette.fill.withMultipliedAlpha(opacity)),
            floatArrayOf(0f, 0.35f, 0.8f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private data class Sprite(val image: Bitmap, val shadow: Bitmap?, val radius: Float, val dimension: Float)
    private class Atlas {
        val stars = CelebrationPalette.entries.flatMap { palette ->
            listOf(false, true).flatMap { fivePoint -> (2..6).map { makeSprite(palette, it.toFloat(), fivePoint) } }
        }
        val glows = CelebrationPalette.entries.map { makeSprite(it, 3f, null) }
    }

    private companion object {
        val atlas by lazy { Atlas() }
        const val SPRITE_SCALE = 6f

        fun makeSprite(palette: CelebrationPalette, radius: Float, fivePoint: Boolean?): Sprite {
            val blur = max(1.5f, radius * 0.42f)
            val dimension = if (fivePoint == null) 24f else ceil((radius + blur * 3 + 1) * 2)
            val pixels = (dimension * SPRITE_SCALE).toInt()
            fun bitmap() = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            val image = bitmap()
            val canvas = Canvas(image)
            val center = pixels / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var shadow: Bitmap? = null
            if (fivePoint == null) {
                val extent = radius * 3 * SPRITE_SCALE
                paint.shader = RadialGradient(center, center, extent,
                    intArrayOf(palette.edge.argb, palette.fill.withMultipliedAlpha(0.85f),
                        palette.edge.withMultipliedAlpha(0.16f), palette.edge.withMultipliedAlpha(0f)),
                    floatArrayOf(0f, 0.2f, 0.55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(center, center, extent, paint)
                paint.shader = null
                paint.color = Color.WHITE
                paint.alpha = 204
                canvas.drawCircle(center, center, radius * 0.2f * SPRITE_SCALE, paint)
            } else {
                val path = Path()
                val count = if (fivePoint) 5 else 4
                repeat(count * 2) { index ->
                    val angle = index * PI / count
                    val distance = radius * SPRITE_SCALE * if (index % 2 == 0) 1f else if (fivePoint) 0.43f else 0.16f
                    val x = center + cos(angle).toFloat() * distance
                    val y = center + sin(angle).toFloat() * distance
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                shadow = bitmap()
                paint.color = palette.fill.withMultipliedAlpha(0.55f)
                paint.maskFilter = BlurMaskFilter(blur * SPRITE_SCALE, BlurMaskFilter.Blur.NORMAL)
                Canvas(shadow).drawPath(path, paint)
                paint.maskFilter = null
                paint.color = palette.fill.argb
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(0.8f, radius * 0.13f) * SPRITE_SCALE
                paint.color = palette.edge.argb
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
                paint.color = palette.fill.argb
                canvas.drawPath(path, paint)
            }
            return Sprite(image, shadow, radius, dimension)
        }
    }
}
