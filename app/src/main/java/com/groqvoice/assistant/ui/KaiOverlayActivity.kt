package com.groqvoice.assistant.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.groqvoice.assistant.R
import com.groqvoice.assistant.api.GroqApiClient
import com.groqvoice.assistant.audio.AudioRecorder
import com.groqvoice.assistant.commands.CommandExecutor
import com.groqvoice.assistant.data.ChatMessage
import com.groqvoice.assistant.data.KaiDatabase
import com.groqvoice.assistant.service.VoiceService
import com.groqvoice.assistant.tts.TtsManager
import kotlinx.coroutines.*

class KaiOverlayActivity : Activity() {

    private lateinit var orbView: KaiOrbView
    private lateinit var tvStatus: TextView
    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var chatAdapter: ChatAdapter

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val groqClient = GroqApiClient()
    private val audioRecorder by lazy { AudioRecorder(this) }
    private val ttsManager by lazy { TtsManager(this) }
    private val commandExecutor by lazy { CommandExecutor(this) }
    private val db by lazy { KaiDatabase.getInstance(this) }

    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private var isRecording = false
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.parseColor("#0A0A14")
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        setContentView(R.layout.activity_kai_overlay)
        bindViews()
        setupChat()
        setupButtons()
        pauseWakeWord()
        loadHistory()

        // התחל האזנה אוטומטית
        scope.launch {
            delay(500)
            startVoiceListening()
        }
    }

    private fun bindViews() {
        orbView = findViewById(R.id.kaiOrb)
        tvStatus = findViewById(R.id.tvStatus)
        rvChat = findViewById(R.id.rvChat)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)
        btnStop = findViewById(R.id.btnStop)
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        rvChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@KaiOverlayActivity).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupButtons() {
        // כפתור שלח טקסט
        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                etInput.setText("")
                hideKeyboard()
                processInput(text)
            }
        }

        // Enter שולח
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnSend.performClick()
                true
            } else false
        }

        // כפתור מיק - הקלטה ידנית
        btnMic.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isProcessing) startManualRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) stopManualRecording()
                    true
                }
                else -> false
            }
        }

        // כפתור עצור
        btnStop.setOnClickListener {
            ttsManager.stop()
            audioRecorder.cleanup()
            isRecording = false
            isProcessing = false
            orbView.setState(KaiState.IDLE)
            tvStatus.text = "לחץ מיק או הקלד"
        }
    }

    private fun loadHistory() {
        scope.launch {
            val msgs = withContext(Dispatchers.IO) { db.chatDao().getMessages() }
            chatAdapter.setMessages(msgs)
            // טען היסטוריה לשיחה
            conversationHistory.addAll(msgs.map { it.role to it.content })
            if (msgs.isNotEmpty()) rvChat.scrollToPosition(msgs.size - 1)
        }
    }

    // ─── הקלטה אוטומטית (אחרי wake word) ────────────────────────────────────
    private fun startVoiceListening() {
        if (isProcessing) return
        orbView.setState(KaiState.LISTENING)
        tvStatus.text = "מאזין... (עד 8 שניות)"

        scope.launch {
            try {
                val file = audioRecorder.startRecording()
                isRecording = true

                // המתן עד 8 שניות
                delay(8000)
                if (!isRecording) return@launch

                audioRecorder.stopRecording()
                isRecording = false
                processAudioFile(file)
            } catch (e: Exception) {
                isRecording = false
                tvStatus.text = "שגיאה בהקלטה"
            }
        }
    }

    // ─── הקלטה ידנית (לחיצה על מיק) ─────────────────────────────────────────
    private fun startManualRecording() {
        if (isProcessing) return
        isRecording = true
        orbView.setState(KaiState.LISTENING)
        tvStatus.text = "מקליט... (שחרר לשלוח)"
        audioRecorder.startRecording()
    }

    private fun stopManualRecording() {
        isRecording = false
        val file = audioRecorder.stopRecording() ?: return
        processAudioFile(file)
    }

    // ─── עיבוד קובץ אודיו ────────────────────────────────────────────────────
    private fun processAudioFile(file: java.io.File) {
        isProcessing = true
        orbView.setState(KaiState.THINKING)
        tvStatus.text = "ממיר לטקסט..."

        scope.launch {
            try {
                val transcription = withContext(Dispatchers.IO) {
                    groqClient.transcribeAudio(file)
                }
                file.delete()

                if (transcription.isBlank()) {
                    tvStatus.text = "לא שמעתי, נסה שוב"
                    isProcessing = false
                    orbView.setState(KaiState.IDLE)
                    return@launch
                }

                processInput(transcription)
            } catch (e: Exception) {
                tvStatus.text = "שגיאה: ${e.message?.take(50)}"
                isProcessing = false
                orbView.setState(KaiState.IDLE)
            }
        }
    }

    // ─── עיבוד קלט (טקסט או דיבור) ──────────────────────────────────────────
    private fun processInput(input: String) {
        isProcessing = true

        // הצג הודעת משתמש
        val userMsg = ChatMessage(role = "user", content = input)
        chatAdapter.addMessage(userMsg)
        rvChat.scrollToPosition(chatAdapter.itemCount - 1)
        saveMessage(userMsg)

        orbView.setState(KaiState.THINKING)
        tvStatus.text = "קאי חושב..."

        scope.launch {
            try {
                // בדוק פקודות
                val cmd = commandExecutor.execute(input)
                if (cmd != "none") {
                    respondWith(cmd)
                    return@launch
                }

                // שאלה ל-AI
                val response = withContext(Dispatchers.IO) {
                    groqClient.chat(
                        input,
                        conversationHistory,
                        systemPrompt = """
                            אתה קאי - עוזר קולי חכם ומועיל בעברית.
                            ענה תמיד בעברית, קצר וברור.
                            זכור את ההקשר של השיחה.
                        """.trimIndent()
                    )
                }

                conversationHistory.add("user" to input)
                conversationHistory.add("assistant" to response)
                if (conversationHistory.size > 20) {
                    conversationHistory.removeAt(0)
                    conversationHistory.removeAt(0)
                }

                respondWith(response)

            } catch (e: Exception) {
                val err = "שגיאה בחיבור לאינטרנט"
                respondWith(err, speak = false)
            }
        }
    }

    private fun respondWith(text: String, speak: Boolean = true) {
        val msg = ChatMessage(role = "assistant", content = text)
        chatAdapter.addMessage(msg)
        rvChat.scrollToPosition(chatAdapter.itemCount - 1)
        saveMessage(msg)

        if (speak) {
            orbView.setState(KaiState.SPEAKING)
            tvStatus.text = "קאי מדבר..."
            ttsManager.speak(text) {
                isProcessing = false
                orbView.setState(KaiState.IDLE)
                tvStatus.text = "לחץ מיק או הקלד"
            }
        } else {
            isProcessing = false
            orbView.setState(KaiState.IDLE)
            tvStatus.text = "לחץ מיק או הקלד"
        }
    }

    private fun saveMessage(msg: ChatMessage) {
        scope.launch(Dispatchers.IO) { db.chatDao().insert(msg) }
    }

    private fun pauseWakeWord() {
        startService(Intent(this, VoiceService::class.java).apply {
            action = VoiceService.ACTION_PAUSE
        })
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etInput.windowToken, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        audioRecorder.cleanup()
        ttsManager.shutdown()
        startService(Intent(this, VoiceService::class.java).apply {
            action = VoiceService.ACTION_RESUME
        })
    }
}
