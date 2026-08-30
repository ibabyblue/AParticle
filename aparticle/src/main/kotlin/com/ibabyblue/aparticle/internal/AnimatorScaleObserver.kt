package com.ibabyblue.aparticle.internal

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

internal class AnimatorScaleObserver(
    context: Context,
    private val onChanged: (Boolean) -> Unit,
) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val applicationContext = context.applicationContext
    private var registered = false

    val animationsEnabled: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(
                applicationContext.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }

    fun start() {
        if (registered) return
        registered = true
        applicationContext.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            this,
        )
        onChanged(animationsEnabled)
    }

    fun stop() {
        if (!registered) return
        registered = false
        applicationContext.contentResolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean) {
        onChanged(animationsEnabled)
    }
}
