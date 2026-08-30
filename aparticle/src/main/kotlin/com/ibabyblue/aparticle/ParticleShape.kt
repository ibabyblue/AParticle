package com.ibabyblue.aparticle

/** A shape drawn by AParticle's Compose and Android View renderers. */
public sealed interface ParticleShape {
    /** A filled heart. */
    public data object Heart : ParticleShape

    /** A filled circle. */
    public data object Circle : ParticleShape

    /** A filled five-point star. */
    public data object Star : ParticleShape

    /**
     * An Android drawable resource tinted with the particle color.
     *
     * If the resource cannot be resolved, [fallback] is drawn instead.
     */
    public data class DrawableResource(
        public val resourceId: Int,
        public val fallback: BuiltIn = BuiltIn.Circle,
    ) : ParticleShape

    /** Built-in fallback choices for drawable resources. */
    public enum class BuiltIn {
        Heart,
        Circle,
        Star,
    }
}
