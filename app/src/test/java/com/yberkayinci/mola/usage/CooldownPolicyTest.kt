package com.yberkayinci.mola.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownPolicyTest {
    @Test
    fun firstInterventionCanShow() {
        assertTrue(CooldownPolicy.shouldShow(nowMillis = 1_000L, lastShownAtMillis = null))
    }

    @Test
    fun interventionIsBlockedBeforeCooldownExpires() {
        assertFalse(
            CooldownPolicy.shouldShow(
                nowMillis = CooldownPolicy.DEFAULT_COOLDOWN_MILLIS - 1L,
                lastShownAtMillis = 0L,
            ),
        )
    }

    @Test
    fun interventionCanShowAtCooldownBoundary() {
        assertTrue(
            CooldownPolicy.shouldShow(
                nowMillis = CooldownPolicy.DEFAULT_COOLDOWN_MILLIS,
                lastShownAtMillis = 0L,
            ),
        )
    }

    @Test
    fun clockRollbackDoesNotLockTheUserOut() {
        assertTrue(CooldownPolicy.shouldShow(nowMillis = 500L, lastShownAtMillis = 1_000L))
    }

    @Test
    fun remainingMillisReportsTimeUntilNextIntervention() {
        assertEquals(
            30_000L,
            CooldownPolicy.remainingMillis(
                nowMillis = 60_000L,
                lastShownAtMillis = 0L,
                cooldownMillis = 90_000L,
            ),
        )
    }

    @Test
    fun nonPositiveCooldownNeverBlocks() {
        assertTrue(
            CooldownPolicy.shouldShow(
                nowMillis = 1_000L,
                lastShownAtMillis = 1_000L,
                cooldownMillis = -1L,
            ),
        )
        assertEquals(
            0L,
            CooldownPolicy.remainingMillis(
                nowMillis = 1_000L,
                lastShownAtMillis = 1_000L,
                cooldownMillis = -1L,
            ),
        )
    }
}
