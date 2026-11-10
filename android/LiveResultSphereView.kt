package com.crashai.neo.integration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.util.Locale

/**
 * Neon sphere displaying a historical estimate and the latest confirmed actual result.
 * Estimate and actual are deliberately labelled separately.
 */
class LiveResultSphereView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD7E3F4.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 13f * resources.displayMetrics.scaledDensity
    }
    private var estimate: Double? = null
    private var actual: Double? = null
    private var connection = "connecting"

    fun setEstimate(value: Double?) {
        estimate = value?.takeIf { it >= 1.0 && it.isFinite() }
        postInvalidate()
    }

    fun setActual(value: Double) {
        actual = value.takeIf { it >= 1.0 && it.isFinite() }
        postInvalidate()
    }

    fun setConnectionState(value: String) {
        connection = value
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.39f
        val accent = if (actual == null) 0xFF29A7FF.toInt() else 0xFF28D7A0.toInt()

        glow.shader = RadialGradient(
            cx, cy, radius * 1.55f,
            intArrayOf((accent and 0x00FFFFFF) or 0x66000000, Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.55f, glow)

        core.shader = RadialGradient(
            cx - radius * 0.28f, cy - radius * 0.32f, radius * 1.45f,
            intArrayOf(0xFF35445C.toInt(), 0xFF101827.toInt(), 0xFF03060C.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, core)
        core.shader = null
        core.style = Paint.Style.STROKE
        core.strokeWidth = 2.2f * resources.displayMetrics.density
        core.color = accent
        canvas.drawCircle(cx, cy, radius, core)
        core.style = Paint.Style.FILL

        text.textSize = radius * 0.34f
        val estimateText = estimate?.let { String.format(Locale.US, "%.2fx", it) } ?: "—"
        canvas.drawText(estimateText, cx, cy - radius * 0.12f, text)

        small.color = 0xFFB8C7DA.toInt()
        canvas.drawText("ESTIMATE", cx, cy - radius * 0.12f + 22f * resources.displayMetrics.density, small)

        text.textSize = radius * 0.25f
        val actualText = actual?.let { String.format(Locale.US, "%.2fx", it) } ?: "—"
        canvas.drawText(actualText, cx, cy + radius * 0.34f, text)

        small.color = if (actual == null) 0xFFFFC46B.toInt() else 0xFF70E8BD.toInt()
        canvas.drawText(
            if (actual == null) "ACTUAL · WAITING" else "ACTUAL · CONFIRMED",
            cx, cy + radius * 0.34f + 20f * resources.displayMetrics.density, small
        )
        small.color = 0xFF9EABC0.toInt()
        canvas.drawText(connection.uppercase(Locale.US), cx, cy + radius * 1.30f, small)
    }
}
