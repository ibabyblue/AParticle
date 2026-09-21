package com.ibabyblue.aparticle.internal

import android.annotation.TargetApi
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.PixelCopy
import android.widget.FrameLayout
import java.io.File
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.View
import com.ibabyblue.aparticle.CelebrationConfiguration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** On-device fixed-seed frame-budget gate, independent of example navigation and random taps. */
@TargetApi(31)
class CelebrationBenchmark : Instrumentation() {
    private var visual = false
    private var referenceBackend = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        visual = arguments?.getString("visual") == "true"
        referenceBackend = arguments?.getString("backend") == "canvas"
        start()
    }
    private var currentActivity: CelebrationBenchmarkActivity? = null
    override fun onStart() {
        try { runBenchmark() } catch (error: Exception) {
            finishSafely(Activity.RESULT_CANCELED, Bundle().apply {
                putString("stream", "BENCHMARK_ERROR=${error.message}")
            })
        }
    }
    private fun finishSafely(result: Int, output: Bundle) {
        currentActivity?.let { activity -> runOnMainSync { activity.finish() } }
        currentActivity = null
        finish(result, output)
    }
    private fun runBenchmark() {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FRAME_BUDGET=UNSUPPORTED: requires Android 12 or newer") })
            return
        }
        val activity = startActivitySync(Intent(targetContext, CelebrationBenchmarkActivity::class.java)
            .putExtra("referenceBackend", referenceBackend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CelebrationBenchmarkActivity
        currentActivity = activity
        check(activity.ready.await(5, TimeUnit.SECONDS)) { "Benchmark did not receive window focus" }
        if (visual && android.os.Build.VERSION.SDK_INT < 34) {
            runOnMainSync { activity.finish() }
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "VISUAL_BUDGET=UNSUPPORTED: requires Android 14 or newer") })
            return
        }
        if (visual) {
            compareFrames(activity)
            return
        }
        val results = mutableListOf<String>()
        var passed = true
        for (run in 0..2) {
            val done = CountDownLatch(1)
            runOnMainSync { activity.surface.play(done) }
            check(done.await(15, TimeUnit.SECONDS)) { "Playback timed out" }
            check(activity.surface.interruption == null) { checkNotNull(activity.surface.interruption) }
            val costs = activity.surface.costs.sorted()
            val gaps = activity.surface.gaps.sorted()
            fun percentile(values: List<Double>, p: Double) = values[((values.size - 1) * p).toInt()]
            val p95 = percentile(costs, .95)
            val gap95 = percentile(gaps, .95)
            val total = activity.surface.totals.sorted()
            val gpu = activity.surface.gpus.sorted()
            val issue = activity.surface.issues.sorted()
            val sync = activity.surface.syncs.sorted()
            val dropped = activity.surface.dropped
            val result = "backend=${if (referenceBackend || android.os.Build.VERSION.SDK_INT < 34) "canvas" else "gpu"} run=$run frames=${costs.size} drawP50=${percentile(costs,.5)} drawP95=$p95 drawMax=${costs.last()} gapP95=$gap95 gapsOver25=${gaps.count { it > 25 }} totalP95=${if (total.isEmpty()) 0 else percentile(total,.95)} gpuP95=${if (gpu.isEmpty()) 0 else percentile(gpu,.95)} issueP95=${if (issue.isEmpty()) 0 else percentile(issue,.95)} syncP95=${if (sync.isEmpty()) 0 else percentile(sync,.95)} missed=${activity.surface.missed}/${total.size} dropped=$dropped"
            results += result
            sendStatus(0, Bundle().apply { putString("stream", "$result\n") })
            // Include the complete rendering pipeline, not only UI-thread display-list recording.
            if (p95 > 16.67 || gap95 > 25 || total.size < 30 ||
                percentile(total, .95) > 33.34 || dropped > 0 || activity.surface.missed.toDouble() / total.size > 0.01) passed = false
        }
        finishSafely(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", results.joinToString("\n") + "\nFRAME_BUDGET=" + if (passed) "PASS" else "FAIL") })
    }
    private fun compareFrames(activity: CelebrationBenchmarkActivity) {
        var passed = true
        val output = File(targetContext.getExternalFilesDir(null), "celebration-visual").apply { mkdirs() }
        data class Sample(val time: Float, val seed: Long = 327942116865947580L, val reduced: Boolean = false, val smallBatches: Boolean = false, val width: Int = -1, val height: Int = -1, val density: Float = 0f)
        val samples = listOf(0.8f, 1.2f, 1.7f, 2.25f, 3.2f).map { Sample(it) } +
            listOf(Sample(1.7f, 42L), Sample(2.25f, 42L), Sample(0.2f, reduced = true),
                Sample(0.7f, reduced = true), Sample(0.8f, reduced = true), Sample(4.2f), Sample(0.25f), Sample(2.25f, smallBatches = true), Sample(0.25f, smallBatches = true)) +
            listOf(1.2f, 1.7f, 2.25f).flatMap { time ->
                listOf(Sample(time, width = 1080, height = 600), Sample(time, width = 540, height = 1204, density = 1.40625f))
            }
        for (background in listOf(Color.BLACK, Color.WHITE)) {
            for (sample in samples) {
                val time = sample.time
                fun capture(reference: Boolean): Bitmap {
                    val ready = CountDownLatch(1)
                    runOnMainSync {
                        activity.surface.freeze(time, reference, background, sample.seed, sample.reduced, sample.smallBatches, sample.width, sample.height, sample.density)
                        activity.surface.postOnAnimation {
                            activity.surface.postOnAnimation {
                                activity.surface.viewTreeObserver.registerFrameCommitCallback { ready.countDown() }
                                activity.surface.invalidate()
                            }
                        }
                    }
                    check(ready.await(5, TimeUnit.SECONDS)) { "Frame was not committed" }
                    val captured = CountDownLatch(1)
                    val bitmap = Bitmap.createBitmap(activity.surface.width, activity.surface.height, Bitmap.Config.ARGB_8888)
                    var result = -1
                    runOnMainSync {
                        val location = IntArray(2)
                        activity.surface.getLocationInWindow(location)
                        PixelCopy.request(activity.window, Rect(location[0], location[1],
                            location[0] + bitmap.width, location[1] + bitmap.height), bitmap,
                            { status -> result = status; captured.countDown() }, Handler(Looper.getMainLooper()))
                    }
                    check(captured.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "PixelCopy failed: $result" }
                    return bitmap
                }
                val expected = capture(true)
                val actual = capture(false)
                val pixels = expected.width * expected.height
                val a = IntArray(pixels); val b = IntArray(pixels)
                expected.getPixels(a, 0, expected.width, 0, 0, expected.width, expected.height)
                actual.getPixels(b, 0, actual.width, 0, 0, actual.width, actual.height)
                var sum = 0L; var changed = 0; var maxDelta = 0; var interiorChanged = 0
                for (index in a.indices) {
                    var largest = 0
                    for (channel in 0..2) {
                        val shift = channel * 8
                        val delta = kotlin.math.abs(((a[index] ushr shift) and 255) - ((b[index] ushr shift) and 255))
                        sum += delta; largest = maxOf(largest, delta)
                    }
                    if (largest > 16) changed++
                    if (largest > 8) {
                        val x = index % expected.width; val y = index / expected.width
                        var contrast = 0
                        for (channel in 0..2) {
                            var low = 255; var high = 0
                            for (dy in -2..2) for (dx in -2..2) {
                                val nx = x + dx; val ny = y + dy
                                if (nx !in 0 until expected.width || ny !in 0 until expected.height) continue
                                val value = (a[ny * expected.width + nx] ushr (channel * 8)) and 255
                                low = minOf(low, value); high = maxOf(high, value)
                            }
                            contrast = maxOf(contrast, high - low)
                        }
                        if (contrast < 4) interiorChanged++
                    }
                    maxDelta = maxOf(maxDelta, largest)
                }
                val mean = sum.toDouble() / (pixels * 3)
                val fraction = changed.toDouble() / pixels
                val label = "${if (background == Color.BLACK) "dark" else "light"}-${sample.seed}-${sample.reduced}-${sample.smallBatches}-${sample.width}x${sample.height}-$time"
                File(output, "$label-reference.png").outputStream().use { expected.compress(Bitmap.CompressFormat.PNG, 100, it) }
                File(output, "$label-gpu.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
                sendStatus(0, Bundle().apply { putString("stream", "VISUAL $label mean=$mean changedOver16=$fraction max=$maxDelta interiorChanged=$interiorChanged\n") })
                // A small edge-only raster difference is permitted; choreography is checked separately.
                if (mean > 0.1 || fraction > 0.001 || interiorChanged != 0) passed = false
                expected.recycle(); actual.recycle()
            }
        }
        finishSafely(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", "VISUAL_BUDGET=" + if (passed) "PASS" else "FAIL") })
    }

}

@TargetApi(31)
class CelebrationBenchmarkActivity : Activity() {
    val ready = CountDownLatch(1)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ready.countDown()
        else if (::surface.isInitialized) surface.interrupt()
    }
    lateinit var surface: BenchmarkSurface
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = BenchmarkSurface(this)
        setContentView(surface)
        window.addOnFrameMetricsAvailableListener({ _, frame, dropped ->
            surface.recordFrame(frame, dropped)
        }, Handler(Looper.getMainLooper()))
    }
}

