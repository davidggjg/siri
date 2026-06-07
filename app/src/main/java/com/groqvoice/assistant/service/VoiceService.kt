package com.groqvoice.assistant.service

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.groqvoice.assistant.ui.KaiOverlayActivity
import com.groqvoice.assistant.ui.MainActivity

class VoiceService : Service() {

    private lateinit var wakeWordDetector: WakeWordDetector
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "KaiChannel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
        const val ACTION_PAUSE = "pause_wake_word"
        const val ACTION_RESUME = "resume_wake_word"
        const val ACTION_TRIGGER = "manual_trigger"
        const val ACTION_UPDATE_NOTIF = "update_notification"
        const val EXTRA_NOTIF_TEXT = "notif_text"
    }

    override fun onCreate() {
        super.onCreate()
        wakeWordDetector = WakeWordDetector(this) { openOverlay() }

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
            ACTION_TRIGGER -> openOverlay()
            ACTION_UPDATE_NOTIF -> {
                val text = intent.getStringExtra(EXTRA_NOTIF_TEXT) ?: "🎙 קאי מאזין..."
                updateNotification(text)
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("🎙 קאי מאזין..."))
                wakeWordDetector.start()
            }
        }
        return START_STICKY
    }

    private fun openOverlay() {
        startActivity(Intent(this, KaiOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

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
        startService(Intent(this, VoiceService::class.java))
    }
}
