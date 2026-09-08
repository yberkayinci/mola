package com.yberkayinci.mola.usage

/**
 * Why an intervention was offered.
 *
 * [THRESHOLD] is the original contract: the user reached the limit they set. The remaining triggers
 * describe a shape of use rather than an amount, so Mola can offer a pause during a difficult
 * moment instead of only after a total has accumulated.
 */
enum class InterventionTrigger(val storageValue: String) {
    THRESHOLD("threshold"),
    IMMEDIATE_REOPEN("immediate_reopen"),
    RAPID_REOPEN_LOOP("rapid_reopen_loop"),
    SESSION_DRIFT("session_drift");

    val isPattern: Boolean
        get() = this != THRESHOLD

    companion object {
        fun fromStorage(value: String): InterventionTrigger? =
            entries.firstOrNull { it.storageValue == value.trim().lowercase() }
    }
}

data class TriggerDecision(
    val trigger: InterventionTrigger?,
    val reason: String,
) {
    val shouldIntervene: Boolean
        get() = trigger != null

    companion object {
        fun none(reason: String) = TriggerDecision(trigger = null, reason = reason)
    }
}

/**
 * Decides whether this moment deserves an intervention, and on what grounds.
 *
 * The threshold path is unchanged from the validated baseline. Pattern triggers are additive and
 * deliberately hard to fire: they need a floor of usage so they cannot interrupt the first minutes
 * of the day, their own longer cooldown, and a small daily cap. A trigger that fires at the wrong
 * moment costs more trust than a trigger that never fires.
 */
object InterventionTriggerPolicy {
    /** Below this many minutes today, no pattern trigger fires at all. */
    const val MIN_USAGE_MINUTES_FOR_PATTERN: Int = 10

    /** Pattern interventions allowed per local day, on top of threshold interventions. */
    const val MAX_PATTERN_INTERVENTIONS_PER_DAY: Int = 2

    const val PATTERN_COOLDOWN_MILLIS: Long = 45L * 60L * 1_000L

    /** Reopening the same app this quickly after leaving it is treated as one compulsive loop. */
    const val IMMEDIATE_REOPEN_MILLIS: Long = 30L * 1_000L

    const val RAPID_REOPEN_MIN_OPENS: Int = 3

    /** Completed sessions needed before a personal session-length baseline is trusted. */
    const val MIN_SESSIONS_FOR_BASELINE: Int = 5

    const val SESSION_DRIFT_MULTIPLIER: Long = 3L

    /** However unusual it is, a session shorter than this is never called a drift. */
    const val SESSION_DRIFT_FLOOR_MILLIS: Long = 5L * 60L * 1_000L

    @Suppress("ReturnCount")
    fun decide(
        snapshot: UsagePatternSnapshot,
        usageMinutes: Int,
        dailyLimitMinutes: Int,
        nowMillis: Long,
        lastThresholdShownAtMillis: Long?,
        lastPatternShownAtMillis: Long?,
        patternInterventionsToday: Int,
    ): TriggerDecision {
        if (usageMinutes >= dailyLimitMinutes) {
            val remaining = CooldownPolicy.remainingMillis(nowMillis, lastThresholdShownAtMillis)
            if (remaining > 0L) {
                return TriggerDecision.none("threshold reached; cooldown ${remaining / 1_000L}s")
            }
            return TriggerDecision(InterventionTrigger.THRESHOLD, "usage $usageMinutes/$dailyLimitMinutes")
        }

        if (!snapshot.isTargetForeground) {
            return TriggerDecision.none("target not in foreground")
        }
        if (usageMinutes < MIN_USAGE_MINUTES_FOR_PATTERN) {
            return TriggerDecision.none("below pattern usage floor ($usageMinutes m)")
        }
        if (patternInterventionsToday >= MAX_PATTERN_INTERVENTIONS_PER_DAY) {
            return TriggerDecision.none("pattern daily cap reached")
        }
        val patternRemaining = CooldownPolicy.remainingMillis(
            nowMillis = nowMillis,
            lastShownAtMillis = lastPatternShownAtMillis,
            cooldownMillis = PATTERN_COOLDOWN_MILLIS,
        )
        if (patternRemaining > 0L) {
            return TriggerDecision.none("pattern cooldown ${patternRemaining / 1_000L}s")
        }

        val gap = snapshot.lastGapMillis
        if (gap != UsagePatternSnapshot.UNKNOWN_GAP && gap <= IMMEDIATE_REOPEN_MILLIS) {
            return TriggerDecision(
                InterventionTrigger.IMMEDIATE_REOPEN,
                "reopened after ${gap / 1_000L}s",
            )
        }
        if (snapshot.targetOpenCount >= RAPID_REOPEN_MIN_OPENS) {
            return TriggerDecision(
                InterventionTrigger.RAPID_REOPEN_LOOP,
                "${snapshot.targetOpenCount} opens in window",
            )
        }
        if (isSessionDrift(snapshot)) {
            return TriggerDecision(
                InterventionTrigger.SESSION_DRIFT,
                "session ${snapshot.currentSessionMillis / 60_000L}m vs median " +
                    "${snapshot.medianSessionMillis / 60_000L}m",
            )
        }
        return TriggerDecision.none("no pattern match")
    }

    private fun isSessionDrift(snapshot: UsagePatternSnapshot): Boolean {
        if (snapshot.completedSessionCount < MIN_SESSIONS_FOR_BASELINE) return false
        if (snapshot.medianSessionMillis <= 0L) return false
        if (snapshot.currentSessionMillis < SESSION_DRIFT_FLOOR_MILLIS) return false
        return snapshot.currentSessionMillis >= snapshot.medianSessionMillis * SESSION_DRIFT_MULTIPLIER
    }
}
