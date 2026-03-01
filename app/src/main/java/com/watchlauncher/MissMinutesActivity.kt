package com.watchlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MissMinutesActivity : AppCompatActivity() {

    private lateinit var gifView: ImageView
    private lateinit var speechBubble: TextView
    private lateinit var statusText: TextView
    private lateinit var timeText: TextView
    private lateinit var gestureDetector: GestureDetectorCompat

    private var gifPlayer: GifPlayer? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var retroFont: Typeface? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isTalking = false
    private var mouthOpen = 0f

    // ── Gemini config ─────────────────────────────────────────────────────
    private val API_KEY = "AIzaSyD3pPUv_tEdUb9-HKBeTwTVSfw5EWJm3cw"
    private val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$API_KEY"

    private val SYSTEM_PROMPT = "You are Miss Minutes, TVA mascot from Loki. Respond as her: cheerful, Southern accent, short replies under 20 words. Say things like Sugar, Sacred Timeline, TVA. Be helpful and in character."

    // Full multi-turn history
    private val history = mutableListOf<JSONObject>()

    // ── Ticker for mouth anim ─────────────────────────────────────────────
    private val mouthTicker = object : Runnable {
        override fun run() {
            if (isTalking) {
                mouthOpen = (0.3f + 0.7f * Math.abs(Math.sin(System.currentTimeMillis() / 90.0))).toFloat()
                drawCurrentFrame()
            } else if (mouthOpen > 0f) {
                mouthOpen = (mouthOpen - 0.1f).coerceAtLeast(0f)
                drawCurrentFrame()
            }
            handler.postDelayed(this, 60)
        }
    }

    private val timeTicker = object : Runnable {
        override fun run() {
            val c = Calendar.getInstance()
            val t = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            timeText.text = t
            handler.postDelayed(this, 15000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(R.layout.activity_miss_minutes)

        gifView      = findViewById(R.id.missGifView)
        speechBubble = findViewById(R.id.speechBubble)
        statusText   = findViewById(R.id.statusText)
        timeText     = findViewById(R.id.timeText)

        // Load retro font
        try {
            retroFont = Typeface.createFromAsset(assets, "fonts/retro.ttf")
            timeText.typeface = retroFont
            speechBubble.typeface = retroFont
            statusText.typeface = retroFont
        } catch (e: Exception) {
            // fallback to monospace
            val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            timeText.typeface = mono
            speechBubble.typeface = mono
            statusText.typeface = mono
        }

        setupGifPlayer()
        setupTTS()
        setupGestures()
        checkAudioPermission()

        handler.post(timeTicker)
        handler.post(mouthTicker)

        // Greeting
        handler.postDelayed({
            say("Well, hey there Sugar! I'm Miss Minutes. Tap me and let's chat!")
        }, 700)
    }

    override fun onDestroy() {
        super.onDestroy()
        gifPlayer?.recycle()
        tts?.stop(); tts?.shutdown()
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ════════════════════════════════════════════════════════════════════════
    // GIF — full screen, time drawn on each frame
    // ════════════════════════════════════════════════════════════════════════
    private fun setupGifPlayer() {
        gifPlayer = GifPlayer(this)
        gifPlayer?.load()
        gifPlayer?.onFrameUpdate = { _ -> drawCurrentFrame() }
        gifPlayer?.start()
    }

    private fun drawCurrentFrame() {
        val src = gifPlayer?.getCurrentFrame() ?: return
        try {
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(out)
            val w = out.width.toFloat(); val h = out.height.toFloat()

            // Dark semi-transparent overlay at top for time
            val overlayPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
            canvas.drawRect(0f, 0f, w, h * 0.18f, overlayPaint)

            // Time text
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF8C00")
                textAlign = Paint.Align.CENTER
                textSize = h * 0.115f
                typeface = retroFont ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val c = Calendar.getInstance()
            val timeStr = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            // Shadow
            timePaint.color = Color.parseColor("#8B3200")
            canvas.drawText(timeStr, w/2f + 2f, h*0.14f + 2f, timePaint)
            // Main
            timePaint.color = Color.parseColor("#FFAA00")
            canvas.drawText(timeStr, w/2f, h*0.14f, timePaint)

            // Talking mouth glow
            if (mouthOpen > 0.05f) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((80 * mouthOpen).toInt(), 255, 200, 0)
                    style = Paint.Style.FILL
                }
                // Miss Minutes mouth area is around 65-70% down her body
                canvas.drawOval(RectF(w*0.35f, h*0.60f, w*0.65f, h*0.68f + h*0.06f*mouthOpen), glowPaint)
            }

            gifView.setImageBitmap(out)
        } catch (_: Exception) {
            gifView.setImageBitmap(src)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TTS
    // ════════════════════════════════════════════════════════════════════════
    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.35f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(uid: String?) {
                        handler.post {
                            isTalking = true
                            statusText.text = "SPEAKING..."
                        }
                    }
                    override fun onDone(uid: String?) {
                        handler.post {
                            isTalking = false
                            statusText.text = "TAP TO TALK"
                        }
                    }
                    override fun onError(uid: String?) { handler.post { isTalking = false } }
                })
            }
        }
    }

    private fun say(text: String) {
        speechBubble.text = text
        speechBubble.visibility = View.VISIBLE
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "mm_${System.currentTimeMillis()}")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "mm_${System.currentTimeMillis()}")
    }

    // ════════════════════════════════════════════════════════════════════════
    // SPEECH RECOGNITION
    // ════════════════════════════════════════════════════════════════════════
    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Speech recognition not available on this device, Sugar!")
            return
        }
        tts?.stop()
        isTalking = false
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                handler.post { statusText.text = "LISTENING..." }
            }
            override fun onBeginningOfSpeech() {
                handler.post { statusText.text = "HEARING YOU..." }
            }
            override fun onResults(r: Bundle?) {
                val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: return
                handler.post {
                    speechBubble.text = "YOU: $text"
                    speechBubble.visibility = View.VISIBLE
                    statusText.text = "THINKING..."
                    askGemini(text)
                }
            }
            override fun onError(e: Int) {
                handler.post {
                    statusText.text = "TAP TO TALK"
                    say("Didn't catch that, Sugar! Tap and try again!")
                }
            }
            override fun onEndOfSpeech() { handler.post { statusText.text = "PROCESSING..." } }
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onPartialResults(r: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        speechRecognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // GEMINI AI — proper multi-turn conversation
    // ════════════════════════════════════════════════════════════════════════
    private fun askGemini(userMsg: String) {
        Thread {
            try {
                val url = URL(API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 12000
                conn.readTimeout = 20000

                // Build contents array with system context + full history + new message
                val contents = JSONArray()

                // First turn: system instruction as user+model exchange
                contents.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                })
                contents.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text",
                        "Well, hey there, Sugar! I'm Miss Minutes, your friendly TVA guide!")))
                })

                // Add conversation history (last 6 turns max)
                for (msg in history.takeLast(6)) {
                    contents.put(msg)
                }

                // Add current user message
                val userTurn = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMsg)))
                }
                contents.put(userTurn)

                val requestBody = JSONObject().apply {
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.85)
                        put("maxOutputTokens", 60)
                        put("topP", 0.9)
                    })
                    put("safetySettings", JSONArray().apply {
                        put(JSONObject().apply {
                            put("category", "HARM_CATEGORY_HARASSMENT")
                            put("threshold", "BLOCK_NONE")
                        })
                    })
                }

                val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode

                if (responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    val jsonResp = JSONObject(responseText)

                    val reply = jsonResp
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                    // Save both turns to history for next request
                    history.add(userTurn)
                    history.add(JSONObject().apply {
                        put("role", "model")
                        put("parts", JSONArray().put(JSONObject().put("text", reply)))
                    })

                    handler.post { say(reply) }
                } else {
                    val errText = conn.errorStream?.bufferedReader()?.readText() ?: "code $responseCode"
                    handler.post {
                        val fallback = listOf(
                            "The Sacred Timeline's a bit wobbly right now, Sugar! Try again!",
                            "Well isn't that something! The TVA servers are busy, Sugar!",
                            "Hold on there, Sugar! The timeline needs a moment!"
                        ).random()
                        say(fallback)
                    }
                }
                conn.disconnect()

            } catch (e: Exception) {
                handler.post {
                    val fallback = listOf(
                        "Bless your heart, network trouble! Try again, Sugar!",
                        "The TVA's fixing a nexus event! One moment, Sugar!"
                    ).random()
                    say(fallback)
                }
            }
        }.start()
    }

    // ════════════════════════════════════════════════════════════════════════
    // GESTURES
    // ════════════════════════════════════════════════════════════════════════
    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                startListening()
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = e2.y - (e1?.y ?: 0f)
                if (dx > 80 && dx > abs(dy)) {
                    finish()
                    return true
                }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.missRoot).setOnTouchListener { _, e ->
            gestureDetector.onTouchEvent(e)
            true
        }
    }
}
