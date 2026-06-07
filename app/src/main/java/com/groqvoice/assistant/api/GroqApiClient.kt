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
        .retryOnConnectionFailure(true)
        .build()

    private val apiKey = BuildConfig.GROQ_API_KEY

    @Throws(IOException::class)
    fun transcribeAudio(audioFile: File, retries: Int = 2): String {
        var lastError: Exception? = null
        repeat(retries + 1) { attempt ->
            try {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", audioFile.name,
                        audioFile.asRequestBody("audio/m4a".toMediaType()))
                    .addFormDataPart("model", "whisper-large-v3")
                    .addFormDataPart("language", "he")
                    .addFormDataPart("response_format", "json")
                    .build()

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/audio/transcriptions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful)
                        throw IOException("STT Error ${response.code}: ${response.body?.string()}")
                    return JSONObject(response.body!!.string()).getString("text")
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < retries) Thread.sleep(1000L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Unknown error")
    }

    @Throws(IOException::class)
    fun chat(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        systemPrompt: String = "אתה קאי - עוזר קולי חכם. ענה תמיד בעברית, קצר וברור."
    ): String {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system"); put("content", systemPrompt)
        })
        // שמור היסטוריה - עד 10 הודעות אחרונות
        val recentHistory = if (history.size > 10) history.takeLast(10) else history
        for ((role, content) in recentHistory) {
            messages.put(JSONObject().apply { put("role", role); put("content", content) })
        }
        messages.put(JSONObject().apply { put("role", "user"); put("content", userMessage) })

        val body = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 1024)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw IOException("LLM Error ${response.code}: ${response.body?.string()}")
            return JSONObject(response.body!!.string())
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
