package com.groqvoice.assistant.service

import android.content.Intent
import android.service.voice.VoiceInteractionService

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
