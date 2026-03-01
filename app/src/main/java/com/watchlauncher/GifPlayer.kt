package com.watchlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper

/**
 * Loads pre-extracted PNG frames from assets/mm_frames/ and cycles through them.
 * Frame duration: 50ms per frame (20fps) — matches original GIF timing.
 */
class GifPlayer(private val context: Context) {

    private val frames = mutableListOf<Bitmap>()
    private var currentFrame = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val frameDuration = 50L   // ms per frame

    var onFrameUpdate: ((Bitmap) -> Unit)? = null

    fun load() {
        Thread {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            for (i in 0..35) {
                try {
                    val stream = context.assets.open("mm_frames/frame_${i.toString().padStart(2,'0')}.png")
                    val bmp = BitmapFactory.decodeStream(stream, null, opts)
                    stream.close()
                    if (bmp != null) frames.add(bmp)
                } catch (_: Exception) {}
            }
        }.start()
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!isRunning || frames.isEmpty()) return
            currentFrame = (currentFrame + 1) % frames.size
            onFrameUpdate?.invoke(frames[currentFrame])
            handler.postDelayed(this, frameDuration)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        handler.post(ticker)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(ticker)
    }

    fun getCurrentFrame(): Bitmap? = frames.getOrNull(currentFrame)

    fun recycle() {
        stop()
        frames.forEach { it.recycle() }
        frames.clear()
    }
}
