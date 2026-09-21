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
import android.os.Build
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Native Canvas renderer with a fixed shared atlas, independent of playback count and density. */
internal class CelebrationCanvasRenderer(
    private val useGpu: Boolean = true,
    private val gpuBatchCapacity: Int = 8192,
) : CelebrationSink {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val destination = RectF()
    private val trailColors = IntArray(4)
    private val trailStops = floatArrayOf(0f, 0.35f, 0.8f, 1f)
    private val edgeRgb = IntArray(3) { CelebrationPalette.entries[it].edge.argb and 0xFFFFFF }
    private val fillRgb = IntArray(3) { CelebrationPalette.entries[it].fill.argb and 0xFFFFFF }
    private val taper = Array(14) { count ->
        FloatArray(count) { index -> if (count > 1) (index.toFloat() / (count - 1)).pow(1.4f) else 0f }
    }
    private val leftX = FloatArray(13)
    private val leftY = FloatArray(13)
    private val rightX = FloatArray(13)
    private val rightY = FloatArray(13)

    private lateinit var activeCanvas: Canvas
    private var activeDensity = 1f
    private var frameWidth = 0f
    private var frameHeight = 0f
    private val trailPoints = FloatArray(26)
    private val gpu by lazy { CelebrationMeshRenderer(atlas.mesh, ::trail, gpuBatchCapacity) }

    fun prepare() {
        if (useGpu && Build.VERSION.SDK_INT >= 34) atlas.mesh.image.prepareToDraw()
        else atlas.prepare()
    }

    /** Production path: no frame snapshot, element, geometry or point-list allocations. */
    fun draw(canvas: Canvas, plan: CelebrationPlan, width: Float, height: Float, density: Float, time: Float) {
        activeCanvas = canvas
        activeDensity = density
        frameWidth = width
        frameHeight = height
        if (useGpu && Build.VERSION.SDK_INT >= 34 && canvas.isHardwareAccelerated) {
            gpu.draw(canvas, plan, width, height, density, time)
            return
        }
        val checkpoint = canvas.save()
        CelebrationSimulation.render(plan, width, height, density, time, trailPoints, this)
        paint.shader = null
        canvas.restoreToCount(checkpoint)
    }

    /** Legacy Canvas inspection path; also used as the hardware visual-comparison oracle. */
    fun draw(canvas: Canvas, frame: CelebrationSnapshot, density: Float) {
        activeCanvas = canvas
        activeDensity = density
        val checkpoint = canvas.save()
        begin(frame.width, frame.height, frame.backdropOpacity)
        frame.elements.forEach { element ->
            when (val geometry = element.geometry) {
                is CelebrationGeometry.Trail -> {
                    geometry.points.forEachIndexed { index, point ->
                        trailPoints[index * 2] = point.x
                        trailPoints[index * 2 + 1] = point.y
                    }
                    trail(trailPoints, geometry.points.size, geometry.width, element.palette, element.opacity)
                }
                is CelebrationGeometry.Glow -> glow(geometry.center, geometry.radius, element.palette, element.opacity)
                is CelebrationGeometry.Star -> star(geometry.center, geometry.radius, geometry.fivePoint,
                    geometry.rotation, element.palette, element.opacity)
            }
        }
        paint.shader = null
        canvas.restoreToCount(checkpoint)
    }

    override fun begin(width: Float, height: Float, backdrop: Float) {
        frameWidth = width
        frameHeight = height
        activeCanvas.clipRect(0f, 0f, width, height)
        paint.shader = null
        paint.color = Color.BLACK
        paint.alpha = (backdrop * 255).roundToInt()
        if (backdrop > 0) activeCanvas.drawRect(0f, 0f, width, height, paint)
    }

    override fun glow(center: CelebrationPoint, radius: Float, palette: CelebrationPalette, opacity: Float) {
        if (opacity <= 0) return
        val sprite = atlas.glows[palette.ordinal]
        val dimension = sprite.dimension * radius / sprite.radius
        if (outside(center, dimension / 2)) return
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
        destination.set(center.x - dimension / 2, center.y - dimension / 2,
            center.x + dimension / 2, center.y + dimension / 2)
        activeCanvas.drawBitmap(sprite.image, null, destination, paint)
    }

    override fun star(center: CelebrationPoint, radius: Float, fivePoint: Boolean, rotation: Float,
        palette: CelebrationPalette, opacity: Float) {
        if (opacity <= 0) return
        val alpha = opacity.coerceIn(0f, 1f)
        val radiusIndex = ((radius / activeDensity).roundToInt() - 2).coerceIn(0, 4)
        val sprite = atlas.stars[palette.ordinal * 10 + (if (fivePoint) 5 else 0) + radiusIndex]
        val dimension = sprite.dimension * radius / sprite.radius
        // Circumscribed square bound includes every rotated texel and its soft halo.
        if (outside(center, dimension * 0.707107f)) return
        paint.shader = null
        paint.color = Color.WHITE
        val saved = activeCanvas.save()
        activeCanvas.translate(center.x, center.y)
        activeCanvas.rotate(rotation * 180f / PI.toFloat())
        destination.set(-dimension / 2, -dimension / 2, dimension / 2, dimension / 2)
        paint.alpha = (alpha * alpha * 255).roundToInt()
        sprite.shadow?.let { activeCanvas.drawBitmap(it, null, destination, paint) }
        paint.alpha = (alpha * (2 - alpha) * 255).roundToInt()
        activeCanvas.drawBitmap(sprite.image, null, destination, paint)
        activeCanvas.restoreToCount(saved)
        paint.alpha = (alpha * 255).roundToInt()
        activeCanvas.drawCircle(center.x, center.y, max(1.2f * activeDensity, radius * 0.16f), paint)
    }

    private fun outside(center: CelebrationPoint, extent: Float): Boolean =
        center.x + extent < -1f || center.y + extent < -1f ||
            center.x - extent > frameWidth + 1f || center.y - extent > frameHeight + 1f

    override fun trail(points: FloatArray, count: Int, width: Float, palette: CelebrationPalette, opacity: Float) {
        if (opacity <= 0 || count < 2 || (opacity * 255).roundToInt() == 0) return
        // A trail fits inside its sampled centerline bounds plus half its maximum width.
        // Skip only fully clipped paths; preserve every visible tail and shader endpoint.
        var minX = points[0]
        var maxX = minX
        var minY = points[1]
        var maxY = minY
        for (index in 1 until count) {
            minX = minOf(minX, points[index * 2])
            maxX = maxOf(maxX, points[index * 2])
            minY = minOf(minY, points[index * 2 + 1])
            maxY = maxOf(maxY, points[index * 2 + 1])
        }
        val margin = width * 0.5f + 1f
        if (maxX < -margin || maxY < -margin ||
            minX > frameWidth + margin || minY > frameHeight + margin) return
        val last = (count - 1) * 2
        if (points[0] == points[last] && points[1] == points[last + 1]) return
        path.rewind()
        for (index in 0 until count) {
            val before = max(0, index - 1) * 2
            val after = minOf(count - 1, index + 1) * 2
            val dx = points[after] - points[before]
            val dy = points[after + 1] - points[before + 1]
            val length = max(0.001f, hypot(dx, dy))
            val half = width * 0.5f * taper[count][index]
            val offsetX = dy / length * half
            val offsetY = dx / length * half
            leftX[index] = points[index * 2] - offsetX
            leftY[index] = points[index * 2 + 1] + offsetY
            rightX[index] = points[index * 2] + offsetX
            rightY[index] = points[index * 2 + 1] - offsetY
            if (index == 0) path.moveTo(leftX[index], leftY[index]) else path.lineTo(leftX[index], leftY[index])
        }
        for (index in count - 1 downTo 0) path.lineTo(rightX[index], rightY[index])
        path.close()
        val edge = edgeRgb[palette.ordinal]
        trailColors[0] = edge
        trailColors[1] = (((opacity * 0.24f) * 255).roundToInt() shl 24) or edge
        trailColors[2] = (((opacity * 0.85f) * 255).roundToInt() shl 24) or edge
        trailColors[3] = ((opacity * 255).roundToInt() shl 24) or fillRgb[palette.ordinal]
        paint.alpha = 255
        // Keep the original endpoint-space shader: normalizing it via a Matrix
        // changes floating-point rasterization at a few boundary pixels.
        paint.shader = LinearGradient(points[0], points[1], points[last], points[last + 1],
            trailColors, trailStops, Shader.TileMode.CLAMP)
        activeCanvas.drawPath(path, paint)
        paint.shader = null
    }

    private class Atlas {
        val stars = CelebrationPalette.entries.flatMap { palette ->
            listOf(false, true).flatMap { fivePoint -> (2..6).map { makeSprite(palette, it.toFloat(), fivePoint) } }
        }
        val glows = CelebrationPalette.entries.map { makeSprite(it, 3f, null) }
        val mesh by lazy { CelebrationMeshAtlas(stars, glows) }
        fun prepare() {
            // Request upload before the first visible meteor; retained GPU textures are reused.
            // Repeat on playback because the system may evict them between celebrations.
            stars.forEach { sprite ->
                sprite.image.prepareToDraw()
                sprite.shadow?.prepareToDraw()
            }
            glows.forEach { it.image.prepareToDraw() }
        }
    }

    private companion object {
        val atlas by lazy { Atlas() }
        const val SPRITE_SCALE = 6f

        fun makeSprite(palette: CelebrationPalette, radius: Float, fivePoint: Boolean?): CelebrationSprite {
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
            return CelebrationSprite(image, shadow, radius, dimension)
        }
    }
}
