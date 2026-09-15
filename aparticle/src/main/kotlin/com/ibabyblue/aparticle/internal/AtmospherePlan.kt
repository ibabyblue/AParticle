package com.ibabyblue.aparticle.internal

import com.ibabyblue.aparticle.ParticleColor
import com.ibabyblue.aparticle.ResolvedAtmosphereConfiguration
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

internal enum class SparkleKind { Dot, FourPoint }

internal enum class BubbleEdge { Left, Right, Bottom, TopCorner }

internal data class SparklePlan(
    val normalizedX: Float,
    val normalizedY: Float,
    val usesCentralUpperZone: Boolean,
    val sizePx: Float,
    val color: ParticleColor,
    val kind: SparkleKind,
    val phase: Float,
    val twinkleSpeed: Float,
    val baseAlpha: Float,
    val driftPhase: Float,
    val driftAmplitudeX: Float,
    val driftAmplitudeY: Float,
)

internal data class BubblePlan(
    val edge: BubbleEdge,
    val normalizedX: Float,
    val normalizedY: Float,
    val radiusPx: Float,
    val baseAlpha: Float,
    val phase: Float,
    val driftAmplitudeX: Float,
    val driftAmplitudeY: Float,
    val driftFrequencyX: Float,
    val driftFrequencyY: Float,
    val breathingPhase: Float,
    val breathingSpeed: Float,
)

internal data class AtmospherePlan(
    val seed: Long,
    val startNanos: Long,
    val configuration: ResolvedAtmosphereConfiguration,
    val sparkles: List<SparklePlan>,
    val bubbles: List<BubblePlan>,
    val density: Float = 1f,
) {
    companion object {
        fun make(
            configuration: ResolvedAtmosphereConfiguration,
            widthPx: Float,
            heightPx: Float,
            density: Float,
            startNanos: Long,
            seed: Long,
        ): AtmospherePlan {
            if (widthPx <= 0f || heightPx <= 0f || !widthPx.isFinite() || !heightPx.isFinite()) {
                return AtmospherePlan(seed, startNanos, configuration, emptyList(), emptyList())
            }
            val safeDensity = density.coerceAtLeast(0.01f)
            val areaDp = (widthPx / safeDensity) * (heightPx / safeDensity)
            val sparkleCount = floor(areaDp / 4_500f * configuration.sparkleDensity)
                .toInt().coerceIn(0, 320)
            val bubbleCount = floor((10f + areaDp / 40_000f) * configuration.bubbleDensity)
                .toInt().coerceIn(0, 32)
            val random = SeededParticleRandom(seed)
            val centralCount = ceil(sparkleCount * 0.75f).toInt()
            val sparkles = List(sparkleCount) { index ->
                makeSparkle(index < centralCount, configuration, safeDensity, random)
            }
            val bubbles = List(bubbleCount) { index ->
                makeBubble(index, bubbleCount, safeDensity, random)
            }
            return AtmospherePlan(seed, startNanos, configuration, sparkles, bubbles, safeDensity)
        }

        private fun makeSparkle(
            central: Boolean,
            configuration: ResolvedAtmosphereConfiguration,
            density: Float,
            random: ParticleRandom,
        ): SparklePlan {
            val xRange = if (central) 0.10f..0.90f else 0f..1f
            val yRange = if (central) 0.05f..0.80f else 0f..1f
            return SparklePlan(
                normalizedX = random.sample(xRange),
                normalizedY = random.sample(yRange),
                usesCentralUpperZone = central,
                sizePx = random.sample(0.8f..3.3f) * density,
                color = random.sampleColor(configuration.sparkleColors),
                kind = if (random.nextFloat() < 0.55f) SparkleKind.FourPoint else SparkleKind.Dot,
                phase = random.sample(0f..(2f * PI.toFloat())),
                twinkleSpeed = random.sample(0.4f..1.6f),
                baseAlpha = random.sample(0.5f..1f),
                driftPhase = random.sample(0f..(2f * PI.toFloat())),
                driftAmplitudeX = random.sample(2.5f..8.5f) * density,
                driftAmplitudeY = random.sample(1.5f..5.5f) * density,
            )
        }

        private fun makeBubble(
            index: Int,
            count: Int,
            density: Float,
            random: ParticleRandom,
        ): BubblePlan {
            val quota = (index + 0.5f) / count.coerceAtLeast(1)
            val edge: BubbleEdge
            val x: Float
            val y: Float
            when {
                quota < 0.28f -> {
                    edge = BubbleEdge.Left
                    x = random.sample(-0.05f..0.22f)
                    y = random.sample(0f..1f)
                }
                quota < 0.56f -> {
                    edge = BubbleEdge.Right
                    x = random.sample(0.78f..1.05f)
                    y = random.sample(0f..1f)
                }
                quota < 0.78f -> {
                    edge = BubbleEdge.Bottom
                    x = random.sample(0f..1f)
                    y = random.sample(0.65f..1.05f)
                }
                else -> {
                    edge = BubbleEdge.TopCorner
                    x = random.sample(if (random.nextFloat() < 0.5f) 0f..0.20f else 0.80f..1f)
                    y = random.sample(-0.04f..0.25f)
                }
            }
            return BubblePlan(
                edge = edge,
                normalizedX = x,
                normalizedY = y,
                radiusPx = random.sample(38f..118f) * density,
                baseAlpha = random.sample(0.05f..0.25f),
                phase = random.sample(0f..(2f * PI.toFloat())),
                driftAmplitudeX = random.sample(28f..83f) * density,
                driftAmplitudeY = random.sample(18f..56f) * density,
                driftFrequencyX = random.sample(0.08f..0.23f),
                driftFrequencyY = random.sample(0.06f..0.18f),
                breathingPhase = random.sample(0f..(2f * PI.toFloat())),
                breathingSpeed = random.sample(0.15f..0.45f),
            )
        }
    }
}

