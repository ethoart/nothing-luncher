package com.watchlauncher

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import kotlin.math.abs

/**
 * TopMenuActivity — quick-access overlay menu.
 * Swipe right to dismiss. Tap any item to navigate.
 *
 * Fix: replaced unresolved `MISS_MINUTES` symbol with
 * the correct class reference `MissMinutesActivity::class.java`.
 */
class TopMenuActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetectorCompat

    // ── Menu item descriptors ─────────────────────────────────────────────
    private data class MenuItem(val label: String, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(R.layout.activity_top_menu)
        setupMenu()
        setupGestures()
    }

    // ════════════════════════════════════════════════════════════════════════
    // Menu construction
    // ════════════════════════════════════════════════════════════════════════
    private fun setupMenu() {
        val retroFont: Typeface? = try {
            Typeface.createFromAsset(assets, "fonts/retro.ttf")
        } catch (_: Exception) {
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val menuItems = listOf(
            MenuItem("HOME") {
                finish()
            },
            MenuItem("APPS") {
                startActivity(Intent(this, AppDrawerActivity::class.java))
                overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
                finish()
            },
            MenuItem("QUICK PANEL") {
                startActivity(Intent(this, QuickPanelActivity::class.java))
                overridePendingTransition(R.anim.slide_left, R.anim.fade_out)
                finish()
            },
            // ── FIX: was `MISS_MINUTES` (unresolved), corrected to MissMinutesActivity ──
            MenuItem("MISS MINUTES") {
                startActivity(Intent(this, MissMinutesActivity::class.java))
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            },
            MenuItem("WALLPAPER") {
                startActivity(Intent(this, WallpaperPickerActivity::class.java))
                finish()
            },
            MenuItem("SETTINGS") {
                val i = packageManager.getLaunchIntentForPackage("com.android.settings")
                if (i != null) startActivity(i)
                finish()
            }
        )

        val container = findViewById<LinearLayout>(R.id.menuContainer)

        menuItems.forEach { item ->
            val tv = TextView(this).apply {
                text = item.label
                typeface = retroFont
                textSize = 16f
                setTextColor(android.graphics.Color.parseColor("#FFAA00"))
                setPadding(32, 28, 32, 28)
                setOnClickListener { item.action() }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.argb(40, 255, 170, 0))
                    cornerRadius = 12f
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            container.addView(tv, lp)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Gestures — swipe right to dismiss
    // ════════════════════════════════════════════════════════════════════════
    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
            ): Boolean {
                val dX = e2.x - (e1?.x ?: 0f)
                val dY = e2.y - (e1?.y ?: 0f)
                if (dX > 80 && dX > abs(dY)) {
                    finish()
                    overridePendingTransition(R.anim.fade_in, R.anim.slide_right)
                    return true
                }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.topMenuRoot).setOnTouchListener { _, e ->
            gestureDetector.onTouchEvent(e)
            false
        }
    }
}
