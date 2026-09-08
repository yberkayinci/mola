package com.yberkayinci.mola.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yberkayinci.mola.ai.AiGateway
import com.yberkayinci.mola.ai.CrisisFilter
import com.yberkayinci.mola.ai.LocalAiGateway
import com.yberkayinci.mola.data.PersonalizationProfile
import com.yberkayinci.mola.data.ProfileIntake
import com.yberkayinci.mola.data.UserProfile
import com.yberkayinci.mola.permissions.LaunchableApp
import com.yberkayinci.mola.ui.components.MolaCard
import com.yberkayinci.mola.ui.components.MolaScreen
import com.yberkayinci.mola.ui.components.MolaTopBar
import com.yberkayinci.mola.ui.components.PrimaryActionButton
import com.yberkayinci.mola.ui.components.SectionTitle
import com.yberkayinci.mola.ui.components.StatusPill
import com.yberkayinci.mola.ui.theme.MolaSpacing
import com.yberkayinci.mola.voice.SpeechInput
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    installedApps: List<LaunchableApp>,
    hasUsageAccess: Boolean,
    canDrawOverlays: Boolean,
    aiGateway: AiGateway,
    onOpenUsagePermission: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onComplete: (UserProfile) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var department by rememberSaveable { mutableStateOf("") }
    var biography by rememberSaveable { mutableStateOf("") }
    var hobbiesText by rememberSaveable { mutableStateOf("") }
    var improvementArea by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var generatedProfile by remember { mutableStateOf<PersonalizationProfile?>(null) }
    var profileConfirmed by rememberSaveable { mutableStateOf(false) }
    var isGeneratingProfile by remember { mutableStateOf(false) }
    var profileMessage by remember { mutableStateOf<String?>(null) }
    var showOptionalDetails by rememberSaveable { mutableStateOf(false) }

    val initialTarget = remember(installedApps) {
        installedApps.firstOrNull { it.packageName == "com.instagram.android" }
            ?: installedApps.firstOrNull()
    }
    var targetAppLabel by rememberSaveable {
        mutableStateOf(initialTarget?.label ?: "Instagram")
    }
    var targetPackage by rememberSaveable {
        mutableStateOf(initialTarget?.packageName ?: "com.instagram.android")
    }
    var limitText by rememberSaveable { mutableStateOf("60") }
    var showAppPicker by remember { mutableStateOf(false) }
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            SpeechInput.extractText(result.data)?.let { spokenText ->
                biography = listOf(biography.trim(), spokenText)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                generatedProfile = null
                profileConfirmed = false
                profileMessage = "Sesli anlatım metne eklendi. İstersen düzenleyebilirsin."
            }
        }
    }

    val hobbies = hobbiesText
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
    val limit = limitText.toIntOrNull()
    val valid = name.isNotBlank() && biography.isNotBlank() && targetPackage.isNotBlank() &&
        limit != null && limit > 0

    fun intake(): ProfileIntake = ProfileIntake(
        name = name.trim(),
        department = department.trim(),
        biography = biography.trim(),
        hobbies = hobbies,
        improvementArea = improvementArea.trim(),
        reason = reason.trim(),
    )

    fun generateProfile(onReady: (PersonalizationProfile) -> Unit = {}) {
        if (!valid || isGeneratingProfile) return
        isGeneratingProfile = true
        profileConfirmed = false
        profileMessage = null
        val currentIntake = intake()
        val crisisSignal = CrisisFilter.check(
            listOf(
                currentIntake.biography,
                currentIntake.reason,
                currentIntake.improvementArea,
            ).joinToString(" "),
        ).isCrisisSignal

        scope.launch {
            val generated = if (crisisSignal) {
                LocalAiGateway().generateProfile(currentIntake)
            } else {
                try {
                    aiGateway.generateProfile(currentIntake)
                } catch (_: Exception) {
                    LocalAiGateway().generateProfile(currentIntake)
                }
            }
            generatedProfile = generated
            profileConfirmed = false
            isGeneratingProfile = false
            profileMessage = if (crisisSignal) {
                "Bunu tek başına taşımak zorunda değilsin. Yakınındaki acil yardım hizmetine, güvendiğin bir kişiye veya profesyonel desteğe şimdi ulaş. Bu metin AI servisine gönderilmedi."
            } else {
                "Profil özeti cihazında saklanır. Canlı AI açıksa üretim sırasında anlatım metni Gemini API'ye gönderilir."
            }
            onReady(generated)
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
                title = "Sana göre bir başlangıç",
                subtitle = "Mola, kendi hedefin geldiğinde kısa bir durak sunar.",
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ) {
                Text(
                    text = "Hesap yok. Kayıtların cihazında kalır. Canlı AI açıksa yalnızca ilgili anlatım metni Gemini API'ye gönderilir.",
                    modifier = Modifier.padding(MolaSpacing.large),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(
                title = "Önce seni tanıyalım",
                supportingText = "İsmini ve kendi cümlelerinle kısa bir anlatımı ekle.",
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    generatedProfile = null
                    profileConfirmed = false
                },
                label = { Text("İsim · gerekli") },
                isError = attemptedSubmit && name.isBlank(),
                supportingText = if (attemptedSubmit && name.isBlank()) {
                    { Text("Devam etmek için ismini yaz.") }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MolaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.large)) {
                    Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.xSmall)) {
                        Text(
                            "Kendi cümlelerinle anlat",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Telefonu neden daha bilinçli kullanmak istediğini, boş zamanlarında neleri sevdiğini ve son günlerde seni neyin zorladığını anlat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PrimaryActionButton(
                        text = "Sesle anlat",
                        onClick = {
                            profileMessage = null
                            try {
                                voiceLauncher.launch(
                                    SpeechInput.createIntent("Kendinden ve hedeflerinden bahset"),
                                )
                            } catch (_: ActivityNotFoundException) {
                                profileMessage = "Bu telefonda sesli giriş kullanılamıyor; metinle devam edebilirsin."
                            }
                        },
                    )
                    OutlinedTextField(
                        value = biography,
                        onValueChange = {
                            biography = it
                            generatedProfile = null
                            profileConfirmed = false
                            profileMessage = null
                        },
                        label = { Text("Yazarak anlat · gerekli") },
                        placeholder = { Text("Örn. Akşamları dinlenmek için telefona yöneliyorum…") },
                        minLines = 5,
                        maxLines = 10,
                        isError = attemptedSubmit && biography.isBlank(),
                        supportingText = if (attemptedSubmit && biography.isBlank()) {
                            { Text("Devam etmek için birkaç cümle ekle.") }
                        } else {
                            { Text("Sesli anlatımın burada görünür; göndermeden önce düzenleyebilirsin.") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showOptionalDetails = !showOptionalDetails },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("İsteğe bağlı ayrıntılar", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Profili daha kişisel hale getirir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (showOptionalDetails) "Kapat" else "Ekle",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (showOptionalDetails) {
                MolaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.medium)) {
                        OutlinedTextField(
                            value = department,
                            onValueChange = {
                                department = it
                                generatedProfile = null
                                profileConfirmed = false
                            },
                            label = { Text("Bölüm / alan") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = hobbiesText,
                            onValueChange = {
                                hobbiesText = it
                                generatedProfile = null
                                profileConfirmed = false
                            },
                            label = { Text("Sevdiğin aktiviteler") },
                            supportingText = { Text("Örn. gitar, koşu, kitap, podcast") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = improvementArea,
                            onValueChange = {
                                improvementArea = it
                                generatedProfile = null
                                profileConfirmed = false
                            },
                            label = { Text("Geliştirmek istediğin alan") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = reason,
                            onValueChange = {
                                reason = it
                                generatedProfile = null
                                profileConfirmed = false
                            },
                            label = { Text("Kendi hedefin") },
                            supportingText = { Text("Örn. gece daha rahat uyumak istiyorum") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            PrimaryActionButton(
                text = if (isGeneratingProfile) "Profil hazırlanıyor" else "Profilimi hazırla",
                onClick = { generateProfile() },
                enabled = valid && !isGeneratingProfile,
                leadingContent = if (isGeneratingProfile) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(MolaSpacing.small))
                    }
                } else {
                    null
                },
            )
            profileMessage?.let { message ->
                MolaCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            generatedProfile?.let { personalization ->
                SectionTitle(
                    title = if (profileConfirmed) "Profilin hazır" else "Seni böyle anladım",
                    supportingText = "Kendi anlattıklarından hazırlanan bir başlangıç noktası.",
                )
                MolaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.large)) {
                        if (personalization.profileSummary.isNotBlank()) {
                            Text(
                                personalization.profileSummary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        ProfileTags(
                            label = "Değiştirmek istediğin",
                            values = (personalization.focusTargets.ifEmpty { personalization.goals })
                                .take(4),
                        )
                        ProfileTags(
                            label = "Sık karşılaştığın anlar",
                            values = personalization.recurringContexts,
                        )
                        ProfileTags(
                            label = "Sana uygun alternatifler",
                            values = (
                                personalization.preferredActivities +
                                    personalization.lowEnergyActivities
                                ).distinct().take(5),
                        )
                        ProfileTags(
                            label = "Hızlı seçeneklerin",
                            values = personalization.quickStatesOrDefault().take(3).map { option ->
                                listOf(option.emoji, option.label)
                                    .filter(String::isNotBlank)
                                    .joinToString(" ")
                            },
                        )
                        Text(
                            "Bu profil yalnızca anlattıklarından oluşturuldu. Mola önerilerini buna göre kişiselleştirecek.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!profileConfirmed) {
                            PrimaryActionButton(
                                text = "Doğru görünüyor",
                                onClick = { profileConfirmed = true },
                            )
                        } else {
                            Text(
                                "Profil onaylandı. İstersen anlatımını aşağıdaki bilgileri düzenleyerek değiştirebilirsin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                generatedProfile = null
                                profileConfirmed = false
                                profileMessage = "Anlatımını düzenleyebilirsin."
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Düzenle")
                        }
                    }
                }
            }

            SectionTitle(
                title = "Hedefini ayarla",
                supportingText = "Bir uygulama seç ve günlük dakika hedefini kendin belirle.",
            )
            MolaCard {
                Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.large)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hedef uygulama · gerekli", style = MaterialTheme.typography.titleSmall)
                            Text(
                                targetAppLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                targetPackage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showAppPicker = true }) {
                            Text("Değiştir")
                        }
                    }
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Günlük hedef · dakika · gerekli") },
                        supportingText = { Text("Mola bu sayıyı önermez veya değiştirmez.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = attemptedSubmit && (limit == null || limit <= 0),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionTitle(
                title = "İzinleri tamamla",
                supportingText = "Kullanım süresini okumak ve destek kartını göstermek için iki Android izni gerekir.",
            )
            MolaCard {
                Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.large)) {
                    SetupPermissionRow(
                        label = "Kullanım verisi erişimi",
                        explanation = "Yalnızca seçtiğin uygulamanın süresini okur.",
                        granted = hasUsageAccess,
                        onClick = onOpenUsagePermission,
                    )
                    SetupPermissionRow(
                        label = "Ekran üstü kart",
                        explanation = "Kendi hedefine ulaştığında müdahale kartını gösterir.",
                        granted = canDrawOverlays,
                        onClick = onOpenOverlayPermission,
                    )
                }
            }

            PrimaryActionButton(
                text = "Mola'yı kullanmaya başla",
                onClick = {
                    attemptedSubmit = true
                    if (valid) {
                        val finish: (PersonalizationProfile) -> Unit = { personalization ->
                            val finalReason = reason.trim().ifBlank {
                                personalization.goals.firstOrNull().orEmpty().ifBlank {
                                    "Telefonu daha bilinçli kullanmak istiyorum"
                                }
                            }
                            onComplete(
                                UserProfile(
                                    name = name.trim(),
                                    department = department.trim(),
                                    hobbies = hobbies.ifEmpty {
                                        personalization.preferredActivities.take(3)
                                    },
                                    improvementArea = improvementArea.trim().ifBlank {
                                        personalization.goals.firstOrNull().orEmpty()
                                    },
                                    reason = finalReason,
                                    targetAppLabel = targetAppLabel,
                                    targetPackage = targetPackage,
                                    dailyLimitMinutes = requireNotNull(limit),
                                    biography = biography.trim(),
                                    personalization = personalization,
                                ),
                            )
                        }
                        generatedProfile?.let(finish) ?: generateProfile(finish)
                    }
                },
                enabled = !isGeneratingProfile,
            )
            Text(
                "İzinleri şimdi açmasan da kurulumu bitirebilir, daha sonra Ana Sayfa'dan tamamlayabilirsin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Hedef uygulamayı seç") },
            text = {
                if (installedApps.isEmpty()) {
                    Text("Başlatılabilir uygulama bulunamadı. Instagram varsayılanı kullanılacak.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(installedApps, key = { it.packageName }) { app ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        targetAppLabel = app.label
                                        targetPackage = app.packageName
                                        showAppPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("Kapat") }
            },
        )
    }
}

@Composable
private fun ProfileTags(label: String, values: List<String>) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.small)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MolaSpacing.small),
            verticalArrangement = Arrangement.spacedBy(MolaSpacing.small),
        ) {
            values.forEach { value ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        value,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupPermissionRow(
    label: String,
    explanation: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MolaSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MolaSpacing.xSmall),
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(
                label = if (granted) "İzin açık" else "İzin gerekli",
                active = granted,
            )
        }
        TextButton(onClick = onClick) {
            Text(if (granted) "Ayarlar" else "İzin ver")
        }
    }
}
