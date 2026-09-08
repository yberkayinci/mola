package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.UserChoice
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReportEvidenceBuilderTest {
    private val zone = ZoneId.of("Europe/Istanbul")

    @Test
    fun findsDominantStateAndHigherContinueStateWithEnoughEvidence() {
        val records = listOf(
            record("procrastinating", UserChoice.CONTINUE, 10),
            record("procrastinating", UserChoice.CONTINUE, 11),
            record("procrastinating", UserChoice.STOPPED, 12),
            record("tired", UserChoice.STOPPED, 19),
            record("tired", UserChoice.STOPPED, 20),
            record("habit", UserChoice.CONTINUE, 23),
            record("waiting", UserChoice.STOPPED, 14),
        )

        val evidence = DailyReportEvidenceBuilder.build(records, zone)

        assertEquals("procrastinating", evidence.dominantStateId)
        assertEquals("procrastinating", evidence.higherContinueStateId)
        assertTrue(evidence.candidateStateIds.contains("procrastinating"))
        assertTrue(evidence.candidateStateIds.contains("tired"))
        assertEquals(3, evidence.states.first { it.stateId == "procrastinating" }.count)
    }

    @Test
    fun doesNotDeclareDominantStateWhenTopCountsTie() {
        val records = listOf(
            record("procrastinating", UserChoice.CONTINUE, 10),
            record("procrastinating", UserChoice.STOPPED, 11),
            record("tired", UserChoice.CONTINUE, 19),
            record("tired", UserChoice.STOPPED, 20),
            record("habit", UserChoice.CONTINUE, 23),
            record("waiting", UserChoice.STOPPED, 14),
            record("bored", UserChoice.CONTINUE, 15),
        )

        val evidence = DailyReportEvidenceBuilder.build(records, zone)

        assertNull(evidence.dominantStateId)
    }

    @Test
    fun excludesSingleObservationStatesFromEvidenceCandidates() {
        val evidence = DailyReportEvidenceBuilder.build(
            records = listOf(
                record("procrastinating", UserChoice.CONTINUE, 10),
                record("tired", UserChoice.STOPPED, 20),
            ),
            zoneId = zone,
        )

        assertTrue(evidence.candidateStateIds.isEmpty())
        assertNull(evidence.higherContinueStateId)
    }

    @Test
    fun calculatesBroadTimeBucketsLocally() {
        val evidence = DailyReportEvidenceBuilder.build(
            records = listOf(
                record("habit", UserChoice.CONTINUE, 8),
                record("habit", UserChoice.CONTINUE, 14),
                record("habit", UserChoice.STOPPED, 20),
                record("habit", UserChoice.STOPPED, 1),
            ),
            zoneId = zone,
        )

        assertEquals(1, evidence.timeBucketCounts["morning"])
        assertEquals(1, evidence.timeBucketCounts["afternoon"])
        assertEquals(1, evidence.timeBucketCounts["evening"])
        assertEquals(1, evidence.timeBucketCounts["late_night"])
    }

    private fun record(
        stateId: String,
        choice: UserChoice,
        hour: Int,
    ): InterventionRecord = InterventionRecord(
        timestampEpochMillis = LocalDateTime.of(2026, 8, 30, hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli(),
        usageMinutes = 70,
        text = stateId,
        choice = choice,
        stateId = stateId,
        stateLabel = stateId,
    )
}
