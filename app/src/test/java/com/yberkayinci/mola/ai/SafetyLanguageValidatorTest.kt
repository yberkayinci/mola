package com.yberkayinci.mola.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyLanguageValidatorTest {
    @Test
    fun blocksJudgmentalUsagePhrases() {
        assertFalse(SafetyLanguageValidator.isDisplaySafe("Bugün çok kullandın."))
        assertFalse(SafetyLanguageValidator.isDisplaySafe("Bu kullanım fazla olabilir."))
        assertFalse(SafetyLanguageValidator.isDisplaySafe("Gereğinden çok ekrana baktın."))
    }

    @Test
    fun allowsBenignCokInsideUserGoal() {
        assertTrue(SafetyLanguageValidator.isDisplaySafe("Daha çok kitap okumak"))
    }

    @Test
    fun doesNotBlockWordFragments() {
        assertTrue(
            SafetyLanguageValidator.isDisplaySafe(
                "İki dakikalık küçük bir başlangıç yapabilirsin.",
            ),
        )
    }

    @Test
    fun blocksDiagnosisAndFailureLanguage() {
        assertFalse(SafetyLanguageValidator.isDisplaySafe("Bağımlısın."))
        assertFalse(SafetyLanguageValidator.isDisplaySafe("Bu bağımlılık olabilir."))
        assertFalse(SafetyLanguageValidator.isDisplaySafe("Bu depresyon belirtisi."))
        assertFalse(SafetyLanguageValidator.isDisplaySafe("Başarısız oldun."))
    }
}
