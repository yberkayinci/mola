package com.yberkayinci.mola.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionTriggerPolicyTest {
    @Test
    fun reachingTheUserDefinedThresholdStillTriggersExactlyAsBefore() {
        val decision = decide(usageMinutes = 60, dailyLimitMinutes = 60)

        assertEquals(InterventionTrigger.THRESHOLD, decision.trigger)
    }

    @Test
    fun thresholdRespectsTheExistingFifteenMinuteCooldown() {
        val decision = decide(
            usageMinutes = 90,
            dailyLimitMinutes = 60,
            lastThresholdShownAtMillis = NOW - minutes(5),
        )

        assertNull(decision.trigger)
        assertTrue(decision.reason.contains("cooldown"))
    }

    @Test
    fun patternTriggersNeverFireBeforeTheUsageFloor() {
        val decision = decide(
            usageMinutes = InterventionTriggerPolicy.MIN_USAGE_MINUTES_FOR_PATTERN - 1,
            snapshot = loopSnapshot(),
        )

        assertNull(decision.trigger)
        assertTrue(decision.reason.contains("usage floor"))
    }

    @Test
    fun reopeningWithinSecondsIsTreatedAsACompulsiveLoop() {
        val decision = decide(
            snapshot = snapshot(
                isTargetForeground = true,
                lastGapMillis = 12_000L,
            ),
        )

        assertEquals(InterventionTrigger.IMMEDIATE_REOPEN, decision.trigger)
        assertTrue(decision.trigger!!.isPattern)
    }

    @Test
    fun repeatedOpensInAShortWindowTrigger() {
        val decision = decide(snapshot = loopSnapshot())

        assertEquals(InterventionTrigger.RAPID_REOPEN_LOOP, decision.trigger)
    }

    @Test
    fun aSessionMuchLongerThanTheUsersOwnBaselineTriggers() {
        val decision = decide(
            snapshot = snapshot(
                isTargetForeground = true,
                currentSessionMillis = minutes(21),
                completedSessionCount = 6,
                medianSessionMillis = minutes(6),
            ),
        )

        assertEquals(InterventionTrigger.SESSION_DRIFT, decision.trigger)
    }

    @Test
    fun driftNeedsEnoughSessionsBeforeTheBaselineIsTrusted() {
        val decision = decide(
            snapshot = snapshot(
                isTargetForeground = true,
                currentSessionMillis = minutes(30),
                completedSessionCount = InterventionTriggerPolicy.MIN_SESSIONS_FOR_BASELINE - 1,
                medianSessionMillis = minutes(2),
            ),
        )

        assertNull(decision.trigger)
    }

    @Test
    fun anUnusuallyLongButStillShortSessionIsNotADrift() {
        val decision = decide(
            snapshot = snapshot(
                isTargetForeground = true,
                currentSessionMillis = minutes(2),
                completedSessionCount = 8,
                medianSessionMillis = 20_000L,
            ),
        )

        assertNull(decision.trigger)
    }

    @Test
    fun patternTriggersHaveTheirOwnLongerCooldown() {
        val decision = decide(
            snapshot = loopSnapshot(),
            lastPatternShownAtMillis = NOW - minutes(20),
        )

        assertNull(decision.trigger)
        assertTrue(decision.reason.contains("pattern cooldown"))
    }

    @Test
    fun patternTriggersAreCappedPerDay() {
        val decision = decide(
            snapshot = loopSnapshot(),
            patternInterventionsToday = InterventionTriggerPolicy.MAX_PATTERN_INTERVENTIONS_PER_DAY,
        )

        assertNull(decision.trigger)
        assertTrue(decision.reason.contains("daily cap"))
    }

    @Test
    fun patternTriggersRequireTheTargetAppToBeInFront() {
        val decision = decide(snapshot = loopSnapshot().copy(isTargetForeground = false))

        assertNull(decision.trigger)
        assertFalse(decision.shouldIntervene)
    }

    @Test
    fun theThresholdAlwaysWinsOverAPatternWhenBothApply() {
        val decision = decide(
            usageMinutes = 75,
            dailyLimitMinutes = 60,
            snapshot = loopSnapshot(),
        )

        assertEquals(InterventionTrigger.THRESHOLD, decision.trigger)
    }

    @Test
    fun aQuietSessionProducesNoTrigger() {
        val decision = decide(
            snapshot = snapshot(isTargetForeground = true, currentSessionMillis = minutes(3)),
        )

        assertNull(decision.trigger)
        assertEquals("no pattern match", decision.reason)
    }

    private fun decide(
        snapshot: UsagePatternSnapshot = UsagePatternSnapshot.EMPTY,
        usageMinutes: Int = 25,
        dailyLimitMinutes: Int = 60,
        lastThresholdShownAtMillis: Long? = null,
        lastPatternShownAtMillis: Long? = null,
        patternInterventionsToday: Int = 0,
    ): TriggerDecision = InterventionTriggerPolicy.decide(
        snapshot = snapshot,
        usageMinutes = usageMinutes,
        dailyLimitMinutes = dailyLimitMinutes,
        nowMillis = NOW,
        lastThresholdShownAtMillis = lastThresholdShownAtMillis,
        lastPatternShownAtMillis = lastPatternShownAtMillis,
        patternInterventionsToday = patternInterventionsToday,
    )

    private fun loopSnapshot(): UsagePatternSnapshot = snapshot(
        isTargetForeground = true,
        targetOpenCount = InterventionTriggerPolicy.RAPID_REOPEN_MIN_OPENS,
        currentSessionMillis = 40_000L,
    )

    private fun snapshot(
        targetOpenCount: Int = 1,
        isTargetForeground: Boolean = false,
        currentSessionMillis: Long = 0L,
        completedSessionCount: Int = 0,
        medianSessionMillis: Long = 0L,
        lastGapMillis: Long = UsagePatternSnapshot.UNKNOWN_GAP,
        continuousActivityMillis: Long = 0L,
    ): UsagePatternSnapshot = UsagePatternSnapshot(
        targetOpenCount = targetOpenCount,
        isTargetForeground = isTargetForeground,
        currentSessionMillis = currentSessionMillis,
        completedSessionCount = completedSessionCount,
        medianSessionMillis = medianSessionMillis,
        lastGapMillis = lastGapMillis,
        previousPackage = "",
        continuousActivityMillis = continuousActivityMillis,
    )

    private fun minutes(value: Long): Long = value * 60L * 1_000L

    private companion object {
        const val NOW = 1_756_000_000_000L
    }
}
