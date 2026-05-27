package com.groqvoice.assistant.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

class CommandExecutor(private val context: Context) {

    fun execute(command: String): String {
        val lower = command.lowercase()
        return when {
            lower.contains("התקשר") || lower.contains("תתקשר") -> handleCall(command)
            lower.contains("וואטסאפ") || lower.contains("whatsapp") -> handleWhatsApp(command)
            lower.contains("אימייל") || lower.contains("מייל") || lower.contains("email") -> handleEmail(command)
            lower.contains("פתח") || lower.contains("הפעל") -> handleOpenApp(command)
            lower.contains("נגן") || lower.contains("שמע") || lower.contains("מוזיקה") -> handleMusic(command)
            else -> "none"
        }
    }

    private fun handleCall(command: String): String {
        val number = extractPhoneNumber(command)
        if (number != null) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return "מתקשר"
        }
        val name = extractName(command, listOf("התקשר", "תתקשר", "ל", "אל"))
        if (name != null) {
            val phone = findContactNumber(name)
            if (phone != null) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return "מתקשר ל$name"
            }
        }
        return "לא מצאתי את המספר"
    }

    private fun handleWhatsApp(command: String): String {
        val name = extractName(command, listOf("שלח", "הודעה", "ב", "וואטסאפ", "ל"))
        val message = extractMessage(command)
        if (name != null) {
            val phone = findContactNumber(name)
            if (phone != null) {
                val clean = phone.replace("[^0-9+]".toRegex(), "")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$clean?text=${Uri.encode(message ?: "")}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return "פותח וואטסאפ עם $name"
            }
        }
        return "לא מצאתי את האיש בר"
    }

    private fun handleEmail(command: String): String {
        val message = extractMessage(command)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_TEXT, message ?: "")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "פותח אפליקציית מייל"
    }

    private fun handleMusic(command: String): String {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (e: Exception) {
            val spotify = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (spotify != null) {
                spotify.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(spotify)
            }
        }
        return "פותח מוזיקה"
    }

    private fun handleOpenApp(command: String): String {
        val appMap = mapOf(
            "יוטיוב" to "com.google.android.youtube",
            "גוגל" to "com.google.android.googlequicksearchbox",
            "מפות" to "com.google.android.apps.maps",
            "ספוטיפיי" to "com.spotify.music",
            "טלגרם" to "org.telegram.messenger",
            "וואטסאפ" to "com.whatsapp",
            "אינסטגרם" to "com.instagram.android",
            "פייסבוק" to "com.facebook.katana",
            "כרום" to "com.android.chrome",
            "מצלמה" to "com.sec.android.app.camera"
        )
        for ((keyword, pkg) in appMap) {
            if (command.lowercase().contains(keyword)) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (intent != null) {
                    context.startActivity(intent)
                    return "פותח $keyword"
                }
            }
        }
        return "none"
    }

    private fun findContactNumber(name: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )
        cursor?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun extractPhoneNumber(text: String): String? =
        Regex("[0-9+][0-9\\-]{7,14}").find(text)?.value

    private fun extractName(text: String, removeWords: List<String>): String? {
        var result = text
        for (word in removeWords) result = result.replace(word, "", ignoreCase = true)
        result = result.trim()
        return if (result.isNotEmpty()) result else null
    }

    private fun extractMessage(text: String): String? {
        val keywords = listOf("תגיד", "כתוב", "שכתוב", "תכתוב")
        for (keyword in keywords) {
            val idx = text.indexOf(keyword, ignoreCase = true)
            if (idx != -1) return text.substring(idx + keyword.length).trim()
        }
        return null
    }
}
