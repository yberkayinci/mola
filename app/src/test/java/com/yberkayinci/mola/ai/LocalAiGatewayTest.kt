package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.InterventionInput
import com.yberkayinci.mola.data.InterventionInputMethod
import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.PersonalizationProfile
import com.yberkayinci.mola.data.ProfileIntake
import com.yberkayinci.mola.data.UserChoice
import com.yberkayinci.mola.data.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiGatewayTest {
    private val gateway = LocalAiGateway()
    private val profile = UserProfile(
        name = "Ayşe",
        department = "İstatistik",
        hobbies = listOf("gitar"),
        improvementArea = "İngilizce",
        reason = "gece uyuyamıyorum",
        targetAppLabel = "Instagram",
        targetPackage = "com.instagram.android",
        dailyLimitMinutes = 60,
    )

    @Test
    fun profileGenerationProducesSixQuickStates() = runBlocking {
        val result = gateway.generateProfile(
            ProfileIntake(
                name = "Ayşe",
                biography = "Derslerden sonra çok yoruluyorum ve çalışmaya başlamak yerine Instagram'da oyalanıyorum.",
                hobbies = listOf("gitar", "koşu"),
                improvementArea = "daha düzenli çalışmak",
                reason = "gece daha rahat uyumak",
            ),
        )

        assertEquals("six quick states", 6, result.quickStates.size)
        assertTrue(
            "yoruluyorum should map to yorgunluk",
            result.recurringContexts.contains("yorgunluk"),
        )
        assertTrue(
            "başlamak yerine oyalanıyorum should map to erteleme",
            result.recurringContexts.contains("erteleme"),
        )
        assertTrue("explicit hobby should be retained", result.preferredActivities.contains("gitar"))
        assertTrue(
            "exercise hobby should be converted to a gentle low-energy version",
            result.lowEnergyActivities.any { it.contains("esne", ignoreCase = true) },
        )
        assertTrue(result.focusTargets.contains("ders çalışmak"))
        assertTrue(result.profileSummary.contains("ders", ignoreCase = true))
    }

    @Test
    fun sparseProfileFallbackDoesNotInventActivityPreferences() = runBlocking {
        val result = gateway.generateProfile(
            ProfileIntake(
                name = "Ayşe",
                biography = "Instagram'da daha az otomatik vakit geçirmek istiyorum.",
            ),
        )

        assertTrue(result.preferredActivities.isEmpty())
        assertTrue(result.lowEnergyActivities.none { it.contains("şarkı", ignoreCase = true) })
        assertTrue(result.profileSummary.contains("Telefonu", ignoreCase = true))
    }

    @Test
    fun quickReplyCardUsesSelectedState() = runBlocking {
        val card = gateway.generateCard(
            profile = profile,
            currentUsageMinutes = 78,
            input = InterventionInput(
                text = "Bir şeyi erteliyorum",
                stateId = "procrastinating",
                stateLabel = "Bir şeyi erteliyorum",
                method = InterventionInputMethod.QUICK_REPLY,
            ),
        )

        assertTrue(card.question.contains("iki dakika"))
        assertTrue(card.reflection.isNotBlank())
        assertTrue(card.activityTitle.isNotBlank())
        assertEquals(3, card.durationMinutes)
        assertEquals("micro_start", card.strategy)
        assertTrue(SafetyLanguageValidator.isDisplaySafe(card.question, card.alternative))
    }

    @Test
    fun tiredFallbackDoesNotPrescribeHighEffortExercise() = runBlocking {
        val tiredProfile = profile.copy(
            hobbies = listOf("koşu", "müzik"),
            personalization = PersonalizationProfile(
                preferredActivities = listOf("koşu", "müzik"),
                lowEnergyActivities = listOf("bir şarkı dinlemek"),
            ),
        )

        val card = gateway.generateCard(
            profile = tiredProfile,
            currentUsageMinutes = 78,
            input = InterventionInput(
                text = "Biraz yoruldum",
                stateId = "tired",
                stateLabel = "Biraz yoruldum",
                method = InterventionInputMethod.QUICK_REPLY,
            ),
        )

        assertTrue(card.alternative.contains("müzik") || card.alternative.contains("şarkı"))
        assertFalse(card.alternative.contains("koşu", ignoreCase = true))
        assertFalse(card.alternative.contains("spor", ignoreCase = true))
    }

    @Test
    fun customTextOverridesGenericStateInFallback() = runBlocking {
        val card = gateway.generateCard(
            profile = profile.copy(
                personalization = PersonalizationProfile(goals = listOf("rapora başlamak")),
            ),
            currentUsageMinutes = 78,
            input = InterventionInput(
                text = "Ders çalışmam lazım ama başlamayı erteliyorum",
                stateId = "habit",
                stateLabel = "Alışkanlıkla açtım",
                method = InterventionInputMethod.TEXT,
            ),
        )

        assertTrue(card.question.contains("iki dakika"))
        assertTrue(card.alternative.contains("iki dakika"))
    }

    @Test
    fun lowMotivationFallbackUsesMicroStartInsteadOfGenericAdvice() = runBlocking {
        val card = gateway.generateCard(
            profile = profile.copy(
                personalization = PersonalizationProfile(
                    focusTargets = listOf("ders çalışmak"),
                ),
            ),
            currentUsageMinutes = 78,
            input = InterventionInput(
                text = "Motivasyonum düşük",
                stateId = "low_motivation",
                stateLabel = "Hiç başlayasım yok",
                method = InterventionInputMethod.QUICK_REPLY,
            ),
        )

        assertEquals("micro_start", card.strategy)
        assertEquals(3, card.durationMinutes)
        assertTrue(card.activityTitle.contains("ilk", ignoreCase = true))
        assertTrue(card.alternative.contains("ders çalışmak"))
        assertTrue(SafetyLanguageValidator.isDisplaySafe(card.reflection, card.question, card.alternative))
    }

    @Test
    fun overwhelmedFallbackReducesTheChoiceToOneNextStep() = runBlocking {
        val card = gateway.generateCard(
            profile = profile,
            currentUsageMinutes = 78,
            input = InterventionInput(
                text = "Nereden başlayacağımı bilmiyorum, çok fazla iş var",
                method = InterventionInputMethod.TEXT,
            ),
        )

        assertEquals("micro_start", card.strategy)
        assertEquals(3, card.durationMinutes)
        assertTrue(card.question.endsWith("?"))
        assertTrue(card.alternative.contains("yalnızca birini"))
    }

    @Test
    fun fallbackProvidesRichSafeCardsForEveryCanonicalState() = runBlocking {
        val states = listOf(
            "tired" to "Biraz yoruldum",
            "procrastinating" to "Bir şeyi erteliyorum",
            "relaxing" to "Sadece kafa dağıtıyorum",
            "bored" to "Biraz sıkıldım",
            "habit" to "Alışkanlıkla açtım",
            "waiting" to "Bir şeyi bekliyorum",
            "low_motivation" to "Motivasyonum düşük",
            "overwhelmed" to "Her şey bunaltıyor",
            "late_night" to "Uyumadan önce bakıyorum",
            "other" to "Başka bir şey",
        )

        states.forEach { (id, label) ->
            val card = gateway.generateCard(
                profile = profile,
                currentUsageMinutes = 78,
                input = InterventionInput(
                    text = label,
                    stateId = id,
                    stateLabel = label,
                    method = InterventionInputMethod.QUICK_REPLY,
                ),
            )

            assertTrue("$id reflection", card.reflection.isNotBlank())
            assertTrue("$id question", card.question.endsWith("?"))
            assertTrue("$id title", card.activityTitle.isNotBlank())
            assertTrue("$id alternative", card.alternative.isNotBlank())
            assertTrue("$id duration", card.durationMinutes > 0)
            assertTrue(
                "$id safety",
                SafetyLanguageValidator.isDisplaySafe(
                    card.reflection,
                    card.question,
                    card.activityTitle,
                    card.alternative,
                ),
            )
        }
    }

    @Test
    fun repeatedStateFallbackRotatesLocalAlternatives() = runBlocking {
        val input = InterventionInput(
            text = "Biraz yoruldum",
            stateId = "tired",
            stateLabel = "Biraz yoruldum",
            method = InterventionInputMethod.QUICK_REPLY,
        )
        val first = gateway.generateCard(profile, 78, input)
        val second = gateway.generateCard(
            profile = profile,
            currentUsageMinutes = 78,
            input = input,
            recentRecords = listOf(
                record(1).copy(stateId = "tired", aiAlternative = first.alternative),
            ),
        )

        assertTrue(first.alternative != second.alternative)
    }

    @Test
    fun reportIsUnavailableBelowSevenRecords() = runBlocking {
        val report = gateway.generateDailyReport(
            profile = profile,
            records = List(6, ::record),
            currentUsageMinutes = 78,
        )

        assertTrue(report.insufficientData)
    }

    @Test
    fun reportIsAvailableAtSevenRecords() = runBlocking {
        val report = gateway.generateDailyReport(
            profile = profile,
            records = List(7, ::record),
            currentUsageMinutes = 78,
        )

        assertFalse(report.insufficientData)
        assertTrue(report.observationQuestion.endsWith('?'))
        assertTrue(report.microStep.contains("dakika"))
        assertTrue(
            SafetyLanguageValidator.isDisplaySafe(
                report.observationQuestion,
                report.microStep,
            ),
        )
    }

    private fun record(index: Int): InterventionRecord = InterventionRecord(
        timestampEpochMillis = index.toLong(),
        usageMinutes = 60 + index,
        text = "örnek",
        choice = if (index % 2 == 0) UserChoice.CONTINUE else UserChoice.STOPPED,
        stateId = if (index % 2 == 0) "procrastinating" else "tired",
        stateLabel = if (index % 2 == 0) "Bir şeyi erteliyorum" else "Biraz yoruldum",
    )
}
