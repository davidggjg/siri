package com.groqvoice.assistant.api

import com.groqvoice.assistant.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GroqApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GROQ_API_KEY

    // ─── STT: שליחת קובץ אודיו ל-Whisper ───────────────────────────────────
    @Throws(IOException::class)
    fun transcribeAudio(audioFile: File): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaType())
            )
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("language", "he")
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Groq STT Error: ${response.code} - ${response.body?.string()}")
            }
            val json = JSONObject(response.body!!.string())
            return json.getString("text")
        }
    }

    // ─── LLM: שליחת הטקסט ל-LLaMA ──────────────────────────────────────────
    @Throws(IOException::class)
    fun chat(
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        systemPrompt: String = "אתה עוזר קולי חכם ומועיל. ענה בקצרה ובבהירות."
    ): String {
        val messagesArray = JSONArray()

        // System message
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // History
        for ((role, content) in conversationHistory) {
            messagesArray.put(JSONObject().apply {
                put("role", role)
                put("content", content)
            })
        }

        // Current message
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val body = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_tokens", 1024)
        }

        val requestBody = body.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Groq LLM Error: ${response.code} - ${response.body?.string()}")
            }
            val json = JSONObject(response.body!!.string())
            return json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
