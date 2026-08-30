package com.ibabyblue.aparticle

import kotlin.math.roundToInt

/** A finite sRGB color used by both AParticle renderers. */
public class ParticleColor(
    red: Float,
    green: Float,
    blue: Float,
    alpha: Float = 1f,
) {
    /** Red component resolved into `0..1`. */
    public val red: Float = red.resolvedUnit()

    /** Green component resolved into `0..1`. */
    public val green: Float = green.resolvedUnit()

    /** Blue component resolved into `0..1`. */
    public val blue: Float = blue.resolvedUnit()

    /** Alpha component resolved into `0..1`. */
    public val alpha: Float = alpha.resolvedUnit()

    internal val argb: Int
        get() = android.graphics.Color.argb(
            (alpha.resolvedUnit() * 255).roundToInt(),
            (red.resolvedUnit() * 255).roundToInt(),
            (green.resolvedUnit() * 255).roundToInt(),
            (blue.resolvedUnit() * 255).roundToInt(),
        )

    internal fun withMultipliedAlpha(multiplier: Float): Int {
        val base = alpha.resolvedUnit()
        return android.graphics.Color.argb(
            (base * multiplier.coerceIn(0f, 1f) * 255).roundToInt(),
            (red.resolvedUnit() * 255).roundToInt(),
            (green.resolvedUnit() * 255).roundToInt(),
            (blue.resolvedUnit() * 255).roundToInt(),
        )
    }

    override fun equals(other: Any?): Boolean = other is ParticleColor &&
        red == other.red && green == other.green && blue == other.blue && alpha == other.alpha

    override fun hashCode(): Int {
        var result = red.hashCode()
        result = 31 * result + green.hashCode()
        result = 31 * result + blue.hashCode()
        result = 31 * result + alpha.hashCode()
        return result
    }

    override fun toString(): String = "ParticleColor(red=$red, green=$green, blue=$blue, alpha=$alpha)"

    public companion object {
        /** The default warm rose color. */
        public val Rose: ParticleColor = ParticleColor(1f, 77f / 255f, 141f / 255f)

        /** A light pink accent. */
        public val Pink: ParticleColor = ParticleColor(1f, 122f / 255f, 170f / 255f)

        /** A purple accent available to custom configurations. */
        public val Purple: ParticleColor = ParticleColor(194f / 255f, 110f / 255f, 232f / 255f)

        /** Neutral white for atmosphere sparkles. */
        public val White: ParticleColor = ParticleColor(1f, 1f, 1f)

        /** Pale pink equivalent to `#FFE8F2`. */
        public val PalePink: ParticleColor = ParticleColor(1f, 232f / 255f, 242f / 255f)
    }
}

internal fun Float.resolvedUnit(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f
