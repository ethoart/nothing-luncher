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

    // Retro monospace font loaded from assets
    private val retroFont: Typeface by lazy {
        try { Typeface.createFromAsset(context.assets, "fonts/retro.ttf") }
        catch (_: Exception) { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    }
    private val retroRegular: Typeface by lazy {
        try { Typeface.createFromAsset(context.assets, "fonts/retro_regular.ttf") }
        catch (_: Exception) { Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            animTick = (animTick + 0.018f) % 1f
            waveOff += 2.8f; if (waveOff > 800f) waveOff = 0f
            invalidate()
            handler.postDelayed(this, 50)
        }
    }

    private var animTick = 0f
    private var waveOff = 0f

    private val p  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // Dot-matrix 5x7 for Nothing OS face
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
        when (style) {
            WatchFaceStyle.NOTHING_DOT   -> drawNothing(canvas)
            WatchFaceStyle.BOLD_DIGITAL  -> drawBold(canvas)
            WatchFaceStyle.NEON_MINIMAL  -> drawNeon(canvas)
            WatchFaceStyle.RETRO_ORANGE  -> drawRetro(canvas)
            WatchFaceStyle.CLEAN_WHITE   -> drawClean(canvas)
            WatchFaceStyle.WAVE_SEIKO    -> drawWave(canvas)
            WatchFaceStyle.PIP_BOY       -> drawPipBoy(canvas)
            WatchFaceStyle.JAMES_BOND    -> drawJamesBond(canvas)
            WatchFaceStyle.CASIO_RETRO        -> drawCasio(canvas)
            WatchFaceStyle.MISS_MINUTES_FACE -> drawMissMinutesFace(canvas)
        }
    }

    private fun cal() = Calendar.getInstance()

    private fun retroPaint(size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size; this.color = color; this.textAlign = align
            this.typeface = retroFont
        }

    private fun retroSmallPaint(size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size; this.color = color; this.textAlign = align
            this.typeface = retroRegular
        }

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
                        canvas.drawCircle(sx+c*(ds+dg)+ds/2f, cy+r*(ds+dg)+ds/2f, ds/2f, p)
                    }
                }
            }
            sx+=cw+cg
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. NOTHING OS — dot matrix + retro date
    // ════════════════════════════════════════════════════════════════════════
    private fun drawNothing(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        p.color=Color.WHITE;p.style=Paint.Style.FILL;canvas.drawCircle(cx,cy,min(w,h)/2f,p)
        val pulse=0.75f+0.25f*sin(animTick*2*PI).toFloat()
        p.color=Color.parseColor("#FF3B2F");canvas.drawCircle(cx-w*0.28f,cy-h*0.28f,w*0.048f*pulse,p)
        val ts=String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE))
        drawDotMatrix(canvas,ts,cx,cy-h*0.16f,w*0.028f,Color.parseColor("#111111"),Color.parseColor("#FF3B2F"))
        // Seconds progress bar
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val bw=w*0.52f;val bx=cx-bw/2f;val by=cy+h*0.24f
        p.color=Color.parseColor("#EEEEEE");canvas.drawRoundRect(bx,by,bx+bw,by+4f,2f,2f,p)
        p.color=Color.parseColor("#FF3B2F");canvas.drawRoundRect(bx,by,bx+bw*(sec/60f),by+4f,2f,2f,p)
        // Date — retro font
        val rp = retroSmallPaint(h*0.068f, Color.parseColor("#888888"))
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]} ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}",cx,cy+h*0.37f,rp)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. BOLD DIGITAL — retro huge numbers
    // ════════════════════════════════════════════════════════════════════════
    private fun drawBold(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        canvas.drawColor(Color.BLACK)
        // Scan line
        val sy=animTick*h;p.color=Color.argb(22,255,59,47);p.style=Paint.Style.FILL
        canvas.drawRect(0f,sy-1.5f,w,sy+1.5f,p)
        // Hour
        val hrP = retroPaint(h*0.38f, Color.WHITE)
        canvas.drawText(String.format("%02d",c.get(Calendar.HOUR_OF_DAY)),cx,cy-h*0.02f,hrP)
        // Animated red line
        p.color=Color.parseColor("#FF3B2F");p.style=Paint.Style.FILL
        val lw=w*0.38f*(0.7f+0.3f*sin(animTick*2*PI).toFloat())
        canvas.drawRect(cx-lw,cy+h*0.060f,cx+lw,cy+h*0.068f,p)
        // Minute
        val mnP = retroPaint(h*0.38f, Color.parseColor("#FF3B2F"))
        canvas.drawText(String.format("%02d",c.get(Calendar.MINUTE)),cx,cy+h*0.44f,mnP)
        // Day tag
        val dayP = retroSmallPaint(h*0.072f, Color.parseColor("#444444"), Paint.Align.RIGHT)
        canvas.drawText(DAYS[c.get(Calendar.DAY_OF_WEEK)-1],w-w*0.07f,cy-h*0.45f,dayP)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. NEON — retro mono time
    // ════════════════════════════════════════════════════════════════════════
    private fun drawNeon(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        canvas.drawColor(Color.parseColor("#050510"))
        val r=min(w,h)/2f-8f
        p.style=Paint.Style.FILL
        for(i in 0..7){val a=(animTick*2*PI+i*PI/4).toFloat();val alpha=(100+155*sin(animTick*2*PI+i).toFloat()).toInt().coerceIn(0,255);p.color=Color.argb(alpha,0,255,200);canvas.drawCircle(cx+r*cos(a),cy+r*sin(a),2.5f,p)}
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val ap=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;strokeCap=Paint.Cap.ROUND;color=Color.parseColor("#00FFCC")}
        canvas.drawArc(RectF(8f,8f,w-8f,h-8f),-90f,sec/60f*360f,false,ap)
        val tp2 = retroPaint(h*0.28f, Color.parseColor("#00FFCC"))
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,cy+h*0.10f,tp2)
        val dp2 = retroSmallPaint(h*0.068f, Color.parseColor("#007755"))
        canvas.drawText("${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}",cx,cy+h*0.40f,dp2)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. RETRO ORANGE — CRT with retro font
    // ════════════════════════════════════════════════════════════════════════
    private fun drawRetro(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        canvas.drawColor(Color.parseColor("#0A0A0A"))
        p.color=Color.argb(18,0,0,0);p.style=Paint.Style.FILL
        var sy=0f;while(sy<h){canvas.drawRect(0f,sy,w,sy+1f,p);sy+=3f}
        val fl=0.93f+0.07f*sin(animTick*2*PI*7).toFloat()
        canvas.saveLayerAlpha(0f,0f,w,h,(255*fl).toInt())
        val tP = retroPaint(h*0.30f, Color.parseColor("#FF6B00"))
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,cy+h*0.10f,tP)
        // Seconds hand
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val sa=(sec/60f*2*PI-PI/2).toFloat();val hr2=min(w,h)/2f-16f
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2f;color=Color.parseColor("#FF6B00");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+hr2*cos(sa),cy+hr2*sin(sa),it)}
        p.color=Color.parseColor("#FF6B00");p.style=Paint.Style.FILL;canvas.drawCircle(cx,cy,4f,p)
        val ampm=if(c.get(Calendar.HOUR_OF_DAY)<12)"AM" else "PM"
        p.color=Color.parseColor("#FF6B00");canvas.drawRoundRect(RectF(cx+w*0.18f,cy-h*0.43f,cx+w*0.42f,cy-h*0.28f),6f,6f,p)
        val apP = retroPaint(h*0.085f, Color.BLACK)
        canvas.drawText(ampm,cx+w*0.30f,cy-h*0.32f,apP)
        canvas.restore()
        val dP = retroSmallPaint(h*0.068f, Color.parseColor("#554433"))
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]}  ${c.get(Calendar.DAY_OF_MONTH)}",cx,cy+h*0.40f,dP)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. CLEAN ANALOG — retro date
    // ════════════════════════════════════════════════════════════════════════
    private fun drawClean(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        p.color=Color.WHITE;p.style=Paint.Style.FILL;canvas.drawCircle(cx,cy,min(w,h)/2f,p)
        val tickPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.5f;color=Color.parseColor("#DDDDDD")}
        val r=min(w,h)/2f-6f
        for(i in 0..59){val a=(i/60f*2*PI-PI/2).toFloat();val inn=if(i%5==0)r-8f else r-4f;canvas.drawLine(cx+inn*cos(a),cy+inn*sin(a),cx+r*cos(a),cy+r*sin(a),tickPaint)}
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f
        val sa=(sec/60f*2*PI-PI/2).toFloat()
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.5f;color=Color.parseColor("#FF3B2F");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx-(r*0.2f)*cos(sa),cy-(r*0.2f)*sin(sa),cx+(r-10f)*cos(sa),cy+(r-10f)*sin(sa),it)}
        val hr=c.get(Calendar.HOUR)+c.get(Calendar.MINUTE)/60f;val ha=(hr/12f*2*PI-PI/2).toFloat()
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;color=Color.parseColor("#111111");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+(r*0.5f)*cos(ha),cy+(r*0.5f)*sin(ha),it)}
        val mn=c.get(Calendar.MINUTE)+sec/60f;val ma=(mn/60f*2*PI-PI/2).toFloat()
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2.5f;color=Color.parseColor("#333333");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+(r*0.7f)*cos(ma),cy+(r*0.7f)*sin(ma),it)}
        p.color=Color.WHITE;canvas.drawCircle(cx,cy,5f,p);p.color=Color.parseColor("#FF3B2F");canvas.drawCircle(cx,cy,3f,p)
        val dP = retroSmallPaint(h*0.075f, Color.parseColor("#AAAAAA"))
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]} ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}",cx,cy+h*0.32f,dP)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. SEIKO WAVE
    // ════════════════════════════════════════════════════════════════════════
    private fun drawWave(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h/2f;val c=cal()
        p.color=Color.parseColor("#0A1628");p.style=Paint.Style.FILL;canvas.drawRect(0f,0f,w,h,p)
        val cols=listOf(Color.parseColor("#0D3B6E"),Color.parseColor("#1565C0"),Color.parseColor("#1976D2"),Color.parseColor("#42A5F5"))
        for(layer in cols.indices){val path=Path();val amp=h*(0.09f-layer*0.015f);val freq=1.5f+layer*0.4f;val phase=waveOff*(0.8f+layer*0.3f);val baseY=h*(0.42f+layer*0.09f);path.moveTo(0f,h);var x=0f;while(x<=w+2f){val y=baseY+amp*sin((x*freq/w*2*PI+phase*0.05f)).toFloat();path.lineTo(x,y);x+=2f};path.lineTo(w,h);path.close();p.color=cols[layer];p.alpha=200-layer*25;canvas.drawPath(path,p);p.alpha=255}
        p.color=Color.argb(160,255,255,255);p.style=Paint.Style.FILL
        for(i in 0..10){val fx=(i/10f*w+waveOff*0.6f)%w;val fy=h*0.36f+h*0.04f*sin((fx/w*4*PI).toFloat());canvas.drawCircle(fx,fy,2f,p)}
        val sP = retroPaint(h*0.10f, Color.WHITE)
        canvas.drawText("SEIKO",cx,cy-h*0.10f,sP)
        val aP = retroSmallPaint(h*0.052f, Color.parseColor("#90CAF9"))
        canvas.drawText("AUTOMATIC",cx,cy-h*0.01f,aP)
        val mr=min(w,h)/2f-8f
        for(i in 0..11){val a=(i/12f*2*PI-PI/2).toFloat();Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;color=Color.parseColor("#CFD8DC");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx+(mr-10f)*cos(a),cy+(mr-10f)*sin(a),cx+mr*cos(a),cy+mr*sin(a),it)}}
        val sec=c.get(Calendar.SECOND)+c.get(Calendar.MILLISECOND)/1000f;val handR=mr-18f
        val hr=c.get(Calendar.HOUR)+c.get(Calendar.MINUTE)/60f;val ha=(hr/12f*2*PI-PI/2).toFloat()
        val mn=c.get(Calendar.MINUTE)+sec/60f;val ma=(mn/60f*2*PI-PI/2).toFloat();val sa=(sec/60f*2*PI-PI/2).toFloat()
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;color=Color.WHITE;strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+handR*0.55f*cos(ha),cy+handR*0.55f*sin(ha),it)}
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2.5f;color=Color.WHITE;strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx,cy,cx+handR*0.78f*cos(ma),cy+handR*0.78f*sin(ma),it)}
        Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.5f;color=Color.parseColor("#EF5350");strokeCap=Paint.Cap.ROUND}.also{canvas.drawLine(cx-handR*0.15f*cos(sa),cy-handR*0.15f*sin(sa),cx+handR*0.88f*cos(sa),cy+handR*0.88f*sin(sa),it)}
        p.color=Color.WHITE;p.style=Paint.Style.FILL;canvas.drawCircle(cx,cy,5f,p);p.color=Color.parseColor("#EF5350");canvas.drawCircle(cx,cy,3f,p)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. PIP-BOY 3000
    // ════════════════════════════════════════════════════════════════════════
    private fun drawPipBoy(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val c=cal()
        val G=Color.parseColor("#00FF41");val DG=Color.parseColor("#003B10");val MG=Color.parseColor("#00C030")
        canvas.drawColor(Color.parseColor("#030D03"))
        p.color=Color.argb(20,0,255,60);p.style=Paint.Style.FILL
        var sy=0f;while(sy<h){canvas.drawRect(0f,sy,w,sy+1f,p);sy+=3f}
        val fl=0.94f+0.06f*sin(animTick*2*PI*13).toFloat()
        canvas.saveLayerAlpha(0f,0f,w,h,(255*fl).toInt())
        val hP = retroPaint(h*0.072f, G)
        canvas.drawText("PIP-BOY 3000",cx,h*0.09f,hP)
        val rP = retroSmallPaint(h*0.050f, MG)
        canvas.drawText("ROBCO INDUSTRIES",cx,h*0.165f,rP)
        p.color=DG;p.style=Paint.Style.FILL;canvas.drawRect(w*0.05f,h*0.20f,w*0.95f,h*0.215f,p)
        val dPL = retroSmallPaint(h*0.058f, G, Paint.Align.LEFT)
        val dPR = retroSmallPaint(h*0.058f, G, Paint.Align.RIGHT)
        canvas.drawText(String.format("%02d.%02d.%04d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR)),w*0.06f,h*0.29f,dPL)
        canvas.drawText(DAYS[c.get(Calendar.DAY_OF_WEEK)-1],w*0.94f,h*0.29f,dPR)
        val t1 = retroPaint(h*0.36f, G)
        canvas.drawText(String.format("%02d",c.get(Calendar.HOUR_OF_DAY)),cx-w*0.15f,h*0.62f,t1)
        val t2 = retroPaint(h*0.18f, G)
        canvas.drawText(":",cx,h*0.56f,t2)
        canvas.drawText(String.format("%02d",c.get(Calendar.MINUTE)),cx+w*0.15f,h*0.62f,t1)
        val apP = retroSmallPaint(h*0.065f, MG)
        canvas.drawText(if(c.get(Calendar.HOUR_OF_DAY)<12)"AM" else "PM",cx,h*0.70f,apP)
        val stats=listOf("HP" to 85,"RAD" to 12,"CAPS" to 247)
        stats.forEachIndexed{i,(label,value)->
            val by=h*(0.775f+i*0.073f)
            val slP = retroSmallPaint(h*0.050f, MG, Paint.Align.LEFT)
            canvas.drawText(label,w*0.05f,by,slP)
            p.color=DG;canvas.drawRect(w*0.20f,by-h*0.038f,w*0.78f,by-h*0.005f,p)
            p.color=G;canvas.drawRect(w*0.20f,by-h*0.038f,w*0.20f+w*0.58f*value/300f,by-h*0.005f,p)
        }
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
        p.style=Paint.Style.STROKE;p.strokeWidth=1.2f;p.color=Color.parseColor("#1AFF8800");canvas.drawCircle(cx,cy,radarR,p)
        val sg=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL;shader=SweepGradient(cx,cy,intArrayOf(Color.argb(0,255,136,0),Color.argb(90,255,136,0),Color.argb(0,255,136,0)),floatArrayOf(0f,0.12f,0.25f))}
        canvas.save();canvas.rotate(sweepAngle,cx,cy);canvas.drawCircle(cx,cy,radarR,sg);canvas.restore()
        for(r in listOf(0.14f,0.24f,0.34f,0.44f)){p.style=Paint.Style.STROKE;p.strokeWidth=0.8f;p.color=Color.argb(55,255,136,0);canvas.drawCircle(cx,cy,min(w,h)*r,p)}
        p.style=Paint.Style.STROKE;p.strokeWidth=0.8f;p.color=Color.argb(70,255,136,0)
        canvas.drawLine(cx-w*0.45f,cy,cx+w*0.45f,cy,p);canvas.drawLine(cx,cy-h*0.45f,cx,cy+h*0.45f,p)
        val oP = retroPaint(h*0.090f, Color.parseColor("#FF8800"))
        canvas.drawText("007",cx,cy-h*0.22f,oP)
        val jP = retroSmallPaint(h*0.058f, Color.parseColor("#CCAA44"))
        canvas.drawText("JAMES BOND",cx,cy-h*0.11f,jP)
        val tP = retroPaint(h*0.26f, Color.parseColor("#FF8800"))
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,cy+h*0.10f,tP)
        p.color=Color.parseColor("#FF8800");p.style=Paint.Style.FILL
        canvas.drawRoundRect(cx-w*0.28f,cy+h*0.20f,cx+w*0.28f,cy+h*0.32f,8f,8f,p)
        val eP = retroPaint(h*0.065f, Color.BLACK)
        canvas.drawText("EDITION",cx,cy+h*0.285f,eP)
        val pd=0.6f+0.4f*sin(animTick*2*PI).toFloat();p.color=Color.parseColor("#FF8800");canvas.drawCircle(cx+w*0.30f,cy-h*0.32f,w*0.026f*pd,p)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. CASIO G-SHOCK
    // ════════════════════════════════════════════════════════════════════════
    private fun drawCasio(canvas: Canvas) {
        val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val c=cal()
        canvas.drawColor(Color.parseColor("#0D0D0D"))
        p.color=Color.parseColor("#1A1A1A");p.style=Paint.Style.FILL;canvas.drawRoundRect(w*0.02f,h*0.02f,w*0.98f,h*0.98f,18f,18f,p)
        p.color=Color.parseColor("#222222");canvas.drawRect(w*0.04f,h*0.04f,w*0.96f,h*0.155f,p)
        val hP = retroPaint(h*0.090f, Color.WHITE)
        canvas.drawText("CASIO",cx,h*0.128f,hP)
        p.color=Color.parseColor("#8A9E88");p.style=Paint.Style.FILL;canvas.drawRoundRect(w*0.06f,h*0.175f,w*0.94f,h*0.575f,8f,8f,p)
        p.color=Color.argb(20,0,0,0);p.style=Paint.Style.STROKE;p.strokeWidth=0.5f
        var gx=w*0.06f;while(gx<w*0.94f){canvas.drawLine(gx,h*0.175f,gx,h*0.575f,p);gx+=w*0.07f}
        var gy=h*0.175f;while(gy<h*0.575f){canvas.drawLine(w*0.06f,gy,w*0.94f,gy,p);gy+=h*0.048f}
        val lP = retroPaint(h*0.27f, Color.parseColor("#1A2A18"))
        canvas.drawText(String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),cx,h*0.455f,lP)
        val sP = retroSmallPaint(h*0.058f, Color.parseColor("#2A3A28"))
        canvas.drawText(String.format(":%02d  %s",c.get(Calendar.SECOND),DAYS[c.get(Calendar.DAY_OF_WEEK)-1]),cx,h*0.535f,sP)
        p.color=Color.parseColor("#111111");p.style=Paint.Style.FILL;canvas.drawRect(w*0.04f,h*0.615f,w*0.96f,h*0.695f,p)
        val modes=listOf("ALM","TMR","STW","MODE")
        modes.forEachIndexed{i,m->val mP=retroSmallPaint(h*0.048f,Color.parseColor("#777777"),Paint.Align.LEFT);canvas.drawText(m,w*0.08f+i*(w*0.22f),h*0.678f,mP)}
        p.color=Color.parseColor("#181818");canvas.drawRoundRect(w*0.06f,h*0.725f,w*0.94f,h*0.960f,8f,8f,p)
        val dP = retroSmallPaint(h*0.065f, Color.parseColor("#555555"))
        canvas.drawText(String.format("%02d/%02d/%04d  %s",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR),DAYS[c.get(Calendar.DAY_OF_WEEK)-1]),cx,h*0.845f,dP)
        val wrP = retroSmallPaint(h*0.048f, Color.parseColor("#333333"))
        canvas.drawText("WR 200M  G-SHOCK",cx,h*0.930f,wrP)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. MISS MINUTES — TVA clock face (amber, ornate, animated)
    // ════════════════════════════════════════════════════════════════════════
    private fun drawMissMinutesFace(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val c = cal()
        val r = minOf(w, h) / 2f

        // ── Background: deep TVA charcoal ──────────────────────────────────
        p.color = Color.parseColor("#0C0800"); p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r, p)

        // Outer rim — burnished gold
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = r * 0.065f
            color = Color.parseColor("#B8860B")
        }
        canvas.drawCircle(cx, cy, r - rimPaint.strokeWidth / 2f, rimPaint)

        // Inner rim — thin amber
        val innerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f
            color = Color.parseColor("#FFB300")
        }
        canvas.drawCircle(cx, cy, r * 0.87f, innerRim)

        // ── Roman numeral tick marks ───────────────────────────────────────
        val tickR = r * 0.82f
        val romans = listOf("XII","I","II","III","IV","V","VI","VII","VIII","IX","X","XI")
        val romanPaint = retroPaint(r * 0.068f, Color.parseColor("#D4A017"))
        for (i in 0..11) {
            val angle = (i / 12f * 2 * PI - PI / 2).toFloat()
            val tx = cx + (tickR - r * 0.055f) * cos(angle)
            val ty = cy + (tickR - r * 0.055f) * sin(angle) + romanPaint.textSize / 3f
            canvas.drawText(romans[i], tx, ty, romanPaint)
        }

        // Minor tick marks (5-min intervals already covered by romans, draw 60 minor)
        for (i in 0..59) {
            if (i % 5 == 0) continue
            val angle = (i / 60f * 2 * PI - PI / 2).toFloat()
            val inner = r * 0.74f; val outer = r * 0.79f
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.2f
                color = Color.parseColor("#664400")
            }
            canvas.drawLine(cx + inner * cos(angle), cy + inner * sin(angle),
                cx + outer * cos(angle), cy + outer * sin(angle), tp)
        }

        // ── TVA branding ───────────────────────────────────────────────────
        val tvaP = retroPaint(r * 0.095f, Color.parseColor("#FF8C00"))
        canvas.drawText("TVA", cx, cy - r * 0.30f, tvaP)
        val subP = retroSmallPaint(r * 0.052f, Color.parseColor("#7A5C00"))
        canvas.drawText("SACRED TIMELINE", cx, cy - r * 0.18f, subP)

        // ── Animated gear ornaments ────────────────────────────────────────
        val gearR = r * 0.06f; val gearDist = r * 0.55f
        val gearAngle = animTick * 2 * PI.toFloat()
        p.color = Color.parseColor("#4A3000"); p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f
        canvas.save(); canvas.rotate(Math.toDegrees(gearAngle.toDouble()).toFloat(), cx - gearDist, cy + r * 0.20f)
        canvas.drawCircle(cx - gearDist, cy + r * 0.20f, gearR, p); canvas.restore()
        canvas.save(); canvas.rotate(-Math.toDegrees(gearAngle.toDouble()).toFloat(), cx + gearDist, cy + r * 0.20f)
        canvas.drawCircle(cx + gearDist, cy + r * 0.20f, gearR, p); canvas.restore()

        // ── Clock hands ───────────────────────────────────────────────────
        val sec  = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000f
        val minF = c.get(Calendar.MINUTE) + sec / 60f
        val hrF  = (c.get(Calendar.HOUR) % 12) + minF / 60f

        val hrAngle  = (hrF / 12f  * 2 * PI - PI / 2).toFloat()
        val minAngle = (minF / 60f * 2 * PI - PI / 2).toFloat()
        val secAngle = (sec  / 60f * 2 * PI - PI / 2).toFloat()

        val handR = r * 0.68f

        // Hour hand — thick gold
        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = r * 0.055f
            color = Color.parseColor("#D4A017"); strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - handR * 0.18f * cos(hrAngle), cy - handR * 0.18f * sin(hrAngle),
            cx + handR * 0.52f * cos(hrAngle), cy + handR * 0.52f * sin(hrAngle), hourPaint)

        // Minute hand — slimmer gold
        val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = r * 0.032f
            color = Color.parseColor("#FFB300"); strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - handR * 0.15f * cos(minAngle), cy - handR * 0.15f * sin(minAngle),
            cx + handR * 0.72f * cos(minAngle), cy + handR * 0.72f * sin(minAngle), minPaint)

        // Second hand — red/orange thin
        val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f
            color = Color.parseColor("#FF4500"); strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - handR * 0.20f * cos(secAngle), cy - handR * 0.20f * sin(secAngle),
            cx + handR * 0.85f * cos(secAngle), cy + handR * 0.85f * sin(secAngle), secPaint)

        // Center jewel
        p.color = Color.parseColor("#D4A017"); p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r * 0.045f, p)
        p.color = Color.parseColor("#0C0800")
        canvas.drawCircle(cx, cy, r * 0.020f, p)

        // ── Date window at 3 o'clock position ─────────────────────────────
        val dWx = cx + r * 0.52f; val dWy = cy
        p.color = Color.parseColor("#1A1000"); p.style = Paint.Style.FILL
        canvas.drawRoundRect(dWx - r*0.12f, dWy - r*0.08f, dWx + r*0.12f, dWy + r*0.08f, 4f, 4f, p)
        p.color = Color.parseColor("#B8860B"); p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f
        canvas.drawRoundRect(dWx - r*0.12f, dWy - r*0.08f, dWx + r*0.12f, dWy + r*0.08f, 4f, 4f, p)
        val datePaint = retroSmallPaint(r * 0.065f, Color.parseColor("#FFB300"))
        canvas.drawText("${c.get(Calendar.DAY_OF_MONTH)}", dWx, dWy + r * 0.025f, datePaint)

        // ── Miss Minutes tagline ───────────────────────────────────────────
        val tagP = retroSmallPaint(r * 0.048f, Color.parseColor("#553300"))
        canvas.drawText("DOUBLE-TAP TO CHAT", cx, cy + r * 0.46f, tagP)
    }
}
