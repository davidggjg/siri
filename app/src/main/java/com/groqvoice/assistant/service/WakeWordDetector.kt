package com.groqvoice.assistant.service

import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.*
import kotlinx.coroutines.*

class WakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit
) {
    private var isRunning = false
    private var isPaused = false
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wakeWords = listOf("קאי", "kai", "היי קאי", "hey kai", "כאי", "קיי", "כי")

    // AudioFocus - משחרר מיק לאפליקציות אחרות מיד
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // אפליקציה אחרת צריכה מיק - עצור מיד ושחרר
                    releaseRecognizer()
                    scope.launch {
                        delay(4000) // המתן שהאפליקציה האחרת תשחרר
                        if (isRunning && !isPaused) startListen()
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (isRunning && !isPaused) scope.launch {
                        delay(500)
                        startListen()
                    }
                }
            }
        }
        .build()

    fun start() {
        isRunning = true
        startListen()
    }

    private fun startListen() {
        if (!isRunning || isPaused) return

        // בדוק אם יש AudioFocus זמין
        val result = audioManager.requestAudioFocus(focusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // מישהו אחר מחזיק - נסה שוב
            scope.launch {
                delay(3000)
                if (isRunning && !isPaused) startListen()
            }
            return
        }

        releaseRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                // שחרר AudioFocus מיד!
                audioManager.abandonAudioFocusRequest(focusRequest)

                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""

                if (wakeWords.any { heard.contains(it) }) {
                    releaseRecognizer()
                    onDetected()
                    // חזור להאזנה אחרי שקאי סיים לדבר
                    scope.launch {
                        delay(8000)
                        if (isRunning && !isPaused) startListen()
                    }
                } else {
                    scope.launch {
                        delay(200)
                        if (isRunning && !isPaused) startListen()
                    }
                }
            }

            override fun onError(error: Int) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                scope.launch {
                    val delay = if (error == SpeechRecognizer.ERROR_NO_MATCH) 200L else 1000L
                    delay(delay)
                    if (isRunning && !isPaused) startListen()
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
        recognizer?.startListening(intent)
    }

    private fun releaseRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    fun pause() {
        isPaused = true
        audioManager.abandonAudioFocusRequest(focusRequest)
        releaseRecognizer()
    }

    fun resume() {
        isPaused = false
        scope.launch {
            delay(500)
            if (isRunning) startListen()
        }
    }

    fun stop() {
        isRunning = false
        audioManager.abandonAudioFocusRequest(focusRequest)
        releaseRecognizer()
        scope.cancel()
    }
}
