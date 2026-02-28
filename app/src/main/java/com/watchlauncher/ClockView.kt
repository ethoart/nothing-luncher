package com.watchlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.min

class ClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var style: WatchFaceStyle = WatchFaceStyle.NOTHING_DOT
        set(value) { field = value; invalidate() }

    private val dotPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint    = Paint(Paint.ANTI_ALIAS_FLAG)

    // 5x7 dot-matrix font
    private val dotDigits = mapOf(
        '0' to listOf("01110","10001","10011","10101","11001","10001","01110"),
        '1' to listOf("00100","01100","00100","00100","00100","00100","01110"),
        '2' to listOf("01110","10001","00001","00010","00100","01000","11111"),
        '3' to listOf("11111","00010","00100","00010","00001","10001","01110"),
        '4' to listOf("00010","00110","01010","10010","11111","00010","00010"),
        '5' to listOf("11111","10000","11110","00001","00001","10001","01110"),
        '6' to listOf("00110","01000","10000","11110","10001","10001","01110"),
        '7' to listOf("11111","00001","00010","00100","01000","01000","01000"),
        '8' to listOf("01110","10001","10001","01110","10001","10001","01110"),
        '9' to listOf("01110","10001","10001","01111","00001","00010","01100"),
        ':' to listOf("00000","00100","00100","00000","00100","00100","00000")
    )

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(tickReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(tickReceiver) } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (style) {
            WatchFaceStyle.NOTHING_DOT  -> drawNothingFace(canvas)
            WatchFaceStyle.BOLD_DIGITAL -> drawBoldFace(canvas)
            WatchFaceStyle.NEON_MINIMAL -> drawNeonFace(canvas)
            WatchFaceStyle.RETRO_ORANGE -> drawRetroFace(canvas)
            WatchFaceStyle.CLEAN_WHITE  -> drawCleanFace(canvas)
        }
    }

    private fun cal() = Calendar.getInstance()
    private val DAYS   = arrayOf("SUN","MON","TUE","WED","THU","FRI","SAT")
    private val MONTHS = arrayOf("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC")

    // ── Nothing OS dot-matrix ────────────────────────────────────────────────
    private fun drawNothingFace(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val c = cal()
        bgPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, min(w,h)/2f, bgPaint)

        accentPaint.color = Color.parseColor("#FF3B2F")
        canvas.drawCircle(cx - w*0.28f, cy - h*0.30f, w*0.055f, accentPaint)

        val timeStr = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val dotSize = w * 0.030f
        val dotGap  = dotSize * 0.55f
        val charW   = 5 * dotSize + 4 * dotGap
        val charGap = dotSize * 1.1f
        val totalW  = timeStr.length * charW + (timeStr.length - 1) * charGap
        var startX  = cx - totalW / 2f
        dotPaint.color = Color.parseColor("#111111")
        for (ch in timeStr) {
            val grid = dotDigits[ch]
            if (grid != null) {
                for (row in grid.indices) {
                    for (col in 0..4) {
                        if (col < grid[row].length && grid[row][col] == '1') {
                            val px = startX + col*(dotSize+dotGap) + dotSize/2f
                            val py = cy - h*0.10f + row*(dotSize+dotGap) + dotSize/2f
                            canvas.drawCircle(px, py, dotSize/2f, dotPaint)
                        }
                    }
                }
            }
            startX += charW + charGap
        }
        datePaint.color = Color.parseColor("#999999")
        datePaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        datePaint.textSize = h * 0.072f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]}  ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}", cx, cy + h*0.32f, datePaint)
    }

    // ── Bold Digital ─────────────────────────────────────────────────────────
    private fun drawBoldFace(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h/2f
        val c = cal()
        bgPaint.color = Color.BLACK
        canvas.drawRect(0f,0f,w,h,bgPaint)
        timePaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        timePaint.textSize = h * 0.36f
        timePaint.color = Color.WHITE
        canvas.drawText(String.format("%02d", c.get(Calendar.HOUR_OF_DAY)), cx, cy - h*0.02f, timePaint)
        accentPaint.color = Color.parseColor("#FF3B2F")
        accentPaint.strokeWidth = 2.5f
        canvas.drawLine(cx - w*0.32f, cy + h*0.06f, cx + w*0.32f, cy + h*0.06f, accentPaint)
        timePaint.color = Color.parseColor("#FF3B2F")
        canvas.drawText(String.format("%02d", c.get(Calendar.MINUTE)), cx, cy + h*0.44f, timePaint)
        datePaint.color = Color.parseColor("#555555")
        datePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        datePaint.textSize = h * 0.075f
        datePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(DAYS[c.get(Calendar.DAY_OF_WEEK)-1], w - w*0.10f, cy - h*0.44f, datePaint)
        datePaint.textAlign = Paint.Align.CENTER
    }

    // ── Neon Minimal ─────────────────────────────────────────────────────────
    private fun drawNeonFace(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h/2f
        val c = cal()
        bgPaint.color = Color.parseColor("#050510")
        canvas.drawRect(0f,0f,w,h,bgPaint)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f
            color = Color.parseColor("#00FFCC"); alpha = 50
        }
        canvas.drawCircle(cx, cy, min(w,h)/2f - 5f, ringPaint)
        timePaint.color = Color.parseColor("#00FFCC")
        timePaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        timePaint.textSize = h * 0.30f
        canvas.drawText(String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), cx, cy + timePaint.textSize*0.35f, timePaint)
        val sec = c.get(Calendar.SECOND)
        val bx = cx - w*0.28f; val by = cy + h*0.28f
        accentPaint.color = Color.parseColor("#0A2A1A")
        canvas.drawRoundRect(bx, by, bx+w*0.56f, by+5f, 3f, 3f, accentPaint)
        accentPaint.color = Color.parseColor("#00FF88")
        canvas.drawRoundRect(bx, by, bx + w*0.56f*sec/60f, by+5f, 3f, 3f, accentPaint)
        datePaint.color = Color.parseColor("#009977")
        datePaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        datePaint.textSize = h * 0.075f
        canvas.drawText("${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}", cx, cy + h*0.42f, datePaint)
    }

    // ── Retro Orange ─────────────────────────────────────────────────────────
    private fun drawRetroFace(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h/2f
        val c = cal()
        bgPaint.color = Color.parseColor("#0A0A0A")
        canvas.drawRect(0f,0f,w,h,bgPaint)
        timePaint.color = Color.parseColor("#FF6B00")
        timePaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        timePaint.textSize = h * 0.32f
        canvas.drawText(String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), cx, cy + timePaint.textSize*0.35f, timePaint)
        val ampm = if (c.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
        accentPaint.color = Color.parseColor("#FF6B00"); accentPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(cx+w*0.18f, cy-h*0.43f, cx+w*0.42f, cy-h*0.28f), 8f, 8f, accentPaint)
        datePaint.color = Color.BLACK
        datePaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        datePaint.textSize = h * 0.09f
        canvas.drawText(ampm, cx + w*0.30f, cy - h*0.32f, datePaint)
        datePaint.color = Color.parseColor("#555555")
        datePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        datePaint.textSize = h * 0.08f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]}  ${c.get(Calendar.DAY_OF_MONTH)}", cx, cy + h*0.40f, datePaint)
    }

    // ── Clean White ──────────────────────────────────────────────────────────
    private fun drawCleanFace(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h/2f
        val c = cal()
        bgPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, min(w,h)/2f, bgPaint)
        timePaint.color = Color.parseColor("#111111")
        timePaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        timePaint.textSize = h * 0.28f
        canvas.drawText(String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), cx, cy + timePaint.textSize*0.35f, timePaint)
        datePaint.color = Color.parseColor("#AAAAAA")
        datePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        datePaint.textSize = h * 0.085f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1].lowercase().replaceFirstChar{it.uppercase()}}, ${MONTHS[c.get(Calendar.MONTH)].lowercase().replaceFirstChar{it.uppercase()}} ${c.get(Calendar.DAY_OF_MONTH)}", cx, cy + h*0.38f, datePaint)
    }
}
