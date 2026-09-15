package com.ibabyblue.aparticle.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.ibabyblue.aparticle.ParticleColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AtmosphereRenderingTest {
    private val glow = ParticleColor(220f / 255f, 70f / 255f, 105f / 255f, 0.4f)
    private val scene = AtmosphereSnapshot(390f, 844f, glow, 1f, 0f, emptyList(), emptyList())

    @Test
    fun glowAndBubbleBrightnessDoNotDependOnLastSparkleOrPreviousFrame() {
        val renderer = ParticleCanvasRenderer(RuntimeEnvironment.getApplication())
        val clean = render(renderer, scene)
        val withDimSparkle = scene.copy(sparkles = listOf(
            SparkleSnapshot(195f, 100f, 2f, 0.25f, ParticleColor.White, SparkleKind.Dot),
        ))
        val first = render(renderer, withDimSparkle)
        val second = render(renderer, withDimSparkle)
        assertEquals("Bottom glow must ignore sparkle paint alpha", clean.getPixel(195, 830), first.getPixel(195, 830))
        assertEquals("Consecutive identical frames", first.getPixel(195, 830), second.getPixel(195, 830))
        val bubbleScene = scene.copy(bubbles = listOf(BubbleSnapshot(195f, 260f, 50f, 0.2f)))
        val reused = render(renderer, bubbleScene)
        val fresh = render(ParticleCanvasRenderer(RuntimeEnvironment.getApplication()), bubbleScene)
        assertEquals("Bubble must ignore previous-frame paint alpha", fresh.getPixel(242, 260), reused.getPixel(242, 260))
    }

    @Test
    fun sparkleHaloFadesInsteadOfDrawingAFlatTranslucentDisc() {
        val sparkleScene = scene.copy(glowIntensity = 0f, sparkles = listOf(
            SparkleSnapshot(195f, 260f, 3f, 1f, ParticleColor.White, SparkleKind.Dot),
        ))
        val bitmap = render(ParticleCanvasRenderer(RuntimeEnvironment.getApplication()), sparkleScene)
        val near = Color.alpha(bitmap.getPixel(199, 260))
        val far = Color.alpha(bitmap.getPixel(203, 260))
        assertTrue("Soft halo must fade with distance: near=$near far=$far", near > far + 3)
    }

    @Test
    fun gradientFadesKeepRoseHueAndBubbleInteriorStaysTransparent() {
        val renderer = ParticleCanvasRenderer(RuntimeEnvironment.getApplication())
        val bitmap = render(renderer, scene)
        val pixel = bitmap.getPixel(30, 320)
        // Compare premultiplied channels: low-alpha ARGB_8888 unpremultiplication
        // magnifies a one-level storage rounding error into several RGB levels.
        val alpha = Color.alpha(pixel) / 255f
        assertEquals("Fading glow keeps source red", 220f * alpha, Color.red(pixel) * alpha, 1f)
        assertEquals("Fading glow keeps source green", 70f * alpha, Color.green(pixel) * alpha, 1f)
        val bubbleScene = scene.copy(bubbles = listOf(BubbleSnapshot(195f, 330f, 50f, 1f)))
        val bubbleBitmap = render(renderer, bubbleScene)
        assertEquals("Inner 85 percent stays transparent", bitmap.getPixel(235, 330), bubbleBitmap.getPixel(235, 330))
        assertTrue("Only the outer boundary lights up", Color.alpha(bubbleBitmap.getPixel(243, 330)) > Color.alpha(bitmap.getPixel(243, 330)))
        assertEquals("Zero intensity hides bubbles too", 0, Color.alpha(render(renderer, bubbleScene.copy(glowIntensity = 0f)).getPixel(243, 330)))
    }

    @Test
    fun tintedSparkleKeepsPaletteAlphaAndDoesNotTintTheNextGlow() {
        val renderer = ParticleCanvasRenderer(RuntimeEnvironment.getApplication())
        val sparkle = SparkleSnapshot(195f, 330f, 3f, 0.5f, ParticleColor(0.2f, 0.7f, 1f, 0.5f), SparkleKind.Dot)
        val bitmap = render(renderer, scene.copy(glowIntensity = 0f, sparkles = listOf(sparkle)))
        assertEquals(64f, Color.alpha(bitmap.getPixel(195, 330)).toFloat(), 2f)
        val glowAfterSparkle = render(renderer, scene)
        val freshGlow = render(ParticleCanvasRenderer(RuntimeEnvironment.getApplication()), scene)
        assertEquals(freshGlow.getPixel(30, 320), glowAfterSparkle.getPixel(30, 320))
    }

    private fun render(renderer: ParticleCanvasRenderer, snapshot: AtmosphereSnapshot): Bitmap =
        Bitmap.createBitmap(390, 844, Bitmap.Config.ARGB_8888).also {
            renderer.drawAtmosphere(Canvas(it), snapshot)
        }
}
