package com.ibabyblue.aparticle.internal

import android.app.Activity
import android.widget.FrameLayout
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.ibabyblue.aparticle.AParticleView
import com.ibabyblue.aparticle.ParticleEffect
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnowRenderingTest {
    @Test
    fun viewCancelsPendingAndLoadingSnowIndependently() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val view = AParticleView(activity)
        root.addView(view)
        val records = AParticleView::class.java.getDeclaredField("records")
            .apply { isAccessible = true }.get(view) as Map<*, *>
        val first = view.play(ParticleEffect.twinklingSnow())
        view.play(ParticleEffect.perspectiveSnow())
        assertTrue(records.size == 2)
        view.stop(first)
        assertTrue(records.size == 1)
        view.layout(0, 0, 240, 400)
        root.removeView(view)
        assertTrue(records.isEmpty())
    }

    @Test
    fun maximumPerspectiveFieldDrawsOnlyWhiteSnow() {
        val resources = RuntimeEnvironment.getApplication().resources
        val atlas = SnowAtlas.load(resources, perspective = true)
        val field = SnowField(atlas, ParticleEffect.perspectiveSnow(600))
        val frame = Bitmap.createBitmap(240, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        repeat(6) {
            frame.eraseColor(Color.TRANSPARENT)
            field.draw(canvas, 240f, 400f, 1f, 1_000_000_000L + 16_666_667L * it)
        }
        val pixels = IntArray(frame.width * frame.height)
        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        assertTrue(pixels.any { Color.alpha(it) > 128 })
        assertTrue(pixels.all { Color.red(it) == Color.green(it) && Color.green(it) == Color.blue(it) })
    }
}
