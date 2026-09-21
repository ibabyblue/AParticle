package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.CelebrationConfiguration
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class CelebrationSimulationTest {
    private fun plan(seed: Long = 42, reduced: Boolean = false, opacity: Float = 0f) =
        CelebrationPlan(0L, reduced, seed, CelebrationConfiguration(opacity))
    private fun frame(plan: CelebrationPlan, time: Float, width: Float = 390f, height: Float = 844f, density: Float = 1f) =
        CelebrationSimulation.snapshot(plan, width, height, density, time)

    @Test fun backdropClampsWithoutChangingParticlesAndSharesTimeline() {
        val baseline = frame(plan(), 1f).elements
        listOf(Float.NaN to 0f, Float.POSITIVE_INFINITY to 0f, Float.NEGATIVE_INFINITY to 0f,
            -0.2f to 0f, 1.5f to 1f, 0.25f to 0.25f).forEach { (input, expected) ->
            val scene = frame(plan(opacity = input), 1f)
            assertEquals(expected, scene.backdropOpacity, 0f)
            assertEquals(baseline, scene.elements)
        }
        val dimmed = plan(opacity = 0.25f)
        assertEquals(0.125f, frame(dimmed, 0.125f).backdropOpacity, 0.0001f)
        assertEquals(0.125f, frame(dimmed, 3.875f).backdropOpacity, 0.0001f)
        listOf(-1f, 0f, 4.2f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(CelebrationSnapshot(), frame(dimmed, it))
        }
        assertEquals(CelebrationSnapshot(), frame(dimmed, 1f, width = 0f))
        assertEquals(CelebrationSnapshot(), frame(dimmed, 1f, height = Float.NaN))
        assertEquals(CelebrationSnapshot(), frame(dimmed, 1f, density = 0f))
    }

    @Test fun choreographyHasContinuousMeteorImpactsAndLocalSecondaryOrigins() {
        val plan = plan()
        assertTrue(frame(plan, 0.25f).elements.isNotEmpty())
        assertFalse(frame(plan, 0.25f).elements.any { it.geometry is CelebrationGeometry.Star })
        assertTrue(frame(plan, 1.2f).elements.size > frame(plan, 0.25f).elements.size)
        assertTrue(frame(plan, 3.6f).elements.isNotEmpty())
        plan.climaxStars.forEach { star ->
            val flight = plan.fireworks[star.sourceIndex]
            val impact = CelebrationSimulation.meteorPosition(flight, 390f, 844f, flight.flight)
            assertEquals(impact, CelebrationSimulation.starPosition(star, 390f, 844f, 0f))
            val prior = CelebrationSimulation.meteorPosition(flight, 390f, 844f, flight.flight - 0.001f)
            assertTrue(hypot(impact.x - prior.x, impact.y - prior.y) < 2f)
            assertTrue(star.start >= flight.start && star.start <= flight.start + 0.04f)
        }
        plan.secondaryStars.forEach { star ->
            val burst = requireNotNull(star.secondaryBurst)
            val flight = plan.fireworks[star.sourceIndex]
            assertEquals(CelebrationSimulation.secondaryPosition(burst, flight.center, 390f, 844f),
                CelebrationSimulation.starPosition(star, 390f, 844f, 0f))
            assertTrue(star.start >= flight.start + burst.delay)
            assertTrue(star.start <= flight.start + burst.delay + 0.02001f)
        }
    }

    @Test fun seededPlansHaveBoundedCountsAndStableFrames() {
        val counts = mutableSetOf<Int>()
        // Spread seeds across the existing Android xorshift generator's state space.
        repeat(64) { index ->
            val seed = index.toLong() * 0x123456789ABCDEFL
            val plan = plan(seed)
            counts += plan.fireworks.size
            assertTrue(plan.fireworks.size in 4..8)
            assertTrue(plan.fireworks.all { it.flight in 0.65f..0.85001f })
            plan.fireworks.indices.forEach { source ->
                assertEquals(22, plan.sparks.count { it.sourceIndex == source })
                assertTrue(plan.climaxStars.count { it.sourceIndex == source } in 26..34)
                val bursts = plan.secondaryBursts.filter { it.sourceIndex == source }
                assertTrue(bursts.size in 5..7)
                bursts.forEach { burst ->
                    assertTrue(hypot(burst.offset.x, burst.offset.y) in 0.07999f..0.20001f)
                    assertTrue(burst.delay in 0.30f..0.60001f)
                    assertTrue(plan.secondaryStars.count { it.secondaryBurst == burst } in 10..15)
                }
            }
            assertEquals(frame(plan, 1.5f), frame(plan(seed), 1.5f))
        }
        assertEquals((4..8).toSet(), counts)
    }

    @Test fun radialMotionUsesWidthAndDensityOnlyScalesArtwork() {
        val plan = plan()
        plan.climaxStars.forEach { star ->
            fun position(height: Float, age: Float) = CelebrationSimulation.starPosition(star, 390f, height, age)
            val birth = position(844f, 0f)
            val later = position(844f, 1f)
            val tallBirth = position(1200f, 0f)
            val tallLater = position(1200f, 1f)
            assertEquals(later.x - birth.x, tallLater.x - tallBirth.x, 0.001f)
            assertEquals(later.y - birth.y, tallLater.y - tallBirth.y, 0.001f)
            val earlyDx = position(844f, 0.1f).x - birth.x
            val lateDx = position(844f, 1.1f).x - later.x
            assertTrue(kotlin.math.abs(lateDx) <= kotlin.math.abs(earlyDx) + 0.0001f)
            assertTrue(position(844f, 2.1f).y > position(844f, 2f).y)
        }
        val small = frame(plan, 2.3f).elements.map { it.geometry }.filterIsInstance<CelebrationGeometry.Star>()
        val large = frame(plan, 2.3f, density = 3f).elements.map { it.geometry }.filterIsInstance<CelebrationGeometry.Star>()
        small.zip(large).forEach { (a, b) ->
            assertEquals(a.center, b.center)
            assertEquals(a.radius * 3, b.radius, 0.0001f)
        }
        assertTrue(frame(plan, 2.3f).elements.map { (it.opacity * 100).toInt() }.toSet().size > 10)
    }

    @Test fun reducedMotionKeepsTwelveStationaryStarsAndFadesOut() {
        val plan = plan(reduced = true, opacity = 0.25f)
        assertEquals(0.8f, plan.duration, 0f)
        val early = frame(plan, 0.2f)
        val peak = frame(plan, 0.4f)
        assertEquals(12, peak.elements.size)
        assertEquals(early.elements.map { it.geometry }, peak.elements.map { it.geometry })
        assertTrue(early.elements.zip(peak.elements).all { (a, b) -> a.opacity < b.opacity })
        assertEquals(0.25f, peak.backdropOpacity, 0.0001f)
        assertEquals(early.elements.first().opacity * 0.25f, early.backdropOpacity, 0.0001f)
        assertEquals(CelebrationSnapshot(), frame(plan, 0.8f))
    }
}
