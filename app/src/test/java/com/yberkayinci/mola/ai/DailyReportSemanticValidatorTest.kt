package com.yberkayinci.mola.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReportSemanticValidatorTest {
    private val evidence = DailyReportEvidence(
        totalRecords = 8,
        states = listOf(
            StateChoiceEvidence(
                stateId = "procrastinating",
                stateLabel = "Bir şeyi erteliyorum",
                count = 3,
                continuedCount = 2,
                stoppedCount = 1,
            ),
            StateChoiceEvidence(
                stateId = "tired",
                stateLabel = "Biraz yoruldum",
                count = 2,
                continuedCount = 1,
                stoppedCount = 1,
            ),
        ),
        candidateStateIds = setOf("procrastinating", "tired"),
        dominantStateId = "procrastinating",
        higherContinueStateId = "procrastinating",
        timeBucketCounts = mapOf("evening" to 4),
    )

    @Test
    fun acceptsEvidenceBackedTentativeReflection() {
        val result = DailyReportSemanticValidator.validate(
            StructuredDailyReflection(
                evidenceStateId = "procrastinating",
                observationQuestion = "Erteleme dediğin anlarda devam etme kararı daha sık görünmüş olabilir mi?",
                microStep = "Yarın ilk erteleme anında işi iki dakika açıp sonra yeniden karar ver.",
            ),
            evidence,
        )

        assertTrue(result.errors.joinToString(), result.isValid)
    }

    @Test
    fun rejectsStateWithoutEnoughLocalEvidence() {
        val result = DailyReportSemanticValidator.validate(
            validReflection().copy(evidenceStateId = "bored"),
            evidence,
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.contains("evidence_state_not_supported_by_local_aggregates"))
    }

    @Test
    fun rejectsCausalCertainty() {
        val result = DailyReportSemanticValidator.validate(
            validReflection().copy(
                observationQuestion = "Instagram kullanımının asıl nedeninin erteleme olduğu açık mı?",
            ),
            evidence,
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.contains("unsupported_causal_language"))
    }

    @Test
    fun rejectsGenericMicroStep() {
        val result = DailyReportSemanticValidator.validate(
            validReflection().copy(microStep = "Yarın daha dikkatli ol."),
            evidence,
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.contains("micro_step_not_specific"))
    }

    @Test
    fun rejectsMicroStepWithoutShortDuration() {
        val result = DailyReportSemanticValidator.validate(
            validReflection().copy(microStep = "Yarın raporu açıp ilk başlığı yaz."),
            evidence,
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.contains("micro_step_duration_not_two_to_five_minutes"))
    }

    private fun validReflection(): StructuredDailyReflection = StructuredDailyReflection(
        evidenceStateId = "procrastinating",
        observationQuestion = "Erteleme anlarında devam kararı daha sık görünmüş olabilir mi?",
        microStep = "Yarın işi iki dakika açıp sonra yeniden karar ver.",
    )
}
