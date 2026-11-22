package com.taigashinohara.migakeru

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * The hot-blooded buddy (v0.4.0): a flame — not a human, not an animal.
 * Face only. Drawn with Canvas paths in a 0..100 unit space.
 * Colours stay inside the art direction: the app's single orange accent,
 * warmed up one step so the character reads friendly, not like a warning.
 * A very small scale flicker (anchored at the bottom) keeps it alive
 * without ever being noisy; it stops when the view leaves the window.
 */
class FlameFaceView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var flicker: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pivotX = width / 2f
        pivotY = height.toFloat()
        flicker = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 950
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                scaleY = 1f + 0.035f * t
                scaleX = 1f - 0.012f * t
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        flicker?.cancel()
        flicker = null
        super.onDetachedFromWindow()
    }

    private fun flamePath(top: Float, bottom: Float, left: Float, right: Float, tipShift: Float): Path {
        val cx = (left + right) / 2f
        val w = right - left
        return Path().apply {
            moveTo(cx + tipShift, top)
            // Right side sweeps down into the round belly.
            cubicTo(cx + tipShift + w * 0.30f, top + (bottom - top) * 0.22f,
                right, top + (bottom - top) * 0.42f,
                right, top + (bottom - top) * 0.62f)
            cubicTo(right, top + (bottom - top) * 0.84f,
                cx + w * 0.28f, bottom,
                cx, bottom)
            cubicTo(cx - w * 0.28f, bottom,
                left, top + (bottom - top) * 0.84f,
                left, top + (bottom - top) * 0.62f)
            // Left side rises with one small friendly flick before the tip.
            cubicTo(left, top + (bottom - top) * 0.46f,
                left + w * 0.06f, top + (bottom - top) * 0.34f,
                left + w * 0.16f, top + (bottom - top) * 0.26f)
            cubicTo(left + w * 0.02f, top + (bottom - top) * 0.16f,
                cx - w * 0.02f, top + (bottom - top) * 0.10f,
                cx + tipShift, top)
            close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val u = width / 100f // unit scale; view is square
        fun x(v: Float) = v * u
        fun y(v: Float) = v * u

        // Outer flame: orange gradient, bright at the top.
        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.rgb(0xF5, 0x8C, 0x1E), Color.rgb(0xE6, 0x51, 0x00),
            Shader.TileMode.CLAMP,
        )
        paint.style = Paint.Style.FILL
        canvas.drawPath(flamePath(y(3f), y(97f), x(14f), x(86f), x(6f)), paint)

        // Inner flame: warm cream, sitting low in the belly.
        paint.shader = LinearGradient(
            0f, y(48f), 0f, y(95f),
            Color.rgb(0xFF, 0xE9, 0xB8), Color.rgb(0xFF, 0xC4, 0x6B),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(flamePath(y(46f), y(95f), x(31f), x(69f), x(3f)), paint)
        paint.shader = null

        val dark = Color.rgb(0x5D, 0x2E, 0x0C)

        // Brows: thick, angled down toward the centre — determination.
        paint.color = dark
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = x(5f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x(28f), y(50f), x(44f), y(56f), paint)
        canvas.drawLine(x(72f), y(50f), x(56f), y(56f), paint)

        // Eyes: dark ovals with a highlight.
        paint.style = Paint.Style.FILL
        canvas.drawOval(x(31f), y(60f), x(41f), y(71f), paint)
        canvas.drawOval(x(59f), y(60f), x(69f), y(71f), paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x(34f), y(63f), x(2.2f), paint)
        canvas.drawCircle(x(62f), y(63f), x(2.2f), paint)

        // Mouth: open smile with a small tongue.
        paint.color = Color.rgb(0x7A, 0x2E, 0x12)
        val mouth = Path().apply {
            moveTo(x(40f), y(74f))
            quadTo(x(50f), y(90f), x(60f), y(74f))
            quadTo(x(50f), y(79f), x(40f), y(74f))
            close()
        }
        canvas.drawPath(mouth, paint)
        paint.color = Color.rgb(0xFF, 0x9E, 0x7A)
        val tongue = Path().apply {
            moveTo(x(45f), y(81.5f))
            quadTo(x(50f), y(85.5f), x(55f), y(81.5f))
            quadTo(x(50f), y(83.5f), x(45f), y(81.5f))
            close()
        }
        canvas.drawPath(tongue, paint)

        // Cheeks: soft blush keeps the heat friendly.
        paint.color = Color.argb(80, 0xFF, 0x78, 0x5A)
        canvas.drawCircle(x(24f), y(72f), x(6f), paint)
        canvas.drawCircle(x(76f), y(72f), x(6f), paint)
    }
}

/** Small triangle tail for the speech bubble, pointing right toward the buddy. */
class BubbleTailView(context: Context) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(34, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0xD4, 0xAF, 0x37)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val path = Path().apply {
            moveTo(0f, height * 0.18f)
            lineTo(width.toFloat(), height * 0.5f)
            lineTo(0f, height * 0.82f)
            close()
        }
        canvas.drawPath(path, fillPaint)
        // Gold edges on the two slanted sides only (the base hides in the bubble).
        canvas.drawLine(0f, height * 0.18f, width.toFloat(), height * 0.5f, edgePaint)
        canvas.drawLine(width.toFloat(), height * 0.5f, 0f, height * 0.82f, edgePaint)
    }
}
