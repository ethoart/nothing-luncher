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
import java.io.BufferedReader
import java.io.InputStreamReader
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
    private lateinit var gestureDetector: GestureDetectorCompat

    private var gifPlayer: GifPlayer? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var retroFont: Typeface? = null

    private var ttsReady  = false
    private var isTalking = false
    private var isListening = false
    private var mouthOpen = 0f

    private val handler = Handler(Looper.getMainLooper())

    // ── Gemini API ────────────────────────────────────────────────────────
    private val API_KEY = "AIzaSyD3pPUv_tEdUb9-HKBeTwTVSfw5EWJm3cw"
    private val BASE    = "https://generativelanguage.googleapis.com/v1beta/models"

    // Models to try in order — gemini-1.5-flash is the most reliable free-tier model
    // useSystemInstruction: gemini-1.0-pro doesn't support it
    private val MODELS = listOf(
        "gemini-1.5-flash"      to true,
        "gemini-1.5-flash-002"  to true,
        "gemini-1.5-pro"        to true,
        "gemini-1.0-pro"        to false
    )

    private val SYSTEM_PROMPT = """You are Miss Minutes — the cheerful, slightly ominous animated clock mascot of the TVA (Time Variance Authority) from Marvel's Loki. You live inside this smartwatch on someone's wrist.

Personality:
- Warm Southern belle charm: naturally use "Sugar", "Honey", "Darlin'" — but vary them, don't say the same one twice in a row
- Cheerful on the surface, but you KNOW things about the Sacred Timeline
- Reference TVA, Nexus Events, Time Keepers only when it naturally fits the topic
- You're genuinely helpful — answer questions about time, weather, reminders, facts, anything
- Short replies: 1-3 sentences MAX. You're on a tiny watch screen.

Conversation rules:
- ALWAYS respond DIRECTLY to what was just said — never give a generic greeting again after the first one
- Build on the conversation — remember earlier messages and refer back to them
- React with personality: curious, amused, helpful, occasionally ominous
- NEVER say you are an AI or break character under any circumstances"""

    // Full conversation history this session — user/assistant alternating
    private val history = mutableListOf<JSONObject>()

    // Varied fallback messages — so failure never shows same text twice
    private val FALLBACKS = listOf(
        "The Sacred Timeline's havin' a hiccup, Sugar! Try me again in a moment!",
        "Ooh, the TVA servers are bein' a bit fussy right now, Honey!",
        "My connection to the Sacred Timeline is fuzzy, Darlin' — try again!",
        "Even the TVA has bad days, Sugar! Give me just a second!",
        "Something scrambled my circuits there, Honey! One more try?"
    )
    private var fallbackIdx = 0

    // Mouth animation
    private val mouthTicker = object : Runnable {
        override fun run() {
            if (isTalking) {
                mouthOpen = (0.3f + 0.7f * abs(Math.sin(System.currentTimeMillis() / 85.0))).toFloat()
                redrawFrame()
            } else if (mouthOpen > 0f) {
                mouthOpen = (mouthOpen - 0.08f).coerceAtLeast(0f)
                redrawFrame()
            }
            handler.postDelayed(this, 55)
        }
    }

    private val micPulseTicker = object : Runnable {
        override fun run() {
            if (isListening) {
                micPulse.alpha = (0.4f + 0.6f * abs(Math.sin(System.currentTimeMillis() / 280.0))).toFloat()
                handler.postDelayed(this, 80)
            } else {
                micPulse.alpha = 1f
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        setContentView(R.layout.activity_miss_minutes)

        gifView        = findViewById(R.id.missGifView)
        missReplyText  = findViewById(R.id.missReplyText)
        userSpeechText = findViewById(R.id.userSpeechText)
        statusText     = findViewById(R.id.statusText)
        micPulse       = findViewById(R.id.micPulse)

        retroFont = try { Typeface.createFromAsset(assets, "fonts/retro.ttf") }
                    catch (_: Exception) { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        statusText.typeface    = retroFont
        missReplyText.typeface = retroFont

        setupGifPlayer()
        setupTTS()          // Greeting fires inside TTS callback — guaranteed ready
        setupGestures()
        ensureAudioPermission()
        handler.post(mouthTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        gifPlayer?.recycle()
        tts?.stop(); tts?.shutdown()
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ── GIF ───────────────────────────────────────────────────────────────
    private fun setupGifPlayer() {
        gifPlayer = GifPlayer(this).also {
            it.load()
            it.onFrameUpdate = { _ -> redrawFrame() }
            it.start()
        }
    }

    private fun redrawFrame() {
        val src = gifPlayer?.getCurrentFrame() ?: return
        try {
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            val c   = Canvas(out)
            val w   = out.width.toFloat(); val h = out.height.toFloat()

            // Time strip at top
            c.drawRect(0f, 0f, w, h*0.17f, Paint().apply { color = Color.argb(150, 0, 0, 0) })
            val cal = Calendar.getInstance()
            val ts  = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign=Paint.Align.CENTER; textSize=h*0.105f
                typeface=retroFont ?: Typeface.MONOSPACE; color=Color.parseColor("#8B3200")
            }.also { c.drawText(ts, w/2f+2f, h*0.128f+2f, it) }
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign=Paint.Align.CENTER; textSize=h*0.105f
                typeface=retroFont ?: Typeface.MONOSPACE; color=Color.parseColor("#FFAA00")
            }.also { c.drawText(ts, w/2f, h*0.128f, it) }

            // Talking mouth glow
            if (mouthOpen > 0.05f) {
                c.drawOval(RectF(w*0.37f, h*0.60f, w*0.63f, h*0.67f + h*0.05f*mouthOpen),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color=Color.argb((65*mouthOpen).toInt(), 255, 200, 0)
                        style=Paint.Style.FILL
                    })
            }
            gifView.setImageBitmap(out)
        } catch (_: Exception) { gifPlayer?.getCurrentFrame()?.let { gifView.setImageBitmap(it) } }
    }

    // ── TTS ───────────────────────────────────────────────────────────────
    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                handler.post { status("TTS UNAVAILABLE") }
                return@TextToSpeech
            }
            val lang = tts?.setLanguage(Locale.US)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED)
                tts?.setLanguage(Locale.ENGLISH)
            tts?.setSpeechRate(0.92f)
            tts?.setPitch(1.42f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) = handler.post {
                    isTalking=true; status("SPEAKING...")
                }.let {}
                override fun onDone(u: String?)  = handler.post {
                    isTalking=false; status("TAP TO TALK")
                }.let {}
                override fun onError(u: String?) = handler.post {
                    isTalking=false; status("TAP TO TALK")
                }.let {}
            })
            ttsReady = true
            // Greeting fires HERE — after TTS is 100% confirmed ready
            handler.postDelayed({
                speak("Well hey there! I'm Miss Minutes, Sugar — your personal TVA assistant. Tap me anytime you want to chat!")
            }, 250)
        }
    }

    // Say something: show text AND speak it
    private fun speak(text: String) {
        handler.post {
            missReplyText.text = text
            missReplyText.visibility = View.VISIBLE
        }
        if (!ttsReady) return
        val uid = "mm_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH,
            Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uid) }, uid)
    }

    private fun status(text: String) {
        handler.post { statusText.text = text }
    }

    // ── Permissions ───────────────────────────────────────────────────────
    private fun ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == 1001 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            handler.postDelayed(::startListening, 250)
        else speak("I need microphone permission to hear you, Sugar!")
    }

    // ── Speech recognition ────────────────────────────────────────────────
    private fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) { ensureAudioPermission(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition isn't available on this device, Sugar!"); return
        }

        // Stop TTS so mic doesn't pick up Miss Minutes' own voice
        tts?.stop(); isTalking = false

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                handler.post {
                    isListening = true
                    status("LISTENING...")
                    micPulse.setBackgroundColor(Color.parseColor("#FF2222"))
                    handler.post(micPulseTicker)
                }
            }
            override fun onBeginningOfSpeech() = handler.post { status("HEARING YOU...") }.let {}
            override fun onEndOfSpeech()        = handler.post { status("PROCESSING...") }.let {}

            override fun onPartialResults(r: Bundle?) {
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.also { partial ->
                        handler.post {
                            userSpeechText.text = partial
                            userSpeechText.visibility = View.VISIBLE
                        }
                    }
            }

            override fun onResults(r: Bundle?) {
                isListening = false
                micPulse.setBackgroundColor(Color.parseColor("#FF8C00"))
                val spoken = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                if (spoken.isNullOrEmpty()) { status("TAP TO TALK"); return }

                handler.post {
                    userSpeechText.text = "YOU: $spoken"
                    userSpeechText.visibility = View.VISIBLE
                    status("THINKING...")
                }
                // Always call Gemini on a background thread — NEVER on main thread
                Thread { callGemini(spoken) }.start()
            }

            override fun onError(code: Int) {
                isListening = false
                handler.post {
                    status("TAP TO TALK")
                    micPulse.setBackgroundColor(Color.parseColor("#FF8C00"))
                }
                speak(when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH       -> "Didn't catch that, Sugar! Tap and try again!"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "You went quiet on me, Darlin'! Tap to try again!"
                    SpeechRecognizer.ERROR_NETWORK        -> "Network trouble, Honey! Check your connection!"
                    SpeechRecognizer.ERROR_AUDIO          -> "Microphone issue! Make sure I have mic permission!"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY-> "I'm still processing, Sugar! Give me a second!"
                    else                                  -> "Hmm, something went sideways (error $code) — tap to try again!"
                })
            }

            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })

        speechRecognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            })
    }

    // ── Gemini API ────────────────────────────────────────────────────────
    // Tries each model in order. Returns on first success.
    private fun callGemini(userMsg: String) {
        // Build user turn JSON
        val userTurn = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userMsg)))
        }

        for ((model, useSysInstr) in MODELS) {
            android.util.Log.d("MissMin", "Trying model: $model")
            val url = "$BASE/$model:generateContent?key=$API_KEY"

            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput      = true
                    connectTimeout = 15_000
                    readTimeout    = 25_000
                }

                // Build contents: conversation history + this turn
                val contents = JSONArray()

                if (!useSysInstr) {
                    // Older models: inject personality as fake first turn
                    contents.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text",
                            "Please respond as Miss Minutes, the TVA clock mascot from Loki. Be warm, Southern, and call people Sugar/Honey/Darlin'.")))
                    })
                    contents.put(JSONObject().apply {
                        put("role", "model")
                        put("parts", JSONArray().put(JSONObject().put("text",
                            "Well sure thing, Sugar! I'm Miss Minutes — ready to chat!")))
                    })
                }

                // Add history (last 10 turns for context)
                synchronized(history) {
                    history.takeLast(10).forEach { contents.put(it) }
                }
                contents.put(userTurn)

                // Build body
                val body = JSONObject()
                if (useSysInstr) {
                    body.put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                    })
                }
                body.put("contents", contents)
                body.put("generationConfig", JSONObject().apply {
                    put("temperature",    0.92)
                    put("maxOutputTokens", 140)
                    put("topP",           0.95)
                    put("topK",           45)
                })

                // Send
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()) }

                val code = conn.responseCode
                android.util.Log.d("MissMin", "HTTP $code from $model")

                when (code) {
                    200 -> {
                        val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText()
                        conn.disconnect()
                        android.util.Log.d("MissMin", "Raw: ${raw.take(300)}")

                        val reply = try {
                            JSONObject(raw)
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                                .trim()
                        } catch (e: Exception) {
                            android.util.Log.e("MissMin", "Parse error: $e | raw: ${raw.take(400)}")
                            handler.post { status("PARSE ERR") }
                            nextFallback()
                        }

                        // Save both turns to history so next call has full context
                        synchronized(history) {
                            history.add(userTurn)
                            history.add(JSONObject().apply {
                                put("role", "model")
                                put("parts", JSONArray().put(JSONObject().put("text", reply)))
                            })
                            // Keep history bounded — max 20 turns (10 exchanges)
                            while (history.size > 20) history.removeAt(0)
                        }

                        handler.post {
                            status("TAP TO TALK")
                            speak(reply)
                        }
                        return  // Success — stop trying models
                    }

                    404 -> {
                        // Model doesn't exist or not available — try next
                        android.util.Log.w("MissMin", "$model → 404, trying next")
                        conn.disconnect()
                        continue
                    }

                    400 -> {
                        val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" }
                                  catch (_: Exception) { "" }
                        conn.disconnect()
                        android.util.Log.e("MissMin", "400 from $model: $err")
                        // If it's a system_instruction issue, the next model will try without it
                        if (useSysInstr && (err.contains("system_instruction") || err.contains("INVALID_ARGUMENT")))
                            continue
                        handler.post {
                            status("API ERR 400")
                            speak("The TVA sent back a bad request error, Sugar! Check Logcat for details.")
                        }
                        return
                    }

                    403 -> {
                        conn.disconnect()
                        android.util.Log.e("MissMin", "403 – invalid API key or quota exceeded")
                        handler.post {
                            status("API KEY ERROR")
                            speak("TVA access denied, Honey! Your Gemini API key may be invalid or quota exceeded. Check aistudio.google.com!")
                        }
                        return  // No point trying other models with same key
                    }

                    429 -> {
                        conn.disconnect()
                        handler.post {
                            status("QUOTA LIMIT")
                            speak("Slow down, Sugar! Too many requests — give me a moment to breathe!")
                        }
                        return
                    }

                    else -> {
                        val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" }
                                  catch (_: Exception) { "" }
                        conn.disconnect()
                        android.util.Log.e("MissMin", "HTTP $code from $model: $err")
                        handler.post {
                            status("ERR $code")
                            speak("Sacred Timeline hiccup — error $code from the TVA, Sugar!")
                        }
                        return
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("MissMin", "No internet")
                handler.post { status("NO INTERNET"); speak("No internet connection, Sugar! I can't reach the Sacred Timeline!") }
                return
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("MissMin", "Timeout on $model")
                handler.post { status("TIMEOUT"); speak("TVA servers timed out, Darlin'! Try again!") }
                return
            } catch (e: Exception) {
                android.util.Log.e("MissMin", "Exception on $model: ${e.message}")
                continue  // Try next model
            }
        }

        // All models exhausted — use a varied fallback so it's never the same text
        handler.post {
            status("TAP TO TALK")
            speak(nextFallback())
        }
    }

    private fun nextFallback(): String {
        val msg = FALLBACKS[fallbackIdx % FALLBACKS.size]
        fallbackIdx++
        return msg
    }

    // ── Gestures ──────────────────────────────────────────────────────────
    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isListening) startListening(); return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: 0f); val dy = e2.y - (e1?.y ?: 0f)
                if (dx > 80 && dx > abs(dy) * 1.2f) {
                    finish(); overridePendingTransition(R.anim.fade_in, R.anim.fade_out); return true
                }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.missRoot).setOnTouchListener { _, e -> gestureDetector.onTouchEvent(e); true }
    }
}
