package com.groqvoice.assistant.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*

class WakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit
) {
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val wakeWords = listOf(
        "קאי", "kai", "היי קאי", "hey kai"
    )

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 4

    fun start() {
        isRunning = true
        scope.launch { listenLoop() }
    }

    private suspend fun listenLoop() {
        while (isRunning) {
            try {
                detectWithSpeechRecognizer()
            } catch (e: Exception) {
                delay(1000)
            }
        }
    }

    private suspend fun detectWithSpeechRecognizer() {
        withContext(Dispatchers.Main) {
            val recognizer = android.speech.SpeechRecognizer
                .createSpeechRecognizer(context)

            val intent = android.content.Intent(
                android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }

            val deferred = CompletableDeferred<String?>()

            recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(
                        android.speech.SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    deferred.complete(matches?.firstOrNull())
                    recognizer.destroy()
                }
                override fun onError(error: Int) {
                    deferred.complete(null)
                    recognizer.destroy()
                }
                override fun onReadyForSpeech(p: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: android.os.Bundle?) {}
                override fun onEvent(t: Int, b: android.os.Bundle?) {}
            })

            recognizer.startListening(intent)
            val heard = deferred.await()?.lowercase() ?: ""

            if (wakeWords.any { heard.contains(it) }) {
                withContext(Dispatchers.Main) { onDetected() }
            }
        }
        delay(300)
    }

    fun stop() {
        isRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        scope.cancel()
    }
}
