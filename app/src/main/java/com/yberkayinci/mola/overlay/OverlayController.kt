package com.yberkayinci.mola.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yberkayinci.mola.ai.AiGateway
import com.yberkayinci.mola.ai.CrisisFilter
import com.yberkayinci.mola.ai.NeedHypothesis
import com.yberkayinci.mola.ai.LocalAiGateway
import com.yberkayinci.mola.ai.SafetyLanguageValidator
import com.yberkayinci.mola.data.AiCard
import com.yberkayinci.mola.data.MolaRepository
import com.yberkayinci.mola.data.InterventionInput
import com.yberkayinci.mola.data.InterventionInputMethod
import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.JsonMolaRepository
import com.yberkayinci.mola.data.UserChoice
import com.yberkayinci.mola.data.UserProfile
import com.yberkayinci.mola.usage.InterventionTrigger
import com.yberkayinci.mola.voice.VoiceInputCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayController(
    context: Context,
    private val repository: MolaRepository = JsonMolaRepository(context),
    private val aiGateway: AiGateway = LocalAiGateway(),
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayView: View? = null
    private var requestJob: Job? = null

    private val ink = Color.rgb(27, 33, 31)
    private val mutedInk = Color.rgb(93, 102, 98)
    private val warmBackground = Color.rgb(247, 244, 238)
    private val warmSurface = Color.rgb(255, 252, 247)
    private val warmOutline = Color.rgb(203, 199, 189)
    private val accent = Color.rgb(36, 107, 92)
    private val accentContainer = Color.rgb(217, 238, 231)
    private val errorInk = Color.rgb(125, 47, 42)
    private val errorContainer = Color.rgb(255, 218, 213)

    val isShowing: Boolean
        get() = overlayView != null

    fun show(
        profile: UserProfile,
        usageMinutes: Int,
        trigger: InterventionTrigger = InterventionTrigger.THRESHOLD,
        hypothesis: NeedHypothesis? = null,
    ): Boolean {
        if (isShowing || !Settings.canDrawOverlays(appContext)) return false

        // Null unless the user is asked to confirm a guess, so an unanswered moment is never
        // recorded as a rejection.
        var hypothesisAccepted: Boolean? = null
        val hypothesisLabel = hypothesis?.let { guess ->
            profile.personalization.quickStatesOrDefault()
                .firstOrNull { it.id == guess.stateId }
                ?.label
                ?.takeIf(String::isNotBlank)
                ?: guess.defaultLabel
        }

        val root = FrameLayout(appContext).apply {
            setBackgroundColor(Color.argb(184, 16, 22, 19))
            isClickable = true
            isFocusable = true
        }
        val scroll = ScrollView(appContext).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(18.dp, 28.dp, 18.dp, 28.dp)
        }
        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 24.dp, 22.dp, 24.dp)
            background = roundedBackground(warmBackground, 28f)
            elevation = 8.dp.toFloat()
        }
        scroll.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER },
        )
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val brand = TextView(appContext).apply {
            text = "MOLA · KISA BİR DURAK"
            textSize = 12f
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val headline = TextView(appContext).apply {
            text = when (trigger) {
                InterventionTrigger.THRESHOLD ->
                    "${profile.targetAppLabel}: bugün $usageMinutes dakika. Kendi hedefin ${profile.dailyLimitMinutes} dakika."

                InterventionTrigger.IMMEDIATE_REOPEN ->
                    "${profile.targetAppLabel} az önce kapanıp yeniden açıldı. Bugün $usageMinutes dakika oldu."

                InterventionTrigger.RAPID_REOPEN_LOOP ->
                    "Birkaç dakikadır ${profile.targetAppLabel} açılıp kapanıyor. Bugün $usageMinutes dakika oldu."

                InterventionTrigger.SESSION_DRIFT ->
                    "Bu oturum her zamankinden uzun sürüyor. Bugün $usageMinutes dakika oldu."
            }
            textSize = 15f
            setTextColor(mutedInk)
        }
        val question = TextView(appContext).apply {
            text = "Şu an seni burada tutan ne?"
            textSize = 25f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        }
        val choices = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        val hypothesisBlock = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val customActions = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val input = EditText(appContext).apply {
            hint = "Kendi kelimelerinle anlat"
            minLines = 3
            maxLines = 6
            setTextColor(ink)
            setHintTextColor(mutedInk)
            textSize = 16f
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            background = roundedStrokeBackground(warmSurface, 16f, warmOutline)
            visibility = View.GONE
        }
        val submit = Button(appContext).apply {
            text = "Yanıtı değerlendir"
            stylePrimaryButton(this)
            visibility = View.GONE
        }
        val status = TextView(appContext).apply {
            visibility = View.GONE
            textSize = 15f
            setTextColor(mutedInk)
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
        }
        val resultContainer = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val closeButton = Button(appContext).apply {
            text = "Şimdi değil"
            styleSecondaryButton(this)
        }

        var customInputMethod = InterventionInputMethod.TEXT

        fun setControlsEnabled(enabled: Boolean) {
            for (index in 0 until hypothesisBlock.childCount) {
                hypothesisBlock.getChildAt(index).isEnabled = enabled
            }
            for (index in 0 until choices.childCount) {
                choices.getChildAt(index).isEnabled = enabled
            }
            for (index in 0 until customActions.childCount) {
                customActions.getChildAt(index).isEnabled = enabled
            }
            input.isEnabled = enabled
            submit.isEnabled = enabled
            closeButton.isEnabled = enabled
        }

        fun showCustomInput(method: InterventionInputMethod) {
            customInputMethod = method
            input.visibility = View.VISIBLE
            submit.visibility = View.VISIBLE
            resultContainer.visibility = View.GONE
            if (method == InterventionInputMethod.TEXT) {
                input.requestFocus()
                appContext.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        fun showCrisis() {
            hypothesisBlock.visibility = View.GONE
            choices.visibility = View.GONE
            customActions.visibility = View.GONE
            input.visibility = View.GONE
            submit.visibility = View.GONE
            closeButton.visibility = View.GONE
            question.text = "Şu an destek önemli"
            status.apply {
                visibility = View.VISIBLE
                setTextColor(errorInk)
                background = roundedBackground(errorContainer, 16f)
                text = "Bunu tek başına taşımak zorunda değilsin. Yakınındaki acil yardım hizmetine, güvendiğin bir kişiye veya profesyonel desteğe şimdi ulaş. Bu metin AI servisine gönderilmedi."
            }
            resultContainer.removeAllViews()
            resultContainer.background = null
            resultContainer.setPadding(0, 0, 0, 0)
            resultContainer.addView(Button(appContext).apply {
                text = "Kapat"
                styleSecondaryButton(this)
                setOnClickListener { dismiss() }
            })
            resultContainer.visibility = View.VISIBLE
        }

        fun renderCard(inputData: InterventionInput, cardResult: AiCard) {
            if (overlayView !== root) return
            hypothesisBlock.visibility = View.GONE
            choices.visibility = View.GONE
            customActions.visibility = View.GONE
            input.visibility = View.GONE
            submit.visibility = View.GONE
            closeButton.visibility = View.GONE
            status.visibility = View.GONE
            question.text = "Bir an için bunu deneyebilirsin"
            resultContainer.removeAllViews()
            resultContainer.background = roundedBackground(accentContainer, 20f)
            resultContainer.setPadding(18.dp, 18.dp, 18.dp, 18.dp)
            if (cardResult.reflection.isNotBlank()) {
                resultContainer.addView(TextView(appContext).apply {
                    text = cardResult.reflection
                    textSize = 14f
                    setTextColor(mutedInk)
                }, matchWrap(bottom = 8.dp))
            }
            resultContainer.addView(TextView(appContext).apply {
                text = "DÜŞÜNMEK İÇİN"
                textSize = 11f
                setTextColor(accent)
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.06f
            }, matchWrap(bottom = 8.dp))
            resultContainer.addView(TextView(appContext).apply {
                text = cardResult.question
                textSize = 20f
                setTextColor(ink)
                setTypeface(typeface, Typeface.BOLD)
            }, matchWrap(bottom = 14.dp))
            val activityHeader = LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            activityHeader.addView(TextView(appContext).apply {
                text = cardResult.activityTitle.ifBlank { "Küçük adım" }
                textSize = 15f
                setTextColor(accent)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (cardResult.durationMinutes > 0) {
                activityHeader.addView(TextView(appContext).apply {
                    text = "${cardResult.durationMinutes} dk"
                    textSize = 13f
                    setTextColor(accent)
                    setTypeface(typeface, Typeface.BOLD)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ))
            }
            resultContainer.addView(activityHeader, matchWrap(bottom = 4.dp))
            resultContainer.addView(TextView(appContext).apply {
                text = cardResult.alternative
                textSize = 17f
                setTextColor(ink)
            }, matchWrap(bottom = 18.dp))

            val actions = LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
            }
            actions.addView(Button(appContext).apply {
                text = "Bunu deneyeceğim"
                stylePrimaryButton(this)
                setOnClickListener {
                    saveRecord(inputData, cardResult, usageMinutes, UserChoice.STOPPED, trigger, hypothesis, hypothesisAccepted)
                    dismiss()
                    openLauncher()
                }
            }, matchWrap(bottom = 8.dp))
            actions.addView(Button(appContext).apply {
                text = "Yine de devam et"
                styleSecondaryButton(this)
                setOnClickListener {
                    saveRecord(inputData, cardResult, usageMinutes, UserChoice.CONTINUE, trigger, hypothesis, hypothesisAccepted)
                    dismiss()
                }
            }, matchWrap())
            resultContainer.addView(actions, matchWrap())
            resultContainer.visibility = View.VISIBLE
        }

        fun requestCard(inputData: InterventionInput) {
            if (inputData.text.isBlank() || requestJob?.isActive == true) return
            hideKeyboard(input)
            if (CrisisFilter.check(inputData.text).isCrisisSignal) {
                showCrisis()
                return
            }

            setControlsEnabled(false)
            status.apply {
                visibility = View.VISIBLE
                setTextColor(ink)
                background = roundedBackground(accentContainer, 16f)
                text = "Sana uygun küçük bir seçenek hazırlanıyor…"
            }
            val recentRecords = repository.loadRecords().takeLast(6)
            requestJob = scope.launch {
                val generated = try {
                    aiGateway.generateCard(profile, usageMinutes, inputData, recentRecords)
                } catch (_: Exception) {
                    LocalAiGateway().generateCard(profile, usageMinutes, inputData, recentRecords)
                }
                val safeResult = if (
                    SafetyLanguageValidator.isDisplaySafe(
                        generated.reflection,
                        generated.question,
                        generated.activityTitle,
                        generated.alternative,
                    )
                ) {
                    generated
                } else {
                    LocalAiGateway().generateCard(profile, usageMinutes, inputData, recentRecords)
                }
                renderCard(inputData, safeResult)
            }
        }

        profile.personalization.quickStatesOrDefault().take(3).forEach { option ->
            choices.addView(Button(appContext).apply {
                text = listOf(option.emoji, option.label)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                setAllCaps(false)
                styleQuickStateButton(this)
                setOnClickListener {
                    requestCard(
                        InterventionInput(
                            text = option.label,
                            stateId = option.id,
                            stateLabel = option.label,
                            method = InterventionInputMethod.QUICK_REPLY,
                        ),
                    )
                }
            }, matchWrap(bottom = 8.dp))
        }

        // When behaviour is unambiguous enough, offer the reading instead of an open question: at a
        // difficult moment one tap is a far smaller ask than describing yourself in writing. The
        // answer is recorded either way, because a rejected guess is what keeps the guessing honest.
        if (hypothesis != null && !hypothesisLabel.isNullOrBlank()) {
            question.text = "Bu, “$hypothesisLabel” gibi görünüyor. Öyle mi?"
            choices.visibility = View.GONE
            customActions.visibility = View.GONE
            hypothesisBlock.visibility = View.VISIBLE
            hypothesisBlock.addView(
                Button(appContext).apply {
                    text = "Evet, öyle"
                    setAllCaps(false)
                    stylePrimaryButton(this)
                    setOnClickListener {
                        hypothesisAccepted = true
                        requestCard(
                            InterventionInput(
                                text = hypothesisLabel,
                                stateId = hypothesis.stateId,
                                stateLabel = hypothesisLabel,
                                method = InterventionInputMethod.QUICK_REPLY,
                            ),
                        )
                    }
                },
                matchWrap(bottom = 8.dp),
            )
            hypothesisBlock.addView(
                Button(appContext).apply {
                    text = "Hayır, başka bir şey"
                    setAllCaps(false)
                    styleSecondaryButton(this)
                    setOnClickListener {
                        hypothesisAccepted = false
                        hypothesisBlock.visibility = View.GONE
                        question.text = "Şu an seni burada tutan ne?"
                        choices.visibility = View.VISIBLE
                        customActions.visibility = View.VISIBLE
                    }
                },
                matchWrap(),
            )
        }

        customActions.addView(Button(appContext).apply {
            text = "✏️ Yaz"
            styleSecondaryButton(this)
            setOnClickListener {
                status.visibility = View.GONE
                showCustomInput(InterventionInputMethod.TEXT)
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = 6.dp
        })
        customActions.addView(Button(appContext).apply {
            text = "🎙 Anlat"
            styleSecondaryButton(this)
            setOnClickListener {
                hideKeyboard(input)
                root.visibility = View.GONE
                val started = VoiceInputCoordinator.request(
                    context = appContext,
                    prompt = "Şu an ne olduğunu anlat",
                ) { spokenText ->
                    if (overlayView !== root) return@request
                    root.visibility = View.VISIBLE
                    if (spokenText.isNullOrBlank()) {
                        showCustomInput(InterventionInputMethod.TEXT)
                        status.apply {
                            visibility = View.VISIBLE
                            text = "Sesli yanıt alınamadı; yazarak devam edebilirsin."
                        }
                    } else {
                        input.setText(spokenText)
                        status.visibility = View.GONE
                        showCustomInput(InterventionInputMethod.VOICE)
                    }
                }
                if (!started) {
                    root.visibility = View.VISIBLE
                    showCustomInput(InterventionInputMethod.TEXT)
                    status.apply {
                        visibility = View.VISIBLE
                        text = "Bu telefonda sesli giriş kullanılamıyor; yazarak devam edebilirsin."
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 6.dp
        })

        submit.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isBlank()) {
                input.error = "Bir cümle yaz ya da anlat"
                input.requestFocus()
            } else {
                requestCard(
                    InterventionInput(
                        text = text,
                        method = customInputMethod,
                    ),
                )
            }
        }
        closeButton.setOnClickListener { dismiss() }

        card.addView(brand, matchWrap(bottom = 8.dp))
        card.addView(headline, matchWrap(bottom = 16.dp))
        card.addView(question, matchWrap(bottom = 18.dp))
        card.addView(hypothesisBlock, matchWrap(bottom = 6.dp))
        card.addView(choices, matchWrap(bottom = 6.dp))
        card.addView(customActions, matchWrap(bottom = 10.dp))
        card.addView(input, matchWrap(bottom = 8.dp))
        card.addView(submit, matchWrap(bottom = 8.dp))
        card.addView(status, matchWrap(bottom = 10.dp))
        card.addView(resultContainer, matchWrap(bottom = 8.dp))
        card.addView(closeButton, matchWrap())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        return runCatching {
            windowManager.addView(root, params)
            overlayView = root
            true
        }.getOrElse {
            overlayView = null
            false
        }
    }

    fun dismiss() {
        requestJob?.cancel()
        requestJob = null
        VoiceInputCoordinator.cancel()
        val view = overlayView ?: return
        overlayView = null
        runCatching { windowManager.removeView(view) }
    }

    fun close() {
        dismiss()
        scope.cancel()
    }

    private fun saveRecord(
        input: InterventionInput,
        card: AiCard,
        usageMinutes: Int,
        choice: UserChoice,
        trigger: InterventionTrigger,
        hypothesis: NeedHypothesis?,
        accepted: Boolean?,
    ) {
        repository.appendRecord(
            InterventionRecord(
                timestampEpochMillis = System.currentTimeMillis(),
                usageMinutes = usageMinutes,
                text = input.text,
                choice = choice,
                stateId = input.stateId,
                stateLabel = input.stateLabel,
                inputMethod = input.method,
                aiQuestion = card.question,
                aiAlternative = card.alternative,
                aiReflection = card.reflection,
                aiActivityTitle = card.activityTitle,
                aiDurationMinutes = card.durationMinutes,
                aiStrategy = card.strategy,
                trigger = trigger.storageValue,
                hypothesisStateId = hypothesis?.stateId.orEmpty(),
                hypothesisAccepted = accepted,
            ),
        )
    }

    private fun openLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    private fun hideKeyboard(view: View) {
        appContext.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp.dp
        }

    private fun roundedStrokeBackground(
        color: Int,
        radiusDp: Float,
        strokeColor: Int,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp.dp
        setStroke(1.dp, strokeColor)
    }

    private fun stylePrimaryButton(button: Button) {
        button.apply {
            setAllCaps(false)
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            minHeight = 54.dp
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
            background = roundedBackground(accent, 16f)
        }
    }

    private fun styleSecondaryButton(button: Button) {
        button.apply {
            setAllCaps(false)
            textSize = 15f
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            minHeight = 52.dp
            setPadding(14.dp, 11.dp, 14.dp, 11.dp)
            background = roundedStrokeBackground(warmSurface, 16f, warmOutline)
        }
    }

    private fun styleQuickStateButton(button: Button) {
        button.apply {
            setAllCaps(false)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textSize = 16f
            setTextColor(ink)
            minHeight = 60.dp
            setPadding(18.dp, 13.dp, 18.dp, 13.dp)
            background = roundedStrokeBackground(warmSurface, 16f, warmOutline)
        }
    }

    private fun matchWrap(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = bottom }

    private val Int.dp: Int
        get() = (this * appContext.resources.displayMetrics.density).toInt()

    private val Float.dp: Float
        get() = this * appContext.resources.displayMetrics.density
}
