package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.ParticleColor
import com.ibabyblue.aparticle.ParticleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class ParticleSimulationTest {
    @Test
    fun particleUsesVelocityGravityAndLifetime() {
        val particle = fixture(velocityX = 100f, velocityY = -50f, gravity = 200f, drag = 0f, lifetime = 2f)
        val value = requireNotNull(ParticleSimulation.particleSnapshot(particle, 1f))

        assertEquals(110f, value.x, 0.001f)
        assertEquals(70f, value.y, 0.001f)
        assertEquals(0.5f, value.opacity, 0.001f)
        assertNull(ParticleSimulation.particleSnapshot(particle, 2.001f))
    }

    @Test
    fun dragUsesFrameRateIndependentClosedForm() {
        val particle = fixture(velocityX = 100f, velocityY = -50f, gravity = 200f, drag = 2f, lifetime = 3f)
        val value = requireNotNull(ParticleSimulation.particleSnapshot(particle, 1f))
        val decay = exp(-2.0).toFloat()

        assertEquals(10f + 100f * (1f - decay) / 2f, value.x, 0.001f)
        assertEquals(20f + (-50f - 100f) * (1f - decay) / 2f + 100f, value.y, 0.001f)
    }

    @Test
    fun trailContainsOlderSamplesWithIncreasingOpacity() {
        val particle = fixture(velocityX = 100f, velocityY = -50f, gravity = 200f, drag = 0f, lifetime = 2f)
        val values = ParticleSimulation.trailSnapshots(particle, 1f, 5)

        assertEquals(5, values.size)
        assertTrue(values.zipWithNext().all { (first, second) -> first.opacity < second.opacity })
        assertTrue(values.all { it.x < 110f })
    }

    private fun fixture(
        velocityX: Float,
        velocityY: Float,
        gravity: Float,
        drag: Float,
        lifetime: Float,
    ) = BurstParticlePlan(
        originX = 10f,
        originY = 20f,
        velocityX = velocityX,
        velocityY = velocityY,
        gravity = gravity,
        drag = drag,
        lifetimeSeconds = lifetime,
        sizePx = 8f,
        rotation = 0f,
        angularVelocity = 1f,
        color = ParticleColor.Rose,
        shape = ParticleShape.Heart,
    )
}
