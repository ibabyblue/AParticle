package com.ibabyblue.aparticle.internal

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.ibabyblue.aparticle.AParticleView
import com.ibabyblue.aparticle.CelebrationConfiguration
import com.ibabyblue.aparticle.ParticleEffect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CelebrationViewTest {
    @Test fun restartReplacesOnlyCelebrationAndOldHandleCannotStopNewPlayback() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = AParticleView(activity)
        view.layout(0, 0, 390, 844)
        val rise = view.play(ParticleEffect.riseAndBurst())
        val old = view.play(ParticleEffect.celebration())
        val current = view.play(ParticleEffect.celebration(CelebrationConfiguration(0.25f)))
        assertEquals(2, records(view).size)
        view.stop(old)
        assertEquals(2, records(view).size)
        assertEquals(0.25f, celebrationPlan(view)!!.backdropOpacity, 0f)
        view.stop(current)
        assertEquals(1, records(view).size)
        view.stop(rise)
        assertTrue(records(view).isEmpty())
        view.stopAll()
    }

    @Test fun pendingConfigurationStartsAtPositiveLayoutAndResizeCancels() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = AParticleView(activity)
        view.play(ParticleEffect.celebration(CelebrationConfiguration(0.25f)))
        assertNull(celebrationPlan(view))
        view.play(ParticleEffect.celebration(CelebrationConfiguration(0.5f)))
        assertEquals(1, records(view).size)
        view.layout(0, 0, 390, 844)
        assertEquals(0.5f, celebrationPlan(view)!!.backdropOpacity, 0f)
        view.layout(0, 0, 600, 844)
        assertTrue(records(view).isEmpty())
        view.play(ParticleEffect.celebration())
        view.layout(0, 0, 0, 0)
        assertTrue(records(view).isEmpty())
        view.layout(0, 0, 390, 844)
        assertTrue(records(view).isEmpty())
    }

    @Test fun disabledAnimationsUseReducedFadeAndDetachClearsWithoutReplay() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, 0f)
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val container = FrameLayout(activity)
        activity.setContentView(container)
        val view = AParticleView(activity)
        container.addView(view)
        view.layout(0, 0, 390, 844)
        view.play(ParticleEffect.riseAndBurst())
        assertTrue(records(view).isEmpty())
        view.play(ParticleEffect.celebration())
        assertTrue(celebrationPlan(view)!!.reduceMotion)
        assertEquals(0.8f, celebrationPlan(view)!!.duration, 0f)
        container.removeView(view)
        assertTrue(records(view).isEmpty())
        container.addView(view)
        assertTrue(records(view).isEmpty())
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, 1f)
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        controller.pause().stop().destroy()
    }

    @Test fun expiryRemovesBackdropAndSurfaceIsNoninteractive() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = AParticleView(activity)
        view.layout(0, 0, 390, 844)
        view.play(ParticleEffect.celebration(CelebrationConfiguration(0.25f)))
        val record = records(view).values.single()!!
        record.javaClass.getDeclaredField("startNanos").apply { isAccessible = true }
            .setLong(record, System.nanoTime() - 5_000_000_000L)
        view.draw(Canvas(Bitmap.createBitmap(390, 844, Bitmap.Config.ARGB_8888)))
        assertTrue(records(view).isEmpty())
        assertFalse(view.isClickable)
        assertFalse(view.isFocusable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, view.importantForAccessibility)
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        assertFalse(view.onTouchEvent(event))
        event.recycle()
    }

    private fun records(view: AParticleView): Map<*, *> =
        AParticleView::class.java.getDeclaredField("records").apply { isAccessible = true }.get(view) as Map<*, *>

    private fun celebrationPlan(view: AParticleView): CelebrationPlan? = records(view).values.firstNotNullOfOrNull { record ->
        record!!.javaClass.getDeclaredField("celebrationPlan").apply { isAccessible = true }.get(record) as? CelebrationPlan
    }
}
