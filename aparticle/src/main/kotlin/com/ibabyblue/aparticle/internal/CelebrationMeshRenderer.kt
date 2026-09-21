package com.ibabyblue.aparticle.internal

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Mesh
import android.graphics.MeshSpecification
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class CelebrationSprite(val image: Bitmap, val shadow: Bitmap?, val radius: Float, val dimension: Float)

/** Immutable original artwork, packed once with transparent gutters. */
internal class CelebrationMeshAtlas(stars: List<CelebrationSprite>, glows: List<CelebrationSprite>) {
    data class Sprite(val image: Rect, val shadow: Rect?, val radius: Float, val dimension: Float)
    val image: Bitmap
    val stars: List<Sprite>
    val glows: List<Sprite>
    init {
        var x = 24; var y = 2; var row = 20
        fun region(bitmap: Bitmap): Rect {
            if (x + bitmap.width + 2 > 2048) { x = 2; y += row + 4; row = 0 }
            return Rect(x, y, x + bitmap.width, y + bitmap.height).also {
                x += bitmap.width + 4; row = max(row, bitmap.height)
            }
        }
        val artwork = stars + glows
        val sprites = artwork.map { Sprite(region(it.image), it.shadow?.let(::region), it.radius, it.dimension) }
        image = Bitmap.createBitmap(2048, y + row + 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawRect(2f, 2f, 18f, 18f, Paint().apply { color = Color.WHITE })
        artwork.forEachIndexed { index, sprite ->
            val r = sprites[index]
            canvas.drawBitmap(sprite.image, r.image.left.toFloat(), r.image.top.toFloat(), null)
            sprite.shadow?.let { bitmap ->
                val bounds = checkNotNull(r.shadow)
                canvas.drawBitmap(bitmap, bounds.left.toFloat(), bounds.top.toFloat(), null)
            }
        }
        this.stars = sprites.take(stars.size)
        this.glows = sprites.drop(stars.size)
    }
}

/** Painter-ordered GPU primitives. Only raster edge coverage differs from Canvas paths/circles. */
@TargetApi(34)
internal class CelebrationMeshRenderer(private val atlas: CelebrationMeshAtlas,
    private val meteor: (FloatArray, Int, Float, CelebrationPalette, Float) -> Unit,
    private val capacity: Int = 8192,
) : CelebrationSink {
    init { require(capacity in 1..8192) }
    private val vertexData = FloatArray(capacity * 4 * STRIDE / 4)
    private val vertices = FloatBuffer.wrap(vertexData)
    private var vertexFloats = 0
    private val indices = ByteBuffer.allocateDirect(capacity * 6 * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
        for (q in 0 until capacity) {
            val v = q * 4
            put(v.toShort()); put((v + 1).toShort()); put((v + 2).toShort())
            put(v.toShort()); put((v + 2).toShort()); put((v + 3).toShort())
        }
        rewind()
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        color = Color.WHITE
        shader = BitmapShader(atlas.image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val backdropPaint = Paint()
    private val bounds = RectF()
    private var quads = 0
    private lateinit var canvas: Canvas
    private var density = 1f
    private var width = 0f
    private var height = 0f
    private val samples = FloatArray(26)
    private val leftX = FloatArray(13)
    private val leftY = FloatArray(13)
    private val rightX = FloatArray(13)
    private val rightY = FloatArray(13)
    private val padLeftX = FloatArray(13)
    private val padLeftY = FloatArray(13)
    private val padRightX = FloatArray(13)
    private val padRightY = FloatArray(13)
    private val taper = Array(14) { n -> FloatArray(n) { i -> if (n > 1) (i.toFloat() / (n - 1)).pow(1.4f) else 0f } }

    fun draw(canvas: Canvas, plan: CelebrationPlan, width: Float, height: Float, density: Float, time: Float) {
        this.canvas = canvas; this.density = density
        quads = 0; vertexFloats = 0; vertices.clear()
        val save = canvas.save()
        try {
            CelebrationSimulation.render(plan, width, height, density, time, samples, this)
            flush()
        } finally { canvas.restoreToCount(save) }
    }

    override fun begin(width: Float, height: Float, backdrop: Float) {
        this.width = width; this.height = height
        bounds.set(0f, 0f, width, height)
        canvas.clipRect(bounds)
        if (backdrop > 0) {
            backdropPaint.color = Color.BLACK
            backdropPaint.alpha = (backdrop * 255).roundToInt()
            canvas.drawRect(bounds, backdropPaint)
        }
    }

    private fun vertex(x: Float, y: Float, u: Float, v: Float,
        left: Float, right: Float, start: Float, end: Float,
        t: Float, kind: Float, a1: Float, a2: Float, palette: Float, alpha: Float) {
        val offset = vertexFloats
        vertexData[offset] = x; vertexData[offset + 1] = y
        vertexData[offset + 2] = u; vertexData[offset + 3] = v
        vertexData[offset + 4] = left; vertexData[offset + 5] = right
        vertexData[offset + 6] = start; vertexData[offset + 7] = end
        vertexData[offset + 8] = t; vertexData[offset + 9] = kind
        vertexData[offset + 10] = a1; vertexData[offset + 11] = a2
        vertexData[offset + 12] = palette; vertexData[offset + 13] = alpha
        vertexFloats = offset + 14
    }

    private fun reserve() { if (quads == capacity) flush() }
    private fun outside(x: Float, y: Float, extent: Float) =
        x + extent < -1 || y + extent < -1 || x - extent > width + 1 || y - extent > height + 1

    private fun sprite(region: Rect, center: CelebrationPoint, dimension: Float, rotation: Float, alpha: Int) {
        if (alpha == 0) return
        reserve()
        // Canvas.rotate receives degrees, so retain that Float conversion before taking sin/cos.
        val angle = (rotation * 180f / PI.toFloat()).toDouble() * PI / 180.0
        val c = cos(angle).toFloat(); val s = sin(angle).toFloat(); val half = dimension / 2
        for (corner in 0..3) {
            val dx = if (corner == 0 || corner == 3) -half else half
            val dy = if (corner < 2) -half else half
            vertex(center.x + dx * c - dy * s, center.y + dx * s + dy * c,
                (if (corner == 0 || corner == 3) region.left else region.right).toFloat(),
                (if (corner < 2) region.top else region.bottom).toFloat(),
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, alpha / 255f)
        }
        quads++
    }

    override fun glow(center: CelebrationPoint, radius: Float, palette: CelebrationPalette, opacity: Float) {
        if (opacity <= 0) return
        val sprite = atlas.glows[palette.ordinal]
        val dimension = sprite.dimension * radius / sprite.radius
        if (outside(center.x, center.y, dimension / 2)) return
        sprite(sprite.image, center, dimension, 0f, (opacity.coerceIn(0f, 1f) * 255).roundToInt())
    }

    override fun star(center: CelebrationPoint, radius: Float, fivePoint: Boolean, rotation: Float,
        palette: CelebrationPalette, opacity: Float) {
        if (opacity <= 0) return
        val alpha = opacity.coerceIn(0f, 1f)
        val radiusIndex = ((radius / density).roundToInt() - 2).coerceIn(0, 4)
        val sprite = atlas.stars[palette.ordinal * 10 + (if (fivePoint) 5 else 0) + radiusIndex]
        val dimension = sprite.dimension * radius / sprite.radius
        if (outside(center.x, center.y, dimension * 0.707107f)) return
        sprite.shadow?.let { sprite(it, center, dimension, rotation, (alpha * alpha * 255).roundToInt()) }
        sprite(sprite.image, center, dimension, rotation, (alpha * (2 - alpha) * 255).roundToInt())
        val coreRadius = max(1.2f * density, radius * 0.16f)
        val extent = coreRadius + 1
        reserve()
        for (corner in 0..3) {
            val dx = if (corner == 0 || corner == 3) -extent else extent
            val dy = if (corner < 2) -extent else extent
            vertex(center.x + dx, center.y + dy, 8f, 8f, dx, dy, coreRadius, 0f,
                0f, 2f, 0f, 0f, 0f, (alpha * 255).roundToInt() / 255f)
        }
        quads++
    }

    override fun trail(points: FloatArray, count: Int, width: Float, palette: CelebrationPalette, opacity: Float) {
        if (count < 2 || opacity <= 0 || (opacity * 255).roundToInt() == 0) return
        if (count != 6) {
            flush()
            meteor(points, count, width, palette, opacity)
            return
        }
        val last = (count - 1) * 2
        val gradientX = points[last] - points[0]; val gradientY = points[last + 1] - points[1]
        val lengthSquared = gradientX * gradientX + gradientY * gradientY
        if (lengthSquared == 0f) return
        // Keep every original sample and taper width. Padding extends only the GPU
        // raster quad; the shader clips back to the original four segment edges.
        for (i in 0 until count) {
            val before = max(0, i - 1) * 2; val after = min(count - 1, i + 1) * 2
            val dx = points[after] - points[before]; val dy = points[after + 1] - points[before + 1]
            val length = max(0.001f, hypot(dx, dy))
            val nx = -dy / length; val ny = dx / length
            val half = width * 0.5f * taper[count][i]
            leftX[i] = points[i * 2] + nx * half; leftY[i] = points[i * 2 + 1] + ny * half
            rightX[i] = points[i * 2] - nx * half; rightY[i] = points[i * 2 + 1] - ny * half
            val tangent = if (i == 0) -1f else if (i == count - 1) 1f else 0f
            val px = points[i * 2] + dx / length * tangent
            val py = points[i * 2 + 1] + dy / length * tangent
            padLeftX[i] = px + nx * (half + 1.5f); padLeftY[i] = py + ny * (half + 1.5f)
            padRightX[i] = px - nx * (half + 1.5f); padRightY[i] = py - ny * (half + 1.5f)
        }
        val a1 = (opacity * 0.24f * 255).roundToInt() / 255f
        val a2 = (opacity * 0.85f * 255).roundToInt() / 255f
        val a3 = (opacity * 255).roundToInt() / 255f
        // Shared padded vertices keep adjacent segments joined without overlapping
        // their interiors. Only exterior edges receive coverage; no internal seams.
        for (i in 0 until count - 1) {
            val j = i + 1
            val lx = leftX[j] - leftX[i]; val ly = leftY[j] - leftY[i]
            val rx = rightX[j] - rightX[i]; val ry = rightY[j] - rightY[i]
            val ll = max(0.001f, hypot(lx, ly)); val rl = max(0.001f, hypot(rx, ry))
            val tx = points[j * 2] - points[i * 2]; val ty = points[j * 2 + 1] - points[i * 2 + 1]
            val tl = hypot(tx, ty)
            if (tl < 0.001f) continue
            reserve()
            for (corner in 0..3) {
                val index = if (corner == 0 || corner == 3) i else j
                val x = if (corner < 2) padLeftX[index] else padRightX[index]
                val y = if (corner < 2) padLeftY[index] else padRightY[index]
                val dLeft = ((x - leftX[i]) * ly - (y - leftY[i]) * lx) / ll
                val dRight = (-(x - rightX[i]) * ry + (y - rightY[i]) * rx) / rl
                val dStart = if (i == 0) ((x - points[0]) * tx + (y - points[1]) * ty) / tl else 1000f
                val dEnd = if (j == count - 1) (-(x - points[last]) * tx - (y - points[last + 1]) * ty) / tl else 1000f
                val t = ((x - points[0]) * gradientX + (y - points[1]) * gradientY) / lengthSquared
                vertex(x, y, 8f, 8f, dLeft, dRight, dStart, dEnd, t, 1f, a1, a2, palette.ordinal.toFloat(), a3)
            }
            quads++
        }
    }

    private fun flush() {
        if (quads == 0) return
        vertices.position(0); vertices.limit(vertexFloats); indices.position(0); indices.limit(quads * 6)
        // Mesh reads indexBuffer.capacity(), not limit(); a slice prevents stale indices
        // from a larger preceding frame from referencing vertices outside this batch.
        val mesh = Mesh(specification, Mesh.TRIANGLES, vertices, quads * 4, indices.slice(), bounds)
        canvas.drawMesh(mesh, BlendMode.MODULATE, paint)
        vertices.clear(); quads = 0; vertexFloats = 0
    }

    companion object {
        private const val STRIDE = 14 * 4
        private val specification by lazy {
            val edge = CelebrationPalette.entries.map { p ->
                val c = p.edge.argb
                "float3(${Color.red(c) / 255f}, ${Color.green(c) / 255f}, ${Color.blue(c) / 255f})"
            }
            val fill = CelebrationPalette.entries.map { p ->
                val c = p.fill.argb
                "float3(${Color.red(c) / 255f}, ${Color.green(c) / 255f}, ${Color.blue(c) / 255f})"
            }
            MeshSpecification.make(arrayOf(
                MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 0, "position"),
                MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 8, "uv"),
                MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT4, 16, "edges"),
                MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT4, 32, "parameters"),
                MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 48, "paletteAlpha")), STRIDE,
                arrayOf(MeshSpecification.Varying(MeshSpecification.TYPE_FLOAT2, "uv"),
                    MeshSpecification.Varying(MeshSpecification.TYPE_FLOAT4, "edges"),
                    MeshSpecification.Varying(MeshSpecification.TYPE_FLOAT4, "parameters"),
                    MeshSpecification.Varying(MeshSpecification.TYPE_FLOAT2, "paletteAlpha")),
                """Varyings main(const Attributes a) {
                    Varyings v; v.position = a.position; v.uv = a.uv; v.edges = a.edges;
                    v.parameters = a.parameters; v.paletteAlpha = a.paletteAlpha; return v;
                }""", """float2 main(const Varyings v, out half4 color) {
                    float alpha = v.paletteAlpha.y;
                    float3 rgb = float3(1);
                    if (v.parameters.y > 1.5) {
                        alpha *= clamp(v.edges.z - length(v.edges.xy) + 0.5, 0.0, 1.0);
                    } else if (v.parameters.y > 0.5) {
                        float t = clamp(v.parameters.x, 0.0, 1.0);
                        float coverage = clamp(min(v.edges.x + 0.5, 1.0) + min(v.edges.y + 0.5, 1.0) - 1.0, 0.0, 1.0);
                        coverage *= clamp(v.edges.z + 0.5, 0.0, 1.0) * clamp(v.edges.w + 0.5, 0.0, 1.0);
                        float3 edge = v.paletteAlpha.x < 0.5 ? ${edge[0]} : (v.paletteAlpha.x < 1.5 ? ${edge[1]} : ${edge[2]});
                        float3 fill = v.paletteAlpha.x < 0.5 ? ${fill[0]} : (v.paletteAlpha.x < 1.5 ? ${fill[1]} : ${fill[2]});
                        if (t < 0.35) alpha = v.parameters.z * t / 0.35;
                        else if (t < 0.8) alpha = mix(v.parameters.z, v.parameters.w, (t - 0.35) / 0.45);
                        else alpha = mix(v.parameters.w, alpha, (t - 0.8) / 0.2);
                        rgb = mix(edge, fill, clamp((t - 0.8) / 0.2, 0.0, 1.0));
                        alpha *= coverage;
                    }
                    color = half4(rgb * alpha, alpha);
                    return v.uv;
                }""")
        }
    }
}
