package com.yberkayinci.mola.ai

import com.yberkayinci.mola.usage.InterventionTrigger
import com.yberkayinci.mola.usage.InterventionTriggerPolicy
import com.yberkayinci.mola.usage.NeedSignals

/**
 * A guess about what the user currently needs, with the evidence that produced it.
 *
 * It is offered to the user as a question, never applied silently. [reasons] exist so the guess can
 * be explained and audited rather than being an opaque verdict about the person.
 */
data class NeedHypothesis(
    val stateId: String,
    val defaultLabel: String,
    val reasons: List<String>,
) {
    val confidence: Int
        get() = reasons.size
}

/**
 * Reads behaviour instead of asking about it.
 *
 * Deliberately a small set of transparent rules rather than a model: the conclusion is shown to the
 * user and confirmed in one tap, so every rule has to be explainable in a single line. Nothing here
 * diagnoses the person — the output is a situation, offered as a question, that the user can reject.
 */
object NeedInference {
    /** Supporting signals required before a guess is worth showing at all. */
    const val MIN_CONFIDENCE: Int = 2

    const val STATE_LATE_NIGHT: String = "late_night"
    const val STATE_HABIT: String = "habit"
    const val STATE_TIRED: String = "tired"
    const val STATE_BORED: String = "bored"

    private const val LONG_RUN_MILLIS = 90L * 60L * 1_000L
    private const val VERY_LONG_RUN_MILLIS = 150L * 60L * 1_000L
    private const val EVENING_RUN_MILLIS = 45L * 60L * 1_000L
    private const val LONG_SESSION_MILLIS = 20L * 60L * 1_000L
    private const val VERY_SHORT_SESSION_MILLIS = 45L * 1_000L
    private const val MEDIUM_SESSION_MILLIS = 60L * 1_000L

    /** Most specific first: a late-night pattern explains more than plain fatigue. */
    private val PRIORITY = listOf(STATE_LATE_NIGHT, STATE_HABIT, STATE_TIRED, STATE_BORED)

    private val DEFAULT_LABELS = mapOf(
        STATE_LATE_NIGHT to "Uyumadan önce bakıyorum",
        STATE_HABIT to "Alışkanlıkla açtım",
        STATE_TIRED to "Biraz yoruldum",
        STATE_BORED to "Biraz sıkıldım",
    )

    fun infer(signals: NeedSignals): NeedHypothesis? {
        val reasons = mutableMapOf<String, MutableList<String>>()
        fun add(stateId: String, reason: String) {
            reasons.getOrPut(stateId) { mutableListOf() }.add(reason)
        }

        val pattern = signals.pattern
        val isLateHour = signals.hourOfDay >= 23 || signals.hourOfDay <= 4
        val openFloor = InterventionTriggerPolicy.RAPID_REOPEN_MIN_OPENS

        if (isLateHour) add(STATE_LATE_NIGHT, "late_hour")
        if (isLateHour && signals.isCharging) add(STATE_LATE_NIGHT, "charging_at_night")
        if (isLateHour && pattern.continuousActivityMillis >= EVENING_RUN_MILLIS) {
            add(STATE_LATE_NIGHT, "long_run_at_night")
        }

        if (pattern.continuousActivityMillis >= LONG_RUN_MILLIS) {
            add(STATE_TIRED, "long_continuous_use")
        }
        if (pattern.continuousActivityMillis >= VERY_LONG_RUN_MILLIS) {
            add(STATE_TIRED, "very_long_continuous_use")
        }
        if (signals.hourOfDay in 0..4) add(STATE_TIRED, "small_hours")

        if (signals.trigger == InterventionTrigger.IMMEDIATE_REOPEN) {
            add(STATE_HABIT, "reopened_within_seconds")
        }
        if (pattern.targetOpenCount >= openFloor) {
            add(STATE_HABIT, "several_opens_in_short_window")
        }
        if (pattern.medianSessionMillis in 1 until VERY_SHORT_SESSION_MILLIS) {
            add(STATE_HABIT, "very_short_sessions")
        }

        if (signals.trigger == InterventionTrigger.SESSION_DRIFT) {
            add(STATE_BORED, "much_longer_than_usual")
        }
        if (
            pattern.targetOpenCount >= openFloor &&
            pattern.medianSessionMillis >= MEDIUM_SESSION_MILLIS
        ) {
            add(STATE_BORED, "repeated_medium_sessions")
        }
        if (pattern.currentSessionMillis >= LONG_SESSION_MILLIS) {
            add(STATE_BORED, "long_current_session")
        }

        val best = PRIORITY.firstOrNull { stateId ->
            (reasons[stateId]?.size ?: 0) >= MIN_CONFIDENCE
        } ?: return null

        return NeedHypothesis(
            stateId = best,
            defaultLabel = DEFAULT_LABELS[best].orEmpty(),
            reasons = reasons.getValue(best).distinct(),
        )
    }
}
