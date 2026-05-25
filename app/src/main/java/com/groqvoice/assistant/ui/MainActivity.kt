package com.groqvoice.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.groqvoice.assistant.databinding.ActivityMainBinding
import com.groqvoice.assistant.service.VoiceService
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startKai()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupMicButton()
        setupClearButton()
        observeViewModel()
        requestPermissionsAndStart()
    }

    private fun requestPermissionsAndStart() {
        val needed = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) startKai()
        else permissionsLauncher.launch(needed.toTypedArray())
    }

    private fun startKai() {
        if (!VoiceService.isRunning) {
            ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
            Toast.makeText(this, "🎙 קאי פועל ברקע", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupMicButton() {
        binding.btnMic.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { viewModel.startRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewModel.stopRecordingAndProcess(); true
                }
                else -> false
            }
        }
    }

    private fun setupClearButton() {
        binding.btnClear.setOnClickListener { viewModel.clearHistory() }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { updateUiForState(it) }
        viewModel.statusText.observe(this) { binding.tvStatus.text = it }
        viewModel.chatHistory.observe(this) { messages ->
            chatAdapter.updateMessages(messages)
            if (messages.isNotEmpty())
                binding.rvChat.scrollToPosition(messages.size - 1)
        }
        viewModel.errorText.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun updateUiForState(state: AssistantState) {
        when (state) {
            AssistantState.IDLE -> {
                binding.btnMic.isEnabled = true
                binding.btnMic.alpha = 1f
                binding.progressBar.visibility = View.GONE
                binding.waveAnimation.visibility = View.GONE
                binding.tvStatus.text = "אמור 'קאי' או לחץ"
            }
            AssistantState.RECORDING -> {
                binding.btnMic.alpha = 0.7f
                binding.waveAnimation.visibility = View.VISIBLE
                binding.tvStatus.text = "מקליט..."
            }
            AssistantState.PROCESSING -> {
                binding.btnMic.isEnabled = false
                binding.btnMic.alpha = 0.5f
                binding.progressBar.visibility = View.VISIBLE
                binding.tvStatus.text = "קאי חושב..."
            }
            AssistantState.SPEAKING -> {
                binding.btnMic.isEnabled = false
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "קאי מדבר..."
            }
            AssistantState.ERROR -> {
                binding.btnMic.isEnabled = true
                binding.btnMic.alpha = 1f
                binding.progressBar.visibility = View.GONE
                binding.waveAnimation.visibility = View.GONE
            }
        }
    }
}
