package com.ibabyblue.aparticle

/** Configures the continuous noninteractive atmosphere effect. */
public data class AtmosphereConfiguration(
    public val glowColor: ParticleColor = ParticleColor(220f / 255f, 70f / 255f, 105f / 255f),
    public val sparkleColors: List<ParticleColor> = listOf(ParticleColor.White, ParticleColor.PalePink),
    public val sparkleDensity: Float = 1f,
    public val bubbleDensity: Float = 1f,
    public val glowIntensity: Float = 1f,
    public val breathingDuration: Float = 5f,
) {
    internal fun resolved(): ResolvedAtmosphereConfiguration {
        val defaults = AtmosphereConfiguration()
        return ResolvedAtmosphereConfiguration(
            glowColor = glowColor,
            sparkleColors = sparkleColors.ifEmpty { listOf(ParticleColor.White) },
            sparkleDensity = sparkleDensity.resolved(defaults.sparkleDensity, 0f..3f),
            bubbleDensity = bubbleDensity.resolved(defaults.bubbleDensity, 0f..3f),
            glowIntensity = glowIntensity.resolved(defaults.glowIntensity, 0f..2f),
            breathingDuration = breathingDuration.resolved(defaults.breathingDuration, 2f..20f),
        )
    }
}

internal data class ResolvedAtmosphereConfiguration(
    val glowColor: ParticleColor,
    val sparkleColors: List<ParticleColor>,
    val sparkleDensity: Float,
    val bubbleDensity: Float,
    val glowIntensity: Float,
    val breathingDuration: Float,
)

private fun Float.resolved(fallback: Float, limits: ClosedFloatingPointRange<Float>): Float =
    if (isFinite()) coerceIn(limits.start, limits.endInclusive) else fallback
