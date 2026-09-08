package com.yberkayinci.mola.ai

import android.content.Context

/**
 * The user's own AI credentials, held in app-private storage.
 *
 * Mola ships with no provider key. The on-device [LocalAiGateway] is the default engine, and a
 * user who wants generated language supplies a personal Google AI Studio key here. Nothing in this
 * store is bundled into the APK, sent anywhere but the provider the key belongs to, or included in
 * cloud backup and device transfer (see `backup_rules.xml`).
 */
class AiSettingsStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Blank whenever the user has not supplied a key, which keeps the app on the local engine. */
    val apiKey: String
        get() = preferences.getString(KEY_API_KEY, "").orEmpty().trim()

    val hasApiKey: Boolean
        get() = apiKey.isNotEmpty()

    val profileModel: String
        get() = preferences.getString(KEY_PROFILE_MODEL, null)?.trim()?.takeIf(String::isNotEmpty)
            ?: AiModels.DEFAULT_FAST_MODEL

    val cardModel: String
        get() = preferences.getString(KEY_CARD_MODEL, null)?.trim()?.takeIf(String::isNotEmpty)
            ?: AiModels.DEFAULT_FAST_MODEL

    val reportModel: String
        get() = preferences.getString(KEY_REPORT_MODEL, null)?.trim()?.takeIf(String::isNotEmpty)
            ?: AiModels.DEFAULT_REPORT_MODEL

    fun saveApiKey(value: String) {
        preferences.edit().putString(KEY_API_KEY, value.trim()).apply()
    }

    /** Full removal, so "use the local engine only" leaves no credential behind. */
    fun clearApiKey() {
        preferences.edit().remove(KEY_API_KEY).apply()
    }

    fun saveModels(profile: String, card: String, report: String) {
        preferences.edit()
            .putString(KEY_PROFILE_MODEL, profile.trim())
            .putString(KEY_CARD_MODEL, card.trim())
            .putString(KEY_REPORT_MODEL, report.trim())
            .apply()
    }

    companion object {
        const val PREFERENCES_NAME = "mola_ai_settings"

        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_PROFILE_MODEL = "gemini_profile_model"
        private const val KEY_CARD_MODEL = "gemini_card_model"
        private const val KEY_REPORT_MODEL = "gemini_report_model"
    }
}

object AiModels {
    const val DEFAULT_FAST_MODEL = "gemini-2.5-flash-lite"
    const val DEFAULT_REPORT_MODEL = "gemini-3.6-flash"
}