internal data class SparkleSnapshot(
    val x: Float,
    val y: Float,
    val sizePx: Float,
    val opacity: Float,
    val color: ParticleColor,
    val kind: SparkleKind,
    val density: Float = 1f,
)

internal data class BubbleSnapshot(
    val x: Float,
    val y: Float,
    val radiusPx: Float,
    val opacity: Float,
)

internal data class AtmosphereSnapshot(
    val widthPx: Float,
    val heightPx: Float,
    val glowColor: ParticleColor,
    val glowIntensity: Float,
    val breath: Float,
    val sparkles: List<SparkleSnapshot>,
    val bubbles: List<BubbleSnapshot>,
)

internal object AtmosphereSimulation {
    fun snapshot(
        plan: AtmospherePlan,
        widthPx: Float,
        heightPx: Float,
        elapsedSeconds: Float,
    ): AtmosphereSnapshot {
        val time = if (elapsedSeconds.isFinite()) elapsedSeconds.coerceAtLeast(0f) else 0f
        // Mira uses IParticleView: linear, autoreversing Core Animation keyframes.
        val breath = triangle(time / plan.configuration.breathingDuration)
        return AtmosphereSnapshot(
            widthPx = widthPx,
            heightPx = heightPx,
            glowColor = plan.configuration.glowColor,
            glowIntensity = plan.configuration.glowIntensity,
            breath = breath.coerceIn(0f, 1f),
            sparkles = plan.sparkles.map { sparkle ->
                SparkleSnapshot(
                    x = sparkle.normalizedX * widthPx + keyframeDrift(time * 0.18f / TWO_PI) {
                        sin(it + sparkle.driftPhase) * sparkle.driftAmplitudeX
                    },
                    y = sparkle.normalizedY * heightPx + keyframeDrift(time * 0.18f / TWO_PI) {
                        cos(it + sparkle.driftPhase) * sparkle.driftAmplitudeY
                    },
                    sizePx = sparkle.sizePx,
                    // CAKeyframeAnimation replaces the model layer's baseAlpha.
                    opacity = 0.25f + 0.75f * triangle((time * sparkle.twinkleSpeed + sparkle.phase) / PI.toFloat()),
                    color = sparkle.color,
                    kind = sparkle.kind,
                    density = plan.density,
                )
            },
            bubbles = plan.bubbles.map { bubble ->
                BubbleSnapshot(
                    x = bubble.normalizedX * widthPx + keyframeDrift(time / max(16f, TWO_PI / bubble.driftFrequencyX)) {
                        sin(it + bubble.phase) * bubble.driftAmplitudeX
                    },
                    y = bubble.normalizedY * heightPx + keyframeDrift(time / max(16f, TWO_PI / bubble.driftFrequencyX)) {
                        cos((it + bubble.phase) * 0.82f) * bubble.driftAmplitudeY
                    },
                    radiusPx = bubble.radiusPx,
                    opacity = (bubble.baseAlpha *
                        (0.65f + 0.35f * triangle((time * bubble.breathingSpeed + bubble.breathingPhase) / TWO_PI)))
                        .coerceIn(0f, 1f),
                )
            },
        )
    }

    private val TWO_PI = 2f * PI.toFloat()

    private fun triangle(cycles: Float): Float {
        val progress = cycles - floor(cycles)
        return 1f - kotlin.math.abs(2f * progress - 1f)
    }

    /** UIKit interpolates nine sampled positions over eight linear segments. */
    private inline fun keyframeDrift(cycles: Float, valueAtAngle: (Float) -> Float): Float {
        val segment = (cycles - floor(cycles)) * 8f
        val index = floor(segment)
        val from = valueAtAngle(index * TWO_PI / 8f)
        val to = valueAtAngle((index + 1f) * TWO_PI / 8f)
        return from + (to - from) * (segment - index)
    }
}

private fun ParticleRandom.sample(range: ClosedFloatingPointRange<Float>): Float =
    range.start + (range.endInclusive - range.start) * nextFloat().coerceIn(0f, 1f)

private fun ParticleRandom.sampleColor(colors: List<ParticleColor>): ParticleColor {
    if (colors.size == 1) return colors.first()
    if (colors.size == 2) return if (nextFloat() < 0.20f) colors[1] else colors[0]
    return colors[(nextFloat().coerceIn(0f, 0.999999f) * colors.size).toInt()]
}
