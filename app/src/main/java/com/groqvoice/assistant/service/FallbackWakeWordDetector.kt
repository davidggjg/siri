package com.groqvoice.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*

// Fallback למקרה ש-VOSK לא עובד
class FallbackWakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit
) {
    private var isRunning = false
    private var isPaused = false
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val wakeWords = listOf("קאי", "kai", "היי קאי", "hey kai", "כאי")

    fun start() {
        isRunning = true
        listen()
    }

    private fun listen() {
        if (!isRunning || isPaused) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
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
                scope.launch {
                    delay(500)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    fun pause() {
        isPaused = true
        recognizer?.destroy()
        recognizer = null
    }

    fun resume() {
        isPaused = false
        if (isRunning) listen()
    }

    fun stop() {
        isRunning = false
        recognizer?.destroy()
        recognizer = null
        scope.cancel()
    }
}
