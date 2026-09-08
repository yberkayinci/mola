package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.QuickStateTaxonomy
import com.yberkayinci.mola.data.UserChoice

internal data class RecentInterventionContext(
    val state: String,
    val choice: String,
    val previousAlternative: String,
)

internal object RecentInterventionContextBuilder {
    fun build(
        records: List<InterventionRecord>,
        maxItems: Int = MAX_ITEMS,
    ): List<RecentInterventionContext> = records
        .takeLast(maxItems.coerceIn(0, MAX_ITEMS))
        .map { record ->
            RecentInterventionContext(
                state = QuickStateTaxonomy.canonicalize(record.stateId) ?: "other",
                choice = when (record.choice) {
                    UserChoice.CONTINUE -> "continue"
                    UserChoice.STOPPED -> "stopped"
                },
                previousAlternative = record.aiAlternative.trim().take(MAX_ALTERNATIVE_CHARS),
            )
        }

    fun recentAlternatives(
        records: List<InterventionRecord>,
        maxItems: Int = MAX_ITEMS,
    ): List<String> = records
        .takeLast(maxItems.coerceIn(0, MAX_ITEMS))
        .mapNotNull { record ->
            val value = listOf(record.aiActivityTitle.trim(), record.aiAlternative.trim())
                .filter(String::isNotBlank)
                .joinToString(" ")
                .take(MAX_ALTERNATIVE_CHARS)
            value.takeIf(String::isNotBlank)
        }

    private const val MAX_ITEMS = 6
    private const val MAX_ALTERNATIVE_CHARS = 240
}
