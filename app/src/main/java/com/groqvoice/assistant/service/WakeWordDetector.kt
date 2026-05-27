package com.groqvoice.assistant.service

import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*

class WakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit
) {
    private var isRunning = false
    private var isPaused = false
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val wakeWords = listOf("קאי", "kai", "היי קאי", "hey kai", "כאי", "קיי")
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // AudioFocus - משחרר מיק כשאפליקציה אחרת צריכה אותו
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setOnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // אפליקציה אחרת צריכה מיק - עצור מיד
                    stopListening()
                    scope.launch {
                        delay(3000)
                        if (isRunning && !isPaused) listen()
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (isRunning && !isPaused) listen()
                }
            }
        }
        .build()

    fun start() {
        isRunning = true
        listen()
    }

    private fun listen() {
        if (!isRunning || isPaused) return

        // בקש AudioFocus - אם מישהו כבר מחזיק מיק, לא תתחיל
        val result = audioManager.requestAudioFocus(focusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // מישהו אחר מחזיק מיק - נסה שוב אחר כך
            scope.launch {
                delay(2000)
                if (isRunning && !isPaused) listen()
            }
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""

                // שחרר AudioFocus מיד אחרי שמיעה
                audioManager.abandonAudioFocusRequest(focusRequest)

                if (wakeWords.any { heard.contains(it) }) {
                    recognizer?.destroy()
                    recognizer = null
                    onDetected()
                    scope.launch {
                        delay(7000)
                        if (isRunning && !isPaused) listen()
                    }
                } else {
                    scope.launch {
                        delay(300)
                        if (isRunning && !isPaused) listen()
                    }
                }
            }

            override fun onError(error: Int) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                scope.launch {
                    delay(800)
                    if (isRunning && !isPaused) listen()
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        recognizer?.startListening(intent)
    }

    private fun stopListening() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        recognizer?.destroy()
        recognizer = null
    }

    fun pause() {
        isPaused = true
        stopListening()
    }

    fun resume() {
        isPaused = false
        if (isRunning) scope.launch {
            delay(500)
            listen()
        }
    }

    fun stop() {
        isRunning = false
        stopListening()
        scope.cancel()
    }
}
