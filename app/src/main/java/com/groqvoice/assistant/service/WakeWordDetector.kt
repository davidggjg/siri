package com.groqvoice.assistant.service

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.*
import android.content.Intent
import kotlinx.coroutines.*

class WakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit
) {
    private var isRunning = false
    private var isPaused = false  // מושהה כשאפליקציה אחרת צריכה מיק
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val wakeWords = listOf("קאי", "kai", "היי קאי", "hey kai", "כאי")

    // AudioFocus - מאזין מתי אפליקציה אחרת צריכה את המיק
    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // אפליקציה אחרת צריכה מיק - עצור האזנה
                isPaused = true
                stopListening()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // חזרנו - המשך האזנה
                if (isRunning) {
                    isPaused = false
                    scope.launch {
                        delay(500)
                        startListening()
                    }
                }
            }
        }
    }

    fun start() {
        isRunning = true
        startListening()
    }

    private fun startListening() {
        if (!isRunning || isPaused) return

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val heard = matches?.firstOrNull()?.lowercase() ?: ""

                if (wakeWords.any { heard.contains(it) }) {
                    // זוהה wake word - שחרר מיק לפני שמתחיל לעבד
                    stopListening()
                    onDetected()
                    // חזור להאזין אחרי שסיים לדבר
                    scope.launch {
                        delay(7000)
                        if (isRunning && !isPaused) startListening()
                    }
                } else {
                    // המשך להאזין
                    scope.launch {
                        delay(200)
                        if (isRunning && !isPaused) startListening()
                    }
                }
            }

            override fun onError(error: Int) {
                scope.launch {
                    delay(500)
                    if (isRunning && !isPaused) startListening()
                }
            }

            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        recognizer?.startListening(intent)
    }

    private fun stopListening() {
        recognizer?.destroy()
        recognizer = null
    }

    // קרא לזה כשרוצים להשהות ידנית (למשל הקלטה ידנית בתוך האפליקציה)
    fun pause() {
        isPaused = true
        stopListening()
    }

    fun resume() {
        isPaused = false
        if (isRunning) startListening()
    }

    fun stop() {
        isRunning = false
        stopListening()
        scope.cancel()
    }
}
