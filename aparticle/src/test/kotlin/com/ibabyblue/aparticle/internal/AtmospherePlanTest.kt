package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.AtmosphereConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmospherePlanTest {
    @Test
    fun areaCountsAndStableSeedMatchContract() {
        val configuration = AtmosphereConfiguration().resolved()
        val first = AtmospherePlan.make(configuration, 390f, 844f, 1f, 100L, 42L)
        val resized = AtmospherePlan.make(configuration, 844f, 390f, 1f, 100L, 42L)

        assertEquals(73, first.sparkles.size)
        assertEquals(18, first.bubbles.size)
        assertEquals(first.sparkles.first().normalizedX, resized.sparkles.first().normalizedX)
        assertEquals(first.sparkles.first().normalizedY, resized.sparkles.first().normalizedY)
    }

    @Test
    fun countsAreCappedAndAllEdgeRolesAppear() {
        val configuration = AtmosphereConfiguration(sparkleDensity = 3f, bubbleDensity = 3f).resolved()
        val plan = AtmospherePlan.make(configuration, 2_000f, 2_000f, 1f, 100L, 8L)

        assertEquals(320, plan.sparkles.size)
        assertEquals(32, plan.bubbles.size)
        assertEquals(BubbleEdge.entries.toSet(), plan.bubbles.map { it.edge }.toSet())
        assertTrue(plan.sparkles.count { it.usesCentralUpperZone } >= (plan.sparkles.size * 0.75f).toInt())
    }

    @Test
    fun zeroDensityOmitsElements() {
        val configuration = AtmosphereConfiguration(sparkleDensity = 0f, bubbleDensity = 0f).resolved()
        val plan = AtmospherePlan.make(configuration, 390f, 844f, 1f, 100L, 1L)

        assertTrue(plan.sparkles.isEmpty())
        assertTrue(plan.bubbles.isEmpty())
    }
}
