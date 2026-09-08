package com.yberkayinci.mola.usage

/**
 * Everything the app knows about this moment without asking the user a single question.
 *
 * Every field is derived from data Mola already has permission to read. Nothing here is sent
 * anywhere: the signals are interpreted on the device and then discarded.
 */
data class NeedSignals(
    val pattern: UsagePatternSnapshot,
    val trigger: InterventionTrigger,
    val hourOfDay: Int,
    val isCharging: Boolean,
    val usageMinutes: Int,
    val dailyLimitMinutes: Int,
) {
    companion object {
        fun of(
            pattern: UsagePatternSnapshot,
            trigger: InterventionTrigger,
            hourOfDay: Int,
            isCharging: Boolean = false,
            usageMinutes: Int = 0,
            dailyLimitMinutes: Int = 0,
        ): NeedSignals = NeedSignals(
            pattern = pattern,
            trigger = trigger,
            hourOfDay = hourOfDay.coerceIn(0, 23),
            isCharging = isCharging,
            usageMinutes = usageMinutes,
            dailyLimitMinutes = dailyLimitMinutes,
        )
    }
}
