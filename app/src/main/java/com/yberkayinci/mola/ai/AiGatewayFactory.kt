package com.yberkayinci.mola.ai

import android.content.Context

/**
 * Chooses the engine that backs every generated card, profile and report.
 *
 * The on-device [LocalAiGateway] is the default and the guaranteed path: it needs no network, no
 * account and no credential, so the product works fully on any supported device. When the user has
 * entered their own key, [GeminiAiGateway] takes over and still falls back to the local engine on
 * any failure, rejection or crisis short-circuit.
 */
object AiGatewayFactory {
    fun create(context: Context): AiGateway = create(AiSettingsStore(context))

    fun create(settings: AiSettingsStore): AiGateway {
        if (!settings.hasApiKey) return LocalAiGateway()
        return GeminiAiGateway(
            apiKey = settings.apiKey,
            profileModel = settings.profileModel,
            cardModel = settings.cardModel,
            reportModel = settings.reportModel,
        )
    }
}
