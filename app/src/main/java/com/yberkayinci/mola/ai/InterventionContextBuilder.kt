package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.InterventionInput
import com.yberkayinci.mola.data.InterventionInputMethod
import com.yberkayinci.mola.data.QuickStateTaxonomy
import com.yberkayinci.mola.data.UserProfile

object InterventionContextBuilder {
    fun build(
        profile: UserProfile,
        input: InterventionInput,
    ): InterventionPolicy {
        val normalizedText = input.text.normalizeForPolicyMatching()
        val inferredState = inferState(normalizedText)
        val suppliedState = canonicalState(input.stateId)
        val resolvedState = when {
            input.method != InterventionInputMethod.QUICK_REPLY && inferredState != null -> inferredState
            suppliedState != null -> suppliedState
            inferredState != null -> inferredState
            else -> STATE_OTHER
        }
        val lowEnergy = normalizedText.containsAny(FATIGUE_CUES) || resolvedState == STATE_TIRED
        val anchors = profile.toAnchors(lowEnergy = lowEnergy)

        val base = when (resolvedState) {
            STATE_TIRED -> PolicySeed(
                need = InterventionNeed.REST,
                energy = EnergyExpectation.LOW,
                objective = InterventionObjective.PAUSE_AND_RECOVER,
                strategies = setOf(
                    InterventionStrategy.LOW_ENERGY_RESET,
                    InterventionStrategy.SENSORY_BREAK,
                    InterventionStrategy.ENVIRONMENT_CHANGE,
                ),
                maxDurationMinutes = 5,
            )

            STATE_PROCRASTINATING -> PolicySeed(
                need = InterventionNeed.ACTIVATION,
                energy = if (lowEnergy) EnergyExpectation.LOW else EnergyExpectation.NORMAL,
                objective = InterventionObjective.MICRO_START,
                strategies = if (lowEnergy) {
                    setOf(
                        InterventionStrategy.MICRO_START,
                        InterventionStrategy.SENSORY_BREAK,
                    )
                } else {
                    setOf(
                        InterventionStrategy.MICRO_START,
                        InterventionStrategy.ENVIRONMENT_CHANGE,
                    )
                },
                maxDurationMinutes = if (lowEnergy) 3 else 5,
            )

            STATE_RELAXING -> PolicySeed(
                need = InterventionNeed.INTENTIONAL_BREAK,
                energy = if (lowEnergy) EnergyExpectation.LOW else EnergyExpectation.UNKNOWN,
                objective = InterventionObjective.MAKE_BREAK_INTENTIONAL,
                strategies = setOf(
                    InterventionStrategy.TIMED_INTENTIONAL_USE,
                    InterventionStrategy.SENSORY_BREAK,
                ),
                maxDurationMinutes = 10,
            )

            STATE_LOW_MOTIVATION -> PolicySeed(
                need = InterventionNeed.ACTIVATION,
                energy = if (lowEnergy) EnergyExpectation.LOW else EnergyExpectation.UNKNOWN,
                objective = InterventionObjective.MICRO_START,
                strategies = setOf(
                    InterventionStrategy.MICRO_START,
                    InterventionStrategy.SENSORY_BREAK,
                ),
                maxDurationMinutes = 3,
            )

            STATE_OVERWHELMED -> PolicySeed(
                need = InterventionNeed.ACTIVATION,
                energy = if (lowEnergy) EnergyExpectation.LOW else EnergyExpectation.NORMAL,
                objective = InterventionObjective.MICRO_START,
                strategies = setOf(
                    InterventionStrategy.MICRO_START,
                    InterventionStrategy.ENVIRONMENT_CHANGE,
                ),
                maxDurationMinutes = 3,
            )

            STATE_BORED -> PolicySeed(
                need = InterventionNeed.BOREDOM,
                energy = if (lowEnergy) EnergyExpectation.LOW else EnergyExpectation.NORMAL,
                objective = InterventionObjective.CHANGE_STIMULUS,
                strategies = setOf(
                    InterventionStrategy.BRIEF_ACTIVITY,
                    InterventionStrategy.ENVIRONMENT_CHANGE,
                    InterventionStrategy.SENSORY_BREAK,
                ),
                maxDurationMinutes = 5,
            )

            STATE_WAITING -> PolicySeed(
                need = InterventionNeed.WAITING,
                energy = if (lowEnergy) EnergyExpectation.LOW else EnergyExpectation.UNKNOWN,
                objective = InterventionObjective.USE_WAIT_BRIEFLY,
                strategies = setOf(
                    InterventionStrategy.BRIEF_ACTIVITY,
                    InterventionStrategy.SENSORY_BREAK,
                ),
                maxDurationMinutes = 5,
            )

            STATE_LATE_NIGHT -> PolicySeed(
                need = InterventionNeed.REST,
                energy = EnergyExpectation.LOW,
                objective = InterventionObjective.WIND_DOWN,
                strategies = setOf(
                    InterventionStrategy.LOW_ENERGY_RESET,
                    InterventionStrategy.ENVIRONMENT_CHANGE,
                    InterventionStrategy.SENSORY_BREAK,
                ),
                maxDurationMinutes = 5,
            )

            STATE_HABIT -> PolicySeed(
                need = InterventionNeed.HABIT,
                energy = if (lowEnergy) EnergyExpectation.LOW else EnergyExpectation.UNKNOWN,
                objective = InterventionObjective.CLARIFY_INTENTION,
                strategies = setOf(
                    InterventionStrategy.ENVIRONMENT_CHANGE,
                    InterventionStrategy.SENSORY_BREAK,
                    InterventionStrategy.TIMED_INTENTIONAL_USE,
                ),
                maxDurationMinutes = 3,
            )

            else -> PolicySeed(
                need = InterventionNeed.OTHER,
                energy = if (lowEnergy) EnergyExpectation.LOW else EnergyExpectation.UNKNOWN,
                objective = InterventionObjective.CLARIFY_NEED,
                strategies = setOf(
                    InterventionStrategy.SENSORY_BREAK,
                    InterventionStrategy.ENVIRONMENT_CHANGE,
                    InterventionStrategy.BRIEF_ACTIVITY,
                ),
                maxDurationMinutes = 5,
            )
        }

        val forbidden = buildList {
            add("diagnosis_or_person_label")
            add("shame_or_moralizing")
            add("model_defined_threshold")
            add("causal_certainty")
            add("invented_personal_fact")
            add("invented_media_product_or_live_content")
            if (base.energy == EnergyExpectation.LOW) add("high_effort_action")
            if (base.need == InterventionNeed.INTENTIONAL_BREAK) add("automatic_stop_command")
        }

        val source = when {
            input.method == InterventionInputMethod.QUICK_REPLY -> "quick_reply"
            inferredState != null -> "custom_text_cues"
            else -> "ambiguous_custom_text"
        }
        val evidence = buildString {
            append("source=")
            append(source)
            append("; resolved_state=")
            append(resolvedState)
            if (lowEnergy && resolvedState != STATE_TIRED) append("; low_energy_cue=true")
        }

        return InterventionPolicy(
            resolvedStateId = resolvedState,
            need = base.need,
            energy = base.energy,
            objective = base.objective,
            allowedStrategies = base.strategies,
            maxDurationMinutes = base.maxDurationMinutes,
            anchors = anchors,
            forbiddenPatterns = forbidden,
            evidenceSummary = evidence,
        )
    }

