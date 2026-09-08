package com.yberkayinci.mola.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSessionAnalyzerTest {
    @Test
    fun closedSessionIsMeasuredFromForegroundToBackground() {
        val snapshot = analyze(
            foreground(TARGET, 0),
            background(TARGET, minutes(1)),
            nowMillis = minutes(2),
        )

        assertFalse(snapshot.isTargetForeground)
        assertEquals(1, snapshot.completedSessionCount)
        assertEquals(minutes(1), snapshot.medianSessionMillis)
        assertEquals(0L, snapshot.currentSessionMillis)
    }

    @Test
    fun openSessionIsMeasuredUpToNow() {
        val snapshot = analyze(
            foreground(TARGET, minutes(1)),
            nowMillis = minutes(4),
        )

        assertTrue(snapshot.isTargetForeground)
        assertEquals(minutes(3), snapshot.currentSessionMillis)
        assertEquals(0, snapshot.completedSessionCount)
    }

    @Test
    fun switchingToAnotherAppClosesTheSessionWithoutABackgroundEvent() {
        val snapshot = analyze(
            foreground(TARGET, 0),
            foreground(OTHER, minutes(2)),
            nowMillis = minutes(3),
        )

        assertFalse(snapshot.isTargetForeground)
        assertEquals(1, snapshot.completedSessionCount)
        assertEquals(minutes(2), snapshot.medianSessionMillis)
    }

    @Test
    fun onlyOpensInsideTheShortWindowAreCounted() {
        val now = minutes(60)
        val snapshot = analyze(
            foreground(TARGET, minutes(20)),
            background(TARGET, minutes(21)),
            foreground(TARGET, minutes(53)),
            background(TARGET, minutes(54)),
            foreground(TARGET, minutes(56)),
            background(TARGET, minutes(57)),
            foreground(TARGET, minutes(59)),
            nowMillis = now,
        )

        assertEquals("the 20-minute-old open is outside the window", 3, snapshot.targetOpenCount)
    }

    @Test
    fun medianIgnoresTheStillOpenSession() {
        val snapshot = analyze(
            foreground(TARGET, 0),
            background(TARGET, seconds(30)),
            foreground(TARGET, minutes(2)),
            background(TARGET, minutes(3)),
            foreground(TARGET, minutes(4)),
            background(TARGET, minutes(5) + seconds(30)),
            foreground(TARGET, minutes(6)),
            nowMillis = minutes(20),
        )

        assertEquals(3, snapshot.completedSessionCount)
        assertEquals(minutes(1), snapshot.medianSessionMillis)
        assertTrue(snapshot.isTargetForeground)
    }

    @Test
    fun reopeningQuicklyProducesASmallGap() {
        val snapshot = analyze(
            foreground(TARGET, 0),
            background(TARGET, minutes(1)),
            foreground(TARGET, minutes(1) + seconds(12)),
            nowMillis = minutes(2),
        )

        assertEquals(seconds(12), snapshot.lastGapMillis)
    }

    @Test
    fun gapIsUnknownWhenTheAppWasNeverLeft() {
        val snapshot = analyze(
            foreground(TARGET, 0),
            nowMillis = minutes(2),
        )

        assertEquals(UsagePatternSnapshot.UNKNOWN_GAP, snapshot.lastGapMillis)
    }

    @Test
    fun theAppUsedJustBeforeTheTargetIsReported() {
        val snapshot = analyze(
            foreground(OTHER, 0),
            foreground(TARGET, minutes(1)),
            nowMillis = minutes(2),
        )

        assertEquals(OTHER, snapshot.previousPackage)
    }

    @Test
    fun shortSwitchesBetweenAppsCountAsOneContinuousRun() {
        val snapshot = analyze(
            foreground(OTHER, 0),
            background(OTHER, minutes(10)),
            foreground(TARGET, minutes(11)),
            nowMillis = minutes(20),
        )

        assertEquals(minutes(20), snapshot.continuousActivityMillis)
    }

    @Test
    fun anIdleGapBreaksTheContinuousRun() {
        val snapshot = analyze(
            foreground(OTHER, 0),
            background(OTHER, minutes(10)),
            foreground(TARGET, minutes(40)),
            nowMillis = minutes(50),
        )

        assertEquals(minutes(50) - minutes(40), snapshot.continuousActivityMillis)
    }

    @Test
    fun screenOffEndsTheContinuousRunEvenWithoutAnIdleGap() {
        val snapshot = analyze(
            foreground(OTHER, 0),
            background(OTHER, minutes(10)),
            UsageEventSample("android", minutes(11), UsageEventType.SCREEN_OFF),
            foreground(TARGET, minutes(12)),
            nowMillis = minutes(20),
        )

        assertEquals(minutes(20) - minutes(11), snapshot.continuousActivityMillis)
    }

    @Test
    fun aDeviceIdleForLongerThanTheGapReportsNoRun() {
        val snapshot = analyze(
            foreground(TARGET, 0),
            background(TARGET, minutes(5)),
            nowMillis = minutes(40),
        )

        assertEquals(0L, snapshot.continuousActivityMillis)
    }

    @Test
    fun anUnrelatedEventStreamProducesTheEmptySnapshotForTheTarget() {
        val snapshot = analyze(
            foreground(OTHER, 0),
            background(OTHER, minutes(1)),
            nowMillis = minutes(2),
        )

        assertEquals(0, snapshot.targetOpenCount)
        assertFalse(snapshot.isTargetForeground)
        assertEquals(0L, snapshot.medianSessionMillis)
    }

    @Test
    fun aBlankTargetPackageNeverProducesASnapshot() {
        val snapshot = UsageSessionAnalyzer.analyze(
            events = listOf(foreground(TARGET, 0)),
            targetPackage = " ",
            nowMillis = minutes(1),
        )

        assertEquals(UsagePatternSnapshot.EMPTY, snapshot)
    }

    @Test
    fun eventsOutOfOrderAreHandled() {
        val ordered = analyze(
            foreground(TARGET, 0),
            background(TARGET, minutes(1)),
            nowMillis = minutes(2),
        )
        val shuffled = UsageSessionAnalyzer.analyze(
            events = listOf(background(TARGET, minutes(1)), foreground(TARGET, 0)),
            targetPackage = TARGET,
            nowMillis = minutes(2),
        )

        assertEquals(ordered, shuffled)
    }

    private fun analyze(
        vararg events: UsageEventSample,
        nowMillis: Long,
    ): UsagePatternSnapshot = UsageSessionAnalyzer.analyze(
        events = events.toList(),
        targetPackage = TARGET,
        nowMillis = nowMillis,
    )

    private fun foreground(packageName: String, atMillis: Long) =
        UsageEventSample(packageName, atMillis, UsageEventType.FOREGROUND)

    private fun background(packageName: String, atMillis: Long) =
        UsageEventSample(packageName, atMillis, UsageEventType.BACKGROUND)

    private fun minutes(value: Long): Long = value * 60L * 1_000L

    private fun seconds(value: Long): Long = value * 1_000L

    private companion object {
        const val TARGET = "com.instagram.android"
        const val OTHER = "com.whatsapp"
    }
}
