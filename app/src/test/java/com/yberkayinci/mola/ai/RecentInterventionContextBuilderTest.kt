package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.UserChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentInterventionContextBuilderTest {
    @Test
    fun keepsOnlyTheLatestSixRecords() {
        val records = (0..7).map(::record)

        val context = RecentInterventionContextBuilder.build(records)

        assertEquals(6, context.size)
        assertEquals("tired", context.first().state)
        assertEquals("procrastinating", context.last().state)
    }

    @Test
    fun mapsChoicesAndBoundsPreviousAlternative() {
        val context = RecentInterventionContextBuilder.build(
            listOf(
                record(1).copy(
                    choice = UserChoice.CONTINUE,
                    aiAlternative = "x".repeat(300),
                ),
                record(2).copy(
                    choice = UserChoice.STOPPED,
                    aiAlternative = "Telefonu masaya bırakıp iki dakika dinlenebilirsin.",
                ),
            ),
        )

        assertEquals("continue", context[0].choice)
        assertEquals("stopped", context[1].choice)
        assertEquals(240, context[0].previousAlternative.length)
    }

    @Test
    fun recentAlternativesIncludeTitleAndAlternativeWithoutRawUserText() {
        val context = RecentInterventionContextBuilder.recentAlternatives(
            listOf(
                record(1).copy(
                    text = "Özel ve ham kullanıcı metni",
                    aiActivityTitle = "İlk 3 dakika",
                    aiAlternative = "İlk adımı yaz.",
                ),
                record(2).copy(aiAlternative = ""),
            ),
        )

        assertEquals(listOf("İlk 3 dakika İlk adımı yaz."), context)
        assertTrue(context.none { it.contains("ham kullanıcı metni") })
    }

    private fun record(index: Int): InterventionRecord = InterventionRecord(
        timestampEpochMillis = index.toLong(),
        usageMinutes = 60,
        text = "custom text $index",
        choice = UserChoice.STOPPED,
        stateId = if (index % 2 == 0) "tired" else "procrastinating",
        stateLabel = "State $index",
        aiAlternative = "Alternative $index",
    )
}
