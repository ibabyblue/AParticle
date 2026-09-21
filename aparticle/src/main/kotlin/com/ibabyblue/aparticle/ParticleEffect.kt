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

    public companion object {
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
