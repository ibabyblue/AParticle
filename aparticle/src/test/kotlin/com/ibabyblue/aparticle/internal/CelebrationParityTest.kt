package com.ibabyblue.aparticle.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ibabyblue.aparticle.CelebrationConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Frozen pre-optimization behavior is the oracle; no visual tolerance is allowed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CelebrationParityTest {
    @Test fun streamingFramesPreserveEveryParticleAndPixel() {
        val reference = CelebrationReferenceRenderer()
        val renderer = CelebrationCanvasRenderer()
        val surfaces = listOf(Triple(390, 844, 1f), Triple(844, 390, 1f), Triple(540, 1204, 1.40625f))
        for ((width, height, density) in surfaces) {
            val expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (seed in listOf(42L, 327942116865947580L)) {
                renderer.prepare()
                val plan = CelebrationPlan(0L, false, seed, CelebrationConfiguration(0.25f))
                for (time in listOf(0.25f, 0.8f, 1.2f, 1.7f, 2.25f, 3.2f, 3.9f, 4.2f)) {
                    expected.eraseColor(0)
                    actual.eraseColor(0)
                    val frame = CelebrationReferenceSimulation.snapshot(plan, width.toFloat(), height.toFloat(), density, time)
                    assertEquals(frame, CelebrationSimulation.snapshot(plan, width.toFloat(), height.toFloat(), density, time))
                    reference.draw(Canvas(expected), frame, density)
                    renderer.draw(Canvas(actual), plan, width.toFloat(), height.toFloat(), density, time)
                    assertTrue("$width x $height density=$density seed=$seed time=$time", expected.sameAs(actual))
                }
            }
            expected.recycle()
            actual.recycle()
        }
    }

    @Test fun reducedMotionAndReplayKeepTheSamePixels() {
        val reference = CelebrationReferenceRenderer()
        val renderer = CelebrationCanvasRenderer()
        val expected = Bitmap.createBitmap(390, 844, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(390, 844, Bitmap.Config.ARGB_8888)
        // Reuse the renderer across moving, reduced, empty and restarted plans.
        for (reduced in listOf(false, true, false)) {
            val plan = CelebrationPlan(0, reduced, 42, CelebrationConfiguration(0.5f))
            for (time in listOf(0f, 0.2f, 0.4f, 0.7f, 0.8f)) {
                expected.eraseColor(0)
                actual.eraseColor(0)
                reference.draw(Canvas(expected), CelebrationReferenceSimulation.snapshot(plan, 390f, 844f, 1f, time), 1f)
                renderer.draw(Canvas(actual), plan, 390f, 844f, 1f, time)
                assertTrue("reduced=$reduced time=$time", expected.sameAs(actual))
            }
        }
        expected.recycle()
        actual.recycle()
    }
}
