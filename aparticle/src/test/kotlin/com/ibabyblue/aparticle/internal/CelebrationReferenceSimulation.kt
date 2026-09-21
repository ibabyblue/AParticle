package com.ibabyblue.aparticle.internal

import kotlin.math.*

internal object CelebrationReferenceSimulation {
    fun snapshot(plan: CelebrationPlan, width: Float, height: Float, density: Float, time: Float): CelebrationSnapshot {
        if (!time.isFinite() || time <= 0 || time >= plan.duration ||
            !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0 ||
            !density.isFinite() || density <= 0) return CelebrationSnapshot()
        val elements = mutableListOf<CelebrationElement>()
        fun add(geometry: CelebrationGeometry, palette: CelebrationPalette, opacity: Float) {
            elements += CelebrationElement(geometry, palette, opacity)
        }
        val backdrop = plan.backdropOpacity * if (plan.reduceMotion) sin(PI.toFloat() * time / plan.duration)
            else smoothstep(time / 0.25f) * smoothstep((plan.duration - time) / 0.65f)
        if (plan.reduceMotion) {
            CelebrationPlan.reducedStars.forEach { star ->
                add(CelebrationGeometry.Star(scaled(star.center, width, height), star.radius * density,
                    star.fivePoint, star.rotation), star.palette, sin(PI.toFloat() * time / plan.duration))
            }
            return CelebrationSnapshot(width, height, backdrop, elements)
        }
        plan.fireworks.forEach { flight ->
            val age = time - flight.launch
            if (age <= 0) return@forEach
            if (age < flight.flight) {
                val opacity = smoothstep(age / 0.12f)
                val history = min(age, 0.24f)
                val points = List(13) { meteorPosition(flight, width, height, age - history * (1 - it / 12f)) }
                add(CelebrationGeometry.Trail(points, 3f * density), flight.palette, opacity)
                add(CelebrationGeometry.Glow(points.last(), 3.4f * density), flight.palette, opacity)
            } else {
                val burstAge = age - flight.flight
                if (burstAge < 0.24f) {
                    add(CelebrationGeometry.Glow(scaled(flight.center, width, height),
                        (3.4f + 17 * sin(PI.toFloat() * burstAge / 0.24f)) * density),
                        flight.palette, (1 - burstAge / 0.24f).pow(2))
                }
            }
        }
        plan.sparks.forEach { spark ->
            val source = plan.fireworks[spark.sourceIndex]
            val age = time - source.start - spark.delay
            if (age <= 0 || age >= spark.duration) return@forEach
            val opacity = smoothstep(age / 0.035f) * (1 - age / spark.duration).pow(1.3f)
            val points = List(6) { sample ->
                val elapsed = max(0f, age - 0.075f * (1 - sample / 5f))
                val travel = (1 - exp(-2.6f * elapsed)) / 2.6f
                val origin = scaled(source.center, width, height)
                CelebrationPoint(origin.x + spark.velocity.x * width * travel,
                    origin.y + spark.velocity.y * width * travel + width * 0.04f * elapsed * elapsed)
            }
            add(CelebrationGeometry.Trail(points, spark.radius * 1.1f * density), source.palette, opacity * 0.85f)
            add(CelebrationGeometry.Glow(points.last(), spark.radius * density), source.palette, opacity)
        }
        plan.secondaryBursts.forEach { burst ->
            val source = plan.fireworks[burst.sourceIndex]
            val age = time - source.start - burst.delay
            if (age <= 0 || age >= 0.18f) return@forEach
            add(CelebrationGeometry.Glow(secondaryPosition(burst, source.center, width, height),
                (2.2f + 8 * sin(PI.toFloat() * age / 0.18f)) * density),
                source.palette, (1 - age / 0.18f).pow(2))
        }
        fun appendStar(star: CelebrationStar) {
            val age = time - star.start
            if (age <= 0 || age >= star.lifetime) return
            val center = starPosition(star, width, height, age)
            val mix = smoothstep((age - 0.12f) / 0.65f)
            val fade = smoothstep(min(plan.duration - time, star.lifetime - age) / 0.65f)
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * star.twinkleRate + star.phase)).pow(2)
            val opacity = smoothstep(age / 0.045f) * fade
            if (mix < 1) {
                val points = List(6) { starPosition(star, width, height, max(0f, age - 0.055f * (1 - it / 5f))) }
                add(CelebrationGeometry.Trail(points, 1.6f * density), star.palette, opacity * (1 - mix) * 0.8f)
                add(CelebrationGeometry.Glow(center, 1.7f * density), star.palette, opacity * (1 - mix))
            }
            if (mix > 0) {
                add(CelebrationGeometry.Star(center, star.radius * (0.65f + 0.35f * mix) * density,
                    star.fivePoint, star.rotation + 0.12f * sin(age + star.phase)), star.palette, opacity * mix * twinkle)
            }
        }
        plan.climaxStars.forEach(::appendStar)
        plan.secondaryStars.forEach(::appendStar)
        return CelebrationSnapshot(width, height, backdrop, elements)
    }

    fun meteorPosition(flight: CelebrationFlight, width: Float, height: Float, age: Float): CelebrationPoint {
        val progress = (age / flight.flight).coerceIn(0f, 1f)
        val t = progress * (0.65f + 0.35f * progress)
        val remaining = 1 - t
        return scaled(CelebrationPoint(
            remaining * remaining * flight.origin.x + 2 * remaining * t * flight.control.x + t * t * flight.center.x,
            remaining * remaining * flight.origin.y + 2 * remaining * t * flight.control.y + t * t * flight.center.y,
        ), width, height)
    }

    fun secondaryPosition(burst: CelebrationSecondaryBurst, origin: CelebrationPoint, width: Float, height: Float): CelebrationPoint {
        val center = scaled(origin, width, height)
        return CelebrationPoint(center.x + burst.offset.x * width, center.y + burst.offset.y * width)
    }

    fun starPosition(star: CelebrationStar, width: Float, height: Float, age: Float): CelebrationPoint {
        val elapsed = max(0f, age)
        val travel = if (star.drag > 0) (1 - exp(-star.drag * elapsed)) / star.drag else elapsed
        val origin = star.secondaryBurst?.let { secondaryPosition(it, star.center, width, height) }
            ?: scaled(star.center, width, height)
        return CelebrationPoint(origin.x + star.velocity.x * width * travel,
            origin.y + star.velocity.y * width * travel + width * 0.035f * elapsed * elapsed)
    }

    private fun scaled(point: CelebrationPoint, width: Float, height: Float) = CelebrationPoint(point.x * width, point.y * height)
    private fun smoothstep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
