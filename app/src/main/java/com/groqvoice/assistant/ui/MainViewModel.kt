package com.groqvoice.assistant.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.groqvoice.assistant.api.GroqApiClient
import com.groqvoice.assistant.audio.AudioRecorder
import com.groqvoice.assistant.service.VoiceService
import com.groqvoice.assistant.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AssistantState {
    IDLE, RECORDING, PROCESSING, SPEAKING, ERROR
}

data class ChatMessage(
    val role: String,
    val content: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val groqClient = GroqApiClient()
    private val audioRecorder = AudioRecorder(application)
    val ttsManager = TtsManager(application)

    private val _state = MutableLiveData(AssistantState.IDLE)
    val state: LiveData<AssistantState> = _state

    private val _statusText = MutableLiveData("אמור 'קאי' או לחץ")
    val statusText: LiveData<String> = _statusText

    private val _errorText = MutableLiveData<String?>(null)
    val errorText: LiveData<String?> = _errorText

    private val _chatHistory = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatHistory: LiveData<List<ChatMessage>> = _chatHistory

    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private var currentAudioFile: File? = null

    // ─── הקלטה ──────────────────────────────────────────────────────────────
    fun startRecording() {
        if (_state.value == AssistantState.RECORDING) return

        // השהה את קאי בזמן הקלטה ידנית
        val pauseIntent = Intent(getApplication(), VoiceService::class.java).apply {
            action = VoiceService.ACTION_PAUSE
        }
        getApplication<Application>().startService(pauseIntent)

        ttsManager.stop()
        _state.value = AssistantState.RECORDING
        _statusText.value = "מקליט... דבר עכשיו"
        currentAudioFile = audioRecorder.startRecording()
    }

    fun stopRecordingAndProcess() {
        if (_state.value != AssistantState.RECORDING) return

        val audioFile = audioRecorder.stopRecording() ?: run {
            setError("שגיאה בהקלטה")
            resumeWakeWord()
            return
        }
        currentAudioFile = audioFile
        processAudio(audioFile)
    }

    // ─── עיבוד אודיו → STT → LLM → TTS ─────────────────────────────────────
    private fun processAudio(audioFile: File) {
        _state.value = AssistantState.PROCESSING
        _statusText.value = "קאי חושב..."

        viewModelScope.launch {
            try {
                // 1. STT
                _statusText.value = "ממיר דיבור לטקסט..."
                val transcription = withContext(Dispatchers.IO) {
                    groqClient.transcribeAudio(audioFile)
                }

                if (transcription.isBlank()) {
                    setError("לא הצלחתי לשמוע. נסה שוב.")
                    resumeWakeWord()
                    return@launch
                }

                addMessage("user", transcription)

                // 2. LLM
                _statusText.value = "קאי חושב..."
                val response = withContext(Dispatchers.IO) {
                    groqClient.chat(
                        transcription,
                        conversationHistory,
                        systemPrompt = "אתה קאי - עוזר קולי חכם. ענה תמיד בעברית, קצר וברור."
                    )
                }

                addMessage("assistant", response)

                // שמור היסטוריה
                conversationHistory.add(Pair("user", transcription))
                conversationHistory.add(Pair("assistant", response))
                if (conversationHistory.size > 20) {
                    repeat(2) { conversationHistory.removeAt(0) }
                }

                // 3. TTS
                _state.value = AssistantState.SPEAKING
                _statusText.value = "קאי מדבר..."
                ttsManager.speak(response) {
                    _state.postValue(AssistantState.IDLE)
                    _statusText.postValue("אמור 'קאי' או לחץ")
                    resumeWakeWord()
                }

                audioFile.delete()

            } catch (e: Exception) {
                setError("שגיאה: ${e.message}")
                resumeWakeWord()
            }
        }
    }

    // ─── Wake Word ───────────────────────────────────────────────────────────
    private fun resumeWakeWord() {
        val resumeIntent = Intent(getApplication(), VoiceService::class.java).apply {
            action = VoiceService.ACTION_RESUME
        }
        getApplication<Application>().startService(resumeIntent)
    }

    // ─── Utils ───────────────────────────────────────────────────────────────
    private fun addMessage(role: String, content: String) {
        val current = _chatHistory.value?.toMutableList() ?: mutableListOf()
        current.add(ChatMessage(role, content))
        _chatHistory.postValue(current)
    }

    private fun setError(message: String) {
        _state.postValue(AssistantState.ERROR)
        _statusText.postValue(message)
        _errorText.postValue(message)
    }

    fun clearError() {
        if (_state.value == AssistantState.ERROR) {
            _state.value = AssistantState.IDLE
            _statusText.value = "אמור 'קאי' או לחץ"
            _errorText.value = null
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
        _chatHistory.value = emptyList()
    }

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.cleanup()
        ttsManager.shutdown()
        currentAudioFile?.delete()
    }
}
