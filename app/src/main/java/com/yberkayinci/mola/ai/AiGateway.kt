package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.AiCard
import com.yberkayinci.mola.data.DailyReport
import com.yberkayinci.mola.data.InterventionInput
import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.PersonalizationProfile
import com.yberkayinci.mola.data.ProfileIntake
import com.yberkayinci.mola.data.UserProfile

interface AiGateway {
    suspend fun generateProfile(intake: ProfileIntake): PersonalizationProfile

    suspend fun generateCard(
        profile: UserProfile,
        currentUsageMinutes: Int,
        input: InterventionInput,
        recentRecords: List<InterventionRecord> = emptyList(),
    ): AiCard

    suspend fun generateDailyReport(
        profile: UserProfile,
        records: List<InterventionRecord>,
        currentUsageMinutes: Int,
    ): DailyReport
}
