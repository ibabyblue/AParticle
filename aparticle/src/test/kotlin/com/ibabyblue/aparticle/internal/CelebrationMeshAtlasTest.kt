package com.ibabyblue.aparticle.internal

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CelebrationMeshAtlasTest {
    @Test fun packingPreservesTranslucentArtworkAndSeparatesSamplingRegions() {
        fun artwork(width: Int, height: Int, color: Int) =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val body = artwork(9, 7, Color.argb(137, 241, 191, 239))
        val shadow = artwork(9, 7, Color.argb(29, 231, 171, 225))
        // Force a row break immediately after the small sprite and its shadow.
        val glow = artwork(2044, 3, Color.argb(101, 183, 222, 249))
        val atlas = CelebrationMeshAtlas(
            listOf(CelebrationSprite(body, shadow, 3f, 9f)),
            listOf(CelebrationSprite(glow, null, 3f, 2044f)),
        )
        val regions = listOf(atlas.stars.single().image, checkNotNull(atlas.stars.single().shadow), atlas.glows.single().image)
        for ((expected, rect) in listOf(body, shadow, glow).zip(regions)) {
            val actual = Bitmap.createBitmap(atlas.image, rect.left, rect.top, rect.width(), rect.height())
            assertTrue("Packed texels must retain color and alpha", expected.sameAs(actual))
            assertEquals(Color.TRANSPARENT, atlas.image.getPixel(rect.left - 1, rect.top))
            assertEquals(Color.TRANSPARENT, atlas.image.getPixel(rect.right, rect.bottom - 1))
            actual.recycle()
        }
        assertTrue(atlas.glows.single().image.top > atlas.stars.single().image.bottom)
        assertEquals(Color.WHITE, atlas.image.getPixel(8, 8))
        listOf(body, shadow, glow, atlas.image).forEach(Bitmap::recycle)
    }
}
