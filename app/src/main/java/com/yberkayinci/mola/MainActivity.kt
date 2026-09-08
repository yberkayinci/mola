package com.yberkayinci.mola

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yberkayinci.mola.ai.AiSettingsStore
import com.yberkayinci.mola.data.JsonMolaRepository
import com.yberkayinci.mola.ui.theme.MolaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = JsonMolaRepository(this)
        val aiSettings = AiSettingsStore(this)

        setContent {
            MolaTheme {
                MolaApp(
                    repository = repository,
                    aiSettings = aiSettings,
                )
            }
        }
    }
}
