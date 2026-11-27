package com.ibabyblue.aparticle

/** A particle effect supported by AParticle's native Android renderers. */
public sealed interface ParticleEffect {
    /** One shape rises from below the canvas and bursts into smaller particles. */
    public data class RiseAndBurst(
        public val configuration: RiseAndBurstConfiguration = RiseAndBurstConfiguration(),
    ) : ParticleEffect

    /** A continuous transparent atmosphere of sparkles, bubbles, and edge glows. */
    public data class Atmosphere(
        public val configuration: AtmosphereConfiguration = AtmosphereConfiguration(),
    ) : ParticleEffect

    /** A restartable 4.2-second meteor, firework, and twinkling-star celebration. */
    public data class Celebration(
        public val configuration: CelebrationConfiguration = CelebrationConfiguration(),
    ) : ParticleEffect

    /** Persistent snow that follows the drawing surface and ignores touches. */
    public sealed interface Snow : ParticleEffect

    /** Fine snow with individually timed pastel stars. */
    public object TwinklingSnow : Snow

    /** Snowflakes drifting down and outward, growing into white six-armed crystals. */
    public class PerspectiveSnow internal constructor(
        public val amount: Int,
        public val speed: Float,
    ) : Snow {
        override fun equals(other: Any?): Boolean =
            other is PerspectiveSnow && amount == other.amount && speed == other.speed

        override fun hashCode(): Int = 31 * amount + speed.hashCode()
    }

    public companion object {
        /** Returns continuous falling snow with a repeating 5.628-second star sequence. */
        public fun twinklingSnow(): Snow = TwinklingSnow

        /**
         * Returns a continuous field of white perspective snow.
         * @param amount Number of flakes, bounded to 120 through 600.
         * @param speed Fall-speed multiplier, bounded to 0.4 through 1.4. Nonfinite values use 0.9.
         */
        public fun perspectiveSnow(amount: Int = 500, speed: Float = 0.9f): PerspectiveSnow =
            PerspectiveSnow(amount.coerceIn(120, 600), if (speed.isFinite()) speed.coerceIn(.4f, 1.4f) else .9f)

        /** Creates a celebration with an optional noninteractive black backdrop. */
        public fun celebration(
            configuration: CelebrationConfiguration = CelebrationConfiguration(),
        ): Celebration = Celebration(configuration)

        /** Creates a one-shot rise-and-burst effect. */
        public fun riseAndBurst(
            configuration: RiseAndBurstConfiguration = RiseAndBurstConfiguration(),
        ): RiseAndBurst = RiseAndBurst(configuration)

        /** Creates a continuous atmosphere effect. */
        public fun atmosphere(
            configuration: AtmosphereConfiguration = AtmosphereConfiguration(),
        ): Atmosphere = Atmosphere(configuration)
    }
}
