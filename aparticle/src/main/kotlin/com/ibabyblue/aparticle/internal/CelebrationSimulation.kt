package com.ibabyblue.aparticle.internal

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal sealed interface CelebrationGeometry {
    data class Trail(val points: List<CelebrationPoint>, val width: Float) : CelebrationGeometry
    data class Glow(val center: CelebrationPoint, val radius: Float) : CelebrationGeometry
    data class Star(val center: CelebrationPoint, val radius: Float, val fivePoint: Boolean, val rotation: Float) : CelebrationGeometry
}

internal data class CelebrationElement(
    val geometry: CelebrationGeometry, val palette: CelebrationPalette, val opacity: Float,
)

/** Pixel-based geometry in painter order. Empty frames never retain a backdrop. */
internal data class CelebrationSnapshot(
    val width: Float = 0f, val height: Float = 0f, val backdropOpacity: Float = 0f,
    val elements: List<CelebrationElement> = emptyList(),
)

internal object CelebrationSimulation {
    /** Immutable snapshots are retained for deterministic tests and inspection. */
    fun snapshot(plan: CelebrationPlan, width: Float, height: Float, density: Float, time: Float): CelebrationSnapshot {
        val elements = mutableListOf<CelebrationElement>()
        var snapshot = CelebrationSnapshot()
        render(plan, width, height, density, time, FloatArray(26), object : CelebrationSink {
            override fun begin(width: Float, height: Float, backdrop: Float) {
                snapshot = CelebrationSnapshot(width, height, backdrop, elements)
            }
            override fun trail(points: FloatArray, count: Int, width: Float, palette: CelebrationPalette, opacity: Float) {
                elements += CelebrationElement(CelebrationGeometry.Trail(
                    List(count) { CelebrationPoint(points[it * 2], points[it * 2 + 1]) }, width), palette, opacity)
            }
            override fun glow(center: CelebrationPoint, radius: Float, palette: CelebrationPalette, opacity: Float) {
                elements += CelebrationElement(CelebrationGeometry.Glow(center, radius), palette, opacity)
            }
            override fun star(center: CelebrationPoint, radius: Float, fivePoint: Boolean, rotation: Float,
                palette: CelebrationPalette, opacity: Float) {
                elements += CelebrationElement(CelebrationGeometry.Star(center, radius, fivePoint, rotation), palette, opacity)
            }
        })
        return snapshot
    }

