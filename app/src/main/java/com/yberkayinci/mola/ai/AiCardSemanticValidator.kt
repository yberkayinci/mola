package com.yberkayinci.mola.ai

internal object AiCardSemanticValidator {
    fun validate(
        card: StructuredAiCard,
        policy: InterventionPolicy,
        recentAlternatives: List<String> = emptyList(),
    ): CardValidationResult {
        val reflection = card.reflection.trim()
        val question = card.question.trim()
        val activityTitle = card.activityTitle.trim()
        val alternative = card.alternative.trim()
        val normalizedAlternative = alternative.normalizeForSemanticMatching()
        val normalizedFields = listOf(reflection, question, activityTitle, alternative)
            .map { it.normalizeForSemanticMatching() }

        val errors = buildList {
            if (card.need != policy.need) {
                add("need_must_match_compiled_policy")
            }
            if (card.strategy !in policy.allowedStrategies) {
                add("strategy_not_allowed_for_context")
            }
            if (card.durationMinutes !in 1..policy.maxDurationMinutes) {
                add("duration_outside_allowed_range")
            }

            if (question.isBlank()) add("question_blank")
            if (question.length > MAX_QUESTION_CHARS) add("question_too_long")
            if (question.isNotBlank() && !question.endsWith('?')) {
                add("question_must_end_with_question_mark")
            }
            if (activityTitle.length > MAX_ACTIVITY_TITLE_CHARS) {
                add("activity_title_too_long")
            }
            if (alternative.isBlank()) add("alternative_blank")
            if (alternative.length > MAX_ALTERNATIVE_CHARS) add("alternative_too_long")
            if (reflection.length > MAX_REFLECTION_CHARS) add("reflection_too_long")
            if (!SafetyLanguageValidator.isDisplaySafe(reflection, question, activityTitle, alternative)) {
                add("unsafe_or_judgmental_language")
            }

            if (normalizedFields.any(::usesFormalVoice)) {
                add("formal_siz_language")
            }
            if (normalizedFields.any { field -> STYLE_FORBIDDEN_CUES.any(field::contains) }) {
                add("stock_or_moralizing_language")
            }
            if (normalizedAlternative.isNotBlank() && !isConcreteAction(normalizedAlternative)) {
                add("alternative_not_concrete")
            }
            if (GENERIC_ADVICE.any(normalizedAlternative::contains)) {
                add("alternative_not_concrete")
            }
            if (
                policy.energy == EnergyExpectation.LOW &&
                HIGH_EFFORT_ACTIONS.any(normalizedAlternative::contains)
            ) {
                add("high_effort_action_for_low_energy_context")
            }
            if (INVENTED_LIVE_CONTENT.any(normalizedAlternative::contains)) {
                add("invented_or_unverified_live_content")
            }
            if (CAUSAL_CERTAINTY_CUES.any { cue -> normalizedFields.any { it.contains(cue) } }) {
                add("causal_certainty_language")
            }
            if (THRESHOLD_LANGUAGE.any { cue -> normalizedFields.any { it.contains(cue) } }) {
                add("model_defined_threshold")
            }
            if (
                policy.need == InterventionNeed.INTENTIONAL_BREAK &&
                FORCE_STOP_LANGUAGE.any(normalizedAlternative::contains)
            ) {
                add("intentional_break_must_preserve_autonomy")
            }

            val anchor = card.personalizationAnchor.trim()
            if (anchor.isNotEmpty()) {
                val allowedAnchors = policy.anchors.all
                    .map { value -> value.normalizeForSemanticMatching() }
                    .toSet()
                if (anchor.normalizeForSemanticMatching() !in allowedAnchors) {
                    add("personalization_anchor_not_supplied_by_user")
                }
            }

            val candidateTokens = toSimilarityTokens("$activityTitle $alternative")
            if (
                candidateTokens.isNotEmpty() &&
                recentAlternatives.any { recent ->
                    jaccardSimilarity(candidateTokens, toSimilarityTokens(recent)) > DUPLICATE_THRESHOLD
                }
            ) {
                add("too_similar_to_recent_intervention")
            }
        }

        return CardValidationResult(errors.distinct())
    }

    private fun isConcreteAction(value: String): Boolean {
        val tokens = value.split(' ').filter(String::isNotBlank)
        return tokens.any { token ->
            CONCRETE_ACTION_CUES.any { cue -> token == cue || token.startsWith(cue) }
        }
    }

