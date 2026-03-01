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
    private var ttsReady = false

    private val handler = Handler(Looper.getMainLooper())
    private var isTalking = false
    private var mouthOpen = 0f

    // ── Gemini 2.0 Flash config ────────────────────────────────────────────
    // Using gemini-2.0-flash — fast, smart, supports system instructions natively
    private val API_KEY = "AIzaSyD3pPUv_tEdUb9-HKBeTwTVSfw5EWJm3cw"
    private val MODEL   = "gemini-2.0-flash"
    private val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$API_KEY"

    private val SYSTEM_PROMPT = """You are Miss Minutes, the cheerful clockwork mascot of the TVA (Time Variance Authority) from the TV show Loki.
Personality: warm, bubbly, slightly ominous, Southern belle charm, devoted to the Sacred Timeline.
Rules:
- Reply in 1-3 SHORT sentences. Never more.
- Use Southern expressions: Sugar, Honey, Darlin', Bless your heart
- Reference the TVA, Sacred Timeline, Nexus Events, Time Keepers when relevant
- Be genuinely helpful with real questions, but stay in character
- If asked the time, tell them the current watch time
- Never break character"""

    // Multi-turn conversation history (role: user / model alternating)
    private val history = mutableListOf<JSONObject>()

    // Fallback lines when AI is unavailable
    private val fallbacks = listOf(
        "Well Sugar, the Sacred Timeline's a little wobbly right now! Try again, Darlin'!",
        "The Time Keepers are busy, Honey! Give me just a tick!",
        "Oh bless your heart, the TVA servers need a moment! Try again!",
        "Well isn't that somethin'! Hold on one moment, Sugar!"
    )

    // ── Mouth animation ticker ─────────────────────────────────────────────
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
            val cal = Calendar.getInstance()
            timeText.text = String.format("%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        retroFont = try {
            Typeface.createFromAsset(assets, "fonts/retro.ttf")
        } catch (_: Exception) {
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        listOf(timeText, speechBubble, statusText).forEach {
            it.typeface = retroFont
        }

        setupGifPlayer()
        setupTTS()
        setupGestures()
        checkAudioPermission()

        handler.post(timeTicker)
        handler.post(mouthTicker)

        // Greeting after TTS warms up
        handler.postDelayed({
            say("Well hey there, Sugar! I'm Miss Minutes. Tap me and let's chat!")
        }, 900)
    }

    override fun onDestroy() {
        super.onDestroy()
        gifPlayer?.recycle()
        tts?.stop(); tts?.shutdown()
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ════════════════════════════════════════════════════════════════════════
    // GIF player + frame drawing
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

            // Dark overlay at top for time
            val overlayPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
            canvas.drawRect(0f, 0f, w, h * 0.18f, overlayPaint)

            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = h * 0.115f
                typeface = retroFont ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val cal = Calendar.getInstance()
            val timeStr = String.format("%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

            timePaint.color = Color.parseColor("#8B3200")
            canvas.drawText(timeStr, w / 2f + 2f, h * 0.14f + 2f, timePaint)
            timePaint.color = Color.parseColor("#FFAA00")
            canvas.drawText(timeStr, w / 2f, h * 0.14f, timePaint)

            // Mouth glow when talking
            if (mouthOpen > 0.05f) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((80 * mouthOpen).toInt(), 255, 200, 0)
                    style = Paint.Style.FILL
                }
                canvas.drawOval(RectF(w * 0.35f, h * 0.60f,
                    w * 0.65f, h * 0.68f + h * 0.06f * mouthOpen), glowPaint)
            }

            gifView.setImageBitmap(out)
        } catch (_: Exception) {
            gifView.setImageBitmap(src)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TTS — TextToSpeech with higher pitch for Miss Minutes
    // ════════════════════════════════════════════════════════════════════════
    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try en_US with fallback
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.ENGLISH)
                }
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.40f)   // Higher pitch = Miss Minutes' voice
                ttsReady = true

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
                    override fun onError(uid: String?) {
                        handler.post { isTalking = false; statusText.text = "TAP TO TALK" }
                    }
                })
            } else {
                handler.post { statusText.text = "TTS ERROR - TAP TO RETRY" }
            }
        }
    }

    private fun say(text: String) {
        handler.post {
            speechBubble.text = text
            speechBubble.visibility = View.VISIBLE
        }
        if (!ttsReady) { return }
        val uid = "mm_${System.currentTimeMillis()}"
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uid) }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, uid)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Speech recognition
    // ════════════════════════════════════════════════════════════════════════
    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            handler.postDelayed({ startListening() }, 400)
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            checkAudioPermission(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Speech recognition isn't available on this device, Sugar!"); return
        }

        // Stop TTS so it doesn't interfere with mic
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
                }
                askGemini(text)
            }
            override fun onError(errorCode: Int) {
                val msg = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH     -> "Hmm, didn't catch that, Sugar! Try again!"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "You went quiet on me, Darlin'! Tap to try again!"
                    SpeechRecognizer.ERROR_NETWORK      -> "Network trouble, Sugar! Check your connection!"
                    SpeechRecognizer.ERROR_AUDIO        -> "Microphone trouble! Make sure I have permission!"
                    else -> "Something went sideways, Sugar! Tap to try again!"
                }
                handler.post { statusText.text = "TAP TO TALK"; say(msg) }
            }
            override fun onEndOfSpeech() { handler.post { statusText.text = "PROCESSING..." } }
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onPartialResults(r: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })

        speechRecognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // Gemini 2.0 Flash — proper multi-turn with systemInstruction field
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
                conn.connectTimeout = 15_000
                conn.readTimeout = 25_000

                // Build the current user turn
                val userTurn = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMsg)))
                }

                // contents = history (last 8 turns) + new user message
                val contents = JSONArray()
                for (msg in history.takeLast(8)) {
                    contents.put(msg)
                }
                contents.put(userTurn)

                // Full request body — systemInstruction is a top-level field in Gemini 2.0
                val body = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(
                            JSONObject().put("text", SYSTEM_PROMPT)
                        ))
                    })
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.90)
                        put("maxOutputTokens", 80)
                        put("topP", 0.92)
                        put("topK", 40)
                    })
                    put("safetySettings", JSONArray().apply {
                        listOf(
                            "HARM_CATEGORY_HARASSMENT",
                            "HARM_CATEGORY_HATE_SPEECH",
                            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                            "HARM_CATEGORY_DANGEROUS_CONTENT"
                        ).forEach { cat ->
                            put(JSONObject().apply {
                                put("category", cat)
                                put("threshold", "BLOCK_NONE")
                            })
                        }
                    })
                }

                val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(body.toString())
                writer.flush(); writer.close()

                val code = conn.responseCode

                if (code == 200) {
                    val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    val json = JSONObject(raw)

                    val reply = json
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()
                        .take(300) // safety cap

                    // Save to history for next turn
                    history.add(userTurn)
                    history.add(JSONObject().apply {
                        put("role", "model")
                        put("parts", JSONArray().put(JSONObject().put("text", reply)))
                    })

                    handler.post { say(reply) }

                } else {
                    // Read error body for debugging
                    val err = try {
                        conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    } catch (_: Exception) { "HTTP $code" }

                    android.util.Log.e("MissMinutes", "Gemini error $code: $err")
                    handler.post { say(fallbacks.random()) }
                }
                conn.disconnect()

            } catch (e: Exception) {
                android.util.Log.e("MissMinutes", "Network error: ${e.message}")
                handler.post { say(fallbacks.random()) }
            }
        }.start()
    }

    // ════════════════════════════════════════════════════════════════════════
    // Gestures — tap = talk, swipe right = go back
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
