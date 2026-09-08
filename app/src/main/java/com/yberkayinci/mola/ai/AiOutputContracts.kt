package com.yberkayinci.mola.ai

enum class InterventionNeed(val wireValue: String) {
    REST("rest"),
    ACTIVATION("activation"),
    INTENTIONAL_BREAK("intentional_break"),
    BOREDOM("boredom"),
    WAITING("waiting"),
    HABIT("habit"),
    OTHER("other");

    companion object {
        fun fromWire(value: String): InterventionNeed? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }
    }
}

enum class EnergyExpectation(val wireValue: String) {
    LOW("low"),
    NORMAL("normal"),
    UNKNOWN("unknown");
}

enum class InterventionObjective(val wireValue: String) {
    PAUSE_AND_RECOVER("pause_and_recover"),
    MICRO_START("micro_start"),
    MAKE_BREAK_INTENTIONAL("make_break_intentional"),
    CHANGE_STIMULUS("change_stimulus"),
    USE_WAIT_BRIEFLY("use_wait_briefly"),
    CLARIFY_INTENTION("clarify_intention"),
    WIND_DOWN("wind_down"),
    CLARIFY_NEED("clarify_need");
}

enum class InterventionStrategy(val wireValue: String) {
    LOW_ENERGY_RESET("low_energy_reset"),
    MICRO_START("micro_start"),
    TIMED_INTENTIONAL_USE("timed_intentional_use"),
    ENVIRONMENT_CHANGE("environment_change"),
    SENSORY_BREAK("sensory_break"),
    BRIEF_ACTIVITY("brief_activity"),
    OTHER("other");

    companion object {
        fun fromWire(value: String): InterventionStrategy? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }
    }
}

data class PersonalizationAnchors(
    val goals: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val lowEnergyActivities: List<String> = emptyList(),
) {
    val all: List<String>
        get() = (goals + activities + lowEnergyActivities).distinct()
}

data class InterventionPolicy(
    val resolvedStateId: String,
    val need: InterventionNeed,
    val energy: EnergyExpectation,
    val objective: InterventionObjective,
    val allowedStrategies: Set<InterventionStrategy>,
    val maxDurationMinutes: Int,
    val anchors: PersonalizationAnchors,
    val forbiddenPatterns: List<String>,
    val evidenceSummary: String,
)

internal data class StructuredAiCard(
    val need: InterventionNeed,
    val strategy: InterventionStrategy,
    val question: String,
    val alternative: String,
    val durationMinutes: Int,
    val personalizationAnchor: String,
    val reflection: String = "",
    val activityTitle: String = "",
)

internal data class StructuredDailyReflection(
    val evidenceStateId: String,
    val observationQuestion: String,
    val microStep: String,
)

internal data class CardValidationResult(
    val errors: List<String>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

internal data class ReportValidationResult(
    val errors: List<String>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
