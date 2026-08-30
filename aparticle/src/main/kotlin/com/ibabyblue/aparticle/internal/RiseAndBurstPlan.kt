package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.ParticleColor
import com.ibabyblue.aparticle.ParticleShape
import com.ibabyblue.aparticle.ResolvedRiseAndBurstConfiguration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class BurstParticlePlan(
    val originX: Float,
    val originY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val gravity: Float,
    val drag: Float,
    val lifetimeSeconds: Float,
    val sizePx: Float,
    val rotation: Float,
    val angularVelocity: Float,
    val color: ParticleColor,
    val shape: ParticleShape,
)

internal data class RiseAndBurstPlan(
    val seed: Long,
    val startNanos: Long,
    val launchX: Float,
    val launchY: Float,
    val burstX: Float,
    val burstY: Float,
    val launchSizePx: Float,
    val launchDurationSeconds: Float,
    val wobbleAmplitudePx: Float,
    val curveDirection: Float,
    val launchShape: ParticleShape,
    val launchColor: ParticleColor,
    val particles: List<BurstParticlePlan>,
    val trailSampleCount: Int,
) {
    val totalDurationSeconds: Float =
        launchDurationSeconds + (particles.maxOfOrNull { it.lifetimeSeconds } ?: 0f) + 0.2f

    companion object {
        fun make(
            configuration: ResolvedRiseAndBurstConfiguration,
            widthPx: Float,
            heightPx: Float,
            density: Float,
            startNanos: Long,
            seed: Long,
        ): RiseAndBurstPlan {
            val random = SeededParticleRandom(seed)
            val x = random.sample(configuration.spawnXRange) * widthPx
            val burstY = random.sample(configuration.burstYRange) * heightPx
            val launchSizePx = random.sample(configuration.launchSizeRange) * density
            val launchDuration = random.sample(configuration.launchDurationRange)
            val wobblePx = random.sample(configuration.wobbleAmplitudeRange) * density
            val curveDirection = if (random.nextFloat() < 0.5f) -1f else 1f
            val particleCount = random.sample(configuration.burstParticleCountRange)
            val launchColor = configuration.colors.first()
            val particles = List(particleCount) { index ->
                val angle = random.sample(0f..(2f * PI.toFloat()))
                val speedPx = random.sample(configuration.burstSpeedRange) * density
                BurstParticlePlan(
                    originX = x,
                    originY = burstY,
                    velocityX = cos(angle) * speedPx,
                    velocityY = sin(angle) * speedPx,
                    gravity = configuration.gravity * density,
                    drag = configuration.drag,
                    lifetimeSeconds = random.sample(configuration.particleLifetimeRange),
                    sizePx = random.sample(configuration.particleSizeRange) * density,
                    rotation = random.sample(0f..(2f * PI.toFloat())),
                    angularVelocity = random.sample((-2f * PI.toFloat())..(2f * PI.toFloat())),
                    color = launchColor,
                    shape = if (index % 5 == 0) ParticleShape.Circle else configuration.burstShape,
                )
            }
            return RiseAndBurstPlan(
                seed = seed,
                startNanos = startNanos,
                launchX = x,
                launchY = heightPx + launchSizePx / 2f,
                burstX = x,
                burstY = burstY,
                launchSizePx = launchSizePx,
                launchDurationSeconds = launchDuration,
                wobbleAmplitudePx = wobblePx,
                curveDirection = curveDirection,
                launchShape = configuration.launchShape,
                launchColor = launchColor,
                particles = particles,
                trailSampleCount = configuration.trailSampleCount,
            )
        }
    }
}

private fun ParticleRandom.sample(range: ClosedFloatingPointRange<Float>): Float =
    range.start + (range.endInclusive - range.start) * nextFloat().coerceIn(0f, 1f)

private fun ParticleRandom.sample(range: IntRange): Int {
    val count = range.last - range.first + 1
    return range.first + (nextFloat().coerceIn(0f, 0.999999f) * count).toInt()
}
