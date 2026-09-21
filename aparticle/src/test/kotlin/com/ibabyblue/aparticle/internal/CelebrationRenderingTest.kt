package com.ibabyblue.aparticle.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CelebrationRenderingTest {
    private fun render(renderer: CelebrationCanvasRenderer, frame: CelebrationSnapshot): Bitmap =
        Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { renderer.draw(Canvas(it), frame, 1f) }

    @Test fun backdropIsClippedAndExpiredFrameIsTransparent() {
        val renderer = CelebrationCanvasRenderer()
        val image = render(renderer, CelebrationSnapshot(120f, 140f, 0.25f))
        assertEquals(64, Color.alpha(image.getPixel(10, 10)))
        assertEquals(0, Color.alpha(image.getPixel(150, 10)))
        assertEquals(0, Color.alpha(image.getPixel(10, 150)))
        assertEquals(0, Color.alpha(render(renderer, CelebrationSnapshot()).getPixel(10, 10)))
    }

    @Test fun cachedStarsHaveVisibleCoresEdgesAndSoftHalos() {
        val renderer = CelebrationCanvasRenderer()
        CelebrationPalette.entries.forEach { palette ->
            listOf(false, true).forEach { five ->
                val scene = CelebrationSnapshot(200f, 200f, elements = listOf(CelebrationElement(
                    CelebrationGeometry.Star(CelebrationPoint(100f, 100f), 6f, five, 0f), palette, 1f)))
                val image = render(renderer, scene)
                assertTrue(Color.alpha(image.getPixel(100, 100)) > 240)
                assertTrue(Color.alpha(image.getPixel(106, 100)) > 0)
                assertTrue("$palette five=$five near=${Color.alpha(image.getPixel(107, 100))} far=${Color.alpha(image.getPixel(118, 100))}",
                    Color.alpha(image.getPixel(107, 100)) > Color.alpha(image.getPixel(118, 100)))
                assertTrue(image.sameAs(render(renderer, scene)))
            }
        }
    }

    @Test fun trailsFadeTowardTailAndDoNotContaminateFollowingSprites() {
        val renderer = CelebrationCanvasRenderer()
        val trail = CelebrationElement(CelebrationGeometry.Trail(
            List(13) { CelebrationPoint(20f + it * 10f, 50f) }, 6f), CelebrationPalette.Blue, 1f)
        val glow = CelebrationElement(CelebrationGeometry.Glow(CelebrationPoint(100f, 140f), 3f), CelebrationPalette.Pink, 0.5f)
        val scene = CelebrationSnapshot(200f, 200f, elements = listOf(trail, glow))
        val image = render(renderer, scene)
        assertTrue(Color.alpha(image.getPixel(135, 50)) > Color.alpha(image.getPixel(35, 50)))
        val isolated = render(CelebrationCanvasRenderer(), scene.copy(elements = listOf(glow)))
        assertEquals(isolated.getPixel(100, 140), image.getPixel(100, 140))
        assertTrue(image.sameAs(render(renderer, scene)))
    }
}
