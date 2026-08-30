package com.ibabyblue.aparticle

/**
 * Configures a rise-and-burst effect.
 *
 * Visual dimensions use density-independent pixels and time uses seconds.
 * Normalized spawn and burst ranges are resolved against the current canvas.
 */
public data class RiseAndBurstConfiguration(
    public val launchShape: ParticleShape = ParticleShape.Heart,
    public val burstShape: ParticleShape = ParticleShape.Heart,
    public val colors: List<ParticleColor> = listOf(ParticleColor.Rose),
    public val spawnXRange: ClosedFloatingPointRange<Float> = 0.15f..0.85f,
    public val burstYRange: ClosedFloatingPointRange<Float> = 0.15f..0.50f,
    public val launchSizeRange: ClosedFloatingPointRange<Float> = 24f..48f,
    public val launchDurationRange: ClosedFloatingPointRange<Float> = 0.6f..0.9f,
    public val wobbleAmplitudeRange: ClosedFloatingPointRange<Float> = 10f..18f,
    public val burstParticleCountRange: IntRange = 20..33,
    public val particleSizeRange: ClosedFloatingPointRange<Float> = 4f..10f,
    public val burstSpeedRange: ClosedFloatingPointRange<Float> = 140f..300f,
    public val gravity: Float = 240f,
    public val drag: Float = 1.8f,
    public val particleLifetimeRange: ClosedFloatingPointRange<Float> = 0.8f..1.2f,
    public val trailSampleCount: Int = 5,
) {
    internal fun resolved(): ResolvedRiseAndBurstConfiguration {
        val defaults = RiseAndBurstConfiguration()
        return ResolvedRiseAndBurstConfiguration(
            launchShape = launchShape,
            burstShape = burstShape,
            colors = colors.ifEmpty { defaults.colors },
            spawnXRange = spawnXRange.resolved(defaults.spawnXRange, 0f..1f),
            burstYRange = burstYRange.resolved(defaults.burstYRange, 0f..1f),
            launchSizeRange = launchSizeRange.resolved(defaults.launchSizeRange, 1f..512f),
            launchDurationRange = launchDurationRange.resolved(defaults.launchDurationRange, 0.1f..30f),
            wobbleAmplitudeRange = wobbleAmplitudeRange.resolved(defaults.wobbleAmplitudeRange, 1f..512f),
            burstParticleCountRange = burstParticleCountRange.resolved(1..200),
            particleSizeRange = particleSizeRange.resolved(defaults.particleSizeRange, 1f..512f),
            burstSpeedRange = burstSpeedRange.resolved(defaults.burstSpeedRange, 0f..2_000f),
            gravity = gravity.resolved(defaults.gravity, 0f..2_000f),
            drag = drag.resolved(defaults.drag, 0f..2_000f),
            particleLifetimeRange = particleLifetimeRange.resolved(defaults.particleLifetimeRange, 0.1f..30f),
            trailSampleCount = trailSampleCount.coerceIn(0, 12),
        )
    }
}

internal data class ResolvedRiseAndBurstConfiguration(
    val launchShape: ParticleShape,
    val burstShape: ParticleShape,
    val colors: List<ParticleColor>,
    val spawnXRange: ClosedFloatingPointRange<Float>,
    val burstYRange: ClosedFloatingPointRange<Float>,
    val launchSizeRange: ClosedFloatingPointRange<Float>,
    val launchDurationRange: ClosedFloatingPointRange<Float>,
    val wobbleAmplitudeRange: ClosedFloatingPointRange<Float>,
    val burstParticleCountRange: IntRange,
    val particleSizeRange: ClosedFloatingPointRange<Float>,
    val burstSpeedRange: ClosedFloatingPointRange<Float>,
    val gravity: Float,
    val drag: Float,
    val particleLifetimeRange: ClosedFloatingPointRange<Float>,
    val trailSampleCount: Int,
)

private fun ClosedFloatingPointRange<Float>.resolved(
    fallback: ClosedFloatingPointRange<Float>,
    limits: ClosedFloatingPointRange<Float>,
): ClosedFloatingPointRange<Float> {
    if (!start.isFinite() || !endInclusive.isFinite()) return fallback
    return start.coerceIn(limits.start, limits.endInclusive)..endInclusive.coerceIn(limits.start, limits.endInclusive)
}

private fun IntRange.resolved(limits: IntRange): IntRange =
    first.coerceIn(limits)..last.coerceIn(limits)

private fun Float.resolved(fallback: Float, limits: ClosedFloatingPointRange<Float>): Float =
    if (isFinite()) coerceIn(limits.start, limits.endInclusive) else fallback
