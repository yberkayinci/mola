package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.UserChoice
import java.time.Instant
import java.time.ZoneId

internal data class StateChoiceEvidence(
    val stateId: String,
    val stateLabel: String,
    val count: Int,
    val continuedCount: Int,
    val stoppedCount: Int,
) {
    val continueRatio: Double
        get() = if (count == 0) 0.0 else continuedCount.toDouble() / count.toDouble()
}

internal data class DailyReportEvidence(
    val totalRecords: Int,
    val states: List<StateChoiceEvidence>,
    val candidateStateIds: Set<String>,
    val dominantStateId: String?,
    val higherContinueStateId: String?,
    val timeBucketCounts: Map<String, Int>,
)

internal object DailyReportEvidenceBuilder {
    fun build(
        records: List<InterventionRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DailyReportEvidence {
        val grouped = records
            .mapNotNull { record ->
                val id = record.stateId.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
                id to record
            }
            .groupBy({ it.first }, { it.second })

        val states = grouped.map { (stateId, stateRecords) ->
            StateChoiceEvidence(
                stateId = stateId,
                stateLabel = stateRecords
                    .asReversed()
                    .firstNotNullOfOrNull { it.stateLabel.trim().takeIf(String::isNotEmpty) }
                    .orEmpty(),
                count = stateRecords.size,
                continuedCount = stateRecords.count { it.choice == UserChoice.CONTINUE },
                stoppedCount = stateRecords.count { it.choice == UserChoice.STOPPED },
            )
        }.sortedWith(
            compareByDescending<StateChoiceEvidence> { it.count }
                .thenBy { it.stateId },
        )

        val candidateStateIds = states
            .filter { it.count >= MIN_STATE_SAMPLE }
            .mapTo(linkedSetOf(), StateChoiceEvidence::stateId)

        val dominantStateId = states.firstOrNull()
            ?.takeIf { first ->
                first.count >= MIN_STATE_SAMPLE &&
                    (states.getOrNull(1)?.count ?: 0) < first.count
            }
            ?.stateId

        val higherContinueStateId = states
            .asSequence()
            .filter { it.count >= MIN_STATE_SAMPLE }
            .filter { it.continueRatio >= MIN_CONTINUE_RATIO }
            .maxWithOrNull(
                compareBy<StateChoiceEvidence> { it.continueRatio }
                    .thenBy { it.count },
            )
            ?.stateId

        val timeBucketCounts = records
            .groupingBy { record ->
                val hour = Instant.ofEpochMilli(record.timestampEpochMillis)
                    .atZone(zoneId)
                    .hour
                when (hour) {
                    in 5..11 -> "morning"
                    in 12..17 -> "afternoon"
                    in 18..22 -> "evening"
                    else -> "late_night"
                }
            }
            .eachCount()
            .toSortedMap()

        return DailyReportEvidence(
            totalRecords = records.size,
            states = states,
            candidateStateIds = candidateStateIds,
            dominantStateId = dominantStateId,
            higherContinueStateId = higherContinueStateId,
            timeBucketCounts = timeBucketCounts,
        )
    }

    private const val MIN_STATE_SAMPLE = 2
    private const val MIN_CONTINUE_RATIO = 0.6
}
