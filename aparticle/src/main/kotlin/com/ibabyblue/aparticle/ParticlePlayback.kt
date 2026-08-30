package com.ibabyblue.aparticle

/** Opaque identity for one [AParticleView] playback. */
public class ParticlePlayback internal constructor(internal val id: Long) {
    override fun equals(other: Any?): Boolean = other is ParticlePlayback && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ParticlePlayback(opaque)"
}
