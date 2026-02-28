package com.watchlauncher

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var wallpaperView: android.widget.ImageView
    private lateinit var clockView: ClockView
    private lateinit var faceLabel: TextView
    private lateinit var gestureDetector: GestureDetectorCompat

    private val faceStyles = WatchFaceStyle.values()
    private var currentFaceIndex = 0
        set(value) {
            field = value
            val style = faceStyles[value]
            clockView.style = style
            faceLabel.text = style.displayName
            faceLabel.visibility = View.VISIBLE
            faceLabel.alpha = 1f
            faceLabel.animate().setStartDelay(1200).setDuration(600).alpha(0f).start()
            getSharedPreferences("launcher", MODE_PRIVATE).edit().putInt("face_index", value).apply()
            // Light faces: hide wallpaper
            wallpaperView.alpha = when (style) {
                WatchFaceStyle.NOTHING_DOT, WatchFaceStyle.CLEAN_WHITE -> 0f
                else -> 1f
            }
        }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_main)
        wallpaperView = findViewById(R.id.wallpaperView)
        clockView     = findViewById(R.id.clockView)
        faceLabel     = findViewById(R.id.faceLabel)

        val saved = getSharedPreferences("launcher", MODE_PRIVATE).getInt("face_index", 0)
        currentFaceIndex = saved.coerceIn(0, faceStyles.size - 1)
        faceLabel.alpha = 0f

        setupWallpaper()
        setupGestures()
        registerPackageReceiver()
    }

    override fun onResume() {
        super.onResume()
        val saved = getSharedPreferences("launcher", MODE_PRIVATE).getInt("face_index", 0)
        if (saved != currentFaceIndex) {
            currentFaceIndex = saved.coerceIn(0, faceStyles.size - 1)
        }
        setupWallpaper()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    private fun setupWallpaper() {
        try {
            val d = WallpaperManager.getInstance(this).drawable
            wallpaperView.setImageDrawable(d ?: ColorDrawable(Color.TRANSPARENT))
            wallpaperView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        } catch (_: Exception) {
            wallpaperView.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dY = (e1?.y ?: 0f) - e2.y
                val dX = e2.x - (e1?.x ?: 0f)
                val adX = abs(dX); val adY = abs(dY)
                return when {
                    // Swipe UP → app drawer
                    dY > 80 && dY > adX -> { openAppDrawer(); true }
                    // Swipe LEFT → quick panel
                    dX < -80 && adX > adY -> { openQuickPanel(); true }
                    // Swipe RIGHT → next watch face
                    dX > 80 && adX > adY -> { cycleFace(-1); true }
                    else -> false
                }
            }
            override fun onLongPress(e: MotionEvent) = openQuickPanel()
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.rootLayout).setOnTouchListener { _, e -> gestureDetector.onTouchEvent(e); true }
    }

    private fun cycleFace(direction: Int) {
        currentFaceIndex = (currentFaceIndex + direction + faceStyles.size) % faceStyles.size
    }

    private fun openAppDrawer() {
        startActivity(Intent(this, AppDrawerActivity::class.java))
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun openQuickPanel() {
        startActivity(Intent(this, QuickPanelActivity::class.java))
        overridePendingTransition(R.anim.slide_left, R.anim.fade_out)
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }
}
