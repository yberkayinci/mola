package com.yberkayinci.mola.ai

import com.yberkayinci.mola.usage.InterventionTrigger
import com.yberkayinci.mola.usage.NeedSignals
import com.yberkayinci.mola.usage.UsagePatternSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedInferenceTest {
    @Test
    fun oneSupportingSignalIsNotEnoughToGuess() {
        val hypothesis = NeedInference.infer(
            signals(hourOfDay = 15, continuousActivityMillis = minutes(95)),
        )

        assertNull("a single signal must fall back to asking", hypothesis)
    }

    @Test
    fun aVeryLongUnbrokenRunReadsAsFatigue() {
        val hypothesis = NeedInference.infer(
            signals(hourOfDay = 16, continuousActivityMillis = minutes(160)),
        )

        assertEquals(NeedInference.STATE_TIRED, hypothesis?.stateId)
        assertTrue(hypothesis!!.confidence >= NeedInference.MIN_CONFIDENCE)
    }

    @Test
    fun lateHourWhileChargingReadsAsBedtimeUse() {
        val hypothesis = NeedInference.infer(
            signals(hourOfDay = 1, isCharging = true),
        )

        assertEquals(NeedInference.STATE_LATE_NIGHT, hypothesis?.stateId)
        assertTrue(hypothesis!!.reasons.contains("charging_at_night"))
    }

    @Test
    fun reopeningRepeatedlyReadsAsHabit() {
        val hypothesis = NeedInference.infer(
            signals(
                hourOfDay = 15,
                trigger = InterventionTrigger.IMMEDIATE_REOPEN,
                targetOpenCount = 4,
            ),
        )

        assertEquals(NeedInference.STATE_HABIT, hypothesis?.stateId)
        assertTrue(hypothesis!!.reasons.contains("reopened_within_seconds"))
    }

    @Test
    fun aSessionFarLongerThanUsualReadsAsBoredom() {
        val hypothesis = NeedInference.infer(
            signals(
                hourOfDay = 15,
                trigger = InterventionTrigger.SESSION_DRIFT,
                currentSessionMillis = minutes(25),
                medianSessionMillis = minutes(5),
            ),
        )

        assertEquals(NeedInference.STATE_BORED, hypothesis?.stateId)
    }

    @Test
    fun theMoreSpecificNightPatternWinsOverPlainFatigue() {
        val hypothesis = NeedInference.infer(
            signals(
                hourOfDay = 2,
                isCharging = true,
                continuousActivityMillis = minutes(160),
            ),
        )

        assertEquals(NeedInference.STATE_LATE_NIGHT, hypothesis?.stateId)
    }

    @Test
    fun anOrdinaryMomentProducesNoGuessAtAll() {
        val hypothesis = NeedInference.infer(
            signals(hourOfDay = 14, currentSessionMillis = minutes(4)),
        )

        assertNull(hypothesis)
    }

    @Test
    fun everyGuessCarriesTheEvidenceThatProducedIt() {
        val hypothesis = NeedInference.infer(
            signals(hourOfDay = 1, isCharging = true),
        )

        assertTrue(hypothesis!!.reasons.isNotEmpty())
        assertEquals(hypothesis.reasons.size, hypothesis.confidence)
        assertTrue(hypothesis.defaultLabel.isNotBlank())
    }

    private fun signals(
        hourOfDay: Int,
        trigger: InterventionTrigger = InterventionTrigger.THRESHOLD,
        isCharging: Boolean = false,
        targetOpenCount: Int = 1,
        currentSessionMillis: Long = 0L,
        medianSessionMillis: Long = 0L,
        continuousActivityMillis: Long = 0L,
    ): NeedSignals = NeedSignals.of(
        pattern = UsagePatternSnapshot(
            targetOpenCount = targetOpenCount,
            isTargetForeground = true,
            currentSessionMillis = currentSessionMillis,
            completedSessionCount = 0,
            medianSessionMillis = medianSessionMillis,
            lastGapMillis = UsagePatternSnapshot.UNKNOWN_GAP,
            previousPackage = "",
            continuousActivityMillis = continuousActivityMillis,
        ),
        trigger = trigger,
        hourOfDay = hourOfDay,
        isCharging = isCharging,
        usageMinutes = 40,
        dailyLimitMinutes = 60,
    )

    private fun minutes(value: Long): Long = value * 60L * 1_000L
}
