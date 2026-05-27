package com.groqvoice.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.groqvoice.assistant.databinding.ActivityMainBinding
import com.groqvoice.assistant.service.VoiceService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.RECORD_AUDIO] == true) startKai()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnActivate.setOnClickListener {
            startActivity(Intent(this, KaiOverlayActivity::class.java))
        }

        requestPermsAndStart()
    }

    private fun requestPermsAndStart() {
        val needed = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isEmpty()) startKai()
        else permLauncher.launch(needed.toTypedArray())
    }

    private fun startKai() {
        if (!VoiceService.isRunning) {
            ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
            Toast.makeText(this, "🎙 קאי פועל ברקע", Toast.LENGTH_SHORT).show()
        }
    }
}
