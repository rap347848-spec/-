package com.talal.netcard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FrameCornersView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f * d
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val s = paint.strokeWidth / 2
        val l = 40f * d
        val r = 22f * d
        val w = width - s
        val h = height - s
        val p = Path()
        p.moveTo(s, l); p.lineTo(s, s + r)
        p.arcTo(RectF(s, s, s + 2 * r, s + 2 * r), 180f, 90f, false); p.lineTo(l, s)
        p.moveTo(w - l, s); p.lineTo(w - r, s)
        p.arcTo(RectF(w - 2 * r, s, w, s + 2 * r), 270f, 90f, false); p.lineTo(w, l)
        p.moveTo(w, h - l); p.lineTo(w, h - r)
        p.arcTo(RectF(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false); p.lineTo(w - l, h)
        p.moveTo(l, h); p.lineTo(s + r, h)
        p.arcTo(RectF(s, h - 2 * r, s + 2 * r, h), 90f, 90f, false); p.lineTo(s, h - l)
        canvas.drawPath(p, paint)
    }
}
