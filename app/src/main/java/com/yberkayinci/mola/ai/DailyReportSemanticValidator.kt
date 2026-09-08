package com.yberkayinci.mola.ai

internal object DailyReportSemanticValidator {
    fun validate(
        reflection: StructuredDailyReflection,
        evidence: DailyReportEvidence,
    ): ReportValidationResult {
        val errors = buildList {
            val evidenceStateId = reflection.evidenceStateId.trim()
            if (
                evidenceStateId.isNotEmpty() &&
                evidenceStateId !in evidence.candidateStateIds
            ) {
                add("evidence_state_not_supported_by_local_aggregates")
            }

            val observation = reflection.observationQuestion.trim()
            val microStep = reflection.microStep.trim()
            if (observation.isBlank()) add("observation_blank")
            if (observation.length > MAX_FIELD_CHARS) add("observation_too_long")
            if (observation.isNotBlank() && !observation.endsWith('?')) {
                add("observation_must_be_tentative_question")
            }
            if (microStep.isBlank()) add("micro_step_blank")
            if (microStep.length > MAX_FIELD_CHARS) add("micro_step_too_long")
            if (!SafetyLanguageValidator.isDisplaySafe(observation, microStep)) {
                add("unsafe_or_judgmental_language")
            }

            val normalizedObservation = observation.normalizeForReportMatching()
            val normalizedMicroStep = microStep.normalizeForReportMatching()
            if (CAUSAL_CERTAINTY.any(normalizedObservation::contains)) {
                add("unsupported_causal_language")
            }
            if (GENERIC_MICRO_STEPS.any(normalizedMicroStep::contains)) {
                add("micro_step_not_specific")
            }
            if (microStep.isNotBlank() && !SHORT_DURATION_CUES.any(normalizedMicroStep::contains)) {
                add("micro_step_duration_not_two_to_five_minutes")
            }
        }

        return ReportValidationResult(errors.distinct())
    }

    private fun String.normalizeForReportMatching(): String =
        lowercase()
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private const val MAX_FIELD_CHARS = 220

    private val CAUSAL_CERTAINTY = setOf(
        "bunun sebebi",
        "asil nedenin",
        "bu yuzden kullaniyorsun",
        "bundan kaynaklaniyor",
        "because you",
        "the cause is",
        "is caused by",
    )
    private val GENERIC_MICRO_STEPS = setOf(
        "daha dikkatli ol",
        "daha az kullanmaya calis",
        "kendine iyi bak",
        "daha iyi bir secim yap",
        "be more mindful",
        "try to use it less",
    )
    private val SHORT_DURATION_CUES = setOf(
        "iki dakika",
        "2 dakika",
        "uc dakika",
        "3 dakika",
        "dort dakika",
        "4 dakika",
        "bes dakika",
        "5 dakika",
        "two minutes",
        "three minutes",
        "four minutes",
        "five minutes",
    )
}
