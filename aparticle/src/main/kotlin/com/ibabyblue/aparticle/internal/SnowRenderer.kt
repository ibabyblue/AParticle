package com.ibabyblue.aparticle.internal

import android.graphics.*
import android.view.Choreographer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.ibabyblue.aparticle.R
import com.ibabyblue.aparticle.ParticleEffect
import kotlin.math.*
import kotlin.random.Random

@Composable
internal fun SnowCanvas(effect: ParticleEffect.Snow, modifier: Modifier) {
    val perspective = effect is ParticleEffect.PerspectiveSnow
    val resources = LocalContext.current.resources
    key(resources, perspective) {
        val atlas by produceState<Bitmap?>(null) {
            value = withContext(Dispatchers.Default) { SnowAtlas.load(resources, perspective) }
        }
        atlas?.let { Spacer(modifier.graphicsLayer().then(SnowElement(it, effect))) }
    }
}

internal object SnowAtlas {
    private var shared: Bitmap? = null
    private var perspectiveAtlas: Bitmap? = null
    @Synchronized fun load(resources: android.content.res.Resources, perspective: Boolean): Bitmap {
        val base = shared ?: requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.aparticle_snow_particles)).also { shared = it }
        if (!perspective || android.os.Build.VERSION.SDK_INT < 29) return base
        return perspectiveAtlas ?: createPerspectiveAtlas(base).also { perspectiveAtlas = it }
    }

    // Precompute the same six arms, branches and two strokes used by the iOS renderer.
    // Each cell varies radius and morph; position, alpha, angle and radius remain continuous.
    private fun createPerspectiveAtlas(base: Bitmap): Bitmap {
        val atlas = Bitmap.createBitmap(1024, 1120, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(atlas)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val dot = Rect(0, 0, 96, 96)
        canvas.drawBitmap(base, dot, dot, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val path = Path()
        for (radius in 1..16) for (column in 0 until 16) {
            val morph = column / 15f
            val arm = radius * (.55f + .9f * morph)
            val joint = arm * .62f
            val dx = .796084f * radius * .4f * morph
            val dy = .605186f * radius * .4f * morph
            path.rewind()
            for (i in 0 until 6) {
                val angle = i * 1.0471976f
                val u = cos(angle)
                val v = sin(angle)
                path.moveTo(0f, 0f)
                path.lineTo(arm * u, arm * v)
                path.moveTo((joint + dx) * u + dy * v, (joint + dx) * v - dy * u)
                path.lineTo(joint * u, joint * v)
                path.lineTo((joint + dx) * u - dy * v, (joint + dx) * v + dy * u)
            }
            canvas.save()
            canvas.translate(column * 64f + 32, 96f + (radius - 1) * 64 + 32)
            val scale = 32f / (1.45f * radius + 2.5f)
            canvas.scale(scale, scale)
            paint.color = 0x1A666666
            paint.strokeWidth = 2.5f + 1.1f * morph
            canvas.drawPath(path, paint)
            paint.color = Color.WHITE
            paint.strokeWidth = 1.25f + 1.05f * morph
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        return atlas
    }
}

private data class SnowElement(val atlas: Bitmap, val effect: ParticleEffect.Snow) : ModifierNodeElement<SnowNode>() {
    override fun create() = SnowNode(SnowField(atlas, effect))
    override fun update(node: SnowNode) { node.field = SnowField(atlas, effect) }
}

/** One frame callback per visible field. Only invalidate drawing, including on high-refresh displays. */
private class SnowNode(var field: SnowField) : Modifier.Node(), DrawModifierNode, Choreographer.FrameCallback {
    private var tick = 0L
    private var nextFrame = 0L
    override fun onAttach() { Choreographer.getInstance().postFrameCallback(this) }
    override fun onDetach() { Choreographer.getInstance().removeFrameCallback(this) }
    override fun doFrame(frameTimeNanos: Long) {
        if (!isAttached) return
        // Match the iOS display link. Keep elapsed time correct on 90/120 Hz panels.
        if (frameTimeNanos + 500_000L >= nextFrame) {
            tick = frameTimeNanos
            val period = 16_666_667L
            nextFrame = if (nextFrame == 0L || frameTimeNanos - nextFrame > period) frameTimeNanos + period
                else nextFrame + period
            invalidateDraw()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun ContentDrawScope.draw() {
        if (tick > 0) drawIntoCanvas { field.draw(it.nativeCanvas, size.width, size.height, density, tick) }
    }
}

private class Flake {
    var x = 0f; var y = 0f
    var radius = 0f; var speed = 0f; var alpha = 0f
    var phase = 0f; var angle = 0f
    var depth = 0f; var depthSpeed = 0f; var drift = 0f
    var sway = 0f; var frequency = 0f; var spread = 0f; var growth = 0f
    var crystal = false
}

internal class SnowField(private val atlas: Bitmap, effect: ParticleEffect.Snow) {
    private val configuration = effect as? ParticleEffect.PerspectiveSnow
    private val perspective = configuration != null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Array(22) { Rect(it % 11 * 96, it / 11 * 96, (it % 11 + 1) * 96, (it / 11 + 1) * 96) }
    private val destination = RectF()
    private val crystalLines = FloatArray(6 * 3 * 4)
    private val mesh = if (configuration != null && android.os.Build.VERSION.SDK_INT >= 29) SnowMesh(atlas, configuration.amount) else null
    private val particles = Array(configuration?.amount ?: 118) { Flake() }
    private val random = Random(Random.nextLong())
    private var width = 0f
    private var height = 0f
    private var started = 0L
    private var previous = 0L
    private var clock = 0f

    private fun sample(low: Float, high: Float) = low + random.nextFloat() * (high - low)

    fun draw(canvas: android.graphics.Canvas, w: Float, h: Float, density: Float, tick: Long) {
        if (w <= 0f || h <= 0f) return
        val logicalW = w / density
        val logicalH = h / density
        if (width != logicalW || height != logicalH) {
            width = logicalW
            height = logicalH
            for (i in particles.indices) {
                if (perspective) seedPerspective(i, true) else seedTwinkling(i)
            }
        }
        if (started == 0L) { started = tick; previous = tick }
        val delta = ((tick - previous) * 1e-9f).coerceIn(0f, .05f)
        previous = tick
        canvas.save()
        canvas.scale(density, density)
        if (perspective) drawPerspective(canvas, delta) else drawTwinkling(canvas, (tick - started) * 1e-9f)
        canvas.restore()
    }

    private fun seedTwinkling(i: Int) {
        val p = particles[i]
        p.phase = sample(0f, 6.283185f)
        p.x = random.nextFloat()
        p.y = random.nextFloat()
        when {
            i < 20 -> { p.radius = sample(17f, 53f); p.speed = sample(42f, 88f); p.alpha = sample(.05f, .18f) }
            i < 100 -> { p.radius = sample(4f, 18f); p.speed = sample(65f, 136f); p.alpha = sample(.55f, .96f) }
            else -> {
                val band = (i - 100) / 6
                p.x = if (band == 1) sample(.07f, .26f) else sample(.74f, .95f)
                p.y = when (band) { 0 -> .13f; 1 -> .32f; else -> .55f } + sample(0f, if (band == 1) .35f else .21f)
                p.radius = sample(9f, 17f); p.speed = sample(3f, 13f); p.alpha = sample(.72f, .98f)
            }
        }
    }

    private fun sprite(c: android.graphics.Canvas, index: Int, x: Float, y: Float, r: Float, alpha: Float, angle: Float = 0f) {
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        destination.set(x - r, y - r, x + r, y + r)
        if (angle != 0f) { c.save(); c.rotate(angle * 57.29578f, x, y) }
        c.drawBitmap(atlas, source[index], destination, paint)
        if (angle != 0f) c.restore()
    }

    private fun column(x: Float) = (.5f + (x - .5f) * .8f) * width

    private fun drawTwinkling(c: android.graphics.Canvas, time: Float) {
        val scale = min(width / 626, height / 1350)
        for (i in 0 until 100) {
            val p = particles[i]
            val r = p.radius * scale
            val travel = height + 2 * r
            sprite(c, if (i < 20) 1 else 3 + i % 4, p.x * width + sin(time + p.phase) * 11 * scale,
                (p.y * travel + time * p.speed * scale) % travel - r, r,
                p.alpha * (.88f + .12f * sin(time * 2 + p.phase)), p.phase + time * .28f)
        }
        val phase = time % 5.628f
        val frame = phase / .067f
        for (star in starTracks) {
            if (frame <= star.onset || frame >= star.end) continue
            val envelope = minOf(1f, (frame - star.onset) / 11, (star.end - frame) / 13)
            val pulse = .5f + .5f * cos((frame - star.crest) * .6544985f)
            val x = column(star.x / 626f)
            val y = star.y / 1350f * height + (frame - 34) * .64f * scale
            val r = star.radius * scale
            sprite(c, 17 + star.color, x, y, r * (.91f + .09f * pulse), envelope * (.15f + .59f * pulse))
            sprite(c, 7 + star.color, x, y, r, envelope * (.47f + .38f * pulse), star.crest + phase * .48f)
        }
        val opacity = minOf(1f, (phase - .92f) / .65f, (4.92f - phase) / .85f)
        if (opacity <= 0) return
        for (i in 100 until 118) {
            val p = particles[i]
            sprite(c, 12 + ((i - 100) / 6 + (i - 100) % 6) % 5,
                column(p.x) + sin(phase + p.phase) * 12 * scale, p.y * height + (phase - 2.3f) * p.speed * scale,
                p.radius * scale, opacity * p.alpha, p.phase + phase * .48f)
        }
    }

    private fun seedPerspective(i: Int, initial: Boolean) {
        val front = random.nextFloat() < .035f
        val near = front || (random.nextFloat() < .22f)
        val p = particles[i]
        p.x = if (front) ((if (random.nextBoolean()) .16f else .84f) + sample(-.08f, .08f)) * width else sample(0f, width)
        p.y = sample(0f, height)
        p.depth = if (initial) random.nextFloat() else sample(.02f, .14f)
        p.depthSpeed = when { front -> sample(.28f,.35f); near -> sample(.2f,.27f); else -> sample(.15f,.21f) }
        p.speed = when { front -> sample(33f,45f); near -> sample(26f,39f); else -> sample(18f,30f) }
        p.radius = when { front -> sample(2.2f,2.7f); near -> sample(1.75f,2.35f); else -> sample(1f,1.5f) }
        p.angle = sample(0f,6.283185f); p.phase = sample(0f,6.283185f)
        p.drift = sample(-2.5f,2.5f); p.sway = sample(2f,9f); p.frequency = sample(.4f,1f)
        p.alpha = sample(.9f,1f)
        p.spread = if (front) .32f else if (near) .22f else .15f
        p.growth = if (front) 5.2f else if (near) 4f else 3.2f
        p.crystal = front || (near && random.nextFloat() < .2f)
    }

    private fun drawPerspective(c: android.graphics.Canvas, delta: Float) {
        clock += delta
        val step = delta * configuration!!.speed
        for (i in particles.indices) {
            val p = particles[i]
            p.depth += p.depthSpeed * step
            p.y += p.speed * (.55f + 2.1f * p.depth) * step
            p.x += (p.drift + (p.x - width / 2) * p.spread * p.depth) * step
            p.angle += delta * .12f
            if (p.depth >= 1 || p.y > height + 24 || p.x < -28 || p.x > width + 28) seedPerspective(i, false)
            val x = p.x + sin(clock * p.frequency + p.phase) * p.sway
            val focused = x >= .06f * width && x < .94f * width && p.y >= .16f * height && p.y < .94f * height
            val r = p.radius * (.85f + p.growth * p.depth * p.depth)
            val opacity = p.alpha * (.82f + .18f * p.depth) * if (focused) .97f else 1f
            val t = if (p.crystal) ((p.depth - .25f) / .65f).coerceIn(0f, 1f) else 0f
            val morph = t * t * (3 - 2 * t)
            if (morph < 1) {
                val radius = 1.5f * r * (1 - .68f * morph)
                val alpha = opacity * (1 - .25f * morph)
                if (mesh != null) mesh.add(x, p.y, radius, alpha, 0f, 0, 0, 96)
                else sprite(c, 0, x, p.y, radius, alpha)
            }
            if (morph > .02f) {
                if (mesh != null) {
                    val cell = r.roundToInt().coerceIn(1, 16)
                    val column = (morph * 15).roundToInt()
                    val extent = (1.45f * cell + 2.5f) * r / cell
                    mesh.add(x, p.y, extent, opacity * morph, p.angle, column * 64, 96 + (cell - 1) * 64, 64)
                } else crystal(c, x, p.y, r, morph, opacity * morph, p.angle)
            }
        }
        mesh?.flush(c)
    }

    private fun crystal(c: android.graphics.Canvas, x: Float, y: Float, radius: Float, morph: Float, alpha: Float, angle: Float) {
        val arm = radius * (.55f + .9f * morph)
        val joint = arm * .62f
        val dx = .796084f * radius * .4f * morph
        val dy = .605186f * radius * .4f * morph
        var ux = cos(angle)
        var uy = sin(angle)
        for (armIndex in 0 until 6) {
            val base = armIndex * 12
            val jointX = x + joint * ux
            val jointY = y + joint * uy
            crystalLines[base] = x
            crystalLines[base + 1] = y
            crystalLines[base + 2] = x + arm * ux
            crystalLines[base + 3] = y + arm * uy
            crystalLines[base + 4] = jointX + dx * ux + dy * uy
            crystalLines[base + 5] = jointY + dx * uy - dy * ux
            crystalLines[base + 6] = jointX
            crystalLines[base + 7] = jointY
            crystalLines[base + 8] = jointX
            crystalLines[base + 9] = jointY
            crystalLines[base + 10] = jointX + dx * ux - dy * uy
            crystalLines[base + 11] = jointY + dx * uy + dy * ux
            val nextX = .5f * ux - .8660254f * uy
            uy = .8660254f * ux + .5f * uy
            ux = nextX
        }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.GRAY; paint.alpha = (alpha * 25.5f).toInt(); paint.strokeWidth = 2.5f + 1.1f * morph
        c.drawLines(crystalLines, paint)
        paint.color = Color.WHITE; paint.alpha = (alpha * 255).toInt(); paint.strokeWidth = 1.25f + 1.05f * morph
        c.drawLines(crystalLines, paint)
        paint.style = Paint.Style.FILL
    }
}

/** One ordered texture mesh for the entire field, including morphing crystals. */
private class SnowMesh(atlas: Bitmap, amount: Int) {
    private val capacity = amount * 2
    private val positions = FloatArray(capacity * 8)
    private val colors = IntArray(capacity * 4)
    private val uv = FloatArray(capacity * 8)
    private val indices = ShortArray(capacity * 6) {
        (it / 6 * 4 + when (it % 6) { 1 -> 1; 2, 4 -> 2; 5 -> 3; else -> 0 }).toShort()
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private var count = 0

    fun add(x: Float, y: Float, radius: Float, alpha: Float, angle: Float, left: Int, top: Int, size: Int) {
        val i = count * 8
        val u = if (angle == 0f) radius else cos(angle) * radius
        val v = if (angle == 0f) 0f else sin(angle) * radius
        positions[i] = x - u + v
        positions[i + 1] = y - u - v
        positions[i + 2] = x + u + v
        positions[i + 3] = y - u + v
        positions[i + 4] = x + u - v
        positions[i + 5] = y + u + v
        positions[i + 6] = x - u - v
        positions[i + 7] = y + u - v
        uv[i] = left.toFloat()
        uv[i + 1] = top.toFloat()
        uv[i + 2] = (left + size).toFloat()
        uv[i + 3] = top.toFloat()
        uv[i + 4] = (left + size).toFloat()
        uv[i + 5] = (top + size).toFloat()
        uv[i + 6] = left.toFloat()
        uv[i + 7] = (top + size).toFloat()
        colors.fill(Color.argb((alpha * 255).toInt(), 255, 255, 255), count * 4, count * 4 + 4)
        count++
    }

    fun flush(canvas: android.graphics.Canvas) {
        if (count == 0) return
        canvas.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLES, count * 8, positions, 0,
            uv, 0, colors, 0, indices, 0, count * 6, paint)
        count = 0
    }
}

private data class Star(val x: Int, val y: Int, val radius: Int, val color: Int, val onset: Int, val crest: Int, val end: Int)
private val starTracks = arrayOf(
    Star(505, 193, 25, 0, 16, 35, 71),
    Star(557, 195, 22, 3, 16, 43, 75),
    Star(576, 188, 28, 1, 15, 29, 65),
    Star(523, 297, 31, 0, 11, 42, 68),
    Star(477, 384, 23, 4, 13, 48, 76),
    Star(567, 392, 26, 2, 13, 30, 73),
    Star(504, 445, 31, 0, 18, 42, 69),
    Star(140, 441, 20, 4, 23, 47, 77),
    Star(162, 521, 32, 2, 18, 45, 74),
    Star(76, 597, 26, 2, 15, 41, 70),
    Star(68, 667, 24, 1, 15, 45, 72),
    Star(103, 712, 31, 0, 14, 46, 65),
    Star(112, 797, 31, 3, 17, 43, 69),
    Star(158, 934, 23, 3, 14, 32, 63),
    Star(486, 625, 21, 2, 18, 49, 75),
    Star(551, 750, 27, 0, 20, 35, 74),
    Star(465, 830, 23, 4, 20, 32, 70),
)
