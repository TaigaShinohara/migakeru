package com.taigashinohara.migakeru

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

/**
 * v0.7.1: a challenge's icon — a coloured medallion with a simple glyph
 * drawn in a 0..100 unit space (same convention as the buddy). One glyph
 * per challenge so the catalog reads as a wall of distinct cards, not a
 * settings list. Glyph colour flips dark/light by the medallion's
 * luminance so every colour stays readable.
 */
class ChallengeIconView(context: Context, private val itemId: String, rgb: Int) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bg = Color.rgb((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    private val glyph: Int = run {
        val lum = (0.299 * ((rgb shr 16) and 0xFF) + 0.587 * ((rgb shr 8) and 0xFF) +
            0.114 * (rgb and 0xFF)) / 255.0
        if (lum > 0.62) Color.rgb(0x24, 0x1D, 0x08) else Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val u = width / 100f
        fun x(v: Float) = v * u
        fun y(v: Float) = v * u
        fun oval(l: Float, t: Float, r: Float, b: Float) = RectF(x(l), y(t), x(r), y(b))
        fun rect(l: Float, t: Float, r: Float, b: Float) = RectF(x(l), y(t), x(r), y(b))
        fun fill() {
            paint.style = Paint.Style.FILL
            paint.color = glyph
        }
        fun fillBg() {
            paint.style = Paint.Style.FILL
            paint.color = bg
        }
        fun stroke(w: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * u
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = glyph
        }

        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawCircle(x(50f), y(50f), x(48f), paint)

        when (itemId) {
            "kintore" -> { // barbell
                stroke(6f)
                canvas.drawLine(x(16f), y(50f), x(84f), y(50f), paint)
                fill()
                canvas.drawRect(x(24f), y(33f), x(34f), y(67f), paint)
                canvas.drawRect(x(66f), y(33f), x(76f), y(67f), paint)
                canvas.drawRect(x(15f), y(42f), x(21f), y(58f), paint)
                canvas.drawRect(x(79f), y(42f), x(85f), y(58f), paint)
            }
            "walk" -> { // two footprints
                fill()
                canvas.drawOval(oval(28f, 26f, 46f, 54f), paint)
                canvas.drawOval(oval(54f, 48f, 72f, 76f), paint)
                canvas.drawCircle(x(34f), y(19f), x(4f), paint)
                canvas.drawCircle(x(60f), y(41f), x(4f), paint)
            }
            "protein" -> { // egg with a yolk
                fill()
                canvas.drawOval(oval(30f, 15f, 70f, 85f), paint)
                fillBg()
                canvas.drawCircle(x(50f), y(57f), x(11f), paint)
            }
            "bath" -> { // bathtub
                stroke(6f)
                canvas.drawLine(x(16f), y(42f), x(84f), y(42f), paint)
                val body = Path().apply {
                    moveTo(x(21f), y(42f))
                    quadTo(x(25f), y(72f), x(50f), y(72f))
                    quadTo(x(75f), y(72f), x(79f), y(42f))
                }
                canvas.drawPath(body, paint)
                canvas.drawLine(x(31f), y(74f), x(27f), y(84f), paint)
                canvas.drawLine(x(69f), y(74f), x(73f), y(84f), paint)
            }
            "sleep_prep" -> { // crescent moon + a small star
                fill()
                val moon = Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addCircle(x(46f), y(50f), x(28f), Path.Direction.CW)
                    addCircle(x(61f), y(40f), x(22f), Path.Direction.CW)
                }
                canvas.drawPath(moon, paint)
                canvas.drawCircle(x(71f), y(68f), x(3.5f), paint)
            }
            "stretch" -> { // a rising curve with an arrowhead
                stroke(6f)
                val curve = Path().apply {
                    moveTo(x(26f), y(76f))
                    quadTo(x(30f), y(36f), x(64f), y(28f))
                }
                canvas.drawPath(curve, paint)
                canvas.drawLine(x(64f), y(28f), x(51f), y(27f), paint)
                canvas.drawLine(x(64f), y(28f), x(62f), y(41f), paint)
            }
            "stairs" -> { // ascending steps
                fill()
                val steps = Path().apply {
                    moveTo(x(16f), y(84f))
                    lineTo(x(36f), y(84f))
                    lineTo(x(36f), y(64f))
                    lineTo(x(56f), y(64f))
                    lineTo(x(56f), y(44f))
                    lineTo(x(84f), y(44f))
                    lineTo(x(84f), y(84f))
                    close()
                }
                canvas.drawPath(steps, paint)
            }
            "neckroll" -> { // circular arrow around a point
                stroke(6f)
                canvas.drawArc(oval(27f, 27f, 73f, 73f), 40f, 285f, false, paint)
                fill()
                val head = Path().apply {
                    moveTo(x(72f), y(29f))
                    lineTo(x(79f), y(44f))
                    lineTo(x(62f), y(43f))
                    close()
                }
                canvas.drawPath(head, paint)
                canvas.drawCircle(x(50f), y(50f), x(4f), paint)
            }
            "nails" -> { // nail polish bottle
                fill()
                canvas.drawRect(x(43f), y(13f), x(57f), y(33f), paint)
                canvas.drawRoundRect(oval(34f, 37f, 66f, 85f), x(8f), y(8f), paint)
                stroke(3.5f)
                paint.color = bg
                canvas.drawLine(x(43f), y(47f), x(43f), y(73f), paint)
            }
            "skin" -> { // droplet
                fill()
                val drop = Path().apply {
                    moveTo(x(50f), y(13f))
                    quadTo(x(79f), y(52f), x(50f), y(85f))
                    quadTo(x(21f), y(52f), x(50f), y(13f))
                    close()
                }
                canvas.drawPath(drop, paint)
                fillBg()
                canvas.drawCircle(x(40f), y(63f), x(5f), paint)
            }
            "hair" -> { // comb
                fill()
                canvas.drawRect(x(18f), y(27f), x(82f), y(40f), paint)
                stroke(5f)
                var tx = 26f
                while (tx <= 74f) {
                    canvas.drawLine(x(tx), y(40f), x(tx), y(61f), paint)
                    tx += 8f
                }
            }
            "posture" -> { // standing figure, straight back
                fill()
                canvas.drawCircle(x(50f), y(21f), x(8f), paint)
                stroke(6f)
                canvas.drawLine(x(50f), y(30f), x(50f), y(60f), paint)
                canvas.drawLine(x(34f), y(42f), x(66f), y(42f), paint)
                canvas.drawLine(x(50f), y(60f), x(38f), y(82f), paint)
                canvas.drawLine(x(50f), y(60f), x(62f), y(82f), paint)
            }
            "clothes" -> { // t-shirt
                fill()
                val shirt = Path().apply {
                    moveTo(x(36f), y(19f))
                    lineTo(x(18f), y(30f))
                    lineTo(x(27f), y(45f))
                    lineTo(x(35f), y(41f))
                    lineTo(x(35f), y(82f))
                    lineTo(x(65f), y(82f))
                    lineTo(x(65f), y(41f))
                    lineTo(x(73f), y(45f))
                    lineTo(x(82f), y(30f))
                    lineTo(x(64f), y(19f))
                    quadTo(x(50f), y(29f), x(36f), y(19f))
                    close()
                }
                canvas.drawPath(shirt, paint)
            }
            "brow" -> { // eyebrow over an eye
                stroke(6f)
                val browPath = Path().apply {
                    moveTo(x(24f), y(37f))
                    quadTo(x(50f), y(22f), x(76f), y(37f))
                }
                canvas.drawPath(browPath, paint)
                val eye = Path().apply {
                    moveTo(x(26f), y(60f))
                    quadTo(x(50f), y(44f), x(74f), y(60f))
                    quadTo(x(50f), y(73f), x(26f), y(60f))
                    close()
                }
                canvas.drawPath(eye, paint)
                fill()
                canvas.drawCircle(x(50f), y(58f), x(5.5f), paint)
            }
            "shoes" -> { // shoe with a sole
                fill()
                val shoe = Path().apply {
                    moveTo(x(16f), y(67f))
                    lineTo(x(16f), y(50f))
                    quadTo(x(17f), y(39f), x(28f), y(39f))
                    lineTo(x(43f), y(39f))
                    quadTo(x(52f), y(39f), x(59f), y(48f))
                    lineTo(x(83f), y(59f))
                    quadTo(x(87f), y(67f), x(78f), y(67f))
                    close()
                }
                canvas.drawPath(shoe, paint)
                canvas.drawRect(x(14f), y(69f), x(86f), y(76f), paint)
            }
            "scent" -> { // flower
                fill()
                for (deg in listOf(-90.0, -18.0, 54.0, 126.0, 198.0)) {
                    val rad = Math.toRadians(deg)
                    val cx = 50f + (15 * Math.cos(rad)).toFloat()
                    val cy = 50f + (15 * Math.sin(rad)).toFloat()
                    canvas.drawCircle(x(cx), y(cy), x(11.5f), paint)
                }
                fillBg()
                canvas.drawCircle(x(50f), y(50f), x(7f), paint)
            }
            "meditation" -> { // lotus
                fill()
                val petal = Path().apply {
                    moveTo(x(50f), y(20f))
                    quadTo(x(35f), y(46f), x(50f), y(63f))
                    quadTo(x(65f), y(46f), x(50f), y(20f))
                    close()
                }
                canvas.drawPath(petal, paint)
                canvas.save()
                canvas.rotate(-32f, x(50f), y(62f))
                canvas.drawOval(oval(22f, 42f, 45f, 64f), paint)
                canvas.restore()
                canvas.save()
                canvas.rotate(32f, x(50f), y(62f))
                canvas.drawOval(oval(55f, 42f, 78f, 64f), paint)
                canvas.restore()
                stroke(5f)
                val base = Path().apply {
                    moveTo(x(24f), y(71f))
                    quadTo(x(50f), y(84f), x(76f), y(71f))
                }
                canvas.drawPath(base, paint)
            }
            "journal" -> { // notebook
                stroke(5f)
                canvas.drawRoundRect(oval(30f, 13f, 72f, 87f), x(6f), y(6f), paint)
                fill()
                for (dy in listOf(28f, 43f, 58f, 73f)) {
                    canvas.drawCircle(x(37f), y(dy), x(2.6f), paint)
                }
                stroke(4f)
                canvas.drawLine(x(47f), y(32f), x(63f), y(32f), paint)
                canvas.drawLine(x(47f), y(48f), x(63f), y(48f), paint)
                canvas.drawLine(x(47f), y(64f), x(57f), y(64f), paint)
            }
            "phone_off" -> { // phone with a slash
                stroke(5.5f)
                canvas.drawRoundRect(oval(35f, 12f, 65f, 88f), x(7f), y(7f), paint)
                fill()
                canvas.drawCircle(x(50f), y(78f), x(2.6f), paint)
                stroke(7f)
                canvas.drawLine(x(22f), y(83f), x(78f), y(17f), paint)
            }
            "decide" -> { // flag planted
                fill()
                val flag = Path().apply {
                    moveTo(x(36f), y(17f))
                    lineTo(x(75f), y(26f))
                    lineTo(x(36f), y(38f))
                    close()
                }
                canvas.drawPath(flag, paint)
                stroke(6f)
                canvas.drawLine(x(36f), y(13f), x(36f), y(86f), paint)
                canvas.drawLine(x(23f), y(86f), x(49f), y(86f), paint)
            }
            "music" -> { // eighth notes
                fill()
                val beam = Path().apply {
                    moveTo(x(42f), y(27f))
                    lineTo(x(82f), y(21f))
                    lineTo(x(82f), y(34f))
                    lineTo(x(42f), y(40f))
                    close()
                }
                canvas.drawPath(beam, paint)
                canvas.drawRect(x(42f), y(27f), x(47.5f), y(70f), paint)
                canvas.drawRect(x(76.5f), y(21f), x(82f), y(64f), paint)
                canvas.drawOval(oval(27f, 63f, 46f, 78f), paint)
                canvas.drawOval(oval(62f, 57f, 81f, 72f), paint)
            }
            "breath" -> { // wind swirls
                stroke(5.5f)
                canvas.drawLine(x(16f), y(35f), x(58f), y(35f), paint)
                canvas.drawArc(oval(54f, 27f, 70f, 43f), -90f, 300f, false, paint)
                canvas.drawLine(x(16f), y(55f), x(48f), y(55f), paint)
                canvas.drawArc(oval(44f, 47f, 60f, 63f), -90f, 300f, false, paint)
                canvas.drawLine(x(16f), y(74f), x(44f), y(74f), paint)
            }
            "sky" -> { // sun behind a cloud
                fill()
                canvas.drawCircle(x(72f), y(27f), x(9f), paint)
                stroke(4f)
                canvas.drawLine(x(72f), y(11f), x(72f), y(15f), paint)
                canvas.drawLine(x(86f), y(20f), x(83f), y(23f), paint)
                canvas.drawLine(x(58f), y(20f), x(61f), y(23f), paint)
                fill()
                canvas.drawCircle(x(37f), y(59f), x(13f), paint)
                canvas.drawCircle(x(54f), y(49f), x(17f), paint)
                canvas.drawCircle(x(68f), y(60f), x(11f), paint)
                canvas.drawRect(x(25f), y(57f), x(78f), y(70f), paint)
            }
            "thanks" -> { // heart
                fill()
                val heart = Path().apply {
                    moveTo(x(50f), y(84f))
                    quadTo(x(16f), y(62f), x(20f), y(38f))
                    quadTo(x(24f), y(19f), x(38f), y(21f))
                    quadTo(x(47f), y(23f), x(50f), y(34f))
                    quadTo(x(53f), y(23f), x(62f), y(21f))
                    quadTo(x(76f), y(19f), x(80f), y(38f))
                    quadTo(x(84f), y(62f), x(50f), y(84f))
                    close()
                }
                canvas.drawPath(heart, paint)
            }
            // ---- v0.9.0: glyphs for the 24 added challenges ----
            "squat" -> { // a figure squatting, arms forward
                fill()
                canvas.drawCircle(x(50f), y(24f), x(7f), paint)
                stroke(6f)
                canvas.drawLine(x(50f), y(31f), x(50f), y(50f), paint)
                canvas.drawLine(x(50f), y(38f), x(70f), y(42f), paint)
                canvas.drawLine(x(50f), y(50f), x(35f), y(62f), paint)
                canvas.drawLine(x(35f), y(62f), x(37f), y(78f), paint)
                canvas.drawLine(x(50f), y(50f), x(65f), y(62f), paint)
                canvas.drawLine(x(65f), y(62f), x(63f), y(78f), paint)
            }
            "pushup" -> { // a figure in push-up position
                fill()
                canvas.drawCircle(x(74f), y(32f), x(6.5f), paint)
                stroke(6.5f)
                canvas.drawLine(x(68f), y(38f), x(30f), y(56f), paint)
                canvas.drawLine(x(56f), y(44f), x(56f), y(68f), paint)
                canvas.drawLine(x(30f), y(56f), x(22f), y(70f), paint)
            }
            "plank" -> { // a straight body held on one arm
                fill()
                canvas.drawCircle(x(78f), y(26f), x(6.5f), paint)
                stroke(7f)
                canvas.drawLine(x(72f), y(32f), x(26f), y(60f), paint)
                canvas.drawLine(x(60f), y(40f), x(60f), y(66f), paint)
            }
            "radio" -> { // radio calisthenics: arms up in a V
                fill()
                canvas.drawCircle(x(50f), y(30f), x(7f), paint)
                stroke(6f)
                canvas.drawLine(x(50f), y(44f), x(31f), y(22f), paint)
                canvas.drawLine(x(50f), y(44f), x(69f), y(22f), paint)
                canvas.drawLine(x(50f), y(44f), x(50f), y(62f), paint)
                canvas.drawLine(x(50f), y(62f), x(39f), y(80f), paint)
                canvas.drawLine(x(50f), y(62f), x(61f), y(80f), paint)
            }
            "water" -> { // a glass of water
                stroke(6f)
                canvas.drawLine(x(35f), y(24f), x(40f), y(76f), paint)
                canvas.drawLine(x(65f), y(24f), x(60f), y(76f), paint)
                canvas.drawLine(x(40f), y(76f), x(60f), y(76f), paint)
                stroke(4f)
                canvas.drawLine(x(37.5f), y(45f), x(62.5f), y(45f), paint)
            }
            "abs" -> { // a torso with a six-pack hint
                stroke(6f)
                canvas.drawRoundRect(rect(37f, 20f, 63f, 80f), x(10f), y(10f), paint)
                stroke(4f)
                canvas.drawLine(x(50f), y(28f), x(50f), y(72f), paint)
                canvas.drawLine(x(39f), y(42f), x(61f), y(42f), paint)
                canvas.drawLine(x(39f), y(58f), x(61f), y(58f), paint)
            }
            "weigh" -> { // a bathroom scale with a dial
                stroke(6f)
                canvas.drawRoundRect(rect(29f, 28f, 71f, 80f), x(8f), y(8f), paint)
                stroke(4f)
                canvas.drawArc(oval(40f, 38f, 60f, 56f), 180f, 180f, false, paint)
                canvas.drawLine(x(50f), y(52f), x(57f), y(43f), paint)
            }
            "eyes" -> { // an eye, resting open
                stroke(6f)
                val eye = Path().apply {
                    moveTo(x(20f), y(50f))
                    quadTo(x(50f), y(26f), x(80f), y(50f))
                    quadTo(x(50f), y(74f), x(20f), y(50f))
                    close()
                }
                canvas.drawPath(eye, paint)
                fill()
                canvas.drawCircle(x(50f), y(50f), x(8f), paint)
            }
            "teeth" -> { // a tooth
                fill()
                val tooth = Path().apply {
                    moveTo(x(34f), y(30f))
                    cubicTo(x(34f), y(19f), x(66f), y(19f), x(66f), y(30f))
                    cubicTo(x(66f), y(42f), x(62f), y(45f), x(61f), y(57f))
                    cubicTo(x(60f), y(70f), x(54f), y(73f), x(52f), y(60f))
                    lineTo(x(48f), y(60f))
                    cubicTo(x(46f), y(73f), x(40f), y(70f), x(39f), y(57f))
                    cubicTo(x(38f), y(45f), x(34f), y(42f), x(34f), y(30f))
                    close()
                }
                canvas.drawPath(tooth, paint)
            }
            "smile" -> { // a smiling face
                stroke(6f)
                canvas.drawCircle(x(50f), y(50f), x(27f), paint)
                fill()
                canvas.drawCircle(x(40f), y(44f), x(3.5f), paint)
                canvas.drawCircle(x(60f), y(44f), x(3.5f), paint)
                stroke(5f)
                canvas.drawArc(oval(36f, 38f, 64f, 68f), 25f, 130f, false, paint)
            }
            "sunscreen" -> { // a tube with a sun on it
                stroke(6f)
                canvas.drawRect(rect(40f, 34f, 60f, 74f), paint)
                fill()
                canvas.drawRect(rect(44f, 24f, 56f, 34f), paint)
                canvas.drawCircle(x(50f), y(54f), x(7f), paint)
            }
            "lips" -> { // lips: an M-shaped top edge and a lip line,
                // otherwise the silhouette reads as a moustache.
                fill()
                val lips = Path().apply {
                    moveTo(x(21f), y(50f))
                    quadTo(x(35f), y(31f), x(50f), y(45f))
                    quadTo(x(65f), y(31f), x(79f), y(50f))
                    quadTo(x(65f), y(65f), x(50f), y(59f))
                    quadTo(x(35f), y(65f), x(21f), y(50f))
                    close()
                }
                canvas.drawPath(lips, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * u
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = bg
                canvas.drawLine(x(31f), y(51f), x(69f), y(51f), paint)
            }
            "deodorant" -> { // a spray can with puffs
                stroke(6f)
                canvas.drawRoundRect(rect(40f, 36f, 58f, 78f), x(6f), y(6f), paint)
                fill()
                canvas.drawRect(rect(45f, 26f, 53f, 36f), paint)
                canvas.drawCircle(x(68f), y(28f), x(3f), paint)
                canvas.drawCircle(x(75f), y(38f), x(3f), paint)
                canvas.drawCircle(x(67f), y(44f), x(2.5f), paint)
            }
            "accessory" -> { // a wristwatch
                stroke(6f)
                canvas.drawLine(x(43f), y(16f), x(43f), y(35f), paint)
                canvas.drawLine(x(57f), y(16f), x(57f), y(35f), paint)
                canvas.drawLine(x(43f), y(65f), x(43f), y(84f), paint)
                canvas.drawLine(x(57f), y(65f), x(57f), y(84f), paint)
                canvas.drawCircle(x(50f), y(50f), x(16f), paint)
                stroke(4f)
                canvas.drawLine(x(50f), y(50f), x(50f), y(41f), paint)
                canvas.drawLine(x(50f), y(50f), x(58f), y(54f), paint)
            }
            "iron" -> { // an iron
                fill()
                val body = Path().apply {
                    moveTo(x(22f), y(68f))
                    lineTo(x(78f), y(68f))
                    lineTo(x(78f), y(59f))
                    quadTo(x(62f), y(44f), x(42f), y(46f))
                    lineTo(x(28f), y(52f))
                    close()
                }
                canvas.drawPath(body, paint)
                stroke(5f)
                canvas.drawLine(x(42f), y(38f), x(62f), y(38f), paint)
                canvas.drawLine(x(42f), y(38f), x(42f), y(45f), paint)
                canvas.drawLine(x(62f), y(38f), x(62f), y(46f), paint)
            }
            "reading" -> { // an open book
                stroke(6f)
                val book = Path().apply {
                    moveTo(x(50f), y(35f))
                    cubicTo(x(40f), y(27f), x(28f), y(26f), x(21f), y(29f))
                    lineTo(x(21f), y(73f))
                    cubicTo(x(28f), y(70f), x(40f), y(71f), x(50f), y(79f))
                    cubicTo(x(60f), y(71f), x(72f), y(70f), x(79f), y(73f))
                    lineTo(x(79f), y(29f))
                    cubicTo(x(72f), y(26f), x(60f), y(27f), x(50f), y(35f))
                    close()
                }
                canvas.drawPath(book, paint)
                stroke(4f)
                canvas.drawLine(x(50f), y(35f), x(50f), y(79f), paint)
            }
            "tidy" -> { // a box, tidied away, with a sparkle
                stroke(6f)
                canvas.drawRect(rect(28f, 44f, 72f, 78f), paint)
                canvas.drawLine(x(23f), y(44f), x(77f), y(44f), paint)
                fill()
                val sp = Path().apply {
                    moveTo(x(67f), y(16f))
                    lineTo(x(70.5f), y(24.5f))
                    lineTo(x(79f), y(28f))
                    lineTo(x(70.5f), y(31.5f))
                    lineTo(x(67f), y(40f))
                    lineTo(x(63.5f), y(31.5f))
                    lineTo(x(55f), y(28f))
                    lineTo(x(63.5f), y(24.5f))
                    close()
                }
                canvas.drawPath(sp, paint)
            }
            "message" -> { // a speech bubble with three dots
                stroke(6f)
                canvas.drawRoundRect(rect(25f, 27f, 75f, 59f), x(10f), y(10f), paint)
                fill()
                val tail = Path().apply {
                    moveTo(x(36f), y(57f))
                    lineTo(x(36f), y(73f))
                    lineTo(x(53f), y(59f))
                    close()
                }
                canvas.drawPath(tail, paint)
                canvas.drawCircle(x(38f), y(43f), x(3.2f), paint)
                canvas.drawCircle(x(50f), y(43f), x(3.2f), paint)
                canvas.drawCircle(x(62f), y(43f), x(3.2f), paint)
            }
            "laugh" -> { // a laughing face with an open mouth
                stroke(6f)
                canvas.drawCircle(x(50f), y(50f), x(27f), paint)
                stroke(4.5f)
                canvas.drawArc(oval(33f, 38f, 45f, 48f), 180f, 180f, false, paint)
                canvas.drawArc(oval(55f, 38f, 67f, 48f), 180f, 180f, false, paint)
                fill()
                canvas.drawArc(oval(38f, 48f, 62f, 72f), 0f, 180f, false, paint)
            }
            "sing" -> { // a microphone
                stroke(6f)
                canvas.drawRoundRect(rect(43f, 16f, 57f, 50f), x(7f), y(7f), paint)
                canvas.drawArc(oval(33f, 28f, 67f, 64f), 0f, 180f, false, paint)
                canvas.drawLine(x(50f), y(64f), x(50f), y(78f), paint)
                canvas.drawLine(x(37f), y(80f), x(63f), y(80f), paint)
            }
            "tea" -> { // a cup with steam
                stroke(6f)
                val cup = Path().apply {
                    moveTo(x(29f), y(38f))
                    lineTo(x(71f), y(38f))
                    lineTo(x(66f), y(68f))
                    lineTo(x(34f), y(68f))
                    close()
                }
                canvas.drawPath(cup, paint)
                canvas.drawArc(oval(66f, 42f, 82f, 60f), -60f, 120f, false, paint)
                stroke(4f)
                canvas.drawArc(oval(38f, 14f, 48f, 32f), 270f, 120f, false, paint)
                canvas.drawArc(oval(54f, 14f, 64f, 32f), 270f, 120f, false, paint)
            }
            "declare" -> { // a megaphone with sound arcs
                fill()
                val cone = Path().apply {
                    moveTo(x(26f), y(42f))
                    lineTo(x(60f), y(29f))
                    lineTo(x(60f), y(71f))
                    lineTo(x(26f), y(58f))
                    close()
                }
                canvas.drawPath(cone, paint)
                canvas.drawRect(rect(34f, 58f, 43f, 76f), paint)
                stroke(4.5f)
                canvas.drawArc(oval(62f, 33f, 86f, 67f), -55f, 110f, false, paint)
            }
            "self_praise" -> { // a medal: a job well done, by yourself
                stroke(6f)
                canvas.drawCircle(x(50f), y(42f), x(15f), paint)
                canvas.drawLine(x(40f), y(53f), x(34f), y(76f), paint)
                canvas.drawLine(x(60f), y(53f), x(66f), y(76f), paint)
                fill()
                canvas.drawCircle(x(50f), y(42f), x(5.5f), paint)
            }
            // ---- v0.8.0: generic UI glyphs, used across every screen ----
            "ui_coin" -> { // a coin: ring with a star punched in the middle
                stroke(6f)
                canvas.drawCircle(x(50f), y(50f), x(31f), paint)
                fill()
                val star = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 17.0 else 7.5
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val px = 50f + (r * Math.cos(a)).toFloat()
                    val py = 50f + (r * Math.sin(a)).toFloat()
                    if (i == 0) star.moveTo(x(px), y(py)) else star.lineTo(x(px), y(py))
                }
                star.close()
                canvas.drawPath(star, paint)
            }
            "ui_target" -> { // concentric rings with a bullseye
                stroke(6.5f)
                canvas.drawCircle(x(50f), y(50f), x(30f), paint)
                canvas.drawCircle(x(50f), y(50f), x(18f), paint)
                fill()
                canvas.drawCircle(x(50f), y(50f), x(7f), paint)
            }
            "mirror_check", "ui_mirror" -> { // a hand mirror with a shine
                stroke(6f)
                canvas.drawOval(oval(33f, 12f, 67f, 64f), paint)
                stroke(4f)
                canvas.drawLine(x(44f), y(27f), x(55f), y(43f), paint)
                stroke(6f)
                canvas.drawLine(x(50f), y(64f), x(50f), y(78f), paint)
                canvas.drawLine(x(36f), y(84f), x(64f), y(84f), paint)
            }
            "ui_sparkle" -> { // a four-point sparkle
                fill()
                val spark = Path().apply {
                    moveTo(x(50f), y(10f))
                    quadTo(x(57f), y(43f), x(90f), y(50f))
                    quadTo(x(57f), y(57f), x(50f), y(90f))
                    quadTo(x(43f), y(57f), x(10f), y(50f))
                    quadTo(x(43f), y(43f), x(50f), y(10f))
                    close()
                }
                canvas.drawPath(spark, paint)
            }
            else -> { // fallback: a plain star of effort — a filled circle dot
                fill()
                canvas.drawCircle(x(50f), y(50f), x(16f), paint)
            }
        }
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
