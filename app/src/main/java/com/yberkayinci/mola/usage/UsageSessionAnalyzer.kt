package com.yberkayinci.mola.usage

/** A single foreground/background transition, decoupled from the Android event classes. */
data class UsageEventSample(
    val packageName: String,
    val timestampMillis: Long,
    val type: UsageEventType,
)

enum class UsageEventType {
    FOREGROUND,
    BACKGROUND,

    /** Screen turned off or the keyguard appeared: any continuous-use run ends here. */
    SCREEN_OFF,
}

/** One continuous foreground stretch of a single app. [endMillis] is null while it is still open. */
data class AppSession(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long?,
) {
    val isOpen: Boolean
        get() = endMillis == null

    fun durationMillis(nowMillis: Long): Long =
        ((endMillis ?: nowMillis) - startMillis).coerceAtLeast(0L)
}

/**
 * What the user's recent behaviour looks like, independent of how many minutes they have spent.
 *
 * Total daily minutes are a poor description of a difficult moment: an hour of deliberate viewing is
 * not the same as twelve minutes of opening and closing the same app. These fields describe the
 * shape of the behaviour so the app can react to the shape rather than only to the total.
 */
data class UsagePatternSnapshot(
    val targetOpenCount: Int,
    val isTargetForeground: Boolean,
    val currentSessionMillis: Long,
    val completedSessionCount: Int,
    val medianSessionMillis: Long,
    /** Gap between the previous target session ending and the current one starting; -1 if unknown. */
    val lastGapMillis: Long,
    /** App that was in the foreground immediately before the current target session. */
    val previousPackage: String,
    /** How long the device has been in continuous use across all apps, without an idle break. */
    val continuousActivityMillis: Long,
) {
    companion object {
        val EMPTY = UsagePatternSnapshot(
            targetOpenCount = 0,
            isTargetForeground = false,
            currentSessionMillis = 0L,
            completedSessionCount = 0,
            medianSessionMillis = 0L,
            lastGapMillis = UNKNOWN_GAP,
            previousPackage = "",
            continuousActivityMillis = 0L,
        )

        const val UNKNOWN_GAP: Long = -1L
    }
}

/**
 * Turns a raw event list into sessions and behavioural measures.
 *
 * Deliberately free of Android types so every rule below is unit-testable. `UsageStatsReader` is
 * responsible only for mapping platform events into [UsageEventSample].
 */
object UsageSessionAnalyzer {
    /** Two app switches closer together than this still count as one continuous run of use. */
    const val DEFAULT_IDLE_GAP_MILLIS: Long = 3L * 60L * 1_000L

    /**
     * Window for counting repeated opens.
     *
     * Session length and the continuous-use run are read from the whole event list, but "opened it
     * again and again" only means something over a short stretch.
     */
    const val DEFAULT_OPEN_WINDOW_MILLIS: Long = 10L * 60L * 1_000L

    fun analyze(
        events: List<UsageEventSample>,
        targetPackage: String,
        nowMillis: Long,
        idleGapMillis: Long = DEFAULT_IDLE_GAP_MILLIS,
        openWindowMillis: Long = DEFAULT_OPEN_WINDOW_MILLIS,
    ): UsagePatternSnapshot {
        if (targetPackage.isBlank()) return UsagePatternSnapshot.EMPTY

        val ordered = events.sortedBy(UsageEventSample::timestampMillis)
        val sessions = buildSessions(ordered, nowMillis)
        val targetSessions = sessions.filter { it.packageName == targetPackage }
        if (targetSessions.isEmpty()) {
            return UsagePatternSnapshot.EMPTY.copy(
                continuousActivityMillis = continuousActivityMillis(sessions, ordered, nowMillis, idleGapMillis),
            )
        }

        val current = targetSessions.lastOrNull()?.takeIf(AppSession::isOpen)
        val completed = targetSessions.filterNot(AppSession::isOpen)
        val currentIndex = current?.let(sessions::indexOf) ?: -1

        val openWindowStart = nowMillis - openWindowMillis
        return UsagePatternSnapshot(
            targetOpenCount = targetSessions.count { it.startMillis >= openWindowStart },
            isTargetForeground = current != null,
            currentSessionMillis = current?.durationMillis(nowMillis) ?: 0L,
            completedSessionCount = completed.size,
            medianSessionMillis = medianMillis(completed.map { it.durationMillis(nowMillis) }),
            lastGapMillis = lastGapMillis(targetSessions, current),
            previousPackage = if (currentIndex > 0) sessions[currentIndex - 1].packageName else "",
            continuousActivityMillis = continuousActivityMillis(sessions, ordered, nowMillis, idleGapMillis),
        )
    }

