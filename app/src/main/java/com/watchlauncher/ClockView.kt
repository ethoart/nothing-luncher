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

    var onMissMinutesTap: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            animTick = (animTick + 0.016f) % 1f
            waveOff += 2.5f; if (waveOff > 1000f) waveOff = 0f
            blinkPhase = (blinkPhase + 0.025f) % 1f
            if (isTalking) mouthOpen = (0.3f + 0.7f * abs(sin(animTick * 12 * PI).toFloat()))
            else if (mouthOpen > 0f) mouthOpen = (mouthOpen - 0.05f).coerceAtLeast(0f)
            invalidate()
            handler.postDelayed(this, 50)
        }
    }

    private var animTick = 0f
    private var waveOff = 0f
    private var blinkPhase = 0f
    private var mouthOpen = 0f
    var isTalking = false

    private val p  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

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
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
        })
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(ticker)
        try { context.unregisterReceiver(tickReceiver) } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        when (style) {
            WatchFaceStyle.NOTHING_DOT   -> drawNothing(canvas)
            WatchFaceStyle.BOLD_DIGITAL  -> drawBold(canvas)
            WatchFaceStyle.NEON_MINIMAL  -> drawNeon(canvas)
            WatchFaceStyle.RETRO_ORANGE  -> drawRetro(canvas)
            WatchFaceStyle.CLEAN_WHITE   -> drawClean(canvas)
            WatchFaceStyle.WAVE_SEIKO    -> drawWave(canvas)
            WatchFaceStyle.PIP_BOY       -> drawPipBoy(canvas)
            WatchFaceStyle.JAMES_BOND    -> drawJamesBond(canvas)
            WatchFaceStyle.CASIO_RETRO   -> drawCasio(canvas)
            WatchFaceStyle.MISS_MINUTES  -> drawMissMinutes(canvas)
        }
    }

    private fun cal() = Calendar.getInstance()

    private fun drawDotMatrix(canvas: Canvas, text: String, cx: Float, cy: Float,
                               ds: Float, color: Int, accent: Int = color) {
        val dg=ds*0.55f; val cw=5*ds+4*dg; val cg=ds*1.0f
        val tw=text.length*cw+(text.length-1)*cg; var sx=cx-tw/2f
        p.style=Paint.Style.FILL
        for (ch in text) {
            val grid=DOT[ch] ?: DOT[' ']!!
            for (r in grid.indices) {
                for (c in grid[r].indices) {
                    if (grid[r][c]=='1') {
                        p.color=if(r<4) color else accent
                        canvas.drawCircle(sx+c*(ds+dg)+ds/2f,cy+r*(ds+dg)+ds/2f,ds/2f,p)
                    }
                }
            }
            sx+=cw+cg
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. NOTHING OS
    // ════════════════════════════════════════════════════════════════════════
    private fun drawNothing(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        p.color=Color.WHITE;p.style=Paint.Style.FILL;canvas.drawCircle(cx,cy,min(w,h)/2f,p)
        val pulse=0.7f+0.3f*sin(animTick*2*PI).toFloat()
        p.color=Color.parseColor("#FF3B2F");canvas.drawCircle(cx-w*0.28f,cy-h*0.28f,w*0.052f*pulse,p)
        drawDotMatrix(canvas,String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,cy-h*0.18f,w*0.030f,Color.parseColor("#111111"),Color.parseColor("#FF3B2F"))
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val bw=w*0.55f;val bx=cx-bw/2f;val by=cy+h*0.22f
        p.color=Color.parseColor("#EEEEEE");canvas.drawRoundRect(bx,by,bx+bw,by+4f,2f,2f,p)
        p.color=Color.parseColor("#FF3B2F");canvas.drawRoundRect(bx,by,bx+bw*(sec/60f),by+4f,2f,2f,p)
        dp.color=Color.parseColor("#888888");dp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);dp.textSize=h*0.075f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]}  ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}",cx,cy+h*0.36f,dp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. BOLD DIGITAL
    // ════════════════════════════════════════════════════════════════════════
    private fun drawBold(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        canvas.drawColor(Color.BLACK)
        val sy=animTick*h;p.color=Color.argb(25,255,59,47);p.style=Paint.Style.FILL
        canvas.drawRect(0f,sy-2f,w,sy+2f,p)
        tp.color=Color.WHITE;tp.typeface=Typeface.create(Typeface.DEFAULT_BOLD,Typeface.BOLD);tp.textSize=h*0.36f
        canvas.drawText(String.format("%02d",c.get(Calendar.HOUR_OF_DAY)),cx,cy-h*0.03f,tp)
        p.color=Color.parseColor("#FF3B2F");p.style=Paint.Style.FILL
        val lw=w*0.35f*(0.7f+0.3f*sin(animTick*2*PI).toFloat())
        canvas.drawRect(cx-lw,cy+h*0.055f,cx+lw,cy+h*0.065f,p)
        tp.color=Color.parseColor("#FF3B2F")
        canvas.drawText(String.format("%02d",c.get(Calendar.MINUTE)),cx,cy+h*0.43f,tp)
        dp.color=Color.parseColor("#444444");dp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);dp.textSize=h*0.075f
        dp.textAlign=Paint.Align.RIGHT;canvas.drawText(DAYS[c.get(Calendar.DAY_OF_WEEK)-1],w-w*0.08f,cy-h*0.44f,dp);dp.textAlign=Paint.Align.CENTER
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. NEON
    // ════════════════════════════════════════════════════════════════════════
    private fun drawNeon(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        canvas.drawColor(Color.parseColor("#050510"))
        val r=min(w,h)/2f-8f
        p.style=Paint.Style.FILL
        for (i in 0..7) {
            val a=(animTick*2*PI+i*PI/4).toFloat()
            val alpha=(127+128*sin(animTick*2*PI+i).toFloat()).toInt().coerceIn(0,255)
            p.color=Color.argb(alpha,0,255,200)
            canvas.drawCircle(cx+r*cos(a),cy+r*sin(a),2.5f,p)
        }
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val ap=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;strokeCap=Paint.Cap.ROUND;color=Color.parseColor("#00FFCC")}
        canvas.drawArc(RectF(8f,8f,w-8f,h-8f),-90f,sec/60f*360f,false,ap)
        tp.color=Color.parseColor("#00FFCC");tp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);tp.textSize=h*0.28f
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,cy+tp.textSize*0.35f,tp)
        dp.color=Color.parseColor("#007755");dp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);dp.textSize=h*0.075f
        canvas.drawText("${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}",cx,cy+h*0.40f,dp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. RETRO ORANGE
    // ════════════════════════════════════════════════════════════════════════
    private fun drawRetro(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        canvas.drawColor(Color.parseColor("#0A0A0A"))
        p.color=Color.argb(15,0,0,0);p.style=Paint.Style.FILL
        var sy=0f;while(sy<h){canvas.drawRect(0f,sy,w,sy+1f,p);sy+=3f}
        val fl=0.92f+0.08f*sin(animTick*2*PI*7).toFloat()
        canvas.saveLayerAlpha(0f,0f,w,h,(255*fl).toInt())
        tp.color=Color.parseColor("#FF6B00");tp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);tp.textSize=h*0.30f
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,cy+tp.textSize*0.35f,tp)
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val sa=(sec/60f*2*PI-PI/2).toFloat();val hr2=min(w,h)/2f-16f
        val hp=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2f;color=Color.parseColor("#FF6B00");strokeCap=Paint.Cap.ROUND}
        canvas.drawLine(cx,cy,cx+hr2*cos(sa),cy+hr2*sin(sa),hp)
        p.color=Color.parseColor("#FF6B00");p.style=Paint.Style.FILL;canvas.drawCircle(cx,cy,4f,p)
        val ampm=if(c.get(Calendar.HOUR_OF_DAY)<12)"AM" else "PM"
        p.color=Color.parseColor("#FF6B00");canvas.drawRoundRect(RectF(cx+w*0.18f,cy-h*0.43f,cx+w*0.42f,cy-h*0.28f),6f,6f,p)
        dp.color=Color.BLACK;dp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);dp.textSize=h*0.09f
        canvas.drawText(ampm,cx+w*0.30f,cy-h*0.32f,dp)
        canvas.restore()
        dp.color=Color.parseColor("#554433");dp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);dp.textSize=h*0.075f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]}  ${c.get(Calendar.DAY_OF_MONTH)}",cx,cy+h*0.40f,dp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. CLEAN ANALOG
    // ════════════════════════════════════════════════════════════════════════
    private fun drawClean(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        p.color=Color.WHITE;p.style=Paint.Style.FILL;canvas.drawCircle(cx,cy,min(w,h)/2f,p)
        val tickPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.5f;color=Color.parseColor("#DDDDDD")}
        val r=min(w,h)/2f-6f
        for(i in 0..59){val a=(i/60f*2*PI-PI/2).toFloat();val inn=if(i%5==0)r-8f else r-4f
            canvas.drawLine(cx+inn*cos(a),cy+inn*sin(a),cx+r*cos(a),cy+r*sin(a),tickPaint)}
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val sa=(sec/60f*2*PI-PI/2).toFloat()
        val sp=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.5f;color=Color.parseColor("#FF3B2F");strokeCap=Paint.Cap.ROUND}
        canvas.drawLine(cx-(r*0.2f)*cos(sa),cy-(r*0.2f)*sin(sa),cx+(r-10f)*cos(sa),cy+(r-10f)*sin(sa),sp)
        val hr=c.get(Calendar.HOUR)+c.get(Calendar.MINUTE)/60f;val ha=(hr/12f*2*PI-PI/2).toFloat()
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;color=Color.parseColor("#111111");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+(r*0.5f)*cos(ha),cy+(r*0.5f)*sin(ha),it)}
        val mn=c.get(Calendar.MINUTE)+sec/60f;val ma=(mn/60f*2*PI-PI/2).toFloat()
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2.5f;color=Color.parseColor("#333333");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+(r*0.7f)*cos(ma),cy+(r*0.7f)*sin(ma),it)}
        p.color=Color.WHITE;canvas.drawCircle(cx,cy,5f,p);p.color=Color.parseColor("#FF3B2F");canvas.drawCircle(cx,cy,3f,p)
        dp.color=Color.parseColor("#AAAAAA");dp.typeface=Typeface.create(Typeface.DEFAULT,Typeface.NORMAL);dp.textSize=h*0.08f
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]} ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}",cx,cy+h*0.32f,dp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. SEIKO WAVE — animated Great Wave ocean
    // ════════════════════════════════════════════════════════════════════════
    private fun drawWave(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        p.color=Color.parseColor("#0A1628");p.style=Paint.Style.FILL;canvas.drawRect(0f,0f,w,h,p)

        // Animated layered waves
        val cols=listOf(Color.parseColor("#0D3B6E"),Color.parseColor("#1565C0"),Color.parseColor("#1976D2"),Color.parseColor("#42A5F5"))
        for (layer in cols.indices) {
            val path=Path()
            val amp=h*(0.09f-layer*0.015f); val freq=1.5f+layer*0.4f
            val phase=waveOff*(0.8f+layer*0.3f); val baseY=h*(0.42f+layer*0.09f)
            path.moveTo(0f,h)
            var x=0f
            while(x<=w+2f){
                val y=baseY+amp*sin((x*freq/w*2*PI+phase*0.05f)).toFloat()
                path.lineTo(x,y); x+=2f
            }
            path.lineTo(w,h);path.close()
            p.color=cols[layer];p.alpha=200-layer*25;canvas.drawPath(path,p);p.alpha=255
        }

        // Foam caps
        p.color=Color.argb(160,255,255,255);p.style=Paint.Style.FILL
        for(i in 0..10){
            val fx=(i/10f*w+waveOff*0.6f)%w
            val fy=h*0.36f+h*0.04f*sin((fx/w*4*PI).toFloat())
            canvas.drawCircle(fx,fy,2f,p)
        }

        // SEIKO text
        tp.color=Color.WHITE;tp.typeface=Typeface.create(Typeface.DEFAULT_BOLD,Typeface.BOLD);tp.textSize=h*0.11f
        canvas.drawText("SEIKO",cx,cy-h*0.08f,tp)
        dp.color=Color.parseColor("#90CAF9");dp.typeface=Typeface.create(Typeface.DEFAULT,Typeface.ITALIC);dp.textSize=h*0.055f
        canvas.drawText("AUTOMATIC · 17 JEWELS",cx,cy+h*0.00f,dp)

        // Hour markers
        val mr=min(w,h)/2f-8f
        for(i in 0..11){
            val a=(i/12f*2*PI-PI/2).toFloat()
            Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;color=Color.parseColor("#CFD8DC");strokeCap=Paint.Cap.ROUND}.also{
                canvas.drawLine(cx+(mr-10f)*cos(a),cy+(mr-10f)*sin(a),cx+mr*cos(a),cy+mr*sin(a),it)}
        }

        // Analog hands
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val handR=mr-18f
        val hr=c.get(Calendar.HOUR)+c.get(Calendar.MINUTE)/60f;val ha=(hr/12f*2*PI-PI/2).toFloat()
        val mn=c.get(Calendar.MINUTE)+sec/60f;val ma=(mn/60f*2*PI-PI/2).toFloat()
        val sa=(sec/60f*2*PI-PI/2).toFloat()
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;color=Color.WHITE;strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+handR*0.55f*cos(ha),cy+handR*0.55f*sin(ha),it)}
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2.5f;color=Color.WHITE;strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+handR*0.78f*cos(ma),cy+handR*0.78f*sin(ma),it)}
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.5f;color=Color.parseColor("#EF5350");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx-handR*0.15f*cos(sa),cy-handR*0.15f*sin(sa),cx+handR*0.88f*cos(sa),cy+handR*0.88f*sin(sa),it)}
        p.color=Color.WHITE;p.style=Paint.Style.FILL;canvas.drawCircle(cx,cy,5f,p);p.color=Color.parseColor("#EF5350");canvas.drawCircle(cx,cy,3f,p)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. PIP-BOY 3000 — Fallout green CRT terminal
    // ════════════════════════════════════════════════════════════════════════
    private fun drawPipBoy(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val c=cal()
        val G=Color.parseColor("#00FF41");val DG=Color.parseColor("#003B10");val MG=Color.parseColor("#00C030")
        canvas.drawColor(Color.parseColor("#030D03"))
        p.color=Color.argb(20,0,255,60);p.style=Paint.Style.FILL
        var sy=0f;while(sy<h){canvas.drawRect(0f,sy,w,sy+1f,p);sy+=3f}
        val fl=0.94f+0.06f*sin(animTick*2*PI*13).toFloat()
        canvas.saveLayerAlpha(0f,0f,w,h,(255*fl).toInt())
        tp.color=G;tp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);tp.textSize=h*0.075f
        canvas.drawText("PIP-BOY 3000",cx,h*0.09f,tp)
        dp.color=MG;dp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);dp.textSize=h*0.052f
        canvas.drawText("ROBCO INDUSTRIES",cx,h*0.165f,dp)
        p.color=DG;p.style=Paint.Style.FILL;canvas.drawRect(w*0.05f,h*0.20f,w*0.95f,h*0.215f,p)
        dp.textSize=h*0.062f;dp.color=G
        val dateStr=String.format("%02d.%02d.%04d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR))
        dp.textAlign=Paint.Align.LEFT;canvas.drawText(dateStr,w*0.06f,h*0.29f,dp)
        dp.textAlign=Paint.Align.RIGHT;canvas.drawText(DAYS[c.get(Calendar.DAY_OF_WEEK)-1],w*0.94f,h*0.29f,dp)
        dp.textAlign=Paint.Align.CENTER
        tp.textSize=h*0.38f;tp.color=G
        canvas.drawText(String.format("%02d",c.get(Calendar.HOUR_OF_DAY)),cx-w*0.15f,h*0.62f,tp)
        tp.textSize=h*0.20f;canvas.drawText(":",cx,h*0.56f,tp)
        tp.textSize=h*0.38f;canvas.drawText(String.format("%02d",c.get(Calendar.MINUTE)),cx+w*0.15f,h*0.62f,tp)
        tp.textSize=h*0.07f;tp.color=MG;canvas.drawText(if(c.get(Calendar.HOUR_OF_DAY)<12)"AM" else "PM",cx,h*0.70f,tp)
        val stats=listOf("HP" to 85,"RAD" to 12,"CAPS" to 247)
        stats.forEachIndexed{i,(label,value)->
            val by=h*(0.775f+i*0.073f)
            dp.textAlign=Paint.Align.LEFT;dp.textSize=h*0.052f;dp.color=MG
            canvas.drawText(label,w*0.05f,by,dp)
            p.color=DG;canvas.drawRect(w*0.20f,by-h*0.038f,w*0.78f,by-h*0.005f,p)
            p.color=G;canvas.drawRect(w*0.20f,by-h*0.038f,w*0.20f+w*0.58f*value/300f,by-h*0.005f,p)
        }
        dp.textAlign=Paint.Align.CENTER
        canvas.restore()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. JAMES BOND 007
    // ════════════════════════════════════════════════════════════════════════
    private fun drawJamesBond(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        canvas.drawColor(Color.parseColor("#0A0E0A"))
        val sweepAngle=animTick*360f
        val radarR=min(w,h)*0.32f
        p.style=Paint.Style.STROKE;p.strokeWidth=1.5f;p.color=Color.parseColor("#1AFF8800")
        canvas.drawCircle(cx,cy,radarR,p)
        val sg=Paint(Paint.ANTI_ALIAS_FLAG).apply{
            style=Paint.Style.FILL
            shader=SweepGradient(cx,cy,intArrayOf(Color.argb(0,255,136,0),Color.argb(90,255,136,0),Color.argb(0,255,136,0)),floatArrayOf(0f,0.12f,0.25f))
        }
        canvas.save();canvas.rotate(sweepAngle,cx,cy);canvas.drawCircle(cx,cy,radarR,sg);canvas.restore()
        for(r in listOf(0.14f,0.24f,0.34f,0.44f)){p.style=Paint.Style.STROKE;p.strokeWidth=0.8f;p.color=Color.argb(60,255,136,0);canvas.drawCircle(cx,cy,min(w,h)*r,p)}
        p.style=Paint.Style.STROKE;p.strokeWidth=0.8f;p.color=Color.argb(80,255,136,0)
        canvas.drawLine(cx-w*0.45f,cy,cx+w*0.45f,cy,p);canvas.drawLine(cx,cy-h*0.45f,cx,cy+h*0.45f,p)
        tp.color=Color.parseColor("#FF8800");tp.typeface=Typeface.create(Typeface.DEFAULT_BOLD,Typeface.BOLD);tp.textSize=h*0.095f
        canvas.drawText("007",cx,cy-h*0.22f,tp)
        dp.color=Color.parseColor("#CCAA44");dp.typeface=Typeface.create(Typeface.DEFAULT,Typeface.NORMAL);dp.textSize=h*0.062f
        canvas.drawText("JAMES BOND",cx,cy-h*0.11f,dp)
        tp.color=Color.parseColor("#FF8800");tp.textSize=h*0.28f
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,cy+h*0.10f,tp)
        p.color=Color.parseColor("#FF8800");p.style=Paint.Style.FILL
        canvas.drawRoundRect(cx-w*0.30f,cy+h*0.20f,cx+w*0.30f,cy+h*0.32f,8f,8f,p)
        tp.color=Color.BLACK;tp.textSize=h*0.072f;canvas.drawText("EDITION",cx,cy+h*0.285f,tp)
        val pd=0.6f+0.4f*sin(animTick*2*PI).toFloat()
        p.color=Color.parseColor("#FF8800");canvas.drawCircle(cx+w*0.30f,cy-h*0.32f,w*0.028f*pd,p)
        dp.color=Color.parseColor("#443322");dp.textSize=h*0.060f
        canvas.drawText(String.format("%02d/%02d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1),cx,cy+h*0.42f,dp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. CASIO G-SHOCK
    // ════════════════════════════════════════════════════════════════════════
    private fun drawCasio(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val c=cal()
        canvas.drawColor(Color.parseColor("#0D0D0D"))
        p.color=Color.parseColor("#1A1A1A");p.style=Paint.Style.FILL
        canvas.drawRoundRect(w*0.02f,h*0.02f,w*0.98f,h*0.98f,18f,18f,p)
        p.color=Color.parseColor("#222222");canvas.drawRect(w*0.04f,h*0.04f,w*0.96f,h*0.155f,p)
        tp.color=Color.WHITE;tp.typeface=Typeface.create(Typeface.DEFAULT_BOLD,Typeface.BOLD);tp.textSize=h*0.095f
        canvas.drawText("CASIO",cx,h*0.130f,tp)
        p.color=Color.parseColor("#8A9E88");p.style=Paint.Style.FILL
        canvas.drawRoundRect(w*0.06f,h*0.175f,w*0.94f,h*0.575f,8f,8f,p)
        p.color=Color.argb(20,0,0,0);p.style=Paint.Style.STROKE;p.strokeWidth=0.5f
        var gx=w*0.06f;while(gx<w*0.94f){canvas.drawLine(gx,h*0.175f,gx,h*0.575f,p);gx+=w*0.07f}
        var gy=h*0.175f;while(gy<h*0.575f){canvas.drawLine(w*0.06f,gy,w*0.94f,gy,p);gy+=h*0.048f}
        tp.color=Color.parseColor("#1A2A18");tp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);tp.textSize=h*0.29f
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,h*0.460f,tp)
        dp.color=Color.parseColor("#2A3A28");dp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);dp.textSize=h*0.062f
        canvas.drawText(String.format(":%02d  %s",c.get(Calendar.SECOND),DAYS[c.get(Calendar.DAY_OF_WEEK)-1]),cx,h*0.540f,dp)
        p.color=Color.parseColor("#111111");p.style=Paint.Style.FILL;canvas.drawRect(w*0.04f,h*0.615f,w*0.96f,h*0.695f,p)
        val modes=listOf("ALM","TMR","STW","MODE")
        modes.forEachIndexed{i,m->dp.color=Color.parseColor("#777777");dp.textSize=h*0.052f;dp.textAlign=Paint.Align.LEFT;canvas.drawText(m,w*0.08f+i*(w*0.22f),h*0.678f,dp)}
        dp.textAlign=Paint.Align.CENTER
        p.color=Color.parseColor("#181818");canvas.drawRoundRect(w*0.06f,h*0.725f,w*0.94f,h*0.960f,8f,8f,p)
        dp.color=Color.parseColor("#555555");dp.textSize=h*0.072f
        canvas.drawText(String.format("%02d/%02d/%04d  %s",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR),DAYS[c.get(Calendar.DAY_OF_WEEK)-1]),cx,h*0.845f,dp)
        dp.color=Color.parseColor("#333333");dp.textSize=h*0.052f;canvas.drawText("WR 200M  G-SHOCK",cx,h*0.935f,dp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. MISS MINUTES — TVA Loki animated character with AI
    // ════════════════════════════════════════════════════════════════════════
    private fun drawMissMinutes(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()

        // TVA amber bg
        val bgG=RadialGradient(cx,cy*0.7f,max(w,h)*0.8f,
            intArrayOf(Color.parseColor("#4A2000"),Color.parseColor("#1A0800")),null,Shader.TileMode.CLAMP)
        p.shader=bgG;p.style=Paint.Style.FILL;canvas.drawRect(0f,0f,w,h,p);p.shader=null

        // Scanlines
        p.color=Color.argb(10,0,0,0);p.style=Paint.Style.FILL
        var sy=0f;while(sy<h){canvas.drawRect(0f,sy,w,sy+1f,p);sy+=2f}

        // TVA header
        tp.color=Color.parseColor("#FF8C00");tp.typeface=Typeface.create(Typeface.DEFAULT_BOLD,Typeface.BOLD);tp.textSize=h*0.068f
        canvas.drawText("⏳  T.V.A.  ⏳",cx,h*0.080f,tp)
        dp.color=Color.parseColor("#AA5500");dp.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);dp.textSize=h*0.042f
        canvas.drawText("TIME VARIANCE AUTHORITY",cx,h*0.135f,dp)

        // ── Miss Minutes body ─────────────────────────────────────────────
        val bodyR=min(w,h)*0.265f
        val bodyX=cx; val bodyY=cy-h*0.030f

        // Glow
        val glow=0.6f+0.4f*sin(animTick*2*PI).toFloat()
        val glowP=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL;shader=RadialGradient(bodyX,bodyY,bodyR*1.5f,intArrayOf(Color.argb((70*glow).toInt(),255,140,0),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)}
        canvas.drawCircle(bodyX,bodyY,bodyR*1.5f,glowP)

        // Body
        val bodyG=RadialGradient(bodyX-bodyR*0.25f,bodyY-bodyR*0.25f,bodyR*1.3f,
            intArrayOf(Color.parseColor("#FFAA00"),Color.parseColor("#DD6600"),Color.parseColor("#8B3200")),
            floatArrayOf(0f,0.55f,1f),Shader.TileMode.CLAMP)
        p.shader=bodyG;p.style=Paint.Style.FILL;canvas.drawCircle(bodyX,bodyY,bodyR,p);p.shader=null

        // Inner body ring
        p.color=Color.parseColor("#7A2800");p.style=Paint.Style.STROKE;p.strokeWidth=2f
        canvas.drawCircle(bodyX,bodyY,bodyR*0.88f,p)

        // Clock ticks
        p.strokeWidth=2.5f
        for(i in 0..11){
            val a=(i/12f*2*PI-PI/2).toFloat()
            val inner=if(i%3==0) bodyR*0.68f else bodyR*0.78f
            p.color=if(i%3==0) Color.parseColor("#6B2200") else Color.parseColor("#8B3200")
            canvas.drawLine(bodyX+inner*cos(a),bodyY+inner*sin(a),bodyX+bodyR*0.88f*cos(a),bodyY+bodyR*0.88f*sin(a),p)
        }

        // ── Eyes ────────────────────────────────────────────────────────────
        val eox=bodyR*0.275f; val eoy=bodyR*0.08f
        val ew=bodyR*0.21f; val eh=bodyR*0.27f
        val blink=if(blinkPhase>0.94f)(1f-(blinkPhase-0.94f)/0.06f).coerceIn(0f,1f) else 1f

        for(ex in listOf(bodyX-eox,bodyX+eox)) {
            p.color=Color.WHITE;p.style=Paint.Style.FILL
            canvas.drawOval(RectF(ex-ew/2f,bodyY-eoy-eh/2f*blink,ex+ew/2f,bodyY-eoy+eh/2f*blink),p)
            if(blink>0.1f){
                p.color=Color.parseColor("#1A0800");canvas.drawCircle(ex,bodyY-eoy+eh*0.08f*blink,ew*0.30f*blink,p)
                p.color=Color.WHITE;canvas.drawCircle(ex+ew*0.07f,bodyY-eoy-eh*0.08f*blink,ew*0.09f*blink,p)
                // Lashes
                p.color=Color.parseColor("#3A1000");p.style=Paint.Style.STROKE;p.strokeWidth=1.5f
                for(l in -2..2){canvas.drawLine(ex+l*ew*0.14f,bodyY-eoy-eh/2f*blink,ex+l*ew*0.14f+(l*1.2f),bodyY-eoy-eh/2f*blink-bodyR*0.07f,p)}
            }
        }

        // ── Mouth ──────────────────────────────────────────────────────────
        val my=bodyY+bodyR*0.40f; val mw=bodyR*0.32f
        val mPath=Path()
        if(mouthOpen>0.08f) {
            // Open/talking
            mPath.moveTo(bodyX-mw,my)
            mPath.quadTo(bodyX,my+bodyR*0.22f*mouthOpen,bodyX+mw,my)
            mPath.quadTo(bodyX,my+bodyR*0.04f,bodyX-mw,my); mPath.close()
            p.color=Color.parseColor("#1A0800");p.style=Paint.Style.FILL;canvas.drawPath(mPath,p)
            p.color=Color.WHITE
            canvas.drawRect(bodyX-mw*0.46f,my,bodyX-mw*0.02f,my+bodyR*0.10f*mouthOpen,p)
            canvas.drawRect(bodyX+mw*0.02f,my,bodyX+mw*0.46f,my+bodyR*0.10f*mouthOpen,p)
        } else {
            // Smile
            mPath.moveTo(bodyX-mw,my);mPath.quadTo(bodyX,my+bodyR*0.16f,bodyX+mw,my)
            p.color=Color.parseColor("#5A1800");p.style=Paint.Style.STROKE;p.strokeWidth=3f;canvas.drawPath(mPath,p)
        }

        // ── Arms ────────────────────────────────────────────────────────────
        val aw=animTick*2*PI; val leftArmAng=(-0.4f+0.25f*sin(aw).toFloat())
        val rightArmAng=(0.4f-0.25f*sin(aw).toFloat())
        val armP=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=bodyR*0.115f;color=Color.parseColor("#CC5500");strokeCap=Paint.Cap.ROUND}
        val lax=bodyX-bodyR*0.88f; val lax2=bodyX-bodyR*0.55f
        canvas.drawLine(lax2,bodyY+bodyR*0.12f,lax,bodyY+bodyR*0.12f+bodyR*0.4f*sin(leftArmAng),armP)
        val rax=bodyX+bodyR*0.88f; val rax2=bodyX+bodyR*0.55f
        canvas.drawLine(rax2,bodyY+bodyR*0.12f,rax,bodyY+bodyR*0.12f+bodyR*0.4f*sin(rightArmAng),armP)
        p.color=Color.parseColor("#FFCC88");p.style=Paint.Style.FILL
        canvas.drawCircle(lax,bodyY+bodyR*0.12f+bodyR*0.4f*sin(leftArmAng),bodyR*0.095f,p)
        canvas.drawCircle(rax,bodyY+bodyR*0.12f+bodyR*0.4f*sin(rightArmAng),bodyR*0.095f,p)

        // ── Legs ────────────────────────────────────────────────────────────
        val lb=sin(animTick*2*PI*2f).toFloat()*bodyR*0.035f
        val legP=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=bodyR*0.095f;color=Color.parseColor("#CC5500");strokeCap=Paint.Cap.ROUND}
        canvas.drawLine(bodyX-bodyR*0.14f,bodyY+bodyR,bodyX-bodyR*0.18f,bodyY+bodyR*1.30f+lb,legP)
        canvas.drawLine(bodyX+bodyR*0.14f,bodyY+bodyR,bodyX+bodyR*0.18f,bodyY+bodyR*1.30f-lb,legP)
        p.color=Color.parseColor("#FF8C00");p.style=Paint.Style.FILL
        canvas.drawRoundRect(bodyX-bodyR*0.33f,bodyY+bodyR*1.32f+lb,bodyX-bodyR*0.02f,bodyY+bodyR*1.46f+lb,7f,7f,p)
        canvas.drawRoundRect(bodyX+bodyR*0.02f,bodyY+bodyR*1.32f-lb,bodyX+bodyR*0.33f,bodyY+bodyR*1.46f-lb,7f,7f,p)

        // ── Time (on her body) ──────────────────────────────────────────────
        tp.color=Color.parseColor("#5A1800");tp.typeface=Typeface.create(Typeface.DEFAULT_BOLD,Typeface.BOLD);tp.textSize=bodyR*0.36f
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),bodyX,bodyY+bodyR*0.66f,tp)

        // ── Tap hint / speech bubble ─────────────────────────────────────
        if(isTalking){
            p.color=Color.parseColor("#FF8C00");p.style=Paint.Style.FILL
            canvas.drawRoundRect(cx-w*0.38f,h*0.88f,cx+w*0.38f,h*0.97f,12f,12f,p)
            dp.color=Color.BLACK;dp.textSize=h*0.048f;dp.typeface=Typeface.create(Typeface.DEFAULT_BOLD,Typeface.BOLD)
            canvas.drawText("Miss Minutes speaking...",cx,h*0.945f,dp)
        } else {
            dp.color=Color.parseColor("#AA550088");dp.textSize=h*0.048f;dp.typeface=Typeface.create(Typeface.DEFAULT,Typeface.ITALIC)
            canvas.drawText("tap to talk to Miss Minutes",cx,h*0.95f,dp)
        }
    }
}
