package com.groqvoice.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

enum class KaiState { IDLE, LISTENING, THINKING, SPEAKING }

class KaiOrbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var state = KaiState.IDLE
    private var pulseProgress = 0f
    private var time = 0f

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { pulseProgress = it.animatedValue as Float; invalidate() }
    }

    private val timeAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { time = it.animatedValue as Float }
    }

    private val stateColors = mapOf(
        KaiState.IDLE to intArrayOf(Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"), Color.parseColor("#0f3460")),
        KaiState.LISTENING to intArrayOf(Color.parseColor("#00d2ff"), Color.parseColor("#0070ff"), Color.parseColor("#00aaff")),
        KaiState.THINKING to intArrayOf(Color.parseColor("#7928CA"), Color.parseColor("#FF0080"), Color.parseColor("#c026d3")),
        KaiState.SPEAKING to intArrayOf(Color.parseColor("#00ff88"), Color.parseColor("#00d4aa"), Color.parseColor("#10b981"))
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)
    }

    fun setState(newState: KaiState) { state = newState; invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulseAnimator.start()
        timeAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator.cancel()
        timeAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseR = minOf(width, height) / 3f
        val colors = stateColors[state]!!

        // Outer glow
        for (i in 3 downTo 1) {
            val r = baseR + i * 20f + pulseProgress * 15f
            val alpha = ((1f - i / 4f) * 60).toInt()
            glowPaint.color = Color.argb(alpha, Color.red(colors[0]), Color.green(colors[0]), Color.blue(colors[0]))
            canvas.drawCircle(cx, cy, r, glowPaint)
        }

        // Morphing blob
        val path = Path()
        val pts = 64
        val noiseAmt = when (state) {
            KaiState.IDLE -> 0.04f
            KaiState.LISTENING -> 0.14f + pulseProgress * 0.08f
            KaiState.THINKING -> 0.18f + sin(time * 2).toFloat() * 0.05f
            KaiState.SPEAKING -> 0.12f + pulseProgress * 0.12f
        }
        for (i in 0..pts) {
            val a = (i.toFloat() / pts) * 2 * PI
            val n = sin(a * 3 + time).toFloat() * noiseAmt +
                    cos(a * 5 - time * 1.5f).toFloat() * noiseAmt * 0.5f +
                    sin(a * 7 + time * 2).toFloat() * noiseAmt * 0.25f
            val r = baseR * (1f + n)
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        paint.shader = RadialGradient(cx, cy, baseR * 1.2f, colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = 255
        canvas.drawPath(path, paint)

        // Core glow
        paint.shader = RadialGradient(cx, cy, baseR * 0.45f,
            intArrayOf(Color.WHITE, Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = (70 + pulseProgress * 50).toInt()
        canvas.drawCircle(cx, cy, baseR * 0.45f, paint)

        // Particles
        if (state != KaiState.IDLE) {
            paint.shader = null
            for (i in 0..7) {
                val a = (i.toFloat() / 8) * 2 * PI + time
                val d = baseR * (1.35f + sin(time * 2 + i).toFloat() * 0.15f)
                val x = cx + (d * cos(a)).toFloat()
                val y = cy + (d * sin(a)).toFloat()
                val sz = 3f + sin((time * 3 + i).toDouble()).toFloat() * 2f
                paint.color = Color.argb(
                    (140 + sin((time + i).toDouble()).toFloat() * 60).toInt().coerceIn(0, 255),
                    Color.red(colors[0]), Color.green(colors[0]), Color.blue(colors[0])
                )
                canvas.drawCircle(x, y, sz, paint)
            }
        }
    }
}