    /**
     * Walks the event list as a state machine.
     *
     * A foreground event for one app implicitly ends whatever else was open, because the platform
     * does not guarantee a matching background event for the app being replaced.
     */
    private fun buildSessions(
        ordered: List<UsageEventSample>,
        nowMillis: Long,
    ): List<AppSession> {
        val sessions = mutableListOf<AppSession>()
        var openPackage: String? = null
        var openStart = 0L

        fun close(atMillis: Long) {
            val packageName = openPackage ?: return
            sessions += AppSession(packageName, openStart, atMillis.coerceAtLeast(openStart))
            openPackage = null
        }

        ordered.forEach { event ->
            when (event.type) {
                UsageEventType.FOREGROUND -> {
                    if (openPackage == event.packageName) return@forEach
                    close(event.timestampMillis)
                    openPackage = event.packageName
                    openStart = event.timestampMillis
                }

                UsageEventType.BACKGROUND -> {
                    if (openPackage == event.packageName) close(event.timestampMillis)
                }

                UsageEventType.SCREEN_OFF -> close(event.timestampMillis)
            }
        }

        openPackage?.let { packageName ->
            sessions += AppSession(packageName, openStart, null)
        }
        return sessions
    }

    private fun lastGapMillis(
        targetSessions: List<AppSession>,
        current: AppSession?,
    ): Long {
        val reference = current ?: return UsagePatternSnapshot.UNKNOWN_GAP
        val previous = targetSessions
            .filterNot(AppSession::isOpen)
            .lastOrNull { it.endMillis != null && it.endMillis <= reference.startMillis }
            ?: return UsagePatternSnapshot.UNKNOWN_GAP
        val previousEnd = previous.endMillis ?: return UsagePatternSnapshot.UNKNOWN_GAP
        return (reference.startMillis - previousEnd).coerceAtLeast(0L)
    }

    private fun medianMillis(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2L
        }
    }

    /**
     * Length of the current unbroken run of device use.
     *
     * Sessions separated by less than [idleGapMillis] belong to the same run. A screen-off event
     * always ends the run, and a run that has already finished reports zero.
     */
    private fun continuousActivityMillis(
        sessions: List<AppSession>,
        ordered: List<UsageEventSample>,
        nowMillis: Long,
        idleGapMillis: Long,
    ): Long {
        if (sessions.isEmpty()) return 0L

        val lastSession = sessions.last()
        val lastEnd = lastSession.endMillis ?: nowMillis
        if (nowMillis - lastEnd > idleGapMillis) return 0L

        val lastScreenOff = ordered.lastOrNull { it.type == UsageEventType.SCREEN_OFF }?.timestampMillis
        var runStart = lastSession.startMillis

        for (index in sessions.lastIndex downTo 1) {
            val earlier = sessions[index - 1]
            val earlierEnd = earlier.endMillis ?: continue
            if (sessions[index].startMillis - earlierEnd > idleGapMillis) break
            runStart = earlier.startMillis
        }

        if (lastScreenOff != null && lastScreenOff > runStart) {
            runStart = lastScreenOff
        }
        return (nowMillis - runStart).coerceAtLeast(0L)
    }
}
