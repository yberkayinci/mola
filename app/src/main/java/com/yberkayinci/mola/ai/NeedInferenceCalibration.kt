package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.InterventionRecord

/** How well one guessed state has matched this user's own answers. */
data class NeedHypothesisAccuracy(
    val stateId: String,
    val answered: Int,
    val accepted: Int,
) {
    val acceptanceRatio: Double
        get() = if (answered == 0) 0.0 else accepted.toDouble() / answered.toDouble()
}

/**
 * Stops Mola from repeating a guess this user keeps rejecting.
 *
 * A wrong guess is worse than no guess: being told "this looks like fatigue" when it is not erodes
 * the trust the whole product depends on. So the app measures its own hit rate per state, from the
 * user's one-tap answers, and goes back to simply asking when it has been wrong often enough.
 *
 * Silence is not evidence. Only moments where the user actually answered are counted, and a guess
 * is never suppressed before [MIN_ANSWERS] answers exist for it.
 */
object NeedInferenceCalibration {
    /** Answers required before the app is willing to conclude it guesses this state badly. */
    const val MIN_ANSWERS: Int = 3

    /** At or below this acceptance ratio the guess stops being offered. */
    const val MIN_ACCEPTANCE_RATIO: Double = 0.34

    /** Only the most recent answers matter, so an early bad run is not permanent. */
    const val MAX_CONSIDERED_ANSWERS: Int = 30

    fun accuracy(
        records: List<InterventionRecord>,
        stateId: String,
    ): NeedHypothesisAccuracy {
        val normalized = stateId.trim().lowercase()
        if (normalized.isEmpty()) {
            return NeedHypothesisAccuracy(stateId = "", answered = 0, accepted = 0)
        }

        val answers = records
            .asSequence()
            .filter { it.hypothesisStateId.trim().lowercase() == normalized }
            .mapNotNull { record -> record.hypothesisAccepted?.let { record to it } }
            .sortedBy { (record, _) -> record.timestampEpochMillis }
            .toList()
            .takeLast(MAX_CONSIDERED_ANSWERS)

        return NeedHypothesisAccuracy(
            stateId = normalized,
            answered = answers.size,
            accepted = answers.count { (_, accepted) -> accepted },
        )
    }

    /** Whether this guess may still be shown to the user. */
    fun isTrusted(
        records: List<InterventionRecord>,
        stateId: String,
    ): Boolean {
        val accuracy = accuracy(records, stateId)
        if (accuracy.answered < MIN_ANSWERS) return true
        return accuracy.acceptanceRatio > MIN_ACCEPTANCE_RATIO
    }

    /** Returns the hypothesis only when this user's own answers still support offering it. */
    fun filter(
        hypothesis: NeedHypothesis?,
        records: List<InterventionRecord>,
    ): NeedHypothesis? =
        hypothesis?.takeIf { isTrusted(records, it.stateId) }
}
