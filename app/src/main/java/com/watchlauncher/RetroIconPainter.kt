package com.watchlauncher

import android.graphics.*

/**
 * Draws Nothing-OS-style dot-matrix / pixel-art icons for well-known apps.
 * Falls back to the real app icon for unknown packages.
 * Grid is 8×8 pixels, scaled to the target bitmap size.
 */
object RetroIconPainter {

    // Each icon = 8×8 binary grid (1 = filled dot, 0 = empty)
    // Colors: bg, primary, accent
    data class PixelIcon(
        val grid: List<String>,   // 8 rows of 8 chars
        val bg: Int,
        val fg: Int,
        val accent: Int = fg
    )

    private val icons: Map<String, PixelIcon> = mapOf(

        // Settings — gear
        "com.android.settings" to PixelIcon(listOf(
            "00011000",
            "01111110",
            "11000011",
            "10100101",
            "10100101",
            "11000011",
            "01111110",
            "00011000"
        ), bg = 0xFF1A1A1A.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Phone
        "com.android.phone" to PixelIcon(listOf(
            "01100000",
            "11110000",
            "11110010",
            "01100110",
            "00001111",
            "00011111",
            "00011110",
            "00001100"
        ), bg = 0xFF2E7D32.toInt(), fg = 0xFFFFFFFF.toInt()),

        "com.google.android.dialer" to PixelIcon(listOf(
            "01100000",
            "11110000",
            "11110010",
            "01100110",
            "00001111",
            "00011111",
            "00011110",
            "00001100"
        ), bg = 0xFF2E7D32.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Messages / SMS
        "com.google.android.apps.messaging" to PixelIcon(listOf(
            "11111110",
            "10000010",
            "10110010",
            "10111010",
            "10110010",
            "10000010",
            "11111100",
            "00000000"
        ), bg = 0xFF1565C0.toInt(), fg = 0xFFFFFFFF.toInt()),

        "com.android.mms" to PixelIcon(listOf(
            "11111110",
            "10000010",
            "10110010",
            "10111010",
            "10110010",
            "10000010",
            "11111100",
            "00000000"
        ), bg = 0xFF1565C0.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Maps
        "com.google.android.apps.maps" to PixelIcon(listOf(
            "00111100",
            "01111110",
            "11011011",
            "11011011",
            "01111110",
            "00111100",
            "00011000",
            "00011000"
        ), bg = 0xFFD32F2F.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Fitness / Health
        "com.google.android.apps.fitness" to PixelIcon(listOf(
            "01100110",
            "11111111",
            "11111111",
            "01111110",
            "00111100",
            "00011000",
            "00000000",
            "00000000"
        ), bg = 0xFFE91E63.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Play Store
        "com.android.vending" to PixelIcon(listOf(
            "01000000",
            "01100000",
            "01110000",
            "01111100",
            "01111100",
            "01110000",
            "01100000",
            "01000000"
        ), bg = 0xFF0288D1.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Camera
        "com.android.camera2" to PixelIcon(listOf(
            "00111100",
            "01111110",
            "11100111",
            "11011011",
            "11011011",
            "11100111",
            "01111110",
            "00111100"
        ), bg = 0xFF37474F.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Clock / Alarm
        "com.google.android.deskclock" to PixelIcon(listOf(
            "00111100",
            "01000010",
            "10010001",
            "10011001",
            "10000001",
            "01000010",
            "00111100",
            "00000000"
        ), bg = 0xFF4A148C.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Wear OS companion
        "com.google.android.wearable.app" to PixelIcon(listOf(
            "00111100",
            "01000010",
            "10100101",
            "10111101",
            "10111101",
            "10100101",
            "01000010",
            "00111100"
        ), bg = 0xFF212121.toInt(), fg = 0xFFFF3B2F.toInt()),

        // Music / YouTube Music
        "com.google.android.apps.youtube.music" to PixelIcon(listOf(
            "00111100",
            "01111110",
            "11011011",
            "11100111",
            "01111110",
            "00111100",
            "00011000",
            "00000000"
        ), bg = 0xFFFF0000.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Calculator
        "com.android.calculator2" to PixelIcon(listOf(
            "11111111",
            "10000001",
            "10110101",
            "10000001",
            "10001001",
            "10110101",
            "10000001",
            "11111111"
        ), bg = 0xFF263238.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Browser / Chrome
        "com.android.chrome" to PixelIcon(listOf(
            "00111100",
            "01011010",
            "10111101",
            "11000111",
            "11000111",
            "10111101",
            "01011010",
            "00111100"
        ), bg = 0xFFF57C00.toInt(), fg = 0xFFFFFFFF.toInt()),

        // Contacts
        "com.android.contacts" to PixelIcon(listOf(
            "00111100",
            "00111100",
            "00111100",
            "01111110",
            "11111111",
            "11111111",
            "10000001",
            "00000000"
        ), bg = 0xFF00695C.toInt(), fg = 0xFFFFFFFF.toInt())
    )

    /**
     * Returns a Bitmap with a pixel-art icon for the given package, or null if unknown.
     * Size is in pixels.
     */
    fun getBitmap(packageName: String, size: Int): Bitmap? {
        val icon = icons[packageName] ?: return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rows = icon.grid.size
        val cols = icon.grid[0].length
        val dotW = size.toFloat() / cols
        val dotH = size.toFloat() / rows
        val radius = (minOf(dotW, dotH) * 0.42f)

        // Background
        paint.color = icon.bg
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.2f, size * 0.2f, paint)

        // Dots
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (c < icon.grid[r].length && icon.grid[r][c] == '1') {
                    paint.color = if (r < rows / 2) icon.fg else icon.accent
                    val cx = c * dotW + dotW / 2f
                    val cy = r * dotH + dotH / 2f
                    canvas.drawCircle(cx, cy, radius, paint)
                }
            }
        }
        return bmp
    }
}