    private fun inferState(normalizedText: String): String? = when {
        normalizedText.containsAny(PROCRASTINATION_CUES) -> STATE_PROCRASTINATING
        normalizedText.containsAny(LOW_MOTIVATION_CUES) -> STATE_LOW_MOTIVATION
        normalizedText.containsAny(OVERWHELMED_CUES) -> STATE_OVERWHELMED
        normalizedText.containsAny(FATIGUE_CUES) -> STATE_TIRED
        normalizedText.containsAny(INTENTIONAL_REST_CUES) -> STATE_RELAXING
        normalizedText.containsAny(BOREDOM_CUES) -> STATE_BORED
        normalizedText.containsAny(WAITING_CUES) -> STATE_WAITING
        normalizedText.containsAny(LATE_NIGHT_CUES) -> STATE_LATE_NIGHT
        normalizedText.containsAny(HABIT_CUES) -> STATE_HABIT
        else -> null
    }

    private fun canonicalState(rawState: String): String? {
        return QuickStateTaxonomy.canonicalize(rawState)
    }

    private fun UserProfile.toAnchors(lowEnergy: Boolean): PersonalizationAnchors {
        val groundedGoals = (
            personalization.focusTargets +
                personalization.goals +
                listOf(improvementArea, reason)
            )
            .safeGroundingValues(maxItems = 4)
        val goals = if (lowEnergy) {
            groundedGoals.filterNot(::looksHighEffort)
        } else {
            groundedGoals
        }

        val generalActivities = (
            personalization.preferredActivities + hobbies
            )
            .safeGroundingValues(maxItems = 6)
            .let { values ->
                if (lowEnergy) values.filterNot(::looksHighEffort) else values
            }

        val lowEnergyActivities = personalization.lowEnergyActivities
            .safeGroundingValues(maxItems = 4)
            .filterNot(::looksHighEffort)

        return PersonalizationAnchors(
            goals = goals,
            activities = generalActivities,
            lowEnergyActivities = lowEnergyActivities,
        )
    }

