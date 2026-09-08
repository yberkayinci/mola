package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.PersonalizationProfile
import com.yberkayinci.mola.data.ProfileIntake
import com.yberkayinci.mola.data.QuickStateOption
import com.yberkayinci.mola.data.QuickStateTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileGroundingSanitizerTest {
    private val fallback = PersonalizationProfile(
        goals = listOf("telefonu daha bilinçli kullanmak"),
        recurringContexts = listOf("alışkanlıkla açma"),
        preferredActivities = emptyList(),
        lowEnergyActivities = listOf("bir bardak su içip ekrandan uzaklaşmak"),
        quickStates = listOf(
            QuickStateOption("habit", "Alışkanlıkla açtım"),
            QuickStateOption("tired", "Biraz yoruldum"),
            QuickStateOption("relaxing", "Sadece kafa dağıtıyorum"),
            QuickStateOption("bored", "Biraz sıkıldım"),
            QuickStateOption("waiting", "Bir şeyi bekliyorum"),
            QuickStateOption("procrastinating", "Bir şeyi erteliyorum"),
        ),
    )

    @Test
    fun keepsActivitiesGroundedInNarrative() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(
                name = "Ayşe",
                biography = "Müzik dinlemeyi ve gitar çalmayı seviyorum.",
                hobbies = listOf("gitar", "müzik"),
            ),
            generated = PersonalizationProfile(
                preferredActivities = listOf("gitar çalmak", "müzik dinlemek"),
            ),
            fallback = fallback,
        )

        assertTrue(result.preferredActivities.contains("gitar çalmak"))
        assertTrue(result.preferredActivities.contains("müzik dinlemek"))
    }

    @Test
    fun removesInventedPodcastAndRunningPreferences() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(
                name = "Ayşe",
                biography = "Kitap okumayı seviyorum.",
                hobbies = listOf("kitap"),
            ),
            generated = PersonalizationProfile(
                preferredActivities = listOf("kitap okumak", "podcast dinlemek", "koşu"),
            ),
            fallback = fallback,
        )

        assertTrue(result.preferredActivities.contains("kitap okumak"))
        assertFalse(result.preferredActivities.contains("podcast dinlemek"))
        assertFalse(result.preferredActivities.contains("koşu"))
    }

    @Test
    fun permitsNeutralLowEnergyActionsWithoutInventingPreference() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(
                name = "Ayşe",
                biography = "Derslere başlamakta zorlanıyorum.",
            ),
            generated = PersonalizationProfile(
                lowEnergyActivities = listOf(
                    "bir bardak su içmek",
                    "en sevdiği podcasti dinlemek",
                ),
            ),
            fallback = fallback,
        )

        assertTrue(result.lowEnergyActivities.contains("bir bardak su içmek"))
        assertFalse(result.lowEnergyActivities.contains("en sevdiği podcasti dinlemek"))
    }

    @Test
    fun sparseEvidenceUsesFallbackInsteadOfFabricatedSpecificity() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(name = "Ayşe", biography = "Telefonumu azaltmak istiyorum."),
            generated = PersonalizationProfile(
                goals = listOf("maraton koşmak"),
                preferredActivities = listOf("meditasyon"),
            ),
            fallback = fallback,
        )

        assertTrue(result.goals.contains("telefonu daha bilinçli kullanmak"))
        assertTrue(result.preferredActivities.isEmpty())
        assertTrue(result.focusTargets.isEmpty())
    }

    @Test
    fun keepsGroundedFocusTargetsAndRemovesInventedOnes() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(
                name = "Ayşe",
                biography = "Ders çalışmaya başlayamıyorum, akşamları da uykuya geçmekte zorlanıyorum.",
            ),
            generated = PersonalizationProfile(
                focusTargets = listOf("ders çalışmak", "uykuya geçmek", "gitar çalmak"),
            ),
            fallback = fallback,
        )

        assertTrue(result.focusTargets.contains("ders çalışmak"))
        assertTrue(result.focusTargets.contains("uykuya geçmek"))
        assertFalse(result.focusTargets.contains("gitar çalmak"))
    }

    @Test
    fun unsafeProfileSummaryIsRejectedInFavorOfDeterministicLocalSummary() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(
                name = "Ayşe",
                biography = "Ders çalışmaya başlarken Instagram'a kayıyorum. Müzik seviyorum.",
                hobbies = listOf("müzik"),
            ),
            generated = PersonalizationProfile(
                goals = listOf("ders çalışmak"),
                preferredActivities = listOf("müzik"),
                profileSummary = "Sen bir telefon bağımlısısın ve disiplinsizsin; bu yüzden ders çalışamıyorsun.",
            ),
            fallback = fallback,
        )

        assertFalse(result.profileSummary.contains("bağımlı", ignoreCase = true))
        assertFalse(result.profileSummary.contains("disiplinsiz", ignoreCase = true))
        assertTrue(result.profileSummary.contains("ders", ignoreCase = true))
        assertTrue(result.profileSummary.length <= 320)
    }

    @Test
    fun groundedProfileSummaryIsKept() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(
                name = "Ayşe",
                biography = "Ders çalışmaya başlarken Instagram'a kayıyorum. Müzik seviyorum.",
                hobbies = listOf("müzik"),
            ),
            generated = PersonalizationProfile(
                goals = listOf("derslere daha kolay başlamak"),
                recurringContexts = listOf("başlama anında oyalanma"),
                preferredActivities = listOf("müzik"),
                profileSummary = "Özellikle ders çalışmaya başlarken oyalanmayı anlattın; molalarda müzik gibi seçenekler kullanılabilir.",
            ),
            fallback = fallback,
        )

        assertTrue(result.profileSummary.startsWith("Özellikle ders"))
    }

    @Test
    fun ungroundedProfileSummaryFallsBackToLocalSummary() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(
                name = "Ayşe",
                biography = "Ders çalışmaya başlamakta zorlanıyorum.",
            ),
            generated = PersonalizationProfile(
                goals = listOf("derslere daha kolay başlamak"),
                profileSummary = "Netflix belgeleri izlemeyi seviyorsun ve koşuya yeni başlıyorsun.",
            ),
            fallback = fallback,
        )

        assertFalse(result.profileSummary.contains("Netflix"))
        assertFalse(result.profileSummary.contains("koşuya"))
        assertTrue(result.profileSummary.isNotBlank())
        assertTrue(result.profileSummary.length <= 320)
    }

    @Test
    fun unsupportedNamedEntityIsRejectedEvenWhenSummaryHasGroundedText() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(
                name = "Ayşe",
                biography = "Ders çalışmaya başlamakta zorlanıyorum.",
            ),
            generated = PersonalizationProfile(
                goals = listOf("ders çalışmak"),
                profileSummary = "Ders çalışmaya başlarken Netflix'e kaydığını anlattın.",
            ),
            fallback = fallback,
        )

        assertFalse(result.profileSummary.contains("Netflix"))
        assertTrue(result.profileSummary.contains("ders", ignoreCase = true))
    }

    @Test
    fun sparseSummaryIsNotFabricatedWhenNothingIsGrounded() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(name = "Ayşe", biography = "Telefonumu azaltmak istiyorum."),
            generated = PersonalizationProfile(
                profileSummary = "Sen sabah koşusu yapıyorsun ve gitar çalıyorsun.",
            ),
            fallback = fallback,
        )

        assertFalse(result.profileSummary.contains("koşu", ignoreCase = true))
        assertFalse(result.profileSummary.contains("gitar", ignoreCase = true))
    }

    @Test
    fun nonCanonicalQuickStateIdsAreDroppedAndReplacedByFallback() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(name = "Ayşe", biography = "Yorulunca Instagram açıyorum."),
            generated = PersonalizationProfile(
                quickStates = listOf(
                    QuickStateOption("tired", "Bitkin hissediyorum", "😴", "low_energy"),
                    QuickStateOption("zombie_mode", "Zombi modundayim", "🧟", "other"),
                    QuickStateOption("low_motivation", "Hiç başlayasım yok", "🪫", "activation"),
                ),
            ),
            fallback = fallback,
        )

        assertTrue(result.quickStates.any { it.id == "tired" && it.label == "Bitkin hissediyorum" })
        assertFalse(result.quickStates.any { it.id == "zombie_mode" })
        assertTrue(result.quickStates.any { it.id == "low_motivation" })
        assertTrue(
            result.quickStates.all { it.id in QuickStateTaxonomy.CANONICAL_IDS },
        )
        assertEquals(6, result.quickStates.size)
        assertTrue(
            result.quickStates.all { option ->
                option.label.isNotBlank() && SafetyLanguageValidator.isDisplaySafe(option.label)
            },
        )
    }

    @Test
    fun aliasedQuickStateIdsMapToCanonicalIds() {
        val result = ProfileGroundingSanitizer.sanitize(
            intake = ProfileIntake(name = "Ayşe", biography = "Yorulunca Instagram açıyorum."),
            generated = PersonalizationProfile(
                quickStates = listOf(
                    QuickStateOption("avoidance", "Erteliyorum", "🫠", "avoidance"),
                    QuickStateOption("intentional_rest", "Mola veriyorum", "😌", "intentional_rest"),
                ),
            ),
            fallback = fallback,
        )

        assertTrue(result.quickStates.any { it.id == "procrastinating" && it.label == "Erteliyorum" })
        assertTrue(result.quickStates.any { it.id == "relaxing" && it.label == "Mola veriyorum" })
    }

    @Test
    fun localSummaryBuilderProducesConciseGroundedTurkishSummary() {
        val summary = ProfileGroundingSanitizer.buildLocalSummary(
            focusTargets = listOf("ders çalışmak"),
            goals = listOf("daha düzenli çalışmak"),
            contexts = listOf("erteleme", "yorgunluk"),
            activities = listOf("müzik", "gitar"),
        )

        assertTrue(summary.isNotBlank())
        assertTrue(summary.length <= 320)
        assertTrue(summary.contains("ders çalışmak"))
        assertTrue(summary.contains("erteleme"))
        assertTrue(summary.contains("müzik"))
    }
}
