package com.yberkayinci.mola.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class VoiceInputActivity : ComponentActivity() {
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            SpeechInput.extractText(result.data)
        } else {
            null
        }
        VoiceInputCoordinator.deliver(text)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty().ifBlank {
            "Şu an ne olduğunu anlat"
        }
        try {
            speechLauncher.launch(SpeechInput.createIntent(prompt))
        } catch (_: ActivityNotFoundException) {
            VoiceInputCoordinator.deliver(null)
            finish()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            VoiceInputCoordinator.cancel()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROMPT = "voice_prompt"
    }
}
