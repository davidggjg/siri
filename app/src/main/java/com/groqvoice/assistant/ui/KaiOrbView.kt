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
    private var pulse = 0f
    private var time = 0f

    private val pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800; repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
    }
    private val timeAnim = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 3000; repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { time = it.animatedValue as Float }
    }

    private val colors = mapOf(
        KaiState.IDLE to intArrayOf(Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"), Color.parseColor("#0f3460")),
        KaiState.LISTENING to intArrayOf(Color.parseColor("#00d2ff"), Color.parseColor("#0070ff"), Color.parseColor("#00aaff")),
        KaiState.THINKING to intArrayOf(Color.parseColor("#7928CA"), Color.parseColor("#FF0080"), Color.parseColor("#c026d3")),
        KaiState.SPEAKING to intArrayOf(Color.parseColor("#00ff88"), Color.parseColor("#00d4aa"), Color.parseColor("#10b981"))
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)
    }

    fun setState(s: KaiState) { state = s; invalidate() }
    fun getState() = state

    override fun onAttachedToWindow() { super.onAttachedToWindow(); pulseAnim.start(); timeAnim.start() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); pulseAnim.cancel(); timeAnim.cancel() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val baseR = minOf(width, height) / 3f
        val c = colors[state]!!

        for (i in 3 downTo 1) {
            val r = baseR + i * 18f + pulse * 12f
            glowPaint.color = Color.argb(((1f - i / 4f) * 55).toInt(), Color.red(c[0]), Color.green(c[0]), Color.blue(c[0]))
            canvas.drawCircle(cx, cy, r, glowPaint)
        }

        val path = Path()
        val noise = when (state) {
            KaiState.IDLE -> 0.04f
            KaiState.LISTENING -> 0.13f + pulse * 0.07f
            KaiState.THINKING -> 0.17f + sin(time * 2).toFloat() * 0.05f
            KaiState.SPEAKING -> 0.11f + pulse * 0.11f
        }
        for (i in 0..64) {
            val a = (i.toFloat() / 64) * 2 * PI
            val n = sin(a * 3 + time).toFloat() * noise + cos(a * 5 - time * 1.5f).toFloat() * noise * 0.5f
            val r = baseR * (1f + n)
            val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        paint.shader = RadialGradient(cx, cy, baseR * 1.2f, c, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = 255; canvas.drawPath(path, paint)

        paint.shader = RadialGradient(cx, cy, baseR * 0.4f, intArrayOf(Color.WHITE, Color.argb(0, 255, 255, 255)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = (65 + pulse * 45).toInt(); canvas.drawCircle(cx, cy, baseR * 0.4f, paint)

        if (state != KaiState.IDLE) {
            paint.shader = null
            for (i in 0..7) {
                val a = (i.toFloat() / 8) * 2 * PI + time
                val d = baseR * (1.3f + sin(time * 2 + i).toFloat() * 0.15f)
                paint.color = Color.argb((130 + sin((time + i).toDouble()).toFloat() * 55).toInt().coerceIn(0, 255), Color.red(c[0]), Color.green(c[0]), Color.blue(c[0]))
                canvas.drawCircle(cx + (d * cos(a)).toFloat(), cy + (d * sin(a)).toFloat(), 3f + sin((time * 3 + i).toDouble()).toFloat() * 2f, paint)
            }
        }
    }
}
