package com.groqvoice.assistant.service

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.groqvoice.assistant.api.GroqApiClient
import com.groqvoice.assistant.audio.AudioRecorder
import com.groqvoice.assistant.commands.CommandExecutor
import com.groqvoice.assistant.tts.TtsManager
import com.groqvoice.assistant.ui.KaiOverlayActivity
import com.groqvoice.assistant.ui.MainActivity
import kotlinx.coroutines.*

class VoiceService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
        const val ACTION_PAUSE = "pause_wake_word"
        const val ACTION_RESUME = "resume_wake_word"
        const val ACTION_MANUAL_TRIGGER = "manual_trigger"
    }

    override fun onCreate() {
        super.onCreate()
        groqClient = GroqApiClient()
        ttsManager = TtsManager(this)
        commandExecutor = CommandExecutor(this)
        audioRecorder = AudioRecorder(this)
        wakeWordDetector = WakeWordDetector(this) {
            if (!isProcessing) openOverlay()
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
            ACTION_MANUAL_TRIGGER -> if (!isProcessing) openOverlay()
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("🎙 קאי מאזין..."))
                wakeWordDetector.start()
            }
        }
        return START_STICKY
    }

    private fun openOverlay() {
        val intent = Intent(this, KaiOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    fun processVoice(transcription: String, onResponse: (String) -> Unit) {
        isProcessing = true
        scope.launch {
            try {
                val commandResult = commandExecutor.execute(transcription)
                if (commandResult != "none") {
                    onResponse(commandResult)
                    isProcessing = false
                    return@launch
                }
                val response = groqClient.chat(
                    transcription,
                    systemPrompt = """
                        אתה קאי - עוזר קולי חכם בעברית.
                        ענה תמיד בעברית, קצר וברור.
                        אתה יכול לעזור בכל דבר.
                    """.trimIndent()
                )
                onResponse(response)
                isProcessing = false
            } catch (e: Exception) {
                onResponse("שגיאה: ${e.message}")
                isProcessing = false
            }
        }
    }

    fun getGroqClient() = groqClient
    fun getTtsManager() = ttsManager

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "קאי", NotificationManager.IMPORTANCE_LOW)
            .apply { setSound(null, null) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("קאי")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    fun updateNotification(text: String) {
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
        scope.cancel()
        startService(Intent(this, VoiceService::class.java))
    }
}
