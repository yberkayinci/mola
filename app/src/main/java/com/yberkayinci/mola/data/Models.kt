package com.yberkayinci.mola.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class UserProfile(
    val name: String,
    val department: String,
    val hobbies: List<String>,
    val improvementArea: String,
    val reason: String,
    val targetAppLabel: String,
    val targetPackage: String,
    val dailyLimitMinutes: Int,
    val biography: String = "",
    val personalization: PersonalizationProfile = PersonalizationProfile(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 4
    }
}

data class ProfileIntake(
    val name: String,
    val department: String = "",
    val biography: String,
    val hobbies: List<String> = emptyList(),
    val improvementArea: String = "",
    val reason: String = "",
)

data class PersonalizationProfile(
    val goals: List<String> = emptyList(),
    val recurringContexts: List<String> = emptyList(),
    val profileSummary: String = "",
    val focusTargets: List<String> = emptyList(),
    val preferredActivities: List<String> = emptyList(),
    val lowEnergyActivities: List<String> = emptyList(),
    val quickStates: List<QuickStateOption> = emptyList(),
    val tone: ProfileTone = ProfileTone.SUPPORTIVE_DIRECT,
) {
    fun quickStatesOrDefault(): List<QuickStateOption> =
        quickStates
            .mapNotNull { option ->
                QuickStateTaxonomy.canonicalize(option.id)?.let { id -> option.copy(id = id) }
            }
            .filter { it.label.isNotBlank() }
            .distinctBy { it.id }
            .take(6)
            .takeIf { it.size >= 3 }
            ?: PersonalizationDefaults.quickStates.take(6)
}

data class QuickStateOption(
    val id: String,
    val label: String,
    val emoji: String = "",
    val category: String = id,
)

/**
 * Canonical quick-state semantic IDs. Models may personalize labels and emoji, but never the
 * semantic ID itself. Safely recognizable aliases are mapped; unknown IDs are discarded.
 */
object QuickStateTaxonomy {
    val CANONICAL_IDS: Set<String> = setOf(
        "tired",
        "procrastinating",
        "relaxing",
        "bored",
        "habit",
        "waiting",
        "low_motivation",
        "overwhelmed",
        "late_night",
        "other",
    )

    private val ID_ALIASES: Map<String, String> = mapOf(
        "low_energy" to "tired",
        "fatigue" to "tired",
        "procrastination" to "procrastinating",
        "avoidance" to "procrastinating",
        "intentional_rest" to "relaxing",
        "rest" to "relaxing",
        "boredom" to "bored",
        "unmotivated" to "low_motivation",
        "motivation" to "low_motivation",
        "activation" to "low_motivation",
        "stressed" to "overwhelmed",
        "stress" to "overwhelmed",
        "burnout" to "overwhelmed",
        "automatic" to "habit",
        "night" to "late_night",
    )

    private val NON_ID_CHARS = Regex("[^a-z0-9]+")

    fun canonicalize(rawId: String): String? {
        val normalized = rawId.trim().lowercase().replace(NON_ID_CHARS, "_").trim('_')
        if (normalized in CANONICAL_IDS) return normalized
        return ID_ALIASES[normalized]
    }
}

enum class ProfileTone(val storageValue: String) {
    SUPPORTIVE_DIRECT("supportive_direct"),
    GENTLE("gentle"),
    PRACTICAL("practical");

    companion object {
        fun fromStorage(value: String): ProfileTone =
            entries.firstOrNull { it.storageValue == value } ?: SUPPORTIVE_DIRECT
    }
}

object PersonalizationDefaults {
    val quickStates: List<QuickStateOption> = listOf(
        QuickStateOption("tired", "Biraz yoruldum", "😴", "low_energy"),
        QuickStateOption("procrastinating", "Bir şeyi erteliyorum", "🫠", "avoidance"),
        QuickStateOption("low_motivation", "Motivasyonum düşük", "🪫", "activation"),
        QuickStateOption("overwhelmed", "Her şey bunaltıyor", "🌀", "activation"),
        QuickStateOption("relaxing", "Sadece kafa dağıtıyorum", "😌", "intentional_rest"),
        QuickStateOption("bored", "Biraz sıkıldım", "🥱", "boredom"),
        QuickStateOption("habit", "Alışkanlıkla açtım", "🔁", "habit"),
        QuickStateOption("waiting", "Bir şeyi bekliyorum", "⏳", "waiting"),
    )
}

enum class UserChoice(val storageValue: String) {
    CONTINUE("yine_de_gir"),
    STOPPED("vazgectim");

    companion object {
        fun fromStorage(value: String): UserChoice =
            entries.firstOrNull { it.storageValue == value } ?: CONTINUE
    }
}

enum class InterventionInputMethod(val storageValue: String) {
    QUICK_REPLY("quick_reply"),
    TEXT("text"),
    VOICE("voice");

    companion object {
        fun fromStorage(value: String): InterventionInputMethod =
            entries.firstOrNull { it.storageValue == value } ?: TEXT
    }
}

data class InterventionInput(
    val text: String,
    val stateId: String = "",
    val stateLabel: String = "",
    val method: InterventionInputMethod = InterventionInputMethod.TEXT,
)

data class InterventionRecord(
    val timestampEpochMillis: Long,
    val usageMinutes: Int,
    val text: String,
    val choice: UserChoice,
    val stateId: String = "",
    val stateLabel: String = "",
    val inputMethod: InterventionInputMethod = InterventionInputMethod.TEXT,
    val aiQuestion: String = "",
    val aiAlternative: String = "",
    val aiReflection: String = "",
    val aiActivityTitle: String = "",
    val aiDurationMinutes: Int = 0,
    val aiStrategy: String = "",
    /** Why this moment was interrupted: the user's own threshold, or a behavioural pattern. */
    val trigger: String = "",
    /** State the app guessed from behaviour before asking, if it had enough evidence to guess. */
    val hypothesisStateId: String = "",
    /** Whether the user confirmed that guess. Null means no guess was offered. */
    val hypothesisAccepted: Boolean? = null,
) {
    fun localTime(zoneId: ZoneId = ZoneId.systemDefault()): String =
        TIME_FORMATTER.format(Instant.ofEpochMilli(timestampEpochMillis).atZone(zoneId))

    fun localDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timestampEpochMillis).atZone(zoneId).toLocalDate()

    fun occursOn(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean = localDate(zoneId) == date

    companion object {
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class AiCard(
    val reflection: String = "",
    val question: String,
    val activityTitle: String = "",
    val alternative: String,
    val durationMinutes: Int = 0,
    val strategy: String = "",
)

data class DailyReport(
    val totalUsageMinutes: Int,
    val limitMinutes: Int,
    val interventionCount: Int,
    val continuedCount: Int,
    val stoppedCount: Int,
    val observationQuestion: String,
    val microStep: String,
    val insufficientData: Boolean = false,
)
