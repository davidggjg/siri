package com.groqvoice.assistant.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.groqvoice.assistant.api.GroqApiClient
import com.groqvoice.assistant.audio.AudioRecorder
import com.groqvoice.assistant.commands.CommandExecutor
import com.groqvoice.assistant.tts.TtsManager
import com.groqvoice.assistant.ui.MainActivity
import kotlinx.coroutines.*
import java.util.Locale

class VoiceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var groqClient: GroqApiClient
    private lateinit var ttsManager: TtsManager
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var audioRecorder: AudioRecorder

    companion object {
        const val CHANNEL_ID = "VoiceAssistantChannel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        groqClient = GroqApiClient()
        ttsManager = TtsManager(this)
        commandExecutor = CommandExecutor(this)
        audioRecorder = AudioRecorder(this)
        wakeWordDetector = WakeWordDetector(this) {
            onWakeWordDetected()
        }
        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("מאזין... אמור 'היי עוזר'"))
        wakeWordDetector.start()
        return START_STICKY // מופעל מחדש אוטומטית אם נהרג
    }

    private fun onWakeWordDetected() {
        updateNotification("שומע אותך...")
        ttsManager.speak("כן?") {
            recordAndProcess()
        }
    }

    private fun recordAndProcess() {
        serviceScope.launch {
            try {
                val audioFile = audioRecorder.startRecording()
                delay(5000) // הקלטה של 5 שניות
                audioRecorder.stopRecording()

                updateNotification("מעבד...")

                // STT
                val transcription = groqClient.transcribeAudio(audioFile)
                if (transcription.isBlank()) {
                    ttsManager.speak("לא שמעתי, נסה שוב")
                    updateNotification("מאזין... אמור 'היי עוזר'")
                    return@launch
                }

                // בדוק אם זו פקודה
                val commandResult = commandExecutor.execute(transcription)
                if (commandResult != "none") {
                    ttsManager.speak(commandResult)
                    updateNotification("מאזין... אמור 'היי עוזר'")
                    audioFile.delete()
                    return@launch
                }

                // שאלה רגילה - שלח ל-LLM
                val response = groqClient.chat(transcription)
                ttsManager.speak(response)
                audioFile.delete()
                updateNotification("מאזין... אמור 'היי עוזר'")

            } catch (e: Exception) {
                ttsManager.speak("אירעה שגיאה")
                updateNotification("מאזין... אמור 'היי עוזר'")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "עוזר קולי פעיל ברקע"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎙 עוזר קולי")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        wakeWordDetector.stop()
        audioRecorder.cleanup()
        ttsManager.shutdown()
        serviceScope.cancel()
    }
}
