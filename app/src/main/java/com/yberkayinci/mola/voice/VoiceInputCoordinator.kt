package com.yberkayinci.mola.voice

import android.content.Context
import android.content.Intent

object VoiceInputCoordinator {
    private var callback: ((String?) -> Unit)? = null

    fun request(
        context: Context,
        prompt: String,
        onResult: (String?) -> Unit,
    ): Boolean {
        callback = onResult
        return runCatching {
            context.startActivity(
                Intent(context, VoiceInputActivity::class.java)
                    .putExtra(VoiceInputActivity.EXTRA_PROMPT, prompt)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrElse {
            callback = null
            false
        }
    }

    internal fun deliver(text: String?) {
        val pending = callback
        callback = null
        pending?.invoke(text)
    }

    fun cancel() {
        callback = null
    }
}
