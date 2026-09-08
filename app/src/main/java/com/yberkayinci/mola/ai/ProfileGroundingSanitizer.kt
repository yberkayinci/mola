package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.PersonalizationProfile
import com.yberkayinci.mola.data.ProfileIntake
import com.yberkayinci.mola.data.QuickStateTaxonomy

internal object ProfileGroundingSanitizer {
    fun sanitize(
        intake: ProfileIntake,
        generated: PersonalizationProfile,
        fallback: PersonalizationProfile,
    ): PersonalizationProfile {
        val allEvidence = listOf(
            intake.biography,
            intake.reason,
            intake.improvementArea,
            intake.hobbies.joinToString(" "),
        ).joinToString(" ")
        val activityEvidence = listOf(
            intake.biography,
            intake.hobbies.joinToString(" "),
        ).joinToString(" ")

        val focusTargets = generated.focusTargets
            .filter { value -> isGrounded(value, allEvidence) }
            .ifEmpty { fallback.focusTargets }
            .distinct()
            .take(MAX_FOCUS_TARGETS)

        val goals = generated.goals
            .filter { value -> isGrounded(value, allEvidence) }
            .ifEmpty { fallback.goals }
            .distinct()
            .take(3)

        val contexts = generated.recurringContexts
            .filter { value ->
                isGrounded(value, allEvidence) ||
                    fallback.recurringContexts.any { fallbackValue ->
                        semanticallyRelated(value, fallbackValue)
                    }
            }
            .ifEmpty { fallback.recurringContexts }
            .distinct()
            .take(4)

        val activities = generated.preferredActivities
            .filter { value -> isGrounded(value, activityEvidence) }
            .ifEmpty { fallback.preferredActivities }
            .distinct()
            .take(5)

        val allowedActivityEvidence = (activityEvidence + " " + activities.joinToString(" ")).trim()
        val lowEnergy = generated.lowEnergyActivities
            .filter { value ->
                isGrounded(value, allowedActivityEvidence) ||
                    isNeutralLowEnergyAction(value)
            }
            .ifEmpty { fallback.lowEnergyActivities }
            .distinct()
            .take(3)

        val quickStates = generated.quickStates
            .mapNotNull { option ->
                val canonicalId = QuickStateTaxonomy.canonicalize(option.id)
                canonicalId?.let { option.copy(id = it) }
            }
            .filter { option ->
                option.label.isNotBlank() &&
                    option.label.length <= 70 &&
                    SafetyLanguageValidator.isDisplaySafe(option.label)
            }
            .distinctBy { it.id }
            .take(6)
            .let { values ->
                (values + fallback.quickStates)
                    .distinctBy { it.id }
                    .take(6)
            }

        val groundedFieldText = (
            goals + contexts + activities + lowEnergy + focusTargets
            ).joinToString(" ")

        val profileSummary = generated.profileSummary
            .takeIf { isSafeGroundedSummary(it, groundedFieldText, allEvidence) }
            ?: buildLocalSummary(focusTargets, goals, contexts, activities)

        return generated.copy(
            profileSummary = profileSummary,
            focusTargets = focusTargets,
            goals = goals,
            recurringContexts = contexts,
            preferredActivities = activities,
            lowEnergyActivities = lowEnergy,
            quickStates = quickStates,
        )
    }

    /**
     * Deterministic local summary used when the generated summary fails validation. Built only
     * from already-sanitized fields so it can never introduce ungrounded facts.
     */
    fun buildLocalSummary(
        focusTargets: List<String>,
        goals: List<String>,
        contexts: List<String>,
        activities: List<String>,
    ): String {
        val focus = (focusTargets + goals)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(2)
        val moments = contexts.take(2)
        val options = activities.take(3)

        val body = buildList {
            if (focus.isNotEmpty()) {
                add("Özellikle ${focus.joinToString(" ve ")} konusuna odaklanmak istiyorsun")
            }
            if (moments.isNotEmpty()) {
                add("${moments.joinToString(" ve ")} anları senin için öne çıkıyor")
            }
            if (options.isNotEmpty()) {
                add("Molalarda ${options.joinToString(", ")} gibi seçenekler işine gelebilir")
            }
        }.joinToString(separator = ". ")

        return buildString {
            append("Bu profil yalnızca senin anlattıklarından oluşturuldu. ")
            if (body.isNotEmpty()) {
                append(body)
                append('.')
            } else {
                append("Mola önerilerini senin belirlediğin sınırlar içinde kişiselleştirecek.")
            }
        }.take(MAX_SUMMARY_CHARS)
    }

