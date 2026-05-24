package com.groqvoice.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.groqvoice.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "הרשאת מיקרופון אושרה", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "נדרשת הרשאת מיקרופון", Toast.LENGTH_LONG).show()
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
        requestMicPermissionIfNeeded()
    }

    // ─── Setup ───────────────────────────────────────────────────────────────
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
        // לחיצה ארוכה להקלטה
        binding.btnMic.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (viewModel.hasMicPermission()) {
                        viewModel.startRecording()
                    } else {
                        requestMicPermissionIfNeeded()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewModel.stopRecordingAndProcess()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClearButton() {
        binding.btnClear.setOnClickListener {
            viewModel.clearHistory()
        }
    }

    // ─── Observers ───────────────────────────────────────────────────────────
    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            updateUiForState(state)
        }

        viewModel.statusText.observe(this) { text ->
            binding.tvStatus.text = text
        }

        viewModel.chatHistory.observe(this) { messages ->
            chatAdapter.updateMessages(messages)
            if (messages.isNotEmpty()) {
                binding.rvChat.scrollToPosition(messages.size - 1)
            }
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
            }
            AssistantState.RECORDING -> {
                binding.btnMic.isEnabled = true
                binding.btnMic.alpha = 0.7f
                binding.progressBar.visibility = View.GONE
                binding.waveAnimation.visibility = View.VISIBLE
            }
            AssistantState.PROCESSING -> {
                binding.btnMic.isEnabled = false
                binding.btnMic.alpha = 0.5f
                binding.progressBar.visibility = View.VISIBLE
                binding.waveAnimation.visibility = View.GONE
            }
            AssistantState.SPEAKING -> {
                binding.btnMic.isEnabled = false
                binding.btnMic.alpha = 0.5f
                binding.progressBar.visibility = View.GONE
                binding.waveAnimation.visibility = View.GONE
            }
            AssistantState.ERROR -> {
                binding.btnMic.isEnabled = true
                binding.btnMic.alpha = 1f
                binding.progressBar.visibility = View.GONE
                binding.waveAnimation.visibility = View.GONE
            }
        }
    }

    // ─── Permissions ─────────────────────────────────────────────────────────
    private fun requestMicPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
