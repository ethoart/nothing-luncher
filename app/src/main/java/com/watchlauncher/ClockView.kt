package com.watchlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.*

class ClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var style: WatchFaceStyle = WatchFaceStyle.NOTHING_DOT
        set(value) { field = value; invalidate() }

    // Animation ticker (16ms = ~60fps for smooth animations)
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 50)
        }
    }

    private var animTick = 0f   // 0..1 cycles for pulse animations
    private var lastMs = System.currentTimeMillis()

    // Paints
    private val p  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // Dot-matrix 5×7
    private val DOT = mapOf(
        '0' to "01110_10001_10011_10101_11001_10001_01110",
        '1' to "00100_01100_00100_00100_00100_00100_01110",
        '2' to "01110_10001_00001_00010_00100_01000_11111",
        '3' to "11111_00010_00100_00010_00001_10001_01110",
        '4' to "00010_00110_01010_10010_11111_00010_00010",
        '5' to "11111_10000_11110_00001_00001_10001_01110",
        '6' to "00110_01000_10000_11110_10001_10001_01110",
        '7' to "11111_00001_00010_00100_01000_01000_01000",
        '8' to "01110_10001_10001_01110_10001_10001_01110",
        '9' to "01110_10001_10001_01111_00001_00010_01100",
        ':' to "00000_00100_00100_00000_00100_00100_00000",
        ' ' to "00000_00000_00000_00000_00000_00000_00000"
    ).mapValues { it.value.split("_") }

    private val DAYS   = arrayOf("SUN","MON","TUE","WED","THU","FRI","SAT")
    private val MONTHS = arrayOf("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC")

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(tickReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
        })
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(ticker)
        try { context.unregisterReceiver(tickReceiver) } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        // Update anim tick
        val now = System.currentTimeMillis()
        animTick = (animTick + (now - lastMs) / 2000f) % 1f
        lastMs = now
        when (style) {
            WatchFaceStyle.NOTHING_DOT  -> drawNothing(canvas)
            WatchFaceStyle.BOLD_DIGITAL -> drawBold(canvas)
            WatchFaceStyle.NEON_MINIMAL -> drawNeon(canvas)
            WatchFaceStyle.RETRO_ORANGE -> drawRetro(canvas)
            WatchFaceStyle.CLEAN_WHITE  -> drawClean(canvas)
        }
    }

    private fun cal() = Calendar.getInstance()

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun drawDotMatrix(canvas: Canvas, text: String, cx: Float, cy: Float,
                               dotSize: Float, color: Int, accentColor: Int = color) {
        val dotGap = dotSize * 0.55f
        val charW  = 5 * dotSize + 4 * dotGap
        val charGap = dotSize * 1.0f
        val totalW = text.length * charW + (text.length - 1) * charGap
        var sx = cx - totalW / 2f
        p.style = Paint.Style.FILL
        for (ch in text) {
            val grid = DOT[ch] ?: DOT[' ']!!
            for (row in grid.indices) {
                val rowStr = grid[row]
                for (col in rowStr.indices) {
                    if (rowStr[col] == '1') {
                        p.color = if (row < 4) color else accentColor
                        val px = sx + col * (dotSize + dotGap) + dotSize / 2f
                        val py = cy + row * (dotSize + dotGap) + dotSize / 2f
                        canvas.drawCircle(px, py, dotSize / 2f, p)
                    }
                }
            }
            sx += charW + charGap
        }
    }

    private fun rect(canvas: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float, radius: Float = 0f) {
        p.color = color; p.style = Paint.Style.FILL
        if (radius > 0) canvas.drawRoundRect(l, t, r, b, radius, radius, p)
        else canvas.drawRect(l, t, r, b, p)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. NOTHING OS — white bg, animated pulsing dot, dot-matrix digits
    // ════════════════════════════════════════════════════════════════════════
    private fun drawNothing(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val c = cal()

        // White circle bg
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, min(w,h)/2f, p)

        // Animated red dot — pulses
        val pulse = 0.7f + 0.3f * sin(animTick * 2 * PI).toFloat()
        p.color = Color.parseColor("#FF3B2F")
        canvas.drawCircle(cx - w*0.28f, cy - h*0.28f, w*0.052f * pulse, p)

        // Dot-matrix time
        val timeStr = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        drawDotMatrix(canvas, timeStr, cx, cy - h*0.18f, w*0.030f,
            Color.parseColor("#111111"), Color.parseColor("#FF3B2F"))

        // Animated progress bar for seconds
        val sec = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND)/1000f
        val bw = w * 0.55f; val bx = cx - bw/2f; val by = cy + h*0.22f
        rect(canvas, Color.parseColor("#EEEEEE"), bx, by, bx+bw, by+4f, 2f)
        rect(canvas, Color.parseColor("#FF3B2F"), bx, by, bx+bw*(sec/60f), by+4f, 2f)

        // Date
        dp.color = Color.parseColor("#888888")
        dp.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        dp.textSize = h * 0.075f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]}  ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}", cx, cy + h*0.36f, dp)

        // Nothing OS logo dots row (3 dots bottom)
        val dotY = cy + h*0.42f
        val spacing = w * 0.055f
        for (i in -1..1) {
            p.color = if (i == 0) Color.parseColor("#FF3B2F") else Color.parseColor("#CCCCCC")
            canvas.drawCircle(cx + i*spacing, dotY, w*0.018f, p)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. BOLD DIGITAL — animated color sweep on minutes
    // ════════════════════════════════════════════════════════════════════════
    private fun drawBold(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h/2f
        val c = cal()

        canvas.drawColor(Color.BLACK)

        // Animated scan line
        val scanY = (animTick * h)
        p.color = Color.argb(30, 255, 59, 47)
        p.style = Paint.Style.FILL
        canvas.drawRect(0f, scanY - 2f, w, scanY + 2f, p)

        // Hour
        tp.color = Color.WHITE
        tp.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        tp.textSize = h * 0.36f
        canvas.drawText(String.format("%02d", c.get(Calendar.HOUR_OF_DAY)), cx, cy - h*0.03f, tp)

        // Animated red divider line
        p.color = Color.parseColor("#FF3B2F"); p.style = Paint.Style.FILL
        val lineW = w * 0.35f * (0.7f + 0.3f * sin(animTick * 2 * PI).toFloat())
        canvas.drawRect(cx - lineW, cy + h*0.055f, cx + lineW, cy + h*0.065f, p)

        // Minute
        tp.color = Color.parseColor("#FF3B2F")
        canvas.drawText(String.format("%02d", c.get(Calendar.MINUTE)), cx, cy + h*0.43f, tp)

        // Day top-right
        dp.color = Color.parseColor("#444444")
        dp.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        dp.textSize = h * 0.075f
        dp.textAlign = Paint.Align.RIGHT
        canvas.drawText(DAYS[c.get(Calendar.DAY_OF_WEEK)-1], w - w*0.08f, cy - h*0.44f, dp)
        dp.textAlign = Paint.Align.CENTER
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. NEON — animated particle ring + smooth seconds arc
    // ════════════════════════════════════════════════════════════════════════
    private fun drawNeon(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h/2f
        val c = cal()

        canvas.drawColor(Color.parseColor("#050510"))

        val r = min(w,h)/2f - 8f

        // Animated rotating particles on ring
        p.style = Paint.Style.FILL
        for (i in 0..7) {
            val angle = (animTick * 2 * PI + i * PI / 4).toFloat()
            val px = cx + r * cos(angle)
            val py = cy + r * sin(angle)
            val alpha = (127 + 128 * sin(animTick * 2 * PI + i).toFloat()).toInt().coerceIn(0, 255)
            p.color = Color.argb(alpha, 0, 255, 200)
            canvas.drawCircle(px, py, 2.5f, p)
        }

        // Seconds arc
        val sec = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND)/1000f
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#00FFCC")
        }
        val oval = RectF(8f, 8f, w-8f, h-8f)
        canvas.drawArc(oval, -90f, sec/60f*360f, false, arcPaint)

        // Time
        tp.color = Color.parseColor("#00FFCC")
        tp.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        tp.textSize = h * 0.28f
        canvas.drawText(String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), cx, cy + tp.textSize*0.35f, tp)

        // Date
        dp.color = Color.parseColor("#007755")
        dp.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        dp.textSize = h * 0.075f
        canvas.drawText("${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}", cx, cy + h*0.40f, dp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. RETRO ORANGE — CRT flicker + analog-style seconds hand
    // ════════════════════════════════════════════════════════════════════════
    private fun drawRetro(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h/2f
        val c = cal()

        canvas.drawColor(Color.parseColor("#0A0A0A"))

        // CRT scanlines
        p.color = Color.argb(15, 0, 0, 0); p.style = Paint.Style.FILL
        var sy = 0f
        while (sy < h) { canvas.drawRect(0f, sy, w, sy+1f, p); sy += 3f }

        // CRT flicker
        val flicker = 0.92f + 0.08f * sin(animTick * 2 * PI * 7).toFloat()
        canvas.saveLayerAlpha(0f, 0f, w, h, (255 * flicker).toInt())

        // Time in orange
        tp.color = Color.parseColor("#FF6B00")
        tp.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        tp.textSize = h * 0.30f
        canvas.drawText(String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), cx, cy + tp.textSize*0.35f, tp)

        // Seconds as analog hand
        val sec = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND)/1000f
        val secAngle = (sec/60f * 2 * PI - PI/2).toFloat()
        val handR = min(w,h)/2f - 16f
        val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#FF6B00"); strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, cx + handR*cos(secAngle), cy + handR*sin(secAngle), handPaint)
        p.color = Color.parseColor("#FF6B00"); p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 4f, p)

        // AM/PM badge
        val ampm = if (c.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
        p.color = Color.parseColor("#FF6B00")
        canvas.drawRoundRect(cx+w*0.18f, cy-h*0.43f, cx+w*0.42f, cy-h*0.28f, 6f, 6f, p)
        dp.color = Color.BLACK; dp.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); dp.textSize = h*0.09f
        canvas.drawText(ampm, cx+w*0.30f, cy-h*0.32f, dp)

        canvas.restore()

        dp.color = Color.parseColor("#554433")
        dp.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL); dp.textSize = h*0.075f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]}  ${c.get(Calendar.DAY_OF_MONTH)}", cx, cy+h*0.40f, dp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. CLEAN WHITE — smooth sweep second hand, minimal
    // ════════════════════════════════════════════════════════════════════════
    private fun drawClean(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h/2f
        val c = cal()

        p.color = Color.WHITE; p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, min(w,h)/2f, p)

        // Hour/minute ticks
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.parseColor("#DDDDDD") }
        val r = min(w,h)/2f - 6f
        for (i in 0..59) {
            val a = (i / 60f * 2 * PI - PI/2).toFloat()
            val inner = if (i % 5 == 0) r - 8f else r - 4f
            canvas.drawLine(cx + inner*cos(a), cy + inner*sin(a), cx + r*cos(a), cy + r*sin(a), tickPaint)
        }

        // Smooth second hand
        val sec = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND)/1000f
        val secA = (sec/60f * 2 * PI - PI/2).toFloat()
        val secR = r - 10f
        val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.parseColor("#FF3B2F"); strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(cx - secR*0.2f*cos(secA), cy - secR*0.2f*sin(secA), cx + secR*cos(secA), cy + secR*sin(secA), secPaint)

        // Hour hand
        val hour = c.get(Calendar.HOUR) + c.get(Calendar.MINUTE)/60f
        val hrA = (hour/12f * 2 * PI - PI/2).toFloat()
        val hrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.parseColor("#111111"); strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(cx, cy, cx + (r*0.5f)*cos(hrA), cy + (r*0.5f)*sin(hrA), hrPaint)

        // Minute hand
        val min = c.get(Calendar.MINUTE) + c.get(Calendar.SECOND)/60f
        val minA = (min/60f * 2 * PI - PI/2).toFloat()
        val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = Color.parseColor("#333333"); strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(cx, cy, cx + (r*0.7f)*cos(minA), cy + (r*0.7f)*sin(minA), minPaint)

        p.color = Color.WHITE; canvas.drawCircle(cx, cy, 5f, p)
        p.color = Color.parseColor("#FF3B2F"); canvas.drawCircle(cx, cy, 3f, p)

        dp.color = Color.parseColor("#AAAAAA"); dp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); dp.textSize = h*0.08f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]} ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}", cx, cy + h*0.32f, dp)
    }
}
