package com.yberkayinci.mola.ai

data class CrisisCheck(
    val isCrisisSignal: Boolean,
    val matchedTerm: String? = null,
)

object CrisisFilter {
    private val normalizedTerms = listOf(
        // Turkish
        "intihar",
        "intihar dusun",
        "kendimi oldur",
        "kendime zarar",
        "hayatima son",
        "yasamak istem",
        "olmek ist",
        // English
        "suicide",
        "suicidal",
        "kill myself",
        "hurt myself",
        "self harm",
        "do not want to live",
        "dont want to live",
        "want to die",
        "end my life",
        "ending my life",
        "take my own life",
    )

    fun check(text: String): CrisisCheck {
        val normalized = text.normalizeForMatching()
        val match = normalizedTerms.firstOrNull(normalized::contains)
        return CrisisCheck(isCrisisSignal = match != null, matchedTerm = match)
    }

    private fun String.normalizeForMatching(): String =
        lowercase()
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("\\s+"), " ")
            .trim()
}
