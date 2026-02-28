package com.watchlauncher

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import kotlinx.coroutines.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var wallpaperView: ImageView
    private lateinit var clockView: ClockView
    private lateinit var dockContainer: LinearLayout
    private lateinit var faceLabel: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
            // Save preference
            getSharedPreferences("launcher", MODE_PRIVATE)
                .edit().putInt("face_index", value).apply()
            updateBackgroundForFace(style)
        }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { loadDock() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_main)

        wallpaperView  = findViewById(R.id.wallpaperView)
        clockView      = findViewById(R.id.clockView)
        dockContainer  = findViewById(R.id.dockContainer)
        faceLabel      = findViewById(R.id.faceLabel)

        // Restore saved face
        val saved = getSharedPreferences("launcher", MODE_PRIVATE).getInt("face_index", 0)
        currentFaceIndex = saved.coerceIn(0, faceStyles.size - 1)
        faceLabel.alpha = 0f  // hide on start, only show on change

        setupWallpaper()
        setupGestures()
        loadDock()
        registerPackageReceiver()
    }

    override fun onResume() {
        super.onResume()
        setupWallpaper()
        loadDock()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    private fun setupWallpaper() {
        try {
            val drawable = WallpaperManager.getInstance(this).drawable
            if (drawable != null) {
                wallpaperView.setImageDrawable(drawable)
                wallpaperView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                wallpaperView.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        } catch (_: Exception) {
            wallpaperView.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun updateBackgroundForFace(style: WatchFaceStyle) {
        // For light faces, show wallpaper dimmed; for dark faces, show full
        val scrim = findViewById<View>(R.id.scrimView)
        when (style) {
            WatchFaceStyle.NOTHING_DOT, WatchFaceStyle.CLEAN_WHITE -> {
                // These have their own white bg drawn on canvas, hide wallpaper scrim
                scrim.alpha = 0f
                wallpaperView.alpha = 0f
            }
            else -> {
                scrim.alpha = 1f
                wallpaperView.alpha = 1f
            }
        }
    }

    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dY = (e1?.y ?: 0f) - e2.y
                val dX = e2.x - (e1?.x ?: 0f)
                val adX = abs(dX); val adY = abs(dY)

                return when {
                    // Swipe UP → open app drawer
                    dY > 80 && dY > adX -> { openAppDrawer(); true }
                    // Swipe RIGHT → next watch face
                    dX < -80 && adX > adY -> { cycleFace(1); true }
                    // Swipe LEFT → previous watch face
                    dX > 80 && adX > adY -> { cycleFace(-1); true }
                    else -> false
                }
            }
            override fun onLongPress(e: MotionEvent) = openWallpaperPicker()
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.rootLayout).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event); true
        }
    }

    private fun cycleFace(direction: Int) {
        currentFaceIndex = (currentFaceIndex + direction + faceStyles.size) % faceStyles.size
    }

    private fun loadDock() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) { getPinnedApps() }
            buildDock(apps)
        }
    }

    private fun getPinnedApps(): List<AppInfo> {
        val candidates = listOf(
            "com.google.android.maps", "com.google.android.wearable.app",
            "com.android.settings", "com.google.android.apps.fitness",
            "com.google.android.gms"
        )
        val pm = packageManager
        return candidates.mapNotNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                AppInfo(pm.getApplicationLabel(info).toString(), pkg, pm.getApplicationIcon(pkg), true)
            } catch (_: PackageManager.NameNotFoundException) { null }
        }
    }

    private fun buildDock(apps: List<AppInfo>) {
        dockContainer.removeAllViews()
        val size   = dpToPx(44)
        val margin = dpToPx(5)
        apps.take(5).forEach { app ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginStart = margin; it.marginEnd = margin
                }
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = getDrawable(R.drawable.bg_dock_icon)
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                contentDescription = app.label
                setOnClickListener { launchApp(app.packageName) }
                setOnLongClickListener { Toast.makeText(context, app.label, Toast.LENGTH_SHORT).show(); true }
            }
            dockContainer.addView(iv)
        }
    }

    private fun openAppDrawer() {
        startActivity(Intent(this, AppDrawerActivity::class.java))
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun openWallpaperPicker() {
        startActivity(Intent(this, WallpaperPickerActivity::class.java))
    }

    private fun launchApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
            ?: Toast.makeText(this, "App not available", Toast.LENGTH_SHORT).show()
    }

    private fun registerPackageReceiver() {
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
