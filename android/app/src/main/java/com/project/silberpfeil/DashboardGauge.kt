package com.project.silberpfeil

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class DashboardGauge @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var minVal: Float = 0f
    var maxVal: Float = 100f
    var currentVal: Float = 0f
        set(value) {
            field = value.coerceIn(minVal, maxVal)
            invalidate() // Zeichnet die View neu
        }

    var title: String = ""
    var unit: String = ""
    var arcColor: Int = Color.parseColor("#00FFCC")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val strokeW = size * 0.08f
        val padding = strokeW / 2 + 10

        rectF.set(padding, padding, size - padding, size - padding)

        // 1. Hintergrund-Bogen (Grau)
        paint.color = Color.parseColor("#222222")
        paint.strokeWidth = strokeW
        canvas.drawArc(rectF, 135f, 270f, false, paint)

        // 2. Aktiver Wert-Bogen
        paint.color = arcColor
        val sweepAngle = ((currentVal - minVal) / (maxVal - minVal)) * 270f
        canvas.drawArc(rectF, 135f, sweepAngle, false, paint)

        // 3. Text in der Mitte (Großer Wert)
        textPaint.textSize = size * 0.18f
        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        val valueText = if (maxVal > 1000) (currentVal.toInt()).toString() else "%.1f".format(currentVal)
        canvas.drawText(valueText, size / 2, size / 2 + size * 0.05f, textPaint)

        // 4. Einheit & Titel darunterschreiben
        textPaint.textSize = size * 0.07f
        textPaint.color = Color.parseColor("#777777")
        canvas.drawText("$title ($unit)", size / 2, size / 2 + size * 0.22f, textPaint)
    }
}