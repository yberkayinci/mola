package com.yberkayinci.mola.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrisisFilterTest {
    @Test
    fun catchesTurkishCrisisPhraseWithDiacritics() {
        assertTrue(CrisisFilter.check("Kendimi öldürmek istiyorum.").isCrisisSignal)
    }

    @Test
    fun catchesEnglishSuicidalThoughtsPhrase() {
        assertTrue(CrisisFilter.check("I'm having suicidal thoughts.").isCrisisSignal)
    }

    @Test
    fun catchesEnglishWantToDiePhrase() {
        assertTrue(CrisisFilter.check("Sometimes I want to die.").isCrisisSignal)
    }

    @Test
    fun doesNotFlagOrdinaryFatigueText() {
        assertFalse(CrisisFilter.check("Bugün yoruldum, biraz kafamı dağıtacağım.").isCrisisSignal)
    }
}
