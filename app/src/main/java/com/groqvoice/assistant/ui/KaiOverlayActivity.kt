package com.groqvoice.assistant.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.groqvoice.assistant.R
import com.groqvoice.assistant.api.GroqApiClient
import com.groqvoice.assistant.audio.AudioRecorder
import com.groqvoice.assistant.commands.CommandExecutor
import com.groqvoice.assistant.service.VoiceService
import com.groqvoice.assistant.tts.TtsManager
import kotlinx.coroutines.*

class KaiOverlayActivity : Activity() {

    private lateinit var orbView: KaiOrbView
    private lateinit var tvTranscript: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStop: ImageButton

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var ttsManager: TtsManager
    private lateinit var groqClient: GroqApiClient
    private lateinit var commandExecutor: CommandExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        setContentView(R.layout.activity_kai_overlay)
        orbView = findViewById(R.id.kaiOrb)
        tvTranscript = findViewById(R.id.tvTranscript)
        tvStatus = findViewById(R.id.tvStatus)
        btnStop = findViewById(R.id.btnStop)

        audioRecorder = AudioRecorder(this)
        ttsManager = TtsManager(this)
        groqClient = GroqApiClient()
        commandExecutor = CommandExecutor(this)

        // כפתור עצור
        btnStop.setOnClickListener {
            ttsManager.stop()
            audioRecorder.cleanup()
            finish()
        }

        startService(Intent(this, VoiceService::class.java).apply {
            action = VoiceService.ACTION_PAUSE
        })

        startListening()
    }

    private fun startListening() {
        orbView.setState(KaiState.LISTENING)
        tvStatus.text = "מאזין..."
        tvTranscript.text = ""

        scope.launch {
            try {
                val audioFile = audioRecorder.startRecording()
                delay(5000)
                audioRecorder.stopRecording()

                orbView.setState(KaiState.THINKING)
                tvStatus.text = "קאי חושב..."

                val transcription = withContext(Dispatchers.IO) {
                    groqClient.transcribeAudio(audioFile)
                }
                audioFile.delete()

                if (transcription.isBlank()) {
                    tvTranscript.text = "לא שמעתי..."
                    ttsManager.speak("לא שמעתי, נסה שוב") { finish() }
                    return@launch
                }

                tvTranscript.text = transcription

                val commandResult = commandExecutor.execute(transcription)
                if (commandResult != "none") {
                    orbView.setState(KaiState.SPEAKING)
                    tvStatus.text = "קאי מבצע..."
                    tvTranscript.text = commandResult
                    ttsManager.speak(commandResult) { finish() }
                    return@launch
                }

                val response = withContext(Dispatchers.IO) {
                    groqClient.chat(
                        transcription,
                        systemPrompt = "אתה קאי - עוזר קולי חכם. ענה בעברית, קצר וברור."
                    )
                }

                orbView.setState(KaiState.SPEAKING)
                tvStatus.text = "קאי מדבר..."
                tvTranscript.text = response
                ttsManager.speak(response) { finish() }

            } catch (e: Exception) {
                tvTranscript.text = "שגיאה"
                delay(1500)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        audioRecorder.cleanup()
        ttsManager.shutdown()
        startService(Intent(this, VoiceService::class.java).apply {
            action = VoiceService.ACTION_RESUME
        })
    }
}
