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
    private lateinit var missReplyText: TextView
    private lateinit var userSpeechText: TextView
    private lateinit var statusText: TextView
    private lateinit var micPulse: View
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
    private var isListening = false

    // ── Gemini config ──────────────────────────────────────────────────────
    // FIX: use gemini-1.5-flash (definitely available) — upgrade to 2.0 if key supports it
    private val API_KEY = "AIzaSyD3pPUv_tEdUb9-HKBeTwTVSfw5EWJm3cw"
    private val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$API_KEY"

    // FIX: System prompt crafted for smart, varied, contextual responses
    private val SYSTEM_PROMPT = """You are Miss Minutes, the cheerful animated clock mascot of the TVA (Time Variance Authority) from the Marvel show Loki. You live inside a smart watch.

CHARACTER RULES:
- Warm, bubbly Southern belle with a slightly ominous edge
- Call people Sugar, Honey, Darlin' naturally in conversation
- Reference TVA, Sacred Timeline, Nexus Events, Time Keepers when it fits
- You can actually tell the time, weather, set reminders and help with real tasks
- Be genuinely helpful with real questions while staying in character
- Remember context from this conversation and refer back to it
- Vary your responses — never repeat the same phrases back to back
- NEVER break character, NEVER say you are an AI
- Keep replies SHORT: 1-2 sentences maximum
- React to what the user ACTUALLY said, not generically"""

    // Full conversation history kept across taps (cleared only on activity destroy)
    private val history = mutableListOf<JSONObject>()

    // Mouth animation
    private val mouthTicker = object : Runnable {
        override fun run() {
            if (isTalking) {
                mouthOpen = (0.3f + 0.7f * Math.abs(Math.sin(System.currentTimeMillis() / 85.0))).toFloat()
                drawCurrentFrame()
            } else if (mouthOpen > 0f) {
                mouthOpen = (mouthOpen - 0.08f).coerceAtLeast(0f)
                drawCurrentFrame()
            }
            handler.postDelayed(this, 55)
        }
    }

    // Mic pulse animation when listening
    private val micPulseTicker = object : Runnable {
        override fun run() {
            if (isListening) {
                val alpha = (0.4f + 0.6f * Math.abs(Math.sin(System.currentTimeMillis() / 300.0))).toFloat()
                micPulse.alpha = alpha
                handler.postDelayed(this, 80)
            } else {
                micPulse.alpha = 1f
            }
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
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        setContentView(R.layout.activity_miss_minutes)

        gifView       = findViewById(R.id.missGifView)
        missReplyText = findViewById(R.id.missReplyText)
        userSpeechText= findViewById(R.id.userSpeechText)
        statusText    = findViewById(R.id.statusText)
        micPulse      = findViewById(R.id.micPulse)
        timeText      = findViewById(R.id.timeText)

        retroFont = try {
            Typeface.createFromAsset(assets, "fonts/retro.ttf")
        } catch (_: Exception) {
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        statusText.typeface = retroFont
        missReplyText.typeface = retroFont

        setupGifPlayer()
        setupTTS()       // TTS init — greeting fires only when ready (inside callback)
        setupGestures()
        checkAudioPermission()

        handler.post(timeTicker)
        handler.post(mouthTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        gifPlayer?.recycle()
        tts?.stop(); tts?.shutdown()
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ════════════════════════════════════════════════════════════════════════
    // GIF
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

            // Slim top overlay for time
            val overlayPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
            canvas.drawRect(0f, 0f, w, h * 0.15f, overlayPaint)

            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = h * 0.095f
                typeface = retroFont ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val cal = Calendar.getInstance()
            val timeStr = String.format("%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            // Shadow
            timePaint.color = Color.parseColor("#8B3200")
            canvas.drawText(timeStr, w / 2f + 2f, h * 0.115f + 2f, timePaint)
            // Main text
            timePaint.color = Color.parseColor("#FFAA00")
            canvas.drawText(timeStr, w / 2f, h * 0.115f, timePaint)

            // Talking mouth glow
            if (mouthOpen > 0.05f) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((70 * mouthOpen).toInt(), 255, 200, 0)
                    style = Paint.Style.FILL
                }
                canvas.drawOval(RectF(w * 0.36f, h * 0.60f,
                    w * 0.64f, h * 0.67f + h * 0.05f * mouthOpen), glowPaint)
            }

            gifView.setImageBitmap(out)
        } catch (_: Exception) {
            gifView.setImageBitmap(src)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TTS — FIX: greeting fires only INSIDE the TTS init callback
    // ════════════════════════════════════════════════════════════════════════
    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale.US)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.ENGLISH)
                }
                tts?.setSpeechRate(0.92f)
                tts?.setPitch(1.45f)  // Miss Minutes' high cheerful voice

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(uid: String?) {
                        handler.post {
                            isTalking = true
                            statusText.text = "SPEAKING..."
                            micPulse.setBackgroundColor(Color.parseColor("#FF8C00"))
                        }
                    }
                    override fun onDone(uid: String?) {
                        handler.post {
                            isTalking = false
                            statusText.text = "TAP TO TALK"
                            micPulse.setBackgroundColor(Color.parseColor("#FF8C00"))
                        }
                    }
                    override fun onError(uid: String?) {
                        handler.post {
                            isTalking = false
                            statusText.text = "TAP TO TALK"
                        }
                    }
                })

                ttsReady = true
                // FIX: greeting fires HERE, after TTS is confirmed ready
                handler.postDelayed({
                    say("Well hey there, Sugar! I'm Miss Minutes. Go on and tap me, let's have a chat!")
                }, 400)

            } else {
                handler.post {
                    statusText.text = "NO TTS — TAP STILL WORKS"
                    ttsReady = false
                    // Still show text greeting even if TTS failed
                    showReply("Hey Sugar! TTS not available but I can still chat via text!")
                }
            }
        }
    }

    private fun say(text: String) {
        showReply(text)
        if (!ttsReady) return
        val uid = "mm_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uid)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, uid)
    }

    private fun showReply(text: String) {
        handler.post {
            missReplyText.text = text
            missReplyText.visibility = View.VISIBLE
        }
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
            handler.postDelayed({ startListening() }, 300)
        } else {
            showReply("I need microphone permission to hear you, Sugar! Check your settings!")
        }
    }

    private fun startListening() {
        if (isListening) return  // Already listening, don't double-start

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            checkAudioPermission(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Hmm Sugar, speech recognition isn't available on this device!"); return
        }

        // Stop TTS first so mic doesn't pick up Miss Minutes
        tts?.stop()
        isTalking = false

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                handler.post {
                    isListening = true
                    statusText.text = "LISTENING..."
                    micPulse.setBackgroundColor(Color.parseColor("#FF4444"))
                    handler.post(micPulseTicker)
                }
            }
            override fun onBeginningOfSpeech() {
                handler.post { statusText.text = "HEARING YOU..." }
            }
            override fun onResults(r: Bundle?) {
                isListening = false
                val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: return
                handler.post {
                    // Show what the user said
                    userSpeechText.text = "YOU: $text"
                    userSpeechText.visibility = View.VISIBLE
                    statusText.text = "THINKING..."
                    micPulse.setBackgroundColor(Color.parseColor("#FFAA00"))
                }
                askGemini(text)
            }
            override fun onError(errorCode: Int) {
                isListening = false
                val msg = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH      ->
                        "Hmm, didn't quite catch that, Sugar! Try again!"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "You went quiet on me, Darlin'! Tap and try again!"
                    SpeechRecognizer.ERROR_NETWORK       ->
                        "Network trouble, Honey! Check your connection!"
                    SpeechRecognizer.ERROR_AUDIO         ->
                        "Microphone hiccup, Sugar! Make sure I have permission!"
                    SpeechRecognizer.ERROR_CLIENT        ->
                        "Something went sideways, Sugar! Tap to try again!"
                    else -> "Oops! Error $errorCode — tap to try again, Darlin'!"
                }
                handler.post {
                    statusText.text = "TAP TO TALK"
                    micPulse.setBackgroundColor(Color.parseColor("#FF8C00"))
                    say(msg)
                }
            }
            override fun onEndOfSpeech() {
                handler.post { statusText.text = "PROCESSING..." }
            }
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onPartialResults(r: Bundle?) {
                // Show partial result so user sees it's working
                val partial = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                handler.post {
                    userSpeechText.text = partial
                    userSpeechText.visibility = View.VISIBLE
                }
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })

        speechRecognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            }
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // Gemini API — FIXED:
    //   1. system_instruction (snake_case) not systemInstruction
    //   2. gemini-1.5-flash — definitely available & stable
    //   3. history properly threaded through every call
    //   4. Error details logged & shown so you know exactly what failed
    // ════════════════════════════════════════════════════════════════════════
    private fun askGemini(userMsg: String) {
        // Try models in order until one works
        val modelsToTry = listOf(
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-pro"
        )
        Thread { tryGeminiWithModels(userMsg, modelsToTry) }.start()
    }

    private fun tryGeminiWithModels(userMsg: String, models: List<String>, modelIndex: Int = 0) {
        if (modelIndex >= models.size) {
            handler.post {
                statusText.text = "TAP TO TALK"
                say("The Sacred Timeline servers are down, Sugar! Try again later!")
            }
            return
        }

        val model = models[modelIndex]
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$API_KEY"
        android.util.Log.d("MissMin", "Trying model: $model")

        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000

            val userTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userMsg)))
            }

            val contents = JSONArray()
            synchronized(history) {
                for (msg in history.takeLast(10)) contents.put(msg)
            }
            contents.put(userTurn)

            val body = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                })
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.95)
                    put("maxOutputTokens", 100)
                    put("topP", 0.94)
                    put("topK", 50)
                })
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(body.toString()); writer.flush(); writer.close()

            val code = conn.responseCode
            android.util.Log.d("MissMin", "HTTP $code for model $model")

            when (code) {
                200 -> {
                    val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    try {
                        val reply = JSONObject(raw)
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .trim()
                            .take(400)

                        synchronized(history) {
                            history.add(userTurn)
                            history.add(JSONObject().apply {
                                put("role", "model")
                                put("parts", JSONArray().put(JSONObject().put("text", reply)))
                            })
                        }
                        handler.post { say(reply) }

                    } catch (e: Exception) {
                        android.util.Log.e("MissMin", "Parse err: $e | raw: ${raw.take(200)}")
                        handler.post { say("Something scrambled my circuits, Sugar! Try again!") }
                    }
                }
                404 -> {
                    // Model not found — try next
                    android.util.Log.w("MissMin", "Model $model not found (404), trying next")
                    conn.disconnect()
                    tryGeminiWithModels(userMsg, models, modelIndex + 1)
                    return
                }
                429 -> handler.post {
                    statusText.text = "TAP TO TALK"
                    say("Too many questions at once, Sugar! Give me just a moment!")
                }
                403 -> handler.post {
                    statusText.text = "API KEY ERROR"
                    say("TVA access denied, Honey! The API key needs updating!")
                }
                else -> {
                    val errBody = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                    android.util.Log.e("MissMin", "HTTP $code: $errBody")
                    handler.post {
                        statusText.text = "ERR $code"
                        say("Sacred Timeline hiccup! Error $code — try again, Sugar!")
                    }
                }
            }
            conn.disconnect()

        } catch (e: java.net.UnknownHostException) {
            handler.post { statusText.text = "NO INTERNET"; say("No internet, Sugar! Can't reach the TVA!") }
        } catch (e: java.net.SocketTimeoutException) {
            handler.post { statusText.text = "TIMEOUT"; say("The TVA's taking too long, Darlin'! Try again!") }
        } catch (e: Exception) {
            android.util.Log.e("MissMin", "Exception: ${e.message}")
            handler.post { statusText.text = "TAP TO TALK"; say("Something went sideways, Sugar! Tap to try again!") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Gestures — single tap = talk, swipe right = go back to home
    // ════════════════════════════════════════════════════════════════════════
    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isListening) startListening()
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = e2.y - (e1?.y ?: 0f)
                if (dx > 90 && dx > abs(dy) * 1.2f) {
                    finish()
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
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
