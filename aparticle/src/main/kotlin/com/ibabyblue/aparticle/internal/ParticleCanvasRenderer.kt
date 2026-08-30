package com.ibabyblue.aparticle.internal

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.ibabyblue.aparticle.ParticleColor
import com.ibabyblue.aparticle.ParticleShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal class ParticleCanvasRenderer(private val context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val drawableCache = mutableMapOf<Int, Drawable?>()

    fun drawRiseAndBurst(canvas: Canvas, snapshot: RiseAndBurstSnapshot) {
        snapshot.trails.forEach { drawParticle(canvas, it) }
        snapshot.launch?.let { drawParticle(canvas, it) }
        snapshot.particles.forEach { drawParticle(canvas, it) }
    }

    fun drawAtmosphere(canvas: Canvas, snapshot: AtmosphereSnapshot) {
        drawBubbles(canvas, snapshot)
        drawSparkles(canvas, snapshot)
        drawEdgeGlows(canvas, snapshot)
    }

    private fun drawParticle(canvas: Canvas, value: DrawableParticle) {
        val size = value.sizePx * value.scale
        if (size <= 0f || value.opacity <= 0f) return
        canvas.save()
        canvas.translate(value.x, value.y)
        canvas.rotate(Math.toDegrees(value.rotation.toDouble()).toFloat())
        val half = size / 2f
        when (val shape = value.shape) {
            ParticleShape.Heart -> drawHeart(canvas, -half, -half, half, half, value.color, value.opacity)
            ParticleShape.Circle -> drawCircle(canvas, 0f, 0f, half, value.color, value.opacity)
            ParticleShape.Star -> drawStar(canvas, 0f, 0f, half, value.color, value.opacity)
            is ParticleShape.DrawableResource -> drawDrawable(canvas, shape, half, value.color, value.opacity)
        }
        canvas.restore()
    }

    private fun drawBubbles(canvas: Canvas, snapshot: AtmosphereSnapshot) {
        snapshot.bubbles.forEach { bubble ->
            val edgeColor = snapshot.glowColor.withMultipliedAlpha(
                bubble.opacity * snapshot.glowIntensity,
            )
            paint.shader = RadialGradient(
                bubble.x,
                bubble.y,
                max(1f, bubble.radiusPx),
                intArrayOf(0x00000000, 0x00000000, edgeColor),
                floatArrayOf(0f, 0.85f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(bubble.x, bubble.y, bubble.radiusPx, paint)
        }
        paint.shader = null
    }

    private fun drawSparkles(canvas: Canvas, snapshot: AtmosphereSnapshot) {
        snapshot.sparkles.forEach { sparkle ->
            val coreSize = when (sparkle.kind) {
                SparkleKind.Dot -> max(2f, sparkle.sizePx * 1.8f)
                SparkleKind.FourPoint -> max(2f, sparkle.sizePx * 3.2f)
            }
            paint.shader = null
            paint.color = sparkle.color.withMultipliedAlpha(sparkle.opacity * 0.20f)
            canvas.drawCircle(sparkle.x, sparkle.y, coreSize * 1.7f, paint)
            when (sparkle.kind) {
                SparkleKind.Dot -> drawCircle(
                    canvas,
                    sparkle.x,
                    sparkle.y,
                    coreSize / 2f,
                    sparkle.color,
                    sparkle.opacity,
                )
                SparkleKind.FourPoint -> {
                    paint.color = sparkle.color.withMultipliedAlpha(sparkle.opacity)
                    canvas.drawPath(fourPointPath(sparkle.x, sparkle.y, coreSize), paint)
                }
            }
        }
    }

    private fun drawEdgeGlows(canvas: Canvas, snapshot: AtmosphereSnapshot) {
        val width = snapshot.widthPx
        val height = snapshot.heightPx
        if (width <= 0f || height <= 0f) return
        val intensity = snapshot.glowIntensity
        val breath = snapshot.breath
        val bottomAlpha = ((0.45f + 0.70f * breath) * intensity).coerceIn(0f, 1f)
        val sideAlpha = ((0.38f + 0.62f * breath) * intensity).coerceIn(0f, 1f)
        val topAlpha = ((0.20f + 0.45f * breath) * intensity).coerceIn(0f, 1f)

        val bottomStart = height * 0.38f
        paint.shader = LinearGradient(
            0f,
            bottomStart,
            0f,
            height,
            intArrayOf(
                0x00000000,
                snapshot.glowColor.withMultipliedAlpha(bottomAlpha * 0.55f),
                snapshot.glowColor.withMultipliedAlpha(bottomAlpha * 0.88f),
                snapshot.glowColor.withMultipliedAlpha(bottomAlpha),
            ),
            floatArrayOf(0f, 0.45f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, bottomStart, width, height, paint)

        val sideWidth = width * 0.42f
        drawSideGlow(canvas, snapshot.glowColor, sideAlpha, 0f, sideWidth, false, height)
        drawSideGlow(canvas, snapshot.glowColor, sideAlpha, width - sideWidth, width, true, height)

        val topHeight = height * 0.36f
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            topHeight,
            intArrayOf(snapshot.glowColor.withMultipliedAlpha(topAlpha * 0.90f), 0x00000000),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, topHeight, paint)

        drawCornerGlow(canvas, snapshot.glowColor, width, height, false, breath, intensity)
        drawCornerGlow(canvas, snapshot.glowColor, width, height, true, breath, intensity)
        paint.shader = null
    }

    private fun drawSideGlow(
        canvas: Canvas,
        color: ParticleColor,
        alpha: Float,
        left: Float,
        right: Float,
        reversed: Boolean,
        height: Float,
    ) {
        val startX = if (reversed) right else left
        val endX = if (reversed) left else right
        paint.shader = LinearGradient(
            startX,
            0f,
            endX,
            0f,
            intArrayOf(
                color.withMultipliedAlpha(alpha * 0.95f),
                color.withMultipliedAlpha(alpha * 0.50f),
                0x00000000,
            ),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(left, 0f, right, height, paint)
    }

    private fun drawCornerGlow(
        canvas: Canvas,
        color: ParticleColor,
        width: Float,
        height: Float,
        right: Boolean,
        breath: Float,
        intensity: Float,
    ) {
        val inner = (((if (right) 0.30f else 0.35f) + (if (right) 0.60f else 0.65f) * breath) * intensity)
            .coerceIn(0f, 1f)
        val middle = (((if (right) 0.18f else 0.20f) + (if (right) 0.36f else 0.40f) * breath) * intensity)
            .coerceIn(0f, 1f)
        paint.shader = RadialGradient(
            if (right) width else 0f,
            height,
            max(1f, width * 0.52f),
            intArrayOf(
                color.withMultipliedAlpha(inner),
                color.withMultipliedAlpha(middle),
                0x00000000,
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawDrawable(
        canvas: Canvas,
        shape: ParticleShape.DrawableResource,
        half: Float,
        color: ParticleColor,
        opacity: Float,
    ) {
        val drawable = drawableCache.getOrPut(shape.resourceId) {
            runCatching { context.getDrawable(shape.resourceId)?.mutate() }.getOrNull()
        }
        if (drawable == null) {
            when (shape.fallback) {
                ParticleShape.BuiltIn.Heart -> drawHeart(canvas, -half, -half, half, half, color, opacity)
                ParticleShape.BuiltIn.Circle -> drawCircle(canvas, 0f, 0f, half, color, opacity)
                ParticleShape.BuiltIn.Star -> drawStar(canvas, 0f, 0f, half, color, opacity)
            }
            return
        }
        val side = (half * 2f).toInt().coerceAtLeast(1)
        drawable.bounds = Rect(-side / 2, -side / 2, side / 2, side / 2)
        drawable.setTint(color.argb)
        drawable.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        drawable.draw(canvas)
    }

    private fun drawCircle(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: ParticleColor,
        opacity: Float,
    ) {
        paint.shader = null
        paint.color = color.withMultipliedAlpha(opacity)
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun drawHeart(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: ParticleColor,
        opacity: Float,
    ) {
        val width = right - left
        val height = bottom - top
        val path = Path().apply {
            moveTo(left + width * 0.5f, top + height * 0.92f)
            cubicTo(left + width * 0.08f, top + height * 0.66f, left, top + height * 0.35f, left + width * 0.22f, top + height * 0.18f)
            cubicTo(left + width * 0.37f, top + height * 0.06f, left + width * 0.49f, top + height * 0.18f, left + width * 0.5f, top + height * 0.29f)
            cubicTo(left + width * 0.51f, top + height * 0.18f, left + width * 0.63f, top + height * 0.06f, left + width * 0.78f, top + height * 0.18f)
            cubicTo(right, top + height * 0.35f, left + width * 0.92f, top + height * 0.66f, left + width * 0.5f, top + height * 0.92f)
            close()
        }
        paint.shader = null
        paint.color = color.withMultipliedAlpha(opacity)
        canvas.drawPath(path, paint)
    }

    private fun drawStar(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: ParticleColor,
        opacity: Float,
    ) {
        val path = Path()
        repeat(10) { index ->
            val angle = -PI / 2 + index * PI / 5
            val pointRadius = if (index % 2 == 0) radius else radius * 0.45f
            val x = centerX + cos(angle).toFloat() * pointRadius
            val y = centerY + sin(angle).toFloat() * pointRadius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        paint.shader = null
        paint.color = color.withMultipliedAlpha(opacity)
        canvas.drawPath(path, paint)
    }

    private fun fourPointPath(centerX: Float, centerY: Float, side: Float): Path {
        val half = side / 2f
        val inner = side * 0.12f
        return Path().apply {
            moveTo(centerX, centerY - half)
            lineTo(centerX + inner, centerY - inner)
            lineTo(centerX + half, centerY)
            lineTo(centerX + inner, centerY + inner)
            lineTo(centerX, centerY + half)
            lineTo(centerX - inner, centerY + inner)
            lineTo(centerX - half, centerY)
            lineTo(centerX - inner, centerY - inner)
            close()
        }
    }
}
