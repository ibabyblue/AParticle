package com.ibabyblue.aparticle

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigurationTest {
    @Test
    fun particleColorResolvesEveryComponent() {
        val value = ParticleColor(Float.NaN, 2f, -1f, Float.POSITIVE_INFINITY)

        assertEquals(0f, value.red)
        assertEquals(1f, value.green)
        assertEquals(0f, value.blue)
        assertEquals(0f, value.alpha)
    }

    @Test
    fun riseAndBurstDefaultsMatchReferenceContract() {
        val value = RiseAndBurstConfiguration()

        assertEquals(0.15f..0.85f, value.spawnXRange)
        assertEquals(0.15f..0.50f, value.burstYRange)
        assertEquals(24f..48f, value.launchSizeRange)
        assertEquals(0.6f..0.9f, value.launchDurationRange)
        assertEquals(10f..18f, value.wobbleAmplitudeRange)
        assertEquals(listOf(ParticleColor.Rose), value.colors)
        assertEquals(20..33, value.burstParticleCountRange)
        assertEquals(1.8f, value.drag)
        assertEquals(5, value.trailSampleCount)
    }

    @Test
    fun riseAndBurstResolutionClampsAndFallsBack() {
        val value = RiseAndBurstConfiguration(
            colors = emptyList(),
            spawnXRange = -2f..3f,
            burstYRange = 0.9f..2f,
            launchSizeRange = 0f..900f,
            launchDurationRange = 0.01f..90f,
            wobbleAmplitudeRange = 0f..900f,
            burstParticleCountRange = -4..400,
            particleSizeRange = 0f..900f,
            burstSpeedRange = -1f..3_000f,
            gravity = Float.POSITIVE_INFINITY,
            drag = -12f,
            particleLifetimeRange = 0.01f..90f,
            trailSampleCount = 40,
        ).resolved()

        assertEquals(listOf(ParticleColor.Rose), value.colors)
        assertEquals(0f..1f, value.spawnXRange)
        assertEquals(0.9f..1f, value.burstYRange)
        assertEquals(1f..512f, value.launchSizeRange)
        assertEquals(0.1f..30f, value.launchDurationRange)
        assertEquals(1f..512f, value.wobbleAmplitudeRange)
        assertEquals(1..200, value.burstParticleCountRange)
        assertEquals(1f..512f, value.particleSizeRange)
        assertEquals(0f..2_000f, value.burstSpeedRange)
        assertEquals(240f, value.gravity)
        assertEquals(0f, value.drag)
        assertEquals(0.1f..30f, value.particleLifetimeRange)
        assertEquals(12, value.trailSampleCount)
    }

    @Test
    fun atmosphereResolutionIsFiniteAndBounded() {
        val defaults = AtmosphereConfiguration()
        val value = AtmosphereConfiguration(
            sparkleColors = emptyList(),
            sparkleDensity = Float.POSITIVE_INFINITY,
            bubbleDensity = -1f,
            glowIntensity = 9f,
            breathingDuration = Float.NaN,
        ).resolved()

        assertEquals(listOf(ParticleColor.White), value.sparkleColors)
        assertEquals(defaults.sparkleDensity, value.sparkleDensity)
        assertEquals(0f, value.bubbleDensity)
        assertEquals(2f, value.glowIntensity)
        assertEquals(defaults.breathingDuration, value.breathingDuration)
    }
}
