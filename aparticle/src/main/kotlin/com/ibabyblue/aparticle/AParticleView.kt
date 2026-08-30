package com.ibabyblue.aparticle

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.ibabyblue.aparticle.internal.AnimatorScaleObserver
import com.ibabyblue.aparticle.internal.AtmospherePlan
import com.ibabyblue.aparticle.internal.AtmosphereSimulation
import com.ibabyblue.aparticle.internal.ParticleCanvasRenderer
import com.ibabyblue.aparticle.internal.ParticleSimulation
import com.ibabyblue.aparticle.internal.RiseAndBurstPlan
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Transparent imperative particle surface for Android View layouts.
 *
 * The view never consumes touch events. It owns concurrent playbacks until
 * they finish, are stopped, the view detaches, the window becomes invisible,
 * or the system animator scale becomes zero.
 */
public class AParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private data class Record(
        val playback: ParticlePlayback,
        val effect: ParticleEffect,
        val seed: Long,
        var startNanos: Long = 0L,
        var risePlan: RiseAndBurstPlan? = null,
        var atmospherePlan: AtmospherePlan? = null,
    )

    private val renderer = ParticleCanvasRenderer(context)
    private val records = linkedMapOf<Long, Record>()
    private var animationsEnabled = true
    private val animatorScaleObserver = AnimatorScaleObserver(context) { enabled ->
        animationsEnabled = enabled
        if (!enabled) stopAll()
    }

    init {
        animationsEnabled = animatorScaleObserver.animationsEnabled
        isClickable = false
        isLongClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipToOutline = false
        setWillNotDraw(false)
    }

    /**
     * Starts one independent playback and returns its cancellation identity.
     *
     * A request made before positive layout is retained in insertion order.
     * When the system animator scale is zero, this is a safe no-op.
     */
    public fun play(effect: ParticleEffect): ParticlePlayback {
        val playback = ParticlePlayback(nextPlaybackId.getAndIncrement())
        if (!animationsEnabled) return playback
        val record = Record(
            playback = playback,
            effect = effect,
            seed = nextSeed(),
        )
        records[playback.id] = record
        if (width > 0 && height > 0) buildPlan(record, System.nanoTime())
        postInvalidateOnAnimation()
        return playback
    }

    /** Stops one active or pending playback. Unknown and expired identities are safe no-ops. */
    public fun stop(playback: ParticlePlayback) {
        records.remove(playback.id)
        if (records.isEmpty()) invalidate()
    }

    /** Stops every playback owned by this view. */
    public fun stopAll() {
        if (records.isEmpty()) return
        records.clear()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animatorScaleObserver.start()
    }

    override fun onDetachedFromWindow() {
        animatorScaleObserver.stop()
        stopAll()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) stopAll()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        val materialChange = oldWidth > 0 && oldHeight > 0 &&
            (abs(width - oldWidth) > 1 || abs(height - oldHeight) > 1)
        val now = System.nanoTime()
        val iterator = records.values.iterator()
        while (iterator.hasNext()) {
            val record = iterator.next()
            when {
                record.startNanos == 0L -> buildPlan(record, now)
                materialChange && record.effect is ParticleEffect.RiseAndBurst -> iterator.remove()
                materialChange && record.effect is ParticleEffect.Atmosphere -> buildPlan(record, record.startNanos)
            }
        }
        if (records.isNotEmpty()) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!animationsEnabled || width <= 0 || height <= 0) return
        val now = System.nanoTime()
        val iterator = records.values.iterator()
        while (iterator.hasNext()) {
            val record = iterator.next()
            if (record.startNanos == 0L) buildPlan(record, now)
            val elapsed = (now - record.startNanos).coerceAtLeast(0L) / 1_000_000_000f
            record.risePlan?.let { plan ->
                if (elapsed > plan.totalDurationSeconds) {
                    iterator.remove()
                } else {
                    renderer.drawRiseAndBurst(canvas, ParticleSimulation.snapshot(plan, elapsed))
                }
            }
            record.atmospherePlan?.let { plan ->
                renderer.drawAtmosphere(
                    canvas,
                    AtmosphereSimulation.snapshot(plan, width.toFloat(), height.toFloat(), elapsed),
                )
            }
        }
        if (records.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun buildPlan(record: Record, startNanos: Long) {
        if (width <= 0 || height <= 0) return
        record.startNanos = startNanos
        when (val effect = record.effect) {
            is ParticleEffect.RiseAndBurst -> {
                record.atmospherePlan = null
                record.risePlan = RiseAndBurstPlan.make(
                    configuration = effect.configuration.resolved(),
                    widthPx = width.toFloat(),
                    heightPx = height.toFloat(),
                    density = resources.displayMetrics.density,
                    startNanos = startNanos,
                    seed = record.seed,
                )
            }
            is ParticleEffect.Atmosphere -> {
                record.risePlan = null
                record.atmospherePlan = AtmospherePlan.make(
                    configuration = effect.configuration.resolved(),
                    widthPx = width.toFloat(),
                    heightPx = height.toFloat(),
                    density = resources.displayMetrics.density,
                    startNanos = startNanos,
                    seed = record.seed,
                )
            }
        }
    }

    private fun nextSeed(): Long = System.nanoTime() xor nextPlaybackId.get()

    private companion object {
        val nextPlaybackId = AtomicLong(1L)
    }
}
