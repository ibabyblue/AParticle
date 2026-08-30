package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.ParticleColor
import com.ibabyblue.aparticle.ParticleShape
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

internal data class DrawableParticle(
    val x: Float,
    val y: Float,
    val opacity: Float,
    val scale: Float,
    val rotation: Float,
    val sizePx: Float,
    val shape: ParticleShape,
    val color: ParticleColor,
)

internal data class RiseAndBurstSnapshot(
    val launch: DrawableParticle?,
    val trails: List<DrawableParticle>,
    val particles: List<DrawableParticle>,
)

internal object ParticleSimulation {
    fun snapshot(plan: RiseAndBurstPlan, elapsedSeconds: Float): RiseAndBurstSnapshot {
        if (elapsedSeconds < 0f) return RiseAndBurstSnapshot(null, emptyList(), emptyList())
        if (elapsedSeconds < plan.launchDurationSeconds) {
            val linear = (elapsedSeconds / plan.launchDurationSeconds).coerceIn(0f, 1f)
            val progress = 1f - (1f - linear) * (1f - linear)
            return RiseAndBurstSnapshot(
                launch = DrawableParticle(
                    x = plan.launchX + sin(progress * PI.toFloat()) * plan.wobbleAmplitudePx * plan.curveDirection,
                    y = plan.launchY + (plan.burstY - plan.launchY) * progress,
                    opacity = (progress * 4f).coerceAtMost(1f),
                    scale = 1f,
                    rotation = 0f,
                    sizePx = plan.launchSizePx,
                    shape = plan.launchShape,
                    color = plan.launchColor,
                ),
                trails = emptyList(),
                particles = emptyList(),
            )
        }

        val age = elapsedSeconds - plan.launchDurationSeconds
        val trails = ArrayList<DrawableParticle>(plan.particles.size * plan.trailSampleCount)
        val particles = ArrayList<DrawableParticle>(plan.particles.size)
        plan.particles.forEach { particle ->
            trails += trailSnapshots(particle, age, plan.trailSampleCount)
            particleSnapshot(particle, age)?.let(particles::add)
        }
        return RiseAndBurstSnapshot(null, trails, particles)
    }

    fun particleSnapshot(particle: BurstParticlePlan, ageSeconds: Float): DrawableParticle? {
        if (ageSeconds < 0f || ageSeconds > particle.lifetimeSeconds) return null
        val (x, y) = if (particle.drag > 0f) {
            val decay = exp((-particle.drag * ageSeconds).toDouble()).toFloat()
            val displacementScale = (1f - decay) / particle.drag
            Pair(
                particle.originX + particle.velocityX * displacementScale,
                particle.originY +
                    (particle.velocityY - particle.gravity / particle.drag) * displacementScale +
                    particle.gravity * ageSeconds / particle.drag,
            )
        } else {
            Pair(
                particle.originX + particle.velocityX * ageSeconds,
                particle.originY + particle.velocityY * ageSeconds +
                    particle.gravity * ageSeconds * ageSeconds / 2f,
            )
        }
        val progress = (ageSeconds / particle.lifetimeSeconds).coerceIn(0f, 1f)
        return DrawableParticle(
            x = x,
            y = y,
            opacity = 1f - progress,
            scale = 1f - progress * 0.35f,
            rotation = particle.rotation + particle.angularVelocity * ageSeconds,
            sizePx = particle.sizePx,
            shape = particle.shape,
            color = particle.color,
        )
    }

    fun trailSnapshots(
        particle: BurstParticlePlan,
        ageSeconds: Float,
        sampleCount: Int,
    ): List<DrawableParticle> {
        if (sampleCount <= 0) return emptyList()
        return (sampleCount downTo 1).mapNotNull { offset ->
            val sample = particleSnapshot(particle, ageSeconds - offset / 60f) ?: return@mapNotNull null
            val closeness = (sampleCount - offset + 1f) / (sampleCount + 1f)
            sample.copy(
                opacity = sample.opacity * closeness * 0.45f,
                scale = sample.scale * 0.7f,
            )
        }
    }
}
