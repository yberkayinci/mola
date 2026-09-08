package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.UserChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedInferenceCalibrationTest {
    @Test
    fun aNewGuessIsTrustedUntilThereIsEvidenceAgainstIt() {
        assertTrue(NeedInferenceCalibration.isTrusted(emptyList(), "tired"))
    }

    @Test
    fun aFewRejectionsAreNotEnoughToStopGuessing() {
        val records = answers("tired", accepted = 0, rejected = 2)

        assertTrue(NeedInferenceCalibration.isTrusted(records, "tired"))
    }

    @Test
    fun aRepeatedlyWrongGuessStopsBeingOffered() {
        val records = answers("tired", accepted = 0, rejected = 4)

        assertFalse(NeedInferenceCalibration.isTrusted(records, "tired"))
    }

    @Test
    fun aGuessTheUserKeepsConfirmingStaysTrusted() {
        val records = answers("late_night", accepted = 5, rejected = 1)

        assertTrue(NeedInferenceCalibration.isTrusted(records, "late_night"))
    }

    @Test
    fun evidenceAboutOneStateDoesNotSilenceAnother() {
        val records = answers("tired", accepted = 0, rejected = 5)

        assertFalse(NeedInferenceCalibration.isTrusted(records, "tired"))
        assertTrue(NeedInferenceCalibration.isTrusted(records, "habit"))
    }

    @Test
    fun unansweredMomentsAreNotCountedAsRejections() {
        val records = (1..8).map { index ->
            record(
                timestampEpochMillis = BASE + index * 60_000L,
                hypothesisStateId = "tired",
                hypothesisAccepted = null,
            )
        }

        assertTrue(NeedInferenceCalibration.isTrusted(records, "tired"))
        assertEquals(0, NeedInferenceCalibration.accuracy(records, "tired").answered)
    }

    @Test
    fun accuracyReportsWhatTheUserActuallyAnswered() {
        val accuracy = NeedInferenceCalibration.accuracy(
            answers("bored", accepted = 3, rejected = 1),
            "bored",
        )

        assertEquals(4, accuracy.answered)
        assertEquals(3, accuracy.accepted)
        assertEquals(0.75, accuracy.acceptanceRatio, 0.0001)
    }

    @Test
    fun filteringRemovesAnUntrustedGuessAndKeepsATrustedOne() {
        val hypothesis = NeedHypothesis("tired", "Biraz yoruldum", listOf("a", "b"))

        assertNull(
            NeedInferenceCalibration.filter(
                hypothesis,
                answers("tired", accepted = 0, rejected = 4),
            ),
        )
        assertNotNull(NeedInferenceCalibration.filter(hypothesis, emptyList()))
        assertNull(NeedInferenceCalibration.filter(null, emptyList()))
    }

    private fun answers(
        stateId: String,
        accepted: Int,
        rejected: Int,
    ): List<InterventionRecord> = buildList {
        repeat(accepted) { index ->
            add(record(BASE + index * 60_000L, stateId, hypothesisAccepted = true))
        }
        repeat(rejected) { index ->
            add(record(BASE + (accepted + index) * 60_000L, stateId, hypothesisAccepted = false))
        }
    }

    private fun record(
        timestampEpochMillis: Long,
        hypothesisStateId: String,
        hypothesisAccepted: Boolean?,
    ): InterventionRecord = InterventionRecord(
        timestampEpochMillis = timestampEpochMillis,
        usageMinutes = 45,
        text = "demo",
        choice = UserChoice.STOPPED,
        stateId = hypothesisStateId,
        stateLabel = hypothesisStateId,
        hypothesisStateId = hypothesisStateId,
        hypothesisAccepted = hypothesisAccepted,
    )

    private companion object {
        const val BASE = 1_756_000_000_000L
    }
}
