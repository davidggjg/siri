package com.groqvoice.assistant.service

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.groqvoice.assistant.api.GroqApiClient
import com.groqvoice.assistant.audio.AudioRecorder
import com.groqvoice.assistant.commands.CommandExecutor
import com.groqvoice.assistant.tts.TtsManager
import com.groqvoice.assistant.ui.MainActivity
import kotlinx.coroutines.*

class VoiceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var groqClient: GroqApiClient
    private lateinit var ttsManager: TtsManager
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var audioRecorder: AudioRecorder
    private var wakeLock: PowerManager.WakeLock? = null
    private var isProcessing = false

    companion object {
        const val CHANNEL_ID = "KaiChannel"
        const val NOTIFICATION_ID = 1
        var isRunning = false

        // Actions מה-Activity
        const val ACTION_PAUSE = "pause_wake_word"
        const val ACTION_RESUME = "resume_wake_word"
    }

    override fun onCreate() {
        super.onCreate()
        groqClient = GroqApiClient()
        ttsManager = TtsManager(this)
        commandExecutor = CommandExecutor(this)
        audioRecorder = AudioRecorder(this)
        wakeWordDetector = WakeWordDetector(this) {
            if (!isProcessing) onWakeWordDetected()
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kai::WakeLock")
        wakeLock?.acquire()

        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> wakeWordDetector.pause()
            ACTION_RESUME -> wakeWordDetector.resume()
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("🎙 קאי מאזין..."))
                wakeWordDetector.start()
            }
        }
        return START_STICKY
    }

    private fun onWakeWordDetected() {
        isProcessing = true
        updateNotification("💬 קאי שומע...")
        ttsManager.speak("כן?") {
            recordAndProcess()
        }
    }

    private fun recordAndProcess() {
        serviceScope.launch {
            try {
                val audioFile = audioRecorder.startRecording()
                delay(5000)
                audioRecorder.stopRecording()

                updateNotification("🧠 קאי חושב...")

                val transcription = groqClient.transcribeAudio(audioFile)
                audioFile.delete()

                if (transcription.isBlank()) {
                    ttsManager.speak("לא שמעתי, נסה שוב")
                    finishProcessing()
                    return@launch
                }

                val commandResult = commandExecutor.execute(transcription)
                if (commandResult != "none") {
                    ttsManager.speak(commandResult) { finishProcessing() }
                    return@launch
                }

                val response = groqClient.chat(
                    transcription,
                    systemPrompt = "אתה קאי - עוזר קולי חכם. ענה תמיד בעברית, קצר וברור."
                )
                ttsManager.speak(response) { finishProcessing() }

            } catch (e: Exception) {
                ttsManager.speak("אירעה שגיאה") { finishProcessing() }
            }
        }
    }

    private fun finishProcessing() {
        isProcessing = false
        updateNotification("🎙 קאי מאזין...")
        wakeWordDetector.resume()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "קאי - עוזר קולי",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setSound(null, null) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("קאי")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        wakeLock?.release()
        wakeWordDetector.stop()
        audioRecorder.cleanup()
        ttsManager.shutdown()
        serviceScope.cancel()
        // הפעל מחדש אוטומטית
        startService(Intent(this, VoiceService::class.java))
    }
}
