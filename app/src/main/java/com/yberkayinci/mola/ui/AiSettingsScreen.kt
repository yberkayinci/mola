package com.yberkayinci.mola.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.yberkayinci.mola.ui.components.MolaCard
import com.yberkayinci.mola.ui.components.MolaScreen
import com.yberkayinci.mola.ui.components.MolaTopBar
import com.yberkayinci.mola.ui.components.PrimaryActionButton
import com.yberkayinci.mola.ui.components.SecondaryActionButton
import com.yberkayinci.mola.ui.components.SectionTitle
import com.yberkayinci.mola.ui.components.StatusPill
import com.yberkayinci.mola.ui.theme.MolaSpacing

/**
 * Optional AI setup.
 *
 * The screen's job is to make clear that the local engine is complete on its own, so entering a key
 * reads as an upgrade the user chooses rather than a step they must finish.
 */
@Composable
fun AiSettingsScreen(
    hasApiKey: Boolean,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onBack: () -> Unit,
) {
    var keyText by rememberSaveable { mutableStateOf("") }
    var revealKey by rememberSaveable { mutableStateOf(false) }

    MolaScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MolaSpacing.xLarge, vertical = MolaSpacing.xxLarge),
            verticalArrangement = Arrangement.spacedBy(MolaSpacing.xLarge),
        ) {
            MolaTopBar(
                eyebrow = "AYARLAR",
                title = "Yazı motoru",
                subtitle = "Mola cihazında çalışan motorla eksiksiz çalışır. İstersen kendi anahtarınla daha akıcı metinler üretebilirsin.",
                trailing = { StatusPill(label = if (hasApiKey) "Kendi anahtarın" else "Cihaz içi", active = hasApiKey) },
            )

            SectionTitle(
                title = "Şu anda hangi motor çalışıyor?",
                supportingText = if (hasApiKey) {
                    "Kendi Google AI Studio anahtarın kullanılıyor. İstek başarısız olursa Mola sessizce cihaz içi motora döner."
                } else {
                    "Cihaz içi motor kullanılıyor. Hiçbir metin cihazından çıkmıyor."
                },
            )

            MolaCard {
                Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.medium)) {
                    Text(
                        "Cihaz içi motor Mola'nın temeli: internet, hesap ve anahtar istemez, uçak modunda da çalışır. Kart, profil ve günlük yansıma bu motorla da üretilir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Kendi anahtarını girersen istekler senin Google AI Studio projene gider ve kotan senin olur. Mola uygulamanın içinde hiçbir anahtar taşımaz; anahtarın yalnızca bu cihazın uygulamaya özel alanında saklanır, yedeklemeye ve cihaz aktarımına dahil edilmez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Anahtar girdiğinde onboarding metnin, müdahale kartların ve günlük yansıman Google'a gönderilir. Kriz içeren ifadeler hiçbir durumda gönderilmez, cihazda kalır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle(
                title = "Kendi anahtarın",
                supportingText = "aistudio.google.com/apikey adresinden ücretsiz bir anahtar oluşturabilirsin.",
            )
            MolaCard {
                Column(verticalArrangement = Arrangement.spacedBy(MolaSpacing.medium)) {
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Google AI Studio API anahtarı") },
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        visualTransformation = if (revealKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        supportingText = {
                            Text(
                                if (hasApiKey) {
                                    "Kayıtlı bir anahtar var. Yeni bir anahtar girmen eskisini değiştirir."
                                } else {
                                    "Boş bırakırsan Mola cihaz içi motorla çalışmaya devam eder."
                                },
                            )
                        },
                    )
                    TextButton(onClick = { revealKey = !revealKey }) {
                        Text(if (revealKey) "Anahtarı gizle" else "Anahtarı göster")
                    }
                    PrimaryActionButton(
                        text = "Anahtarı kaydet",
                        onClick = {
                            onSaveApiKey(keyText)
                            keyText = ""
                        },
                        enabled = keyText.isNotBlank(),
                    )
                    if (hasApiKey) {
                        TextButton(
                            onClick = {
                                onClearApiKey()
                                keyText = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Anahtarı sil ve cihaz içi motora dön",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            SecondaryActionButton(text = "Geri dön", onClick = onBack)
        }
    }
}
