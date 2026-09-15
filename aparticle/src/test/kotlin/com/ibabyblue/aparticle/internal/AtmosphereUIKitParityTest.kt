package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.AtmosphereConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Reference: IParticleView's CA animations used by Mira, not IParticleAnimator. */
class AtmosphereUIKitParityTest {
    private val plan = AtmospherePlan.make(
        AtmosphereConfiguration(breathingDuration = 5f).resolved(), 390f, 844f, 1f, 0L, 42L,
    )

    @Test
    fun kissStartsAtLowGlowAndReachesPeakHalfwayThroughFiveSecondCycle() {
        val times = listOf(0f, 1.25f, 2.5f, 3f, 3.75f, 5f)
        val expected = listOf(0f, 0.5f, 1f, 0.8f, 0.5f, 0f)
        times.zip(expected).forEach { (time, breath) ->
            assertEquals("Glow at ${time}s", breath, frame(time).breath, 0.0001f)
        }
    }

    @Test
    fun sparkleTwinkleUsesUIKitKeyframesWithoutMultiplyingModelOpacity() {
        val sparkle = plan.sparkles.first().copy(phase = 0f, twinkleSpeed = 1f, baseAlpha = 0.5f)
        val scene = plan.copy(sparkles = listOf(sparkle), bubbles = emptyList())
        listOf(0f to 0.25f, PI.toFloat() / 4f to 0.625f, PI.toFloat() / 2f to 1f).forEach { (time, alpha) ->
            assertEquals(alpha, AtmosphereSimulation.snapshot(scene, 390f, 844f, time).sparkles.single().opacity, 0.0001f)
        }
    }

    @Test
    fun bubbleBreathingUsesTheSameLowHighLowKeyframesAsUIKit() {
        val bubble = plan.bubbles.first().copy(breathingPhase = 0f, breathingSpeed = 1f, baseAlpha = 0.2f)
        val scene = plan.copy(sparkles = emptyList(), bubbles = listOf(bubble))
        listOf(0f to 0.13f, PI.toFloat() / 2f to 0.165f, PI.toFloat() to 0.2f).forEach { (time, alpha) ->
            assertEquals(alpha, AtmosphereSimulation.snapshot(scene, 390f, 844f, time).bubbles.single().opacity, 0.0001f)
        }
    }

    @Test
    fun driftInterpolatesUIKitNinePointPathForBothAxes() {
        val sparkle = plan.sparkles.first().copy(driftPhase = 0.4f)
        val bubble = plan.bubbles.first().copy(phase = 0.7f, driftFrequencyX = 0.2f)
        val scene = plan.copy(sparkles = listOf(sparkle), bubbles = listOf(bubble))
        // Halfway between the first two keyframes; a sine evaluated directly differs.
        val sparkleFrame = AtmosphereSimulation.snapshot(scene, 390f, 844f, PI.toFloat() / 8f / 0.18f)
        val bubbleFrame = AtmosphereSimulation.snapshot(scene, 390f, 844f, PI.toFloat() / 8f / 0.2f)
        val step = PI.toFloat() / 4f
        assertEquals(sparkle.normalizedX * 390f + (sin(0.4f) + sin(0.4f + step)) / 2f * sparkle.driftAmplitudeX,
            sparkleFrame.sparkles.single().x, 0.0001f)
        assertEquals(sparkle.normalizedY * 844f + (cos(0.4f) + cos(0.4f + step)) / 2f * sparkle.driftAmplitudeY,
            sparkleFrame.sparkles.single().y, 0.0001f)
        assertEquals(bubble.normalizedX * 390f + (sin(0.7f) + sin(0.7f + step)) / 2f * bubble.driftAmplitudeX,
            bubbleFrame.bubbles.single().x, 0.0001f)
        assertEquals(bubble.normalizedY * 844f + (cos(0.7f * 0.82f) + cos((0.7f + step) * 0.82f)) / 2f * bubble.driftAmplitudeY,
            bubbleFrame.bubbles.single().y, 0.0001f)
    }

    private fun frame(time: Float) = AtmosphereSimulation.snapshot(plan, 390f, 844f, time)
}
