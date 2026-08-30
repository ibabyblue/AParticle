package com.ibabyblue.aparticle.internal

internal class TriggerLedger(initial: Any?) {
    private var last: Any? = initial

    fun observe(value: Any?): Boolean {
        if (last == value) return false
        last = value
        return true
    }

    fun register(value: Any?) {
        last = value
    }
}
