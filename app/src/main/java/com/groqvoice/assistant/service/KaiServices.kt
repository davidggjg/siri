package com.groqvoice.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import androidx.core.content.ContextCompat
import com.groqvoice.assistant.ui.KaiOverlayActivity

class KaiInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        startService(Intent(this, VoiceService::class.java))
    }
    override fun onShutdown() {
        super.onShutdown()
        stopService(Intent(this, VoiceService::class.java))
    }
}

class KaiSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?) = KaiSession(this)
}

class KaiSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        context.startActivity(Intent(context, KaiOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        hide()
    }
    override fun onHide() { super.onHide(); hide() }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
        }
    }
}