    /** Streams the identical painter order into reusable renderer-owned storage. */
    fun render(plan: CelebrationPlan, width: Float, height: Float, density: Float, time: Float,
        points: FloatArray, sink: CelebrationSink) {
        if (!time.isFinite() || time <= 0 || time >= plan.duration ||
            !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0 ||
            !density.isFinite() || density <= 0) {
            sink.begin(0f, 0f, 0f)
            return
        }
        val backdrop = plan.backdropOpacity * if (plan.reduceMotion) sin(PI.toFloat() * time / plan.duration)
            else smoothstep(time / 0.25f) * smoothstep((plan.duration - time) / 0.65f)
        sink.begin(width, height, backdrop)
        if (plan.reduceMotion) {
            CelebrationPlan.reducedStars.forEach { star ->
                sink.star(scaled(star.center, width, height), star.radius * density,
                    star.fivePoint, star.rotation, star.palette, sin(PI.toFloat() * time / plan.duration))
            }
            return
        }
        plan.fireworks.forEach { flight ->
            val age = time - flight.launch
            if (age <= 0) return@forEach
            if (age < flight.flight) {
                val opacity = smoothstep(age / 0.12f)
                val history = min(age, 0.24f)
                for (sample in 0..12) {
                    val point = meteorPosition(flight, width, height, age - history * (1 - sample / 12f))
                    points[sample * 2] = point.x
                    points[sample * 2 + 1] = point.y
                }
                sink.trail(points, 13, 3f * density, flight.palette, opacity)
                sink.glow(CelebrationPoint(points[24], points[25]), 3.4f * density, flight.palette, opacity)
            } else {
                val burstAge = age - flight.flight
                if (burstAge < 0.24f) {
                    sink.glow(scaled(flight.center, width, height),
                        (3.4f + 17 * sin(PI.toFloat() * burstAge / 0.24f)) * density,
                        flight.palette, (1 - burstAge / 0.24f).pow(2))
                }
            }
        }
        plan.sparks.forEach { spark ->
            val source = plan.fireworks[spark.sourceIndex]
            val age = time - source.start - spark.delay
            if (age <= 0 || age >= spark.duration) return@forEach
            val opacity = smoothstep(age / 0.035f) * (1 - age / spark.duration).pow(1.3f)
            val origin = scaled(source.center, width, height)
            for (sample in 0..5) {
                val elapsed = max(0f, age - 0.075f * (1 - sample / 5f))
                val travel = (1 - exp(-2.6f * elapsed)) / 2.6f
                points[sample * 2] = origin.x + spark.velocity.x * width * travel
                points[sample * 2 + 1] = origin.y + spark.velocity.y * width * travel + width * 0.04f * elapsed * elapsed
            }
            sink.trail(points, 6, spark.radius * 1.1f * density, source.palette, opacity * 0.85f)
            sink.glow(CelebrationPoint(points[10], points[11]), spark.radius * density, source.palette, opacity)
        }
        plan.secondaryBursts.forEach { burst ->
            val source = plan.fireworks[burst.sourceIndex]
            val age = time - source.start - burst.delay
            if (age <= 0 || age >= 0.18f) return@forEach
            sink.glow(secondaryPosition(burst, source.center, width, height),
                (2.2f + 8 * sin(PI.toFloat() * age / 0.18f)) * density,
                source.palette, (1 - age / 0.18f).pow(2))
        }
        fun appendStar(star: CelebrationStar) {
            val age = time - star.start
            if (age <= 0 || age >= star.lifetime) return
            val origin = starOrigin(star, width, height)
            val center = starPositionFromOrigin(star, origin, width, age)
            val mix = smoothstep((age - 0.12f) / 0.65f)
            val fade = smoothstep(min(plan.duration - time, star.lifetime - age) / 0.65f)
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * star.twinkleRate + star.phase)).pow(2)
            val opacity = smoothstep(age / 0.045f) * fade
            if (mix < 1) {
                for (sample in 0..5) {
                    val point = if (sample == 5) center else
                        starPositionFromOrigin(star, origin, width, max(0f, age - 0.055f * (1 - sample / 5f)))
                    points[sample * 2] = point.x
                    points[sample * 2 + 1] = point.y
                }
                sink.trail(points, 6, 1.6f * density, star.palette, opacity * (1 - mix) * 0.8f)
                sink.glow(center, 1.7f * density, star.palette, opacity * (1 - mix))
            }
            if (mix > 0) {
                sink.star(center, star.radius * (0.65f + 0.35f * mix) * density,
                    star.fivePoint, star.rotation + 0.12f * sin(age + star.phase), star.palette, opacity * mix * twinkle)
            }
        }
        plan.climaxStars.forEach(::appendStar)
        plan.secondaryStars.forEach(::appendStar)
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

    fun starPosition(star: CelebrationStar, width: Float, height: Float, age: Float): CelebrationPoint =
        starPositionFromOrigin(star, starOrigin(star, width, height), width, age)

    private fun starOrigin(star: CelebrationStar, width: Float, height: Float): CelebrationPoint =
        star.secondaryBurst?.let { secondaryPosition(it, star.center, width, height) }
            ?: scaled(star.center, width, height)

    private fun starPositionFromOrigin(star: CelebrationStar, origin: CelebrationPoint, width: Float, age: Float): CelebrationPoint {
        val elapsed = max(0f, age)
        val travel = if (star.drag > 0) (1 - exp(-star.drag * elapsed)) / star.drag else elapsed
        return CelebrationPoint(origin.x + star.velocity.x * width * travel,
            origin.y + star.velocity.y * width * travel + width * 0.035f * elapsed * elapsed)
    }

    private fun scaled(point: CelebrationPoint, width: Float, height: Float) = CelebrationPoint(point.x * width, point.y * height)
    private fun smoothstep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

/** Immediate consumers must not retain the reusable trail coordinates. */
internal interface CelebrationSink {
    fun begin(width: Float, height: Float, backdrop: Float)
    fun trail(points: FloatArray, count: Int, width: Float, palette: CelebrationPalette, opacity: Float)
    fun glow(center: CelebrationPoint, radius: Float, palette: CelebrationPalette, opacity: Float)
    fun star(center: CelebrationPoint, radius: Float, fivePoint: Boolean, rotation: Float,
        palette: CelebrationPalette, opacity: Float)
}
