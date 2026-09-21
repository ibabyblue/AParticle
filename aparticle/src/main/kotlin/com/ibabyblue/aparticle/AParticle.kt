package com.ibabyblue.aparticle

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ibabyblue.aparticle.internal.AnimatorScaleObserver
import com.ibabyblue.aparticle.internal.AtmospherePlan
import com.ibabyblue.aparticle.internal.AtmosphereSimulation
import com.ibabyblue.aparticle.internal.CelebrationCanvasRenderer
import com.ibabyblue.aparticle.internal.CelebrationPlan
import com.ibabyblue.aparticle.internal.ParticleCanvasRenderer
import com.ibabyblue.aparticle.internal.ParticleSimulation
import com.ibabyblue.aparticle.internal.RiseAndBurstPlan
import com.ibabyblue.aparticle.internal.TriggerLedger
import kotlin.math.abs

/**
 * Draws a transparent, noninteractive particle layer with Jetpack Compose.
 *
 * A [ParticleEffect.RiseAndBurst] registers the initial [trigger] without
 * playing and starts one concurrent playback for each later distinct value.
 * A [ParticleEffect.Atmosphere] requires a Boolean trigger: `true` activates
 * it and `false` stops it. A non-Boolean atmosphere trigger is a safe no-op.
 *
 * A [ParticleEffect.Celebration] also registers its initial trigger silently;
 * each later distinct value replaces the previous celebration. System animation
 * scale zero selects twelve stationary fading stars for this effect only.
 *
 * The layer follows [modifier]'s bounds, does not affect sibling layout, does
 * not install pointer input, and clears animation when its lifecycle stops or
 * the system animator scale changes (celebration) or becomes zero (other effects).
 */
@Composable
public fun AParticle(
    effect: ParticleEffect,
    trigger: Any?,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val lifecycleActive = rememberLifecycleActive()
    when (effect) {
        is ParticleEffect.Celebration -> CelebrationParticleCanvas(
            effect, trigger, animationsEnabled, lifecycleActive, modifier,
        )
        is ParticleEffect.RiseAndBurst -> TransientParticleCanvas(
            effect = effect,
            trigger = trigger,
            animationsEnabled = animationsEnabled,
            lifecycleActive = lifecycleActive,
            modifier = modifier,
        )
        is ParticleEffect.Atmosphere -> AtmosphereParticleCanvas(
            effect = effect,
            requestedActive = trigger as? Boolean,
            animationsEnabled = animationsEnabled,
            lifecycleActive = lifecycleActive,
            modifier = modifier,
        )
    }
}

