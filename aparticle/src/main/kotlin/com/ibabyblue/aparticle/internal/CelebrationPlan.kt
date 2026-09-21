package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.CelebrationConfiguration
import com.ibabyblue.aparticle.ParticleColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Two float coordinates stored without allocating a temporary point on the frame path. */
@JvmInline
internal value class CelebrationPoint(private val coordinates: Long) {
    constructor(x: Float, y: Float) : this((x.toRawBits().toLong() shl 32) or (y.toRawBits().toLong() and 0xFFFFFFFFL))
    val x: Float get() = Float.fromBits((coordinates ushr 32).toInt())
    val y: Float get() = Float.fromBits(coordinates.toInt())
}

internal enum class CelebrationPalette(val fill: ParticleColor, val edge: ParticleColor) {
    Pink(ParticleColor(0.996f, 0.937f, 1f), ParticleColor(0.965f, 0.827f, 0.976f)),
    Blue(ParticleColor(0.843f, 0.933f, 1f), ParticleColor(0.718f, 0.871f, 0.976f)),
    Yellow(ParticleColor(1f, 0.996f, 0.827f), ParticleColor(0.976f, 0.933f, 0.682f)),
}

internal data class CelebrationFlight(
    val launch: Float, val flight: Float, val origin: CelebrationPoint,
    val control: CelebrationPoint, val center: CelebrationPoint, val palette: CelebrationPalette,
) {
    val start: Float get() = launch + flight
}

internal data class CelebrationSpark(
    val sourceIndex: Int, val velocity: CelebrationPoint, val duration: Float,
    val radius: Float, val delay: Float,
)

internal data class CelebrationSecondaryBurst(
    val sourceIndex: Int, val offset: CelebrationPoint, val delay: Float,
)

internal data class CelebrationStar(
    val center: CelebrationPoint, val radius: Float, val fivePoint: Boolean,
    val rotation: Float, val palette: CelebrationPalette, val start: Float,
    val phase: Float, val sourceIndex: Int, val twinkleRate: Float,
    val velocity: CelebrationPoint, val drag: Float, val lifetime: Float,
    val secondaryBurst: CelebrationSecondaryBurst? = null,
)

/** Immutable normalized choreography; radii are dp, velocity and offsets use canvas width. */
internal class CelebrationPlan(
    val startNanos: Long,
    val reduceMotion: Boolean,
    seed: Long,
    configuration: CelebrationConfiguration = CelebrationConfiguration(),
) {
    val backdropOpacity = configuration.resolvedBackdropOpacity
    val duration = if (reduceMotion) 0.8f else 4.2f
    val fireworks: List<CelebrationFlight>
    val sparks: List<CelebrationSpark>
    val secondaryBursts: List<CelebrationSecondaryBurst>
    val climaxStars: List<CelebrationStar>
    val secondaryStars: List<CelebrationStar>

    init {
        val random = SeededParticleRandom(seed)
        fun unit() = random.nextFloat()
        fun velocity(speed: Float, angle: Float) = CelebrationPoint(cos(angle) * speed, sin(angle) * speed)
        val flights = mutableListOf<CelebrationFlight>()
        val embers = mutableListOf<CelebrationSpark>()
        val stars = mutableListOf<CelebrationStar>()
        val bursts = mutableListOf<CelebrationSecondaryBurst>()
        val smallStars = mutableListOf<CelebrationStar>()
        if (!reduceMotion) {
            repeat(4 + min(4, (unit() * 5).toInt())) { index ->
                val center = CelebrationPoint(0.18f + unit() * 0.64f, 0.22f + unit() * 0.32f)
                val origin = CelebrationPoint(center.x - 0.30f - unit() * 0.18f, -0.025f)
                val flight = CelebrationFlight(
                    0.03f + index * 0.12f + unit() * 0.05f, 0.65f + unit() * 0.20f,
                    origin, CelebrationPoint(origin.x + (center.x - origin.x) * 0.48f, center.y * 0.36f),
                    center, CelebrationPalette.entries[index % 3],
                )
                flights += flight
                repeat(22) {
                    val angle = unit() * (PI * 2).toFloat()
                    val speed = (0.32f + unit() * 0.72f) * 1.12f
                    embers += CelebrationSpark(index, velocity(speed, angle), 0.58f + unit() * 0.4f,
                        0.8f + unit() * 1.3f, unit() * 0.04f)
                }
                repeat(26 + min(8, (unit() * 9).toInt())) { starIndex ->
                    val angle = unit() * (PI * 2).toFloat()
                    val speed = (0.24f + unit() * 0.82f) * 1.12f
                    stars += CelebrationStar(center, 2.8f + unit() * 3.6f, starIndex % 5 == 0,
                        unit() * PI.toFloat(), flight.palette, flight.start + unit() * 0.04f,
                        unit() * (PI * 2).toFloat(), index, 2.2f + unit() * 3.3f,
                        velocity(speed, angle), 1.9f + unit() * 1.1f, 2.4f + unit() * 1.2f)
                }
            }
            flights.forEachIndexed { index, flight ->
                repeat(5 + min(2, (unit() * 3).toInt())) {
                    val angle = unit() * (PI * 2).toFloat()
                    val distance = 0.08f + unit() * 0.12f
                    val burst = CelebrationSecondaryBurst(index, velocity(distance, angle), 0.30f + unit() * 0.30f)
                    bursts += burst
                    repeat(10 + min(5, (unit() * 6).toInt())) { starIndex ->
                        val starAngle = unit() * (PI * 2).toFloat()
                        val speed = 0.25f + unit() * 0.30f
                        smallStars += CelebrationStar(flight.center, 2.2f + unit() * 2.2f, starIndex % 4 == 0,
                            unit() * PI.toFloat(), flight.palette, flight.start + burst.delay + unit() * 0.02f,
                            unit() * (PI * 2).toFloat(), index, 3f + unit() * 3f,
                            velocity(speed, starAngle), 2.6f + unit() * 0.6f, 1.4f + unit() * 0.7f, burst)
                    }
                }
            }
        }
        fireworks = flights
        sparks = embers
        climaxStars = stars
        secondaryBursts = bursts
        secondaryStars = smallStars
    }

    companion object {
        val reducedStars = List(12) { index ->
            CelebrationStar(CelebrationPoint(unit(index * 7 + 1), 0.08f + unit(index * 11 + 2) * 0.60f),
                4f + unit(index * 13 + 4) * 4f, index % 4 == 0, unit(index + 8) * PI.toFloat(),
                CelebrationPalette.entries[index % 3], 0f, 0f, 0, 0f, CelebrationPoint(0f, 0f), 0f, 0.8f)
        }
        private fun unit(seed: Int): Float = (abs(sin(seed * 12.9898) * 43_758.5453) % 1).toFloat()
    }
}
