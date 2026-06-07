package com.groqvoice.assistant.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

class CommandExecutor(private val context: Context) {

    data class CommandResult(
        val handled: Boolean,
        val response: String = "none"
    )

    fun execute(command: String): String {
        val lower = command.lowercase().trim()
        return when {
            matchesCall(lower) -> handleCall(command, lower)
            matchesWhatsApp(lower) -> handleWhatsApp(command, lower)
            matchesEmail(lower) -> handleEmail(command)
            matchesMusic(lower) -> handleMusic(command, lower)
            matchesYouTube(lower) -> handleYouTubeSearch(extractQuery(command, listOf("יוטיוב","youtube","חפש","הפעל")))
            matchesOpenApp(lower) -> handleOpenApp(lower)
            else -> "none"
        }
    }

    private fun matchesCall(t: String) = t.contains("התקשר") || t.contains("תתקשר") || t.contains("תתקשרי") || t.contains("call")
    private fun matchesWhatsApp(t: String) = (t.contains("שלח") || t.contains("שלחי")) && (t.contains("וואטסאפ") || t.contains("whatsapp") || t.contains("הודעה"))
    private fun matchesEmail(t: String) = t.contains("אימייל") || t.contains("מייל") || t.contains("email")
    private fun matchesMusic(t: String) = t.contains("נגן") || t.contains("תנגן") || (t.contains("שיר") && !t.contains("?")) || t.contains("מוזיקה")
    private fun matchesYouTube(t: String) = t.contains("יוטיוב") || t.contains("youtube")
    private fun matchesOpenApp(t: String) = (t.contains("פתח") || t.contains("הפעל")) && appKeywords.any { t.contains(it.key) }

    private val appKeywords = mapOf(
        "יוטיוב" to "com.google.android.youtube",
        "ספוטיפיי" to "com.spotify.music",
        "מפות" to "com.google.android.apps.maps",
        "גוגל" to "com.google.android.googlequicksearchbox",
        "טלגרם" to "org.telegram.messenger",
        "וואטסאפ" to "com.whatsapp",
        "אינסטגרם" to "com.instagram.android",
        "כרום" to "com.android.chrome",
        "מצלמה" to "com.sec.android.app.camera",
        "netflix" to "com.netflix.mediaclient",
        "נטפליקס" to "com.netflix.mediaclient"
    )

    private fun handleCall(command: String, lower: String): String {
        val phone = extractPhoneNumber(command)
        if (phone != null) {
            dial(phone)
            return "מתקשר"
        }
        val name = extractPersonName(lower, listOf("התקשר","תתקשר","ל","אל","תתקשרי"))
        if (name != null) {
            val num = findContact(name)
            if (num != null) { dial(num); return "מתקשר ל$name" }
            return "לא מצאתי $name באנשי הקשר"
        }
        return "למי להתקשר?"
    }

    private fun handleWhatsApp(command: String, lower: String): String {
        val name = extractPersonName(lower, listOf("שלח","שלחי","הודעה","ב","וואטסאפ","ל","whatsapp"))
        val msg = extractAfterKeyword(command, listOf("שכותבת","שכתוב","תכתוב","כתוב","תגיד","שאומרת")) ?: ""
        if (name != null) {
            val num = findContact(name)
            if (num != null) {
                val clean = num.replace("[^0-9+]".toRegex(), "")
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$clean?text=${Uri.encode(msg)}")
                })
                return "פותח וואטסאפ עם $name"
            }
            return "לא מצאתי $name באנשי הקשר"
        }
        return "למי לשלוח?"
    }

    private fun handleEmail(command: String): String {
        val msg = extractAfterKeyword(command, listOf("תגיד","כתוב","שכתוב")) ?: ""
        startActivity(Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_TEXT, msg)
        })
        return "פותח מייל"
    }

    private fun handleMusic(command: String, lower: String): String {
        val query = extractQuery(command, listOf("נגן","תנגן","שיר","מוזיקה","שמע","תשמיע"))
        // נסה Spotify קודם
        val spotify = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
        if (spotify != null && query.isBlank()) {
            startActivity(spotify)
            return "פותח ספוטיפיי"
        }
        // חפש ב-YouTube
        return handleYouTubeSearch(query)
    }

    private fun handleYouTubeSearch(query: String): String {
        val uri = if (query.isNotBlank())
            Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        else
            Uri.parse("https://www.youtube.com")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
        return if (query.isNotBlank()) "מחפש $query ביוטיוב" else "פותח יוטיוב"
    }

    private fun handleOpenApp(lower: String): String {
        for ((keyword, pkg) in appKeywords) {
            if (lower.contains(keyword)) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) { startActivity(intent); return "פותח $keyword" }
            }
        }
        return "none"
    }

    private fun dial(number: String) {
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    private fun startActivity(intent: Intent) {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun findContact(name: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )
        cursor?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun extractPhoneNumber(text: String) =
        Regex("[0-9+][0-9\\-]{7,14}").find(text)?.value

    private fun extractPersonName(text: String, removeWords: List<String>): String? {
        var r = text
        for (w in removeWords) r = r.replace(w, " ", ignoreCase = true)
        r = r.replace(Regex("\\s+"), " ").trim()
        return if (r.length > 1) r else null
    }

    private fun extractAfterKeyword(text: String, keywords: List<String>): String? {
        for (k in keywords) {
            val idx = text.indexOf(k, ignoreCase = true)
            if (idx != -1) return text.substring(idx + k.length).trim()
        }
        return null
    }

    private fun extractQuery(text: String, removeWords: List<String>): String {
        var r = text
        for (w in removeWords) r = r.replace(w, " ", ignoreCase = true)
        return r.replace(Regex("\\s+"), " ").trim()
    }
}
