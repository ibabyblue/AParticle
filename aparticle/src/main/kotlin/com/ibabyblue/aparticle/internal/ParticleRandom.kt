package com.ibabyblue.aparticle.internal

internal interface ParticleRandom {
    fun nextFloat(): Float
}

internal class SeededParticleRandom(seed: Long) : ParticleRandom {
    private var state: Long = if (seed == 0L) 0x6A09E667F3BCC909L else seed

    override fun nextFloat(): Float {
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        return ((state ushr 40) and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat()
    }
}