@TargetApi(31)
class BenchmarkSurface(activity: Activity) : View(activity) {
    private val renderer = CelebrationCanvasRenderer(!activity.intent.getBooleanExtra("referenceBackend", false))
    private var plan: CelebrationPlan? = null
    private var completion: CountDownLatch? = null
    var interruption: String? = null
        private set
    fun interrupt() {
        if (plan != null && completion != null && frozenTime == null) {
            interruption = "Benchmark lost window focus; sample discarded"
            plan = null
            completion?.countDown()
        }
    }
    private var previousDraw = 0L
    private val smallBatchRenderer by lazy { CelebrationCanvasRenderer(gpuBatchCapacity = 8) }
    private var smallBatches = false
    private var frozenDensity = 0f
    private var frozenTime: Float? = null
    private var reference = false
    private var background = Color.BLACK
    fun freeze(time: Float, reference: Boolean, background: Int, seed: Long, reduced: Boolean, smallBatches: Boolean, width: Int, height: Int, density: Float) {
        renderer.prepare()
        plan = CelebrationPlan(0L, reduced, seed, CelebrationConfiguration(0.25f))
        this.smallBatches = smallBatches
        frozenDensity = density
        layoutParams = FrameLayout.LayoutParams(width, height)
        frozenTime = time; this.reference = reference; this.background = background
        invalidate()
    }
    val costs = mutableListOf<Double>()
    val gaps = mutableListOf<Double>()
    val totals = mutableListOf<Double>()
    val gpus = mutableListOf<Double>()
    val issues = mutableListOf<Double>()
    val syncs = mutableListOf<Double>()
    var missed = 0
    var dropped = 0
    fun recordFrame(frame: FrameMetrics, droppedSinceLast: Int) {
        val current = plan ?: return
        val time = (frame.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) - current.startNanos) / 1_000_000_000.0
        if (time in 0.65..2.5) {
            dropped += droppedSinceLast
            issues += frame.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) / 1_000_000.0
            syncs += frame.getMetric(FrameMetrics.SYNC_DURATION) / 1_000_000.0
            if (android.os.Build.VERSION.SDK_INT >= 31 && frame.getMetric(FrameMetrics.TOTAL_DURATION) > frame.getMetric(FrameMetrics.DEADLINE)) missed++
            totals += frame.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
            if (android.os.Build.VERSION.SDK_INT >= 31) gpus += frame.getMetric(FrameMetrics.GPU_DURATION) / 1_000_000.0
        }
    }
    fun play(done: CountDownLatch) {
        interruption = null
        costs.clear(); gaps.clear(); totals.clear(); gpus.clear(); issues.clear(); syncs.clear(); missed = 0; dropped = 0; previousDraw = 0L
        renderer.prepare()
        plan = CelebrationPlan(System.nanoTime(), false, 327942116865947580L, CelebrationConfiguration(0.25f))
        completion = done
        postInvalidateOnAnimation()
    }
    override fun onDraw(canvas: Canvas) {
        val current = plan ?: return
        frozenTime?.let { time ->
            canvas.drawColor(background)
            val density = if (frozenDensity > 0) frozenDensity else resources.displayMetrics.density
            if (reference) renderer.draw(canvas, CelebrationSimulation.snapshot(current, width.toFloat(), height.toFloat(), density, time), density)
            else (if (smallBatches) smallBatchRenderer else renderer).draw(canvas, current, width.toFloat(), height.toFloat(), density, time)
            return
        }
        val start = System.nanoTime()
        val elapsed = (start - current.startNanos) / 1_000_000_000f
        if (elapsed >= current.duration) {
            plan = null
            completion?.countDown()
            return
        }
        val density = resources.displayMetrics.density
        renderer.draw(canvas, current, width.toFloat(), height.toFloat(), density, elapsed)
        if (elapsed in 0.65f..2.5f) {
            costs += (System.nanoTime() - start) / 1_000_000.0
            if (previousDraw != 0L) gaps += (start - previousDraw) / 1_000_000.0
        }
        previousDraw = start
        postInvalidateOnAnimation()
    }
}
