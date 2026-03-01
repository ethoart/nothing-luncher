package com.watchlauncher

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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

class MissMinutesActivity : AppCompatActivity() {

    private lateinit var clockView: ClockView
    private lateinit var speechBubble: TextView
    private lateinit var statusText: TextView
    private lateinit var gestureDetector: GestureDetectorCompat

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    // ── Replace with your actual Gemini API key ─────────────────────────────
    private val GEMINI_API_KEY = "AIzaSyD3pPUv_tEdUb9-HKBeTwTVSfw5EWJm3cw"
    private val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY"

    private val MISS_MINUTES_SYSTEM = """
You are Miss Minutes, the animated AI mascot of the Time Variance Authority (TVA) from the Loki TV series on Disney+.
You speak in a warm, Southern American accent and are cheerful, slightly ominous but friendly. 
You refer to the TVA mission, Sacred Timeline, and the "He Who Remains". 
Keep responses SHORT — under 3 sentences — since you're on a tiny smartwatch screen.
Use phrases like "Well, hey there!", "Sugar", "Isn't that just the bee's knees!", "sacred timeline", "TVA".
You can also tell the time when asked. Always stay in character as Miss Minutes.
""".trimIndent()

    private val conversationHistory = mutableListOf<Pair<String, String>>() // user, assistant

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_miss_minutes)

        clockView   = findViewById(R.id.missClockView)
        speechBubble = findViewById(R.id.speechBubble)
        statusText  = findViewById(R.id.statusText)

        clockView.style = WatchFaceStyle.MISS_MINUTES

        setupTTS()
        setupGestures()

        // Greeting on open
        handler.postDelayed({
            speak("Well, hey there, Sugar! I'm Miss Minutes! Tap me to chat, or ask about the Sacred Timeline!")
        }, 800)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop(); tts?.shutdown()
        speechRecognizer?.destroy()
    }

    // ════════════════════════════════════════════════════════════════════════
    // TEXT TO SPEECH
    // ════════════════════════════════════════════════════════════════════════
    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Slightly higher pitch for Miss Minutes' cheerful voice
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.25f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        handler.post {
                            clockView.isTalking = true
                            statusText.text = "Miss Minutes is speaking..."
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            clockView.isTalking = false
                            statusText.text = "Tap to talk"
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        handler.post { clockView.isTalking = false }
                    }
                })
            }
        }
    }

    private fun speak(text: String) {
        speechBubble.text = text
        speechBubble.visibility = View.VISIBLE
        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "mm_${System.currentTimeMillis()}")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "mm_${System.currentTimeMillis()}")
    }

    // ════════════════════════════════════════════════════════════════════════
    // SPEECH RECOGNITION
    // ════════════════════════════════════════════════════════════════════════
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Sugar, speech recognition isn't available on this device! Try typing instead!")
            return
        }

        tts?.stop()
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                handler.post { statusText.text = "Listening... speak now!" }
            }
            override fun onBeginningOfSpeech() {
                handler.post { statusText.text = "I hear ya, Sugar!" }
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                handler.post {
                    statusText.text = "You said: $text"
                    speechBubble.text = "You: $text"
                    sendToGemini(text)
                }
            }
            override fun onError(error: Int) {
                handler.post {
                    statusText.text = "Tap to talk"
                    speak("Oh bless your heart, I didn't quite catch that! Try again, Sugar!")
                }
            }
            override fun onEndOfSpeech() { handler.post { statusText.text = "Processing..." } }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Miss Minutes...")
        }
        speechRecognizer?.startListening(intent)
    }

    // ════════════════════════════════════════════════════════════════════════
    // GEMINI AI
    // ════════════════════════════════════════════════════════════════════════
    private fun sendToGemini(userMessage: String) {
        if (GEMINI_API_KEY == "YOUR_GEMINI_API_KEY_HERE") {
            // Demo mode — Miss Minutes responds without real API
            val demoResponses = listOf(
                "Well, hey there, Sugar! The TVA's got everything under control! Isn't that just the bee's knees?",
                "Oh my stars, what a great question! The Sacred Timeline thanks you for your cooperation!",
                "Bless your heart! Every moment is precious on the Sacred Timeline, Sugar!",
                "Well now, that's quite the variant question! He Who Remains would be so proud of you!",
                "Don't you worry your pretty little head! Miss Minutes has got all the answers you need!"
            )
            val response = demoResponses[(userMessage.length) % demoResponses.size]
            conversationHistory.add(Pair(userMessage, response))
            handler.post { speak(response) }
            return
        }

        statusText.text = "Miss Minutes is thinking..."

        Thread {
            try {
                val url = URL(GEMINI_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 15000

                // Build conversation with history
                val contentsArray = JSONArray()

                // System instruction as first user turn
                val sysObj = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply { put(JSONObject().apply { put("text", MISS_MINUTES_SYSTEM) }) })
                }
                contentsArray.put(sysObj)
                val sysResp = JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply { put(JSONObject().apply { put("text", "Well, hey there! I'm Miss Minutes, the TVA's most helpful little assistant! How can I help you today, Sugar?") }) })
                }
                contentsArray.put(sysResp)

                // Add history
                for ((u, a) in conversationHistory.takeLast(4)) {
                    contentsArray.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply { put(JSONObject().apply { put("text", u) }) })
                    })
                    contentsArray.put(JSONObject().apply {
                        put("role", "model")
                        put("parts", JSONArray().apply { put(JSONObject().apply { put("text", a) }) })
                    })
                }

                // Current message
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply { put(JSONObject().apply { put("text", userMessage) }) })
                })

                val body = JSONObject().apply {
                    put("contents", contentsArray)
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 120)
                        put("temperature", 0.8)
                    })
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString()); writer.flush(); writer.close()

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val text = json.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0).getString("text")

                    conversationHistory.add(Pair(userMessage, text))
                    handler.post { speak(text) }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    handler.post { speak("Oh sugar, something went wrong with the Sacred Timeline! Error: $responseCode") }
                }
                conn.disconnect()
            } catch (e: Exception) {
                handler.post { speak("Bless your heart, there was a nexus event! Check your network connection, Sugar!") }
            }
        }.start()
    }

    // ════════════════════════════════════════════════════════════════════════
    // GESTURES
    // ════════════════════════════════════════════════════════════════════════
    private fun setupGestures() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                startListening(); return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dX = e2.x - (e1?.x ?: 0f)
                if (dX > 80 && dX > abs(e2.y - (e1?.y ?: 0f))) {
                    finish(); overridePendingTransition(R.anim.fade_in, R.anim.slide_right); return true
                }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.missRoot).setOnTouchListener { _, e -> gestureDetector.onTouchEvent(e); true }
    }
}