    private fun List<String>.safeGroundingValues(maxItems: Int): List<String> =
        asSequence()
            .map(String::trim)
            .filter { it.length in 2..120 }
            .filterNot { CrisisFilter.check(it).isCrisisSignal }
            .filter(::isGroundingValueSafe)
            .distinctBy { it.normalizeForPolicyMatching() }
            .take(maxItems)
            .toList()

    private fun isGroundingValueSafe(value: String): Boolean {
        val normalized = value.normalizeForPolicyMatching()
        return !normalized.containsAny(UNSAFE_ANCHOR_CUES)
    }

    private fun looksHighEffort(value: String): Boolean =
        value.normalizeForPolicyMatching().containsAny(HIGH_EFFORT_CUES)

    private fun String.containsAny(cues: Set<String>): Boolean =
        cues.any(::contains)

    private fun String.normalizeForPolicyMatching(): String =
        lowercase()
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private data class PolicySeed(
        val need: InterventionNeed,
        val energy: EnergyExpectation,
        val objective: InterventionObjective,
        val strategies: Set<InterventionStrategy>,
        val maxDurationMinutes: Int,
    )

    private const val STATE_TIRED = "tired"
    private const val STATE_PROCRASTINATING = "procrastinating"
    private const val STATE_RELAXING = "relaxing"
    private const val STATE_BORED = "bored"
    private const val STATE_WAITING = "waiting"
    private const val STATE_HABIT = "habit"
    private const val STATE_LATE_NIGHT = "late_night"
    private const val STATE_LOW_MOTIVATION = "low_motivation"
    private const val STATE_OVERWHELMED = "overwhelmed"
    private const val STATE_OTHER = "other"

    private val FATIGUE_CUES = setOf(
        "yorgun",
        "yoruld",
        "bitkin",
        "enerjim yok",
        "tired",
        "exhausted",
        "drained",
        "worn out",
        "switch off",
    )
    private val PROCRASTINATION_CUES = setOf(
        "ertel",
        "baslayam",
        "baslamak yerine",
        "kaciyorum",
        "oyalaniyorum",
        "procrast",
        "avoiding",
        "avoid starting",
        "should be studying",
        "have to study",
        "need to study",
        "should be working",
        "have to work",
    )
    private val INTENTIONAL_REST_CUES = setOf(
        "kafa dagit",
        "dinlen",
        "rahatla",
        "bilerek mola",
        "intentional break",
        "taking a break",
        "relax",
        "unwind",
        "chill",
    )
    private val BOREDOM_CUES = setOf(
        "sikild",
        "canim sikiliyor",
        "bored",
        "nothing to do",
    )
    private val WAITING_CUES = setOf(
        "bekliyorum",
        "beklerken",
        "waiting",
        "wait for",
    )
    private val HABIT_CUES = setOf(
        "aliskanlik",
        "fark etmeden",
        "otomatik",
        "automatic",
        "habit",
        "without thinking",
    )
    private val LATE_NIGHT_CUES = setOf(
        "uyumadan",
        "gece",
        "yatmadan",
        "sleep",
        "bedtime",
        "late night",
    )
    private val LOW_MOTIVATION_CUES = setOf(
        "motivasyonum dusuk",
        "motivasyonum dustu",
        "motivasyonum yok",
        "motivasyon yok",
        "hic baslayasim yok",
        "canim istemiyor",
        "istegim yok",
        "unmotivated",
        "no motivation",
        "don t feel like starting",
        "do not feel like starting",
    )
    private val OVERWHELMED_CUES = setOf(
        "cok fazla sey var",
        "cok fazla is var",
        "nereden baslayacagimi bilmiyorum",
        "nereden baslayacagim",
        "bunaldim",
        "gozumde buyuyor",
        "overwhelmed",
        "too much to do",
        "don t know where to start",
        "do not know where to start",
    )
    private val HIGH_EFFORT_CUES = setOf(
        "agir antrenman",
        "antrenman",
        "workout",
        "gym",
        "kosu",
        "running",
        "egzersiz",
        "exercise",
        "spor",
    )
    private val UNSAFE_ANCHOR_CUES = setOf(
        "bagimlilik",
        "addiction",
        "depresyon",
        "depression",
        "anksiyete",
        "anxiety",
        "tani",
        "diagnosis",
        "tedavi",
        "treatment",
    )
}
