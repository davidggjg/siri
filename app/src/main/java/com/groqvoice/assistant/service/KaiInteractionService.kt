package com.groqvoice.assistant.service

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession

class KaiInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        // הפעל wake word כשהשירות מוכן
        val intent = Intent(this, VoiceService::class.java)
        startService(intent)
    }

    override fun onShutdown() {
        super.onShutdown()
        stopService(Intent(this, VoiceService::class.java))
    }
}