    private fun isSafeGroundedSummary(
        summary: String,
        groundedFieldText: String,
        allEvidence: String,
    ): Boolean {
        val trimmed = summary.trim()
        if (trimmed.length !in MIN_SUMMARY_CHARS..MAX_SUMMARY_CHARS) return false
        if (!SafetyLanguageValidator.isDisplaySafe(trimmed)) return false
        val normalized = trimmed.normalizeForGrounding()
        if (UNSAFE_SUMMARY_CUES.any(normalized::contains)) return false
        val summaryTokens = trimmed.meaningfulTokens()
        val evidenceTokens = groundedFieldText.meaningfulTokens()
        if (NAMED_ENTITY_CUES.any { it in summaryTokens && it !in evidenceTokens }) return false
        val normalizedEvidence = allEvidence.normalizeForGrounding()
        if (PERSONAL_DETAIL_CUES.any { it in summaryTokens && !normalizedEvidence.contains(it) }) {
            return false
        }
        if (groundedFieldText.isBlank()) return false
        if (summaryTokens.any(evidenceTokens::contains)) return true
        return SEMANTIC_GROUPS.any { group ->
            summaryTokens.any(group::contains) && evidenceTokens.any(group::contains)
        }
    }

    private fun isGrounded(value: String, evidence: String): Boolean {
        val valueTokens = value.meaningfulTokens()
        val evidenceTokens = evidence.meaningfulTokens()
        if (valueTokens.any(evidenceTokens::contains)) return true
        return SEMANTIC_GROUPS.any { group ->
            valueTokens.any(group::contains) && evidenceTokens.any(group::contains)
        }
    }

    private fun semanticallyRelated(first: String, second: String): Boolean {
        val firstTokens = first.meaningfulTokens()
        val secondTokens = second.meaningfulTokens()
        if (firstTokens.any(secondTokens::contains)) return true
        return SEMANTIC_GROUPS.any { group ->
            firstTokens.any(group::contains) && secondTokens.any(group::contains)
        }
    }

    private fun isNeutralLowEnergyAction(value: String): Boolean {
        val normalized = value.normalizeForGrounding()
        return NEUTRAL_LOW_ENERGY_CUES.any(normalized::contains)
    }

    private fun String.meaningfulTokens(): Set<String> =
        normalizeForGrounding()
            .split(' ')
            .asSequence()
            .filter { token -> token.length >= 4 }
            .filterNot(STOP_WORDS::contains)
            .toSet()

    private fun String.normalizeForGrounding(): String =
        lowercase()
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private const val MAX_SUMMARY_CHARS = 320
    private const val MIN_SUMMARY_CHARS = 24
    private const val MAX_FOCUS_TARGETS = 4

    private val STOP_WORDS = setOf(
        "daha",
        "icin",
        "gibi",
        "olan",
        "olarak",
        "biraz",
        "kendi",
        "with",
        "that",
        "this",
        "from",
        "more",
        "want",
    )
    private val SEMANTIC_GROUPS = listOf(
        setOf("muzik", "sarki", "music", "song"),
        setOf("kitap", "okumak", "okuma", "book", "read", "reading"),
        setOf("gitar", "guitar"),
        setOf("yuruyus", "yurumek", "walk", "walking"),
        setOf("kosu", "kosmak", "run", "running"),
        setOf("spor", "egzersiz", "exercise", "workout", "gym"),
        setOf("ders", "calismak", "odev", "study", "studying", "homework"),
        setOf("uyku", "uyumak", "gece", "sleep", "bedtime"),
        setOf("yorgunluk", "yorgun", "yorulmak", "tired", "fatigue", "exhausted"),
        setOf("erteleme", "ertelemek", "baslayamamak", "procrastination", "procrastinating", "avoidance"),
        setOf("sikilma", "sikilmak", "bored", "boredom"),
    )
    private val NEUTRAL_LOW_ENERGY_CUES = setOf(
        "su ic",
        "bardak su",
        "nefes",
        "gozlerini dinlendir",
        "ekrandan uzaklas",
        "telefonu birak",
        "kisa mola",
        "bulundugun ortami dinle",
        "drink water",
        "breath",
        "screen break",
        "put the phone down",
    )
    private val UNSAFE_SUMMARY_CUES = setOf(
        "tembel",
        "disiplinsiz",
        "dopamin",
        "bagimli",
        "iradesiz",
        "kusurlu",
        "sorunlu",
        "hastalik",
        "nedeniyle",
        "yuzunden",
        "dolayi",
        "sebebi",
        "cunku",
    )
    private val NAMED_ENTITY_CUES = setOf(
        "discord",
        "facebook",
        "instagram",
        "netflix",
        "reddit",
        "spotify",
        "telegram",
        "tiktok",
        "twitter",
        "youtube",
    )
    private val PERSONAL_DETAIL_CUES = setOf(
        "ders",
        "egzersiz",
        "gitar",
        "kitap",
        "kosu",
        "muzik",
        "odev",
        "podcast",
        "sarki",
        "spor",
        "uyku",
        "yuruyus",
    )
}
