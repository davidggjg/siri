package com.groqvoice.assistant.service

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class KaiSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return KaiSession(this)
    }
}

class KaiSession(context: Context) : VoiceInteractionSession(context) {

    private var voiceService: VoiceService? = null

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // הופעל ע"י לחיצה ארוכה על כפתור הבית/הפעלה
        // מפעיל מיד את ההקלטה
        val intent = android.content.Intent(context, VoiceService::class.java).apply {
            action = VoiceService.ACTION_MANUAL_TRIGGER
        }
        context.startService(intent)
    }

    override fun onHide() {
        super.onHide()
        hide()
    }
}
