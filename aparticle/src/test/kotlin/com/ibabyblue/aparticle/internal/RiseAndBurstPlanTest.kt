package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.ParticleColor
import com.ibabyblue.aparticle.RiseAndBurstConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiseAndBurstPlanTest {
    @Test
    fun generatedPlansStayInsideResolvedRanges() {
        val configuration = RiseAndBurstConfiguration().resolved()

        repeat(100) { index ->
            val plan = RiseAndBurstPlan.make(
                configuration = configuration,
                widthPx = 390f,
                heightPx = 844f,
                density = 1f,
                startNanos = index.toLong(),
                seed = index + 1L,
            )

            assertTrue(plan.launchX in 0.15f * 390f..0.85f * 390f)
            assertTrue(plan.burstY in 0.15f * 844f..0.50f * 844f)
            assertTrue(plan.launchSizePx in 24f..48f)
            assertTrue(plan.launchDurationSeconds in 0.6f..0.9f)
            assertTrue(plan.wobbleAmplitudePx in 10f..18f)
            assertTrue(plan.particles.size in 20..33)
            assertTrue(plan.particles.all { it.sizePx in 4f..10f })
            assertTrue(plan.particles.all { it.lifetimeSeconds in 0.8f..1.2f })
        }
    }

    @Test
    fun burstParticlesKeepTheLaunchColor() {
        val configuration = RiseAndBurstConfiguration(
            colors = listOf(ParticleColor.Pink, ParticleColor.Purple),
            burstParticleCountRange = 6..6,
        ).resolved()
        val plan = RiseAndBurstPlan.make(configuration, 390f, 844f, 1f, 0L, 42L)

        assertEquals(ParticleColor.Pink, plan.launchColor)
        assertTrue(plan.particles.all { it.color == plan.launchColor })
    }
}
