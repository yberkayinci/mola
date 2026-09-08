package com.yberkayinci.mola.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

class UsageStatsReader(context: Context) {
    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)
    private val appOpsManager = appContext.getSystemService(AppOpsManager::class.java)

    fun hasUsageAccess(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun todayUsageMinutes(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Int {
        if (!hasUsageAccess() || packageName.isBlank()) return 0
        val startOfDayMillis = LocalDate.now(zoneId)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val stats = usageStatsManager.queryAndAggregateUsageStats(startOfDayMillis, nowMillis)
        val foregroundMillis = stats[packageName]?.totalTimeInForeground ?: 0L
        return (foregroundMillis / 60_000L).toInt().coerceAtLeast(0)
    }

    fun currentForegroundPackage(
        nowMillis: Long = System.currentTimeMillis(),
        lookbackMillis: Long = 5L * 60L * 1_000L,
    ): String? {
        if (!hasUsageAccess()) return null
        val events = usageStatsManager.queryEvents(nowMillis - lookbackMillis, nowMillis)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = Long.MIN_VALUE

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (isForegroundEvent && event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }

    /**
     * Maps platform events into the plain samples the analyzer understands.
     *
     * This is the same `queryEvents` call the foreground check already makes and the same permission
     * the user already granted; the app was simply discarding everything except the last event.
     */
    fun recentEvents(
        nowMillis: Long = System.currentTimeMillis(),
        windowMillis: Long = DEFAULT_PATTERN_WINDOW_MILLIS,
    ): List<UsageEventSample> {
        if (!hasUsageAccess()) return emptyList()
        val events = usageStatsManager.queryEvents(nowMillis - windowMillis, nowMillis)
        val event = UsageEvents.Event()
        val samples = mutableListOf<UsageEventSample>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = sampleTypeOf(event.eventType) ?: continue
            samples += UsageEventSample(
                packageName = event.packageName.orEmpty(),
                timestampMillis = event.timeStamp,
                type = type,
            )
        }
        return samples
    }

    fun patternSnapshot(
        targetPackage: String,
        nowMillis: Long = System.currentTimeMillis(),
        windowMillis: Long = DEFAULT_PATTERN_WINDOW_MILLIS,
    ): UsagePatternSnapshot = UsageSessionAnalyzer.analyze(
        events = recentEvents(nowMillis, windowMillis),
        targetPackage = targetPackage,
        nowMillis = nowMillis,
    )

    private fun sampleTypeOf(eventType: Int): UsageEventType? = when {
        eventType == foregroundEventType -> UsageEventType.FOREGROUND
        eventType == backgroundEventType -> UsageEventType.BACKGROUND
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (
                eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                    eventType == UsageEvents.Event.KEYGUARD_SHOWN
                ) -> UsageEventType.SCREEN_OFF

        else -> null
    }

    private val foregroundEventType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    private val backgroundEventType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_BACKGROUND
        }

    /** Charging while lying still late at night is a different situation from charging at a desk. */
    fun isCharging(): Boolean = runCatching {
        appContext.getSystemService(BatteryManager::class.java)?.isCharging == true
    }.getOrDefault(false)

    companion object {
        /** Long enough for a personal session-length baseline, short enough to stay cheap. */
        const val DEFAULT_PATTERN_WINDOW_MILLIS: Long = 2L * 60L * 60L * 1_000L
    }
}
