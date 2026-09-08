package com.yberkayinci.mola.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yberkayinci.mola.ai.AiGateway
import com.yberkayinci.mola.ai.CrisisFilter
import com.yberkayinci.mola.ai.LocalAiGateway
import com.yberkayinci.mola.ai.SafetyLanguageValidator
import com.yberkayinci.mola.data.AiCard
import com.yberkayinci.mola.data.InterventionInput
import com.yberkayinci.mola.data.InterventionInputMethod
import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.UserChoice
import com.yberkayinci.mola.data.UserProfile
import com.yberkayinci.mola.ui.components.MolaCard
import com.yberkayinci.mola.ui.components.MolaScreen
import com.yberkayinci.mola.ui.components.MolaTopBar
import com.yberkayinci.mola.ui.components.PrimaryActionButton
import com.yberkayinci.mola.ui.components.QuickStateButton
import com.yberkayinci.mola.ui.components.SecondaryActionButton
import com.yberkayinci.mola.ui.components.SectionTitle
import com.yberkayinci.mola.ui.components.StatItem
import com.yberkayinci.mola.ui.theme.MolaSpacing
import com.yberkayinci.mola.voice.SpeechInput
import kotlinx.coroutines.launch

@Composable
fun InterventionScreen(
    profile: UserProfile,
    usageMinutes: Int,
    aiGateway: AiGateway,
    recentRecords: List<InterventionRecord> = emptyList(),
    onChoice: (InterventionInput, AiCard, UserChoice) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val quickStates = remember(profile.personalization) {
        profile.personalization.quickStatesOrDefault().take(3)
    }
    var userText by rememberSaveable { mutableStateOf("") }
    var inputMethod by remember { mutableStateOf(InterventionInputMethod.TEXT) }
    var customMode by rememberSaveable { mutableStateOf(false) }
    var selectedInput by remember { mutableStateOf<InterventionInput?>(null) }
    var generatedCard by remember { mutableStateOf<AiCard?>(null) }
    var crisisState by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            SpeechInput.extractText(result.data)?.let { spokenText ->
                userText = spokenText
                inputMethod = InterventionInputMethod.VOICE
                customMode = true
                generatedCard = null
                crisisState = false
                message = "Sesli yanıt metne çevrildi. Göndermeden önce düzenleyebilirsin."
            }
        }
    }

    fun requestCard(input: InterventionInput) {
        if (isLoading || input.text.isBlank()) return
        message = null
        generatedCard = null
        selectedInput = input

        if (CrisisFilter.check(input.text).isCrisisSignal) {
            crisisState = true
            return
        }

        crisisState = false
        isLoading = true
        scope.launch {
            val result = try {
                aiGateway.generateCard(profile, usageMinutes, input, recentRecords)
            } catch (_: Exception) {
                LocalAiGateway().generateCard(profile, usageMinutes, input, recentRecords)
            }
            generatedCard = if (
                SafetyLanguageValidator.isDisplaySafe(
                    result.reflection,
                    result.question,
                    result.activityTitle,
                    result.alternative,
                )
            ) {
                result
            } else {
                LocalAiGateway().generateCard(profile, usageMinutes, input, recentRecords)
            }
            isLoading = false
        }
    }

    MolaScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MolaSpacing.xLarge, vertical = MolaSpacing.xxLarge),
            verticalArrangement = Arrangement.spacedBy(MolaSpacing.xLarge),
        ) {
            MolaTopBar(
                title = "Kısa bir durak",
                subtitle = "Karar senin; Mola yalnızca düşünmek için alan açar.",
            )
            MolaCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MolaSpacing.section),
                ) {
                    StatItem(
                        value = "$usageMinutes dk",
                        label = "${profile.targetAppLabel} bugün",
                        modifier = Modifier.weight(1f),
                    )
                    StatItem(
                        value = "${profile.dailyLimitMinutes} dk",
                        label = "kendi hedefin",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (crisisState) {
                SectionTitle(
                    title = "Şu an destek önemli",
                    supportingText = "Bu yanıt herhangi bir AI servisine gönderilmedi.",
                )
                MolaCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.medium)) {
                        Text(
                            "Bunu tek başına taşımak zorunda değilsin.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "Yakınındaki acil yardım hizmetine, güvendiğin bir kişiye veya profesyonel desteğe şimdi ulaş.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                SecondaryActionButton("Kapat", onClick = onBack)
            } else if (generatedCard == null) {
                SectionTitle(
                    title = "Şu an seni burada tutan ne?",
                    supportingText = "En yakın seçeneğe dokunabilir ya da kendi cümlelerinle anlatabilirsin.",
                )
                Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.medium)) {
                    quickStates.forEach { option ->
                        QuickStateButton(
                            label = option.label,
                            emoji = option.emoji,
                            onClick = {
                                requestCard(
                                    InterventionInput(
                                        text = option.label,
                                        stateId = option.id,
                                        stateLabel = option.label,
                                        method = InterventionInputMethod.QUICK_REPLY,
                                    ),
                                )
                            },
                            enabled = !isLoading,
                        )
                    }
                }

                MolaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.medium)) {
                        Text(
                            "Başka bir şey mi var?",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MolaSpacing.small),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    customMode = true
                                    inputMethod = InterventionInputMethod.TEXT
                                    message = null
                                },
                                enabled = !isLoading,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("✏️ Yaz")
                            }
                            OutlinedButton(
                                onClick = {
                                    message = null
                                    try {
                                        voiceLauncher.launch(
                                            SpeechInput.createIntent("Şu an ne olduğunu anlat"),
                                        )
                                    } catch (_: ActivityNotFoundException) {
                                        customMode = true
                                        inputMethod = InterventionInputMethod.TEXT
                                        message = "Bu telefonda sesli giriş kullanılamıyor; yazarak devam edebilirsin."
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("🎙 Anlat")
                            }
                        }

                        if (customMode) {
                            OutlinedTextField(
                                value = userText,
                                onValueChange = {
                                    userText = it
                                    if (inputMethod != InterventionInputMethod.VOICE) {
                                        inputMethod = InterventionInputMethod.TEXT
                                    }
                                    message = null
                                },
                                label = { Text("Kendi kelimelerinle anlat") },
                                minLines = 3,
                                maxLines = 7,
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            PrimaryActionButton(
                                text = "Yanıtı değerlendir",
                                onClick = {
                                    val text = userText.trim()
                                    if (text.isBlank()) {
                                        message = "Devam etmek için kısa bir cümle yaz ya da anlat."
                                    } else {
                                        requestCard(
                                            InterventionInput(
                                                text = text,
                                                method = inputMethod,
                                            ),
                                        )
                                    }
                                },
                                enabled = !isLoading,
                            )
                        }
                    }
                }

                if (isLoading) {
                    MolaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MolaSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                            Column {
                                Text(
                                    "Küçük bir seçenek hazırlanıyor",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "Bu birkaç saniye sürebilir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SecondaryActionButton(
                    text = "Şimdi değil",
                    onClick = onBack,
                    enabled = !isLoading,
                )
            } else {
                generatedCard?.let { card ->
                    SectionTitle(
                        title = "Bir an için bunu deneyebilirsin",
                        supportingText = "Bu öneri karar vermene destek olmak için hazırlandı.",
                    )
                    MolaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.large)) {
                            if (card.reflection.isNotBlank()) {
                                Text(
                                    card.reflection,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                card.question,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(MolaSpacing.large),
                                    verticalArrangement = Arrangement.spacedBy(MolaSpacing.xSmall),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            card.activityTitle.ifBlank { "Küçük adım" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (card.durationMinutes > 0) {
                                            Text(
                                                "${card.durationMinutes} dk",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Text(card.alternative, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    PrimaryActionButton(
                        text = "Bunu deneyeceğim",
                        onClick = {
                            onChoice(
                                selectedInput ?: InterventionInput(userText.trim()),
                                card,
                                UserChoice.STOPPED,
                            )
                        },
                    )
                    SecondaryActionButton(
                        text = "Yine de devam et",
                        onClick = {
                            onChoice(
                                selectedInput ?: InterventionInput(userText.trim()),
                                card,
                                UserChoice.CONTINUE,
                            )
                        },
                    )
                    TextButton(
                        onClick = {
                            generatedCard = null
                            selectedInput = null
                            customMode = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Yanıtımı değiştir")
                    }
                }
            }
        }
    }
}