@Composable
private fun CelebrationParticleCanvas(
    effect: ParticleEffect.Celebration,
    trigger: Any?,
    animationsEnabled: Boolean,
    lifecycleActive: Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    val renderer = remember { CelebrationCanvasRenderer() }
    val ledger = remember { TriggerLedger(trigger) }
    var plan by remember { mutableStateOf<CelebrationPlan?>(null) }
    var pending by remember { mutableStateOf<CelebrationConfiguration?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frameNanos by remember { mutableLongStateOf(0L) }
    var previousEnabled by remember { mutableStateOf(animationsEnabled) }
    var previousDensity by remember { mutableFloatStateOf(density) }

    LaunchedEffect(trigger, animationsEnabled, lifecycleActive, density) {
        val changed = ledger.observe(trigger)
        if (!lifecycleActive || previousEnabled != animationsEnabled || previousDensity != density) {
            plan = null
            pending = null
        }
        previousEnabled = animationsEnabled
        previousDensity = density
        if (changed && lifecycleActive) {
            if (size.width > 0 && size.height > 0) {
                renderer.prepare()
                plan = CelebrationPlan(System.nanoTime(), !animationsEnabled, nextSeed(0), effect.configuration)
            } else {
                pending = effect.configuration
            }
        }
    }
    LaunchedEffect(plan) {
        val current = plan ?: return@LaunchedEffect
        while (plan === current) {
            frameNanos = withFrameNanos { it }
            if (elapsedSeconds(frameNanos, current.startNanos) >= current.duration) plan = null
        }
    }
    Canvas(modifier.onSizeChanged { newSize ->
        val changed = size.width > 0 && size.height > 0 &&
            (abs(size.width - newSize.width) > 1 || abs(size.height - newSize.height) > 1)
        size = newSize
        if (changed || newSize.width <= 0 || newSize.height <= 0) plan = null
        if (changed) pending = null
        if (newSize.width > 0 && newSize.height > 0 && lifecycleActive) {
            pending?.let { configuration ->
                renderer.prepare()
                plan = CelebrationPlan(System.nanoTime(), !animationsEnabled, nextSeed(0), configuration)
                pending = null
            }
        }
    }) {
        plan?.let { current ->
            renderer.draw(drawContext.canvas.nativeCanvas, current, size.width.toFloat(), size.height.toFloat(),
                density, elapsedSeconds(frameNanos, current.startNanos))
        }
    }
}

@Composable
private fun TransientParticleCanvas(
    effect: ParticleEffect.RiseAndBurst,
    trigger: Any?,
    animationsEnabled: Boolean,
    lifecycleActive: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(context) { ParticleCanvasRenderer(context) }
    val ledger = remember { TriggerLedger(trigger) }
    val plans = remember { mutableStateListOf<RiseAndBurstPlan>() }
    var pendingCount by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(trigger, animationsEnabled, lifecycleActive) {
        val changed = ledger.observe(trigger)
        if (!animationsEnabled || !lifecycleActive) {
            pendingCount = 0
            plans.clear()
            ledger.register(trigger)
        } else if (changed) {
            if (size.width > 0 && size.height > 0) {
                plans += makeRisePlan(effect, size, density, System.nanoTime(), nextSeed(plans.size))
            } else {
                pendingCount += 1
            }
        }
    }

    LaunchedEffect(plans.isNotEmpty(), animationsEnabled, lifecycleActive) {
        if (!animationsEnabled || !lifecycleActive || plans.isEmpty()) return@LaunchedEffect
        while (plans.isNotEmpty()) {
            frameNanos = withFrameNanos { it }
            plans.removeAll { plan ->
                elapsedSeconds(frameNanos, plan.startNanos) > plan.totalDurationSeconds
            }
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged { newSize ->
            val materialChange = size.width > 0 && size.height > 0 &&
                (abs(size.width - newSize.width) > 1 || abs(size.height - newSize.height) > 1)
            size = newSize
            if (materialChange) plans.clear()
            if (newSize.width > 0 && newSize.height > 0 && pendingCount > 0 && animationsEnabled && lifecycleActive) {
                repeat(pendingCount) { index ->
                    plans += makeRisePlan(
                        effect,
                        newSize,
                        density,
                        System.nanoTime(),
                        nextSeed(plans.size + index),
                    )
                }
                pendingCount = 0
            }
        },
    ) {
        plans.forEach { plan ->
            renderer.drawRiseAndBurst(
                drawContext.canvas.nativeCanvas,
                ParticleSimulation.snapshot(plan, elapsedSeconds(frameNanos, plan.startNanos)),
            )
        }
    }
}

@Composable
private fun AtmosphereParticleCanvas(
    effect: ParticleEffect.Atmosphere,
    requestedActive: Boolean?,
    animationsEnabled: Boolean,
    lifecycleActive: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(context) { ParticleCanvasRenderer(context) }
    val stableSeed = remember { nextSeed(0) }
    var blockedUntilFalse by remember { mutableStateOf(false) }
    var lifecycleHasStarted by remember { mutableStateOf(lifecycleActive) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var plan by remember { mutableStateOf<AtmospherePlan?>(null) }
    var frameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(requestedActive, animationsEnabled, lifecycleActive) {
        if (lifecycleActive) lifecycleHasStarted = true
        when {
            requestedActive != true -> {
                plan = null
                blockedUntilFalse = false
            }
            !animationsEnabled || !lifecycleActive -> {
                plan = null
                if (!animationsEnabled || lifecycleHasStarted) blockedUntilFalse = true
            }
            !blockedUntilFalse && plan == null && size.width > 0 && size.height > 0 -> {
                plan = makeAtmospherePlan(effect, size, density, System.nanoTime(), stableSeed)
            }
        }
    }

    LaunchedEffect(effect.configuration) {
        plan = plan?.let { current ->
            makeAtmospherePlan(effect, size, density, current.startNanos, current.seed)
        }
    }

    LaunchedEffect(plan != null, animationsEnabled, lifecycleActive) {
        if (plan == null || !animationsEnabled || !lifecycleActive) return@LaunchedEffect
        while (plan != null) frameNanos = withFrameNanos { it }
    }

    Canvas(
        modifier = modifier.onSizeChanged { newSize ->
            val oldSize = size
            size = newSize
            val materialChange = oldSize.width > 0 && oldSize.height > 0 &&
                (abs(oldSize.width - newSize.width) > 1 || abs(oldSize.height - newSize.height) > 1)
            val current = plan
            when {
                newSize.width <= 0 || newSize.height <= 0 -> plan = null
                current != null && materialChange -> {
                    plan = makeAtmospherePlan(effect, newSize, density, current.startNanos, current.seed)
                }
                current == null && requestedActive == true && animationsEnabled && lifecycleActive && !blockedUntilFalse -> {
                    plan = makeAtmospherePlan(effect, newSize, density, System.nanoTime(), stableSeed)
                }
            }
        },
    ) {
        plan?.let { current ->
            renderer.drawAtmosphere(
                drawContext.canvas.nativeCanvas,
                AtmosphereSimulation.snapshot(
                    current,
                    size.width.toFloat(),
                    size.height.toFloat(),
                    elapsedSeconds(frameNanos, current.startNanos),
                ),
            )
        }
    }
}

private fun makeRisePlan(
    effect: ParticleEffect.RiseAndBurst,
    size: IntSize,
    density: Float,
    startNanos: Long,
    seed: Long,
): RiseAndBurstPlan = RiseAndBurstPlan.make(
    configuration = effect.configuration.resolved(),
    widthPx = size.width.toFloat(),
    heightPx = size.height.toFloat(),
    density = density,
    startNanos = startNanos,
    seed = seed,
)

private fun makeAtmospherePlan(
    effect: ParticleEffect.Atmosphere,
    size: IntSize,
    density: Float,
    startNanos: Long,
    seed: Long,
): AtmospherePlan = AtmospherePlan.make(
    configuration = effect.configuration.resolved(),
    widthPx = size.width.toFloat(),
    heightPx = size.height.toFloat(),
    density = density,
    startNanos = startNanos,
    seed = seed,
)

private fun elapsedSeconds(frameNanos: Long, startNanos: Long): Float =
    (frameNanos - startNanos).coerceAtLeast(0L) / 1_000_000_000f

private fun nextSeed(salt: Int): Long = System.nanoTime() xor (salt.toLong() shl 32)

@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember(context) { mutableStateOf(AnimatorScaleObserver(context) {}.animationsEnabled) }
    val observer = remember(context) {
        AnimatorScaleObserver(context) { enabled = it }
    }
    DisposableEffect(observer) {
        enabled = observer.animationsEnabled
        observer.start()
        onDispose { observer.stop() }
    }
    return enabled
}

@Composable
private fun rememberLifecycleActive(): Boolean {
    val owner = LocalLifecycleOwner.current
    var active by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return active
}
