package com.ibabyblue.aparticle

/**
 * Optional black backdrop beneath a celebration. [backdropOpacity] defaults to zero;
 * finite values are clamped to `0..1`, and non-finite values resolve to zero.
 *
 * The backdrop covers the particle surface and shares the 4.2-second timeline
 * (0.25-second entrance, 0.65-second exit). With system animations disabled,
 * twelve stationary stars and the backdrop fade over 0.8 seconds instead.
 * Configuration is captured on play/trigger; changing it alone does not restart.
 */
public data class CelebrationConfiguration(
    public val backdropOpacity: Float = 0f,
) {
    internal val resolvedBackdropOpacity: Float get() = backdropOpacity.resolvedUnit()
}
