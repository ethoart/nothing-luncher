package com.watchlauncher

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import java.util.Calendar
import kotlin.math.abs

class TopMenuActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetectorCompat
    private val handler = Handler(Looper.getMainLooper())

    private val FACES = WatchFaceStyle.values()
    private var faceIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_menu)

        faceIdx = getSharedPreferences("launcher", MODE_PRIVATE).getInt("face_index", 0)

        setupUI()
        setupGestures()
        setupActions()
    }

    private fun setupUI() {
        val cal = Calendar.getInstance()
        val days   = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

        // Time
        findViewById<TextView>(R.id.menuTime).text = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        findViewById<TextView>(R.id.menuDate).text = "${days[cal.get(Calendar.DAY_OF_WEEK)-1]}, ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"

        // Battery
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val batteryBar = findViewById<View>(R.id.menuBatteryFill)
        val params = batteryBar.layoutParams
        // set width proportionally in post
        batteryBar.post {
            params.width = (batteryBar.parent as View).width * pct / 100
            batteryBar.layoutParams = params
        }
        val batteryColor = when {
            pct > 50 -> "#00C853"
            pct > 20 -> "#FF8F00"
            else     -> "#D50000"
        }
        batteryBar.setBackgroundColor(android.graphics.Color.parseColor(batteryColor))
        findViewById<TextView>(R.id.menuBatteryPct).text = "$pct%"

        // Current face
        updateFaceLabel()
    }

    private fun updateFaceLabel() {
        val name = FACES.getOrNull(faceIdx)?.displayName ?: "Nothing OS"
        val label = "◉  $name"
        val tv = findViewById<TextView>(R.id.menuFaceName)
        tv?.text = label
    }

    private fun setupActions() {
        // Face prev
        findViewById<View>(R.id.btnFacePrev).setOnClickListener {
            faceIdx = (faceIdx - 1 + FACES.size) % FACES.size
            getSharedPreferences("launcher", MODE_PRIVATE).edit().putInt("face_index", faceIdx).apply()
            updateFaceLabel()
        }
        // Face next
        findViewById<View>(R.id.btnFaceNext).setOnClickListener {
            faceIdx = (faceIdx + 1) % FACES.size
            getSharedPreferences("launcher", MODE_PRIVATE).edit().putInt("face_index", faceIdx).apply()
            updateFaceLabel()
        }
        // Wallpaper
        findViewById<View>(R.id.btnMenuWallpaper).setOnClickListener {
            startActivity(Intent(this, WallpaperPickerActivity::class.java))
        }
        // Settings
        findViewById<View>(R.id.btnMenuSettings).setOnClickListener {
            packageManager.getLaunchIntentForPackage("com.android.settings")?.let { startActivity(it) }
        }
        // Apps
        findViewById<View>(R.id.btnMenuApps).setOnClickListener {
            startActivity(Intent(this, AppDrawerActivity::class.java))
            overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
        }
        // Miss Minutes
        findViewById<View>(R.id.btnMenuMM).setOnClickListener {
            faceIdx = FACES.indexOfFirst { it == WatchFaceStyle.MISS_MINUTES }.coerceAtLeast(0)
            getSharedPreferences("launcher", MODE_PRIVATE).edit().putInt("face_index", faceIdx).apply()
            startActivity(Intent(this, MissMinutesActivity::class.java))
            finish()
        }
        // Close / Dismiss
        findViewById<View>(R.id.btnMenuClose).setOnClickListener { finish() }
    }

    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dY = (e1?.y ?: 0f) - e2.y
                // Swipe UP → dismiss
                if (dY > 60 && dY > abs(e2.x - (e1?.x ?: 0f))) {
                    finish(); overridePendingTransition(R.anim.fade_in, R.anim.slide_up_out); return true
                }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.menuRoot).setOnTouchListener { _, e -> gestureDetector.onTouchEvent(e); false }
    }
}
