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
            WatchFaceStyle.CASIO_RETRO          -> drawCasio(canvas)
            WatchFaceStyle.MISS_MINUTES_FACE    -> drawMissMinutesFace(canvas)
            WatchFaceStyle.MISS_MINUTES_GLOWING -> drawMmGlowing(canvas)
            WatchFaceStyle.MISS_MINUTES_GIF     -> drawMmGif(canvas)
            WatchFaceStyle.TVA_CRT_MONITOR      -> drawTvaMonitor(canvas)
            WatchFaceStyle.TVA_TIMEDOOR         -> drawTvaTimeDoor(canvas)
            WatchFaceStyle.MISS_MINUTES_SCARED  -> drawMmAlert(canvas)
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
    // ════════════════════════════════════════════════════════════════════════
    // IMAGE-BASED ANIMATED WATCH FACES
    // Each image is center-cropped to fill the screen, then animated overlays
    // are blended on top — glow, real clock hands, scan lines, body pulse.
    // ════════════════════════════════════════════════════════════════════════

    // One bitmap cache entry per asset path — loaded once, reused every frame
    private val bmpCache = mutableMapOf<String, Bitmap?>()

    // GIF animation state
    private val gifFrames   = mutableListOf<Bitmap>()
    private var gifLoaded   = false
    // Track real elapsed time for GIF so speed is correct regardless of animTick scaling
    private var gifLastMs   = 0L
    private var gifFrameIdx = 0

    private fun bmp(assetPath: String): Bitmap? {
        if (bmpCache.containsKey(assetPath)) return bmpCache[assetPath]
        return try {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
                .also { bmpCache[assetPath] = it }
        } catch (_: Exception) { bmpCache[assetPath] = null; null }
    }

    // Center-crop params so the image fills the entire view
    private data class CP(val dstL:Float, val dstT:Float, val dstW:Float, val dstH:Float)
    private fun crop(b: Bitmap, w: Float, h: Float): CP {
        val s = maxOf(w/b.width, h/b.height)
        val dw = b.width*s; val dh = b.height*s
        return CP((w-dw)/2f, (h-dh)/2f, dw, dh)
    }

    // Map normalised image position (0..1) → screen px
    private fun px(nx: Float, ny: Float, cp: CP) =
        Pair(cp.dstL + nx*cp.dstW, cp.dstT + ny*cp.dstH)

    // Draw one clock hand: 0° = 12 o'clock, clockwise
    private fun hand(canvas: Canvas, cx:Float, cy:Float, deg:Float, len:Float, w:Float, col:Int) {
        val r = Math.toRadians(deg.toDouble())
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style=Paint.Style.STROKE; strokeWidth=w; color=col; strokeCap=Paint.Cap.ROUND
        }.also { canvas.drawLine(cx, cy, cx+len*sin(r).toFloat(), cy-len*cos(r).toFloat(), it) }
    }

    // Radial gradient paint helper
    private fun radialGlow(cx:Float, cy:Float, r:Float, vararg stops: Pair<Int,Float>): Paint {
        val cols  = stops.map { it.first  }.toIntArray()
        val poses = stops.map { it.second }.toFloatArray()
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r, cols, poses, Shader.TileMode.CLAMP)
        }
    }

    // Semi-transparent fill paint
    private fun fill(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.FILL; color=col
    }

    // ── FACE 11: Miss Minutes — Glowing stage photo ───────────────────────
    // mm_glowing.jpg (1200×1200): clock face centre ≈ (50%, 41%), radius ≈ 32% of img width
    // Animations: body breathe, amber sparkles orbit, real hands move, eye twinkle
    fun drawMmGlowing(canvas: Canvas) {
        val w=width.toFloat(); val h=height.toFloat(); val c=cal()

        // Draw image
        val b = bmp("watchfaces/mm_glowing.jpg") ?: run {
            canvas.drawColor(Color.parseColor("#180800"))
            canvas.drawText("LOADING...", w/2f, h/2f, retroPaint(h*0.07f, 0xFFFF8C00.toInt())); return
        }
        val cp = crop(b, w, h)

        // Breathing scale — her body pulses slightly
        val breath = 1f + 0.012f * sin(animTick * 2f * PI).toFloat()
        canvas.save()
        canvas.scale(breath, breath, w/2f, h*0.42f)
        canvas.drawBitmap(b, null, RectF(cp.dstL, cp.dstT, cp.dstL+cp.dstW, cp.dstT+cp.dstH), null)
        canvas.restore()

        // Clock face reference: centre (50%, 41%), radius 32% of cp.dstW
        val (cx, cy) = px(0.50f, 0.408f, cp)
        val cr = cp.dstW * 0.318f

        // ── Pulsing warm body glow ────────────────────────────────────────
        val pulse = 0.5f + 0.5f * sin(animTick * 2f * PI).toFloat()
        canvas.drawCircle(cx, cy, cr*(1.05f+0.08f*pulse),
            radialGlow(cx, cy, cr*1.2f,
                Pair(Color.argb((110*pulse).toInt(), 255, 170, 0), 0f),
                Pair(Color.argb((50*pulse).toInt(),  255, 100, 0), 0.6f),
                Pair(Color.argb(0, 0, 0, 0), 1f)))

        // ── Orbiting sparkles around her body ────────────────────────────
        for (i in 0..9) {
            val angle = (animTick * 2f * PI + i * PI / 5f).toFloat()
            val dist  = cr * (1.22f + 0.10f * sin((animTick*4f*PI + i*0.8f).toFloat()))
            val sx    = cx + dist * sin(angle); val sy = cy - dist * cos(angle)
            val alpha = (0.35f + 0.65f * abs(sin((animTick*3f*PI + i).toFloat()))).coerceIn(0f,1f)
            val size  = cr * (0.018f + 0.010f * sin((animTick*5f*PI + i).toFloat()))
            canvas.drawCircle(sx, sy, size, fill(Color.argb((200*alpha).toInt(), 255, 180, 50)))
        }

        // ── Eye twinkle — tiny bright flash that drifts across eyes ──────
        val eyeFlash = abs(sin(animTick * 2f * PI * 1.3f)).toFloat()
        if (eyeFlash > 0.7f) {
            val alpha = ((eyeFlash - 0.7f) / 0.3f * 180).toInt()
            // Left eye area: (~37%, 39%), Right eye area: (~62%, 39%)
            val (lex, ley) = px(0.368f, 0.390f, cp)
            val (rex, rey) = px(0.630f, 0.390f, cp)
            val er = cr * 0.075f
            canvas.drawCircle(lex, ley, er, fill(Color.argb(alpha, 255, 240, 200)))
            canvas.drawCircle(rex, rey, er, fill(Color.argb(alpha, 255, 240, 200)))
        }

        // ── Real clock hands ──────────────────────────────────────────────
        val sec  = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND)/1000f
        val minF = c.get(Calendar.MINUTE) + sec/60f
        val hrF  = (c.get(Calendar.HOUR)%12) + minF/60f
        hand(canvas, cx, cy, hrF/12f*360f,  cr*0.42f, cr*0.058f, Color.parseColor("#2A0E00"))
        hand(canvas, cx, cy, minF/60f*360f, cr*0.58f, cr*0.036f, Color.parseColor("#2A0E00"))
        hand(canvas, cx, cy, sec/60f*360f,  cr*0.64f, cr*0.016f, Color.parseColor("#EE4400"))
        canvas.drawCircle(cx, cy, cr*0.038f, fill(Color.parseColor("#2A0E00")))
        canvas.drawCircle(cx, cy, cr*0.018f, fill(Color.parseColor("#FF6600")))

        drawTimeOverlay(canvas, w, h, c)
    }

    // ── FACE 12: Miss Minutes — Animated GIF ─────────────────────────────
    // gif 400×444: 47 frames @50ms each = 2.35s cycle
    // Animation: smooth frame advance + warm vignette + time overlay
    fun drawMmGif(canvas: Canvas) {
        val w=width.toFloat(); val h=height.toFloat(); val c=cal()
        canvas.drawColor(Color.parseColor("#0C0800"))

        if (!gifLoaded) { gifLoaded=true; loadGifFramesAsync() }

        val frames: List<Bitmap>
        synchronized(gifFrames) { frames = gifFrames.toList() }

        if (frames.isEmpty()) {
            canvas.drawText("LOADING...", w/2f, h/2f, retroPaint(h*0.07f, 0xFFFF8C00.toInt()))
            return
        }

        // Real-time frame advance: 50ms per frame
        val now = System.currentTimeMillis()
        if (gifLastMs == 0L) gifLastMs = now
        val elapsed = now - gifLastMs
        val advance = (elapsed / 50L).toInt()
        if (advance > 0) { gifFrameIdx = (gifFrameIdx + advance) % frames.size; gifLastMs += advance*50L }

        val b = frames[gifFrameIdx]
        val cp = crop(b, w, h)
        canvas.drawBitmap(b, null, RectF(cp.dstL, cp.dstT, cp.dstL+cp.dstW, cp.dstT+cp.dstH), null)

        // Warm amber vignette that breathes with the animation
        val pulse = 0.4f + 0.6f * abs(sin(animTick * 2f*PI)).toFloat()
        canvas.drawRect(0f,0f,w,h,
            radialGlow(w/2f, h*0.45f, minOf(w,h)*0.62f,
                Pair(Color.argb(0, 0,0,0), 0f),
                Pair(Color.argb(0, 0,0,0), 0.55f),
                Pair(Color.argb((90*pulse).toInt(), 20, 8, 0), 1f)))

        drawTimeOverlay(canvas, w, h, c)
    }

    private fun loadGifFramesAsync() {
        Thread {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            for (i in 0..46) {
                try {
                    context.assets.open("watchfaces/mm_gif_frames/frame_${i.toString().padStart(2,'0')}.png")
                        .use { s -> BitmapFactory.decodeStream(s, null, opts) }
                        ?.also { synchronized(gifFrames) { gifFrames.add(it) } }
                } catch (_: Exception) {}
            }
        }.start()
    }

    // ── FACE 13: TVA TimeDoor Wear OS face ───────────────────────────────
    // tva_monitor.jpg (960×960): black grid, TimeDoor wireframe, 12:00 static at top
    // Animations: live time replaces static, door portal glow, CRT scanline, flicker
    fun drawTvaMonitor(canvas: Canvas) {
        val w=width.toFloat(); val h=height.toFloat(); val c=cal()
        canvas.drawColor(Color.BLACK)

        val b = bmp("watchfaces/tva_monitor.jpg") ?: run {
            canvas.drawText("LOADING", w/2f, h/2f, retroPaint(h*0.07f, 0xFFCC6600.toInt())); return
        }
        val cp = crop(b, w, h)
        canvas.drawBitmap(b, null, RectF(cp.dstL, cp.dstT, cp.dstL+cp.dstW, cp.dstT+cp.dstH), null)

        // ── Cover static "12:00 05" with dark rect ────────────────────────
        // In image: time is top-center, roughly y 1.5%–16.5%, x 15%–85%
        val timeBlockT = cp.dstT + cp.dstH*0.015f
        val timeBlockB = cp.dstT + cp.dstH*0.168f
        canvas.drawRect(cp.dstL+cp.dstW*0.12f, timeBlockT, cp.dstL+cp.dstW*0.88f, timeBlockB,
            fill(Color.parseColor("#050200")))

        // CRT flicker — subtle alpha oscillation at high freq
        val flicker = 0.94f + 0.06f * sin(animTick * 2f*PI*17f).toFloat()
        canvas.saveLayerAlpha(0f, 0f, w, h, (255*flicker).toInt())

        // Live hours:minutes — same amber digital style as original
        val (tx, ty) = px(0.46f, 0.093f, cp)
        val tStr = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        canvas.drawText(tStr, tx, ty + cp.dstH*0.090f, retroPaint(cp.dstH*0.108f, Color.parseColor("#CC6600")))
        // Seconds — top-right of time, smaller
        val (sx, sy) = px(0.725f, 0.055f, cp)
        canvas.drawText(String.format("%02d", c.get(Calendar.SECOND)),
            sx, sy + cp.dstH*0.090f, retroSmallPaint(cp.dstH*0.052f, Color.parseColor("#884400")))

        canvas.restore()

        // ── Cover static date "07.20.21" with live date ───────────────────
        // Date area: ~y 79%–84%
        val (dx, dy) = px(0.50f, 0.797f, cp)
        canvas.drawRect(cp.dstL+cp.dstW*0.20f, dy-cp.dstH*0.042f,
            cp.dstL+cp.dstW*0.80f, dy+cp.dstH*0.046f, fill(Color.parseColor("#050200")))
        canvas.drawText(
            String.format("%02d.%02d.%02d", c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.MONTH)+1, c.get(Calendar.YEAR)%100),
            dx, dy+cp.dstH*0.028f,
            retroSmallPaint(cp.dstH*0.055f, Color.parseColor("#AA5500")))

        // ── TimeDoor portal glow — pulses inside the 3D door opening ─────
        // Door inner area centre ~(50%, 43.5%), door inner size ~21% W × 29% H
        val (dcx, dcy) = px(0.500f, 0.435f, cp)
        val pulse = 0.35f + 0.65f * abs(sin(animTick * 2f*PI * 0.75f)).toFloat()
        // Inner portal glow
        canvas.drawRect(cp.dstL+cp.dstW*0.34f, cp.dstT+cp.dstH*0.24f,
            cp.dstL+cp.dstW*0.66f, cp.dstT+cp.dstH*0.64f,
            radialGlow(dcx, dcy, cp.dstW*0.20f,
                Pair(Color.argb((70*pulse).toInt(),  255, 160, 0), 0f),
                Pair(Color.argb((25*pulse).toInt(),  200,  80, 0), 0.6f),
                Pair(Color.argb(0, 0, 0, 0), 1f)))
        // Horizontal shimmer lines sweeping downward inside door
        val shim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style=Paint.Style.STROKE; strokeWidth=1.5f
            color=Color.argb((65*pulse).toInt(), 255, 190, 80)
        }
        val doorT = cp.dstT+cp.dstH*0.25f; val doorB = cp.dstT+cp.dstH*0.63f; val doorH = doorB-doorT
        val scanOff = (animTick * doorH * 3f) % (doorH * 1.3f) - doorH*0.15f
        for (i in 0..4) {
            val ly = doorT + (scanOff + i*doorH*0.26f) % doorH
            if (ly in doorT..doorB)
                canvas.drawLine(cp.dstL+cp.dstW*0.355f, ly, cp.dstL+cp.dstW*0.645f, ly, shim)
        }

        // ── Full-screen CRT scanline sweep ────────────────────────────────
        val scanY = (animTick * h * 2.8f) % h
        canvas.drawRect(0f, scanY, w, scanY+2.5f, fill(Color.argb(30, 255, 130, 0)))
    }

    // ── FACE 14: Miss Minutes Season 2 Poster ────────────────────────────
    // tva_timedoor.jpg (720×720): Miss Minutes with episode dates around rim
    // Clock face centre ≈ (50%, 46%), radius ≈ 37% of image width
    // Animations: real moving hands over the static ones, body amber glow breathes,
    //             eye blink, warm radial shimmer from her body
    fun drawTvaTimeDoor(canvas: Canvas) {
        val w=width.toFloat(); val h=height.toFloat(); val c=cal()

        val b = bmp("watchfaces/tva_timedoor.jpg") ?: run {
            canvas.drawColor(Color.parseColor("#1A0800"))
            canvas.drawText("LOADING", w/2f, h/2f, retroPaint(h*0.07f, 0xFFFF8C00.toInt())); return
        }
        val cp = crop(b, w, h)

        // ── Subtle body breathing — whole image breathes very slightly ────
        val breath = 1f + 0.008f * sin(animTick * 2f*PI).toFloat()
        canvas.save()
        // Scale around her clock face centre
        val (bcx, bcy) = px(0.50f, 0.46f, cp)
        canvas.scale(breath, breath, bcx, bcy)
        canvas.drawBitmap(b, null, RectF(cp.dstL, cp.dstT, cp.dstL+cp.dstW, cp.dstT+cp.dstH), null)
        canvas.restore()

        // Clock face coords (after no-op scale, use original cp for overlay coords)
        val (cx, cy) = px(0.50f, 0.46f, cp)
        val cr = cp.dstW * 0.370f

        // ── Amber body glow — pulses on her clock face ────────────────────
        val pulse = 0.4f + 0.6f * abs(sin(animTick * 2f*PI)).toFloat()
        canvas.drawCircle(cx, cy, cr*1.06f,
            radialGlow(cx, cy, cr*1.1f,
                Pair(Color.argb((60*pulse).toInt(),  255, 150, 0), 0f),
                Pair(Color.argb((20*pulse).toInt(),  200,  80, 0), 0.65f),
                Pair(Color.argb(0, 0, 0, 0), 1f)))

        // ── Blend semi-transparent amber tint over the static hands area ──
        // This softens the old static hands so our new hands read clearly
        canvas.drawCircle(cx, cy, cr*0.72f, fill(Color.argb(55, 210, 100, 0)))

        // ── Real moving clock hands ────────────────────────────────────────
        val sec  = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND)/1000f
        val minF = c.get(Calendar.MINUTE) + sec/60f
        val hrF  = (c.get(Calendar.HOUR)%12) + minF/60f
        // Thick hands matching her dark brown palette
        hand(canvas, cx, cy, hrF/12f*360f,  cr*0.44f, cr*0.068f, Color.parseColor("#2B1000"))
        hand(canvas, cx, cy, minF/60f*360f, cr*0.60f, cr*0.044f, Color.parseColor("#2B1000"))
        hand(canvas, cx, cy, sec/60f*360f,  cr*0.66f, cr*0.018f, Color.parseColor("#CC3300"))
        canvas.drawCircle(cx, cy, cr*0.045f, fill(Color.parseColor("#2B1000")))
        canvas.drawCircle(cx, cy, cr*0.020f, fill(Color.parseColor("#FF4400")))

        // ── Eye blink — her wide eyes occasionally blink ──────────────────
        // Eyes: left ~(37%, 44%), right ~(63%, 44%), roughly 11% radius each
        val blinkPhase = (animTick * 5f) % 1f  // blinks 5x per animTick cycle
        if (blinkPhase > 0.88f) {  // quick blink at top of each cycle
            val bAlpha = ((1f - blinkPhase) / 0.12f * 220).toInt()
            val (lex, ley) = px(0.375f, 0.440f, cp)
            val (rex, rey) = px(0.625f, 0.440f, cp)
            val er = cr * 0.095f
            // Draw a matching orange band over eyes to simulate closed eyelid
            canvas.drawOval(RectF(lex-er, ley-er*0.4f, lex+er, ley+er*0.4f),
                fill(Color.argb(bAlpha, 200, 100, 20)))
            canvas.drawOval(RectF(rex-er, rey-er*0.4f, rex+er, rey+er*0.4f),
                fill(Color.argb(bAlpha, 200, 100, 20)))
        }

        // Time bottom overlay
        drawTimeOverlay(canvas, w, h, c)
    }

    // ── FACE 15: Miss Minutes — Alert / Scared ────────────────────────────
    // mm_scared.jpg (849×804): Miss Minutes with arms raised, scared pose
    // Clock face centre ≈ (50%, 47%), radius ≈ 28% of image width
    // Animations: pulsing red Nexus Event vignette, scan lines, body shake, flash text
    fun drawMmAlert(canvas: Canvas) {
        val w=width.toFloat(); val h=height.toFloat(); val c=cal()

        val b = bmp("watchfaces/mm_scared.jpg") ?: run {
            canvas.drawColor(Color.parseColor("#100200"))
            canvas.drawText("LOADING", w/2f, h/2f, retroPaint(h*0.07f, 0xFFFF2200.toInt())); return
        }
        val cp = crop(b, w, h)

        // ── Body shake — rapid horizontal micro-tremble ───────────────────
        val shake = sin(animTick * 2f*PI * 8f).toFloat() * 3.5f
        canvas.save()
        canvas.translate(shake, 0f)
        canvas.drawBitmap(b, null, RectF(cp.dstL, cp.dstT, cp.dstL+cp.dstW, cp.dstT+cp.dstH), null)
        canvas.restore()

        // ── Pulsing RED Nexus Event vignette ─────────────────────────────
        val pulse = 0.3f + 0.7f * abs(sin(animTick * 2f*PI * 1.9f)).toFloat()
        canvas.drawRect(0f,0f,w,h,
            radialGlow(w/2f, h/2f, minOf(w,h)*0.60f,
                Pair(Color.argb(0, 200, 0, 0), 0f),
                Pair(Color.argb(0, 200, 0, 0), 0.45f),
                Pair(Color.argb((200*pulse).toInt(), 200, 0, 0), 1f)))

        // ── Red scan lines ────────────────────────────────────────────────
        val scanY = (animTick * h * 3.5f) % h
        canvas.drawRect(0f, scanY,      w, scanY+2f,      fill(Color.argb((55*pulse).toInt(), 255,0,0)))
        canvas.drawRect(0f, scanY+h*0.4f, w, scanY+h*0.4f+1.5f, fill(Color.argb((40*pulse).toInt(), 255,0,0)))

        // ── NEXUS EVENT flashing text at top ─────────────────────────────
        val flashOn = (animTick * 3.5f).toInt() % 2 == 0
        val textAlpha = if (flashOn) 255 else 60
        canvas.drawText("⚠  NEXUS EVENT DETECTED  ⚠", w/2f, h*0.070f,
            retroPaint(h*0.065f, Color.argb(textAlpha, 255, 40, 0)))

        // ── Real clock hands on her clock face ────────────────────────────
        val (cx, cy) = px(0.50f, 0.47f, cp)
        val cr = cp.dstW * 0.278f
        val sec  = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND)/1000f
        val minF = c.get(Calendar.MINUTE) + sec/60f
        val hrF  = (c.get(Calendar.HOUR)%12) + minF/60f
        hand(canvas, cx, cy, hrF/12f*360f,  cr*0.42f, cr*0.060f, Color.parseColor("#2A0E00"))
        hand(canvas, cx, cy, minF/60f*360f, cr*0.58f, cr*0.038f, Color.parseColor("#2A0E00"))
        hand(canvas, cx, cy, sec/60f*360f,  cr*0.62f, cr*0.016f, Color.parseColor("#EE0000"))
        canvas.drawCircle(cx, cy, cr*0.038f, fill(Color.parseColor("#2A0E00")))

        drawTimeOverlay(canvas, w, h, c)
    }

    // ── Shared: dark bottom gradient + amber time + date ──────────────────
    private fun drawTimeOverlay(canvas: Canvas, w: Float, h: Float, c: Calendar) {
        // Dark gradient so text is always readable
        canvas.drawRect(0f, h*0.74f, w, h,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, h*0.74f, 0f, h,
                    Color.argb(0,0,0,0), Color.argb(215,0,0,0), Shader.TileMode.CLAMP)
            })
        // Time
        val ts = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val tp = retroPaint(h*0.128f, Color.parseColor("#FF9900"))
        // Shadow
        tp.color = Color.argb(160, 80, 30, 0)
        canvas.drawText(ts, w/2f+2f, h*0.888f+2f, tp)
        tp.color = Color.parseColor("#FF9900")
        canvas.drawText(ts, w/2f, h*0.888f, tp)
        // Date
        canvas.drawText("${DAYS[c.get(Calendar.DAY_OF_WEEK)-1]}  ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}",
            w/2f, h*0.948f, retroSmallPaint(h*0.052f, Color.parseColor("#CC7700")))
    }
}
