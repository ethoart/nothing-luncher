package com.watchlauncher

import android.content.Intent
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import kotlin.math.abs

class QuickPanelActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var batteryText: TextView
    private lateinit var batteryBar: View
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() { updateTime(); handler.postDelayed(this, 30000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_panel)

        batteryText = findViewById(R.id.batteryText)
        batteryBar  = findViewById(R.id.batteryBar)
        timeText    = findViewById(R.id.panelTime)
        dateText    = findViewById(R.id.panelDate)

        setupGestures()
        setupButtons()
        updateTime()
        updateBattery()
        handler.post(ticker)
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(ticker) }

    private fun updateTime() {
        val cal = java.util.Calendar.getInstance()
        timeText.text = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        val days   = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        dateText.text = "${days[cal.get(java.util.Calendar.DAY_OF_WEEK)-1]}, ${months[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    private fun updateBattery() {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        batteryText.text = "$pct%"
        val params = batteryBar.layoutParams
        params.width = (resources.displayMetrics.widthPixels * 0.55f * pct / 100f).toInt()
        batteryBar.layoutParams = params
    }

    private fun setupButtons() {
        // Settings
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            val i = packageManager.getLaunchIntentForPackage("com.android.settings")
            if (i != null) startActivity(i) else finish()
        }
        // Wallpaper
        findViewById<View>(R.id.btnWallpaper).setOnClickListener {
            startActivity(Intent(this, WallpaperPickerActivity::class.java))
        }
        // Watch face cycle
        findViewById<View>(R.id.btnFace).setOnClickListener {
            val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
            val cur = prefs.getInt("face_index", 0)
            val next = (cur + 1) % WatchFaceStyle.values().size
            prefs.edit().putInt("face_index", next).apply()
            val label = WatchFaceStyle.values()[next].displayName
            (it as? TextView)?.text = "Face: $label"
            android.widget.Toast.makeText(this, "Face: $label", android.widget.Toast.LENGTH_SHORT).show()
        }
        // App drawer
        findViewById<View>(R.id.btnApps).setOnClickListener {
            startActivity(Intent(this, AppDrawerActivity::class.java))
            overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
        }
        // Power off / reboot (shows options)
        findViewById<View>(R.id.btnPower).setOnClickListener {
            android.widget.Toast.makeText(this, "Hold power button for options", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dX = e2.x - (e1?.x ?: 0f)
                // Swipe RIGHT → close panel (go back to home)
                if (dX > 80 && dX > abs(e2.y - (e1?.y ?: 0f))) {
                    finish(); overridePendingTransition(R.anim.fade_in, R.anim.slide_right); return true
                }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.panelRoot).setOnTouchListener { _, e -> gestureDetector.onTouchEvent(e); false }
    }
}