    private fun usesFormalVoice(value: String): Boolean =
        value.split(' ').any { token ->
            token in FORMAL_VOICE_WORDS || token.endsWith("siniz")
        }

    private fun toSimilarityTokens(value: String): Set<String> =
        value.normalizeForSemanticMatching()
            .split(' ')
            .asSequence()
            .filter { token -> token.length >= 3 }
            .filterNot(SIMILARITY_STOP_WORDS::contains)
            .toSet()

    private fun jaccardSimilarity(first: Set<String>, second: Set<String>): Double {
        if (first.isEmpty() || second.isEmpty()) return 0.0
        val union = first union second
        if (union.isEmpty()) return 0.0
        return (first intersect second).size.toDouble() / union.size.toDouble()
    }

    private fun String.normalizeForSemanticMatching(): String =
        lowercase()
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private const val MAX_REFLECTION_CHARS = 130
    private const val MAX_QUESTION_CHARS = 150
    private const val MAX_ACTIVITY_TITLE_CHARS = 45
    private const val MAX_ALTERNATIVE_CHARS = 240
    private const val DUPLICATE_THRESHOLD = 0.55

    private val FORMAL_VOICE_WORDS = setOf("siz", "size", "sizi", "sizin")
    private val STYLE_FORBIDDEN_CUES = setOf(
        "kendine alan ac",
        "anda kal",
        "nefesine don",
        "farkindalik kazan",
        "kendine sefkat",
        "kucuk adimlar buyuk fark",
        "tembel",
        "disiplinsiz",
        "iradesiz",
        "bagimli",
        "dopamin",
        "basarisiz oldun",
        "iraden zayif",
    )
    private val GENERIC_ADVICE = setOf(
        "kendine iyi bak",
        "daha iyi hissetmeye calis",
        "daha saglikli bir secim yap",
        "baska bir sey yap",
        "uretken olmaya calis",
        "motivasyonunu bul",
        "bir mola vermeyi deneyebilirsin",
        "biraz mola ver",
        "biraz ders calis",
        "biraz calis",
        "telefonu birakabilirsin",
        "do something else",
        "make a healthier choice",
        "try to be productive",
    )
    private val CONCRETE_ACTION_CUES = setOf(
        "ac",
        "ayir",
        "bak",
        "basla",
        "birak",
        "dinle",
        "gec",
        "ic",
        "kur",
        "koy",
        "oku",
        "say",
        "sec",
        "sil",
        "yap",
        "yaz",
        "yuru",
        "esne",
        "open",
        "choose",
        "drink",
        "listen",
        "put",
        "read",
        "write",
    )
    private val HIGH_EFFORT_ACTIONS = setOf(
        "antrenman yap",
        "spora git",
        "kosuya cik",
        "egzersiz yap",
        "agir spor",
        "work out",
        "go to the gym",
        "go for a run",
        "exercise now",
    )
    private val INVENTED_LIVE_CONTENT = setOf(
        "yeni bolumu cikti",
        "yeni bolumunu dinle",
        "en sevdigin podcastin yeni bolumu",
        "new episode is out",
        "latest episode",
        "new release",
        "bugun yayinlandi",
        "just released",
    )
    private val CAUSAL_CERTAINTY_CUES = setOf(
        "bu yuzden",
        "yuzunden",
        "nedeniyle",
        "sebebiyle",
        "cunku",
        "dolayi",
        "sebebi",
    )
    private val THRESHOLD_LANGUAGE = setOf(
        "limitin olsun",
        "limitini yap",
        "limitini belirle",
        "gunluk limitin",
        "sana ... dakika limit",
        "your limit should",
    )
    private val FORCE_STOP_LANGUAGE = setOf(
        "hemen kapat",
        "uygulamadan cik",
        "telefonu birakmak zorundasin",
        "stop immediately",
        "you must close",
        "leave the app now",
    )
    private val SIMILARITY_STOP_WORDS = setOf(
        "ama",
        "artık",
        "artik",
        "bir",
        "bu",
        "icin",
        "ile",
        "istersen",
        "kadar",
        "sonra",
        "sadece",
        "sen",
        "sana",
        "şu",
        "su",
        "ve",
        "yeniden",
        "deneyebilirsin",
        "yapabilirsin",
        "dakika",
    )
}
