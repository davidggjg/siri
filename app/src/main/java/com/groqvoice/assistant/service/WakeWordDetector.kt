package com.groqvoice.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*

class WakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isListening = false

    // מילות ההתעוררות - אפשר להוסיף עוד
    private val wakeWords = listOf(
        "היי עוזר", "הי עוזר", "עוזר", "hey assistant", "assistant"
    )

    fun start() {
        scope.launch {
            startListening()
        }
    }

    private fun startListening() {
        if (isListening) return
        isListening = true

        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val heard = matches?.firstOrNull()?.lowercase() ?: ""

                if (wakeWords.any { heard.contains(it) }) {
                    isListening = false
                    recognizer?.destroy()
                    recognizer = null
                    onDetected()
                    // המתן קצת ואז חזור להאזנה
                    scope.launch {
                        delay(6000)
                        startListening()
                    }
                } else {
                    // המשך להאזין
                    restartListening()
                }
            }

            override fun onError(error: Int) {
                restartListening()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer?.startListening(intent)
    }

    private fun restartListening() {
        isListening = false
        recognizer?.destroy()
        recognizer = null
        scope.launch {
            delay(500)
            startListening()
        }
    }

    fun stop() {
        isListening = false
        recognizer?.destroy()
        recognizer = null
        scope.cancel()
    }
}
