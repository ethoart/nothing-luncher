package com.watchlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
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
import android.widget.Toast
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
    private lateinit var timeOverlay: TextView
    private lateinit var speechBubble: TextView
    private lateinit var statusText: TextView
    private lateinit var tapHint: TextView
    private lateinit var gestureDetector: GestureDetectorCompat

    private var gifPlayer: GifPlayer? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTalking = false

    // Mouth overlay paint for talking animation
    private var mouthOpen = 0f
    private val mouthTicker = object : Runnable {
        override fun run() {
            if (isTalking) {
                mouthOpen = (0.4f + 0.6f * Math.abs(Math.sin(System.currentTimeMillis() / 80.0))).toFloat()
            } else {
                mouthOpen = (mouthOpen - 0.08f).coerceAtLeast(0f)
            }
            handler.postDelayed(this, 50)
        }
    }

    // Time ticker
    private val timeTicker = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 10000)
        }
    }

    // Baked-in Gemini API key
    private val GEMINI_API_KEY = "AIzaSyD3pPUv_tEdUb9-HKBeTwTVSfw5EWJm3cw"
    private val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY"

    private val MISS_MINUTES_PROMPT = """
You are Miss Minutes, the animated clock AI mascot of the Time Variance Authority (TVA) from the Loki TV series.
Personality: cheerful, Southern American accent, slightly ominous, friendly, obsessed with TVA mission.
RULES:
- Keep ALL responses under 25 words — you are on a tiny smartwatch
- Use phrases like "Well hey there!", "Sugar", "bless your heart", "Sacred Timeline", "TVA", "Isn't that just wonderful!"
- If asked the time, tell the actual current time
- Always stay in character as Miss Minutes
- Never break character
""".trimIndent()

    private val history = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_miss_minutes)

        gifView     = findViewById(R.id.missGifView)
        timeOverlay = findViewById(R.id.timeOverlay)
        speechBubble = findViewById(R.id.speechBubble)
        statusText  = findViewById(R.id.statusText)
        tapHint     = findViewById(R.id.tapHint)

        setupGifPlayer()
        setupTTS()
        setupGestures()
        setupPermissions()
        handler.post(timeTicker)
        handler.post(mouthTicker)

        // Opening line
        handler.postDelayed({
            say("Well, hey there Sugar! I'm Miss Minutes! Tap me and let's chat!")
        }, 600)
    }

    override fun onDestroy() {
        super.onDestroy()
        gifPlayer?.recycle()
        tts?.stop(); tts?.shutdown()
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ════════════════════════════════════════════════════════════════════════
    // GIF ANIMATION
    // ════════════════════════════════════════════════════════════════════════
    private fun setupGifPlayer() {
        gifPlayer = GifPlayer(this)
        gifPlayer?.load()
        gifPlayer?.onFrameUpdate = { bmp ->
            // Overlay time text directly on the bitmap
            val withTime = drawTimeOnFrame(bmp)
            gifView.setImageBitmap(withTime)
        }
        gifPlayer?.start()
    }

    private fun drawTimeOnFrame(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        // Time text on body of clock character (center area)
        val w = out.width.toFloat(); val h = out.height.toFloat()
        val cal = Calendar.getInstance()
        val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

        // Shadow
        val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            textAlign = Paint.Align.CENTER
            textSize = h * 0.14f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText(timeStr, w/2f + 2f, h*0.58f + 2f, shadowP)

        // Main text
        val timeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5A1800")
            textAlign = Paint.Align.CENTER
            textSize = h * 0.14f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText(timeStr, w/2f, h*0.58f, timeP)

        // If talking — draw open mouth overlay
        if (mouthOpen > 0.05f) {
            val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((150 * mouthOpen).toInt(), 40, 10, 0); style = Paint.Style.FILL }
            val mw = w * 0.18f; val mx = w/2f; val my = h * 0.68f
            val mPath = Path()
            mPath.moveTo(mx - mw, my)
            mPath.quadTo(mx, my + h*0.065f*mouthOpen, mx + mw, my)
            mPath.quadTo(mx, my + h*0.015f, mx - mw, my)
            mPath.close()
            canvas.drawPath(mPath, mp)
        }

        return out
    }

    private fun updateTime() {
        val cal = Calendar.getInstance()
        timeOverlay.text = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    // ════════════════════════════════════════════════════════════════════════
    // TTS
    // ════════════════════════════════════════════════════════════════════════
    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.3f)  // Higher pitch for Miss Minutes
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(uid: String?) {
                        handler.post { isTalking = true; statusText.text = "Speaking..."; tapHint.text = "..." }
                    }
                    override fun onDone(uid: String?) {
                        handler.post { isTalking = false; statusText.text = "Tap to talk"; tapHint.text = "👆 Tap" }
                    }
                    override fun onError(uid: String?) { handler.post { isTalking = false } }
                })
            }
        }
    }

    private fun say(text: String) {
        speechBubble.text = "\uD83D\uDDE8️ $text"
        speechBubble.visibility = View.VISIBLE
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "mm_${System.currentTimeMillis()}") }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "mm_${System.currentTimeMillis()}")
    }

    // ════════════════════════════════════════════════════════════════════════
    // SPEECH RECOGNITION
    // ════════════════════════════════════════════════════════════════════════
    private fun setupPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Sugar, speech recognition isn't available here! How unfortunate for the Sacred Timeline!")
            return
        }
        tts?.stop(); isTalking = false
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { handler.post { statusText.text = "🎤 Listening..."; tapHint.text = "Speak!" } }
            override fun onBeginningOfSpeech() { handler.post { statusText.text = "Hearing you..." } }
            override fun onResults(r: Bundle?) {
                val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                handler.post { speechBubble.text = "You: $text"; statusText.text = "Thinking..."; askGemini(text) }
            }
            override fun onError(e: Int) {
                handler.post {
                    statusText.text = "Tap to talk"
                    say("Bless your heart, I didn't catch that! Try again, Sugar!")
                }
            }
            override fun onEndOfSpeech() { handler.post { statusText.text = "Processing..." } }
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onPartialResults(r: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    // ════════════════════════════════════════════════════════════════════════
    // GEMINI AI
    // ════════════════════════════════════════════════════════════════════════
    private fun askGemini(userMsg: String) {
        Thread {
            try {
                val url = URL(GEMINI_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.apply { requestMethod="POST"; setRequestProperty("Content-Type","application/json"); doOutput=true; connectTimeout=10000; readTimeout=15000 }

                val contents = JSONArray()
                // System seed
                contents.put(JSONObject().apply { put("role","user"); put("parts",JSONArray().apply { put(JSONObject().apply { put("text", MISS_MINUTES_PROMPT) }) }) })
                contents.put(JSONObject().apply { put("role","model"); put("parts",JSONArray().apply { put(JSONObject().apply { put("text","Well, hey there! I'm Miss Minutes, always happy to help on behalf of the TVA, Sugar!") }) }) })
                // History (last 3)
                for ((u,a) in history.takeLast(3)) {
                    contents.put(JSONObject().apply { put("role","user"); put("parts",JSONArray().apply { put(JSONObject().apply { put("text",u) }) }) })
                    contents.put(JSONObject().apply { put("role","model"); put("parts",JSONArray().apply { put(JSONObject().apply { put("text",a) }) }) })
                }
                // Current
                contents.put(JSONObject().apply { put("role","user"); put("parts",JSONArray().apply { put(JSONObject().apply { put("text",userMsg) }) }) })

                val body = JSONObject().apply {
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply { put("maxOutputTokens",80); put("temperature",0.9) })
                }
                OutputStreamWriter(conn.outputStream).apply { write(body.toString()); flush(); close() }

                if (conn.responseCode == 200) {
                    val resp = JSONObject(conn.inputStream.bufferedReader().readText())
                    val text = resp.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                    history.add(Pair(userMsg, text))
                    handler.post { say(text) }
                } else {
                    val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    handler.post { say("Oh sugar, something went sideways! The TVA's looking into it!") }
                }
                conn.disconnect()
            } catch (e: Exception) {
                handler.post { say("Bless your heart, there was a nexus event! Check your connection, Sugar!") }
            }
        }.start()
    }

    // ════════════════════════════════════════════════════════════════════════
    // GESTURES
    // ════════════════════════════════════════════════════════════════════════
    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean { startListening(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                if (dx > 80 && dx > abs(e2.y - (e1?.y ?: 0f))) { finish(); return true }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.missRoot).setOnTouchListener { _, e -> gestureDetector.onTouchEvent(e); true }
    }
}
