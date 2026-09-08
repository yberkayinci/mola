package com.yberkayinci.mola.monitor

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.yberkayinci.mola.MainActivity
import com.yberkayinci.mola.R
import com.yberkayinci.mola.ai.AiGatewayFactory
import com.yberkayinci.mola.ai.NeedInference
import com.yberkayinci.mola.ai.NeedInferenceCalibration
import com.yberkayinci.mola.data.JsonMolaRepository
import com.yberkayinci.mola.overlay.OverlayController
import com.yberkayinci.mola.usage.InterventionTrigger
import com.yberkayinci.mola.usage.InterventionTriggerPolicy
import com.yberkayinci.mola.usage.NeedSignals
import com.yberkayinci.mola.usage.UsageStatsReader
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class UsageMonitorService : Service() {
    private lateinit var repository: JsonMolaRepository
    private lateinit var usageStatsReader: UsageStatsReader
    private lateinit var overlayController: OverlayController
    private lateinit var executor: ScheduledExecutorService
    private var isDebuggable = false

    @Volatile
    private var lastDebugState: String? = null

    override fun onCreate() {
        super.onCreate()
        isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        repository = JsonMolaRepository(this)
        usageStatsReader = UsageStatsReader(this)
        overlayController = OverlayController(this, repository, AiGatewayFactory.create(this))
        executor = Executors.newSingleThreadScheduledExecutor()
        setMonitoringEnabled(true)
        promoteToForeground()
        debugState("service started; polling every ${POLL_INTERVAL_SECONDS}s")
        executor.scheduleAtFixedRate(
            ::pollSafely,
            INITIAL_DELAY_SECONDS,
            POLL_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::executor.isInitialized) executor.shutdownNow()
        if (::overlayController.isInitialized) overlayController.close()
        debugState("service destroyed")
        super.onDestroy()
    }

    private fun pollSafely() {
        runCatching { poll() }
            .onFailure { error ->
                Log.e(TAG, "Usage monitor poll failed", error)
            }
    }

    private fun poll() {
        val profile = repository.loadProfile()
            ?: return debugState("waiting: no profile")
        if (profile.targetPackage.isBlank()) {
            return debugState("waiting: target package missing")
        }
        if (!usageStatsReader.hasUsageAccess()) {
            return debugState("waiting: usage access missing")
        }
        if (!Settings.canDrawOverlays(this)) {
            return debugState("waiting: overlay permission missing")
        }
        if (!isScreenAvailableForIntervention()) {
            return debugState("waiting: screen locked or inactive")
        }
        if (overlayController.isShowing) {
            return debugState("waiting: overlay already showing")
        }

        val foregroundPackage = usageStatsReader.currentForegroundPackage()
            ?: return debugState("waiting: foreground package unknown")
        if (foregroundPackage != profile.targetPackage) {
            return debugState(
                "waiting: foreground=$foregroundPackage target=${profile.targetPackage}",
            )
        }

        val now = System.currentTimeMillis()
        val usageMinutes = usageStatsReader.todayUsageMinutes(profile.targetPackage)
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val snapshot = usageStatsReader.patternSnapshot(profile.targetPackage, now)

        val decision = InterventionTriggerPolicy.decide(
            snapshot = snapshot,
            usageMinutes = usageMinutes,
            dailyLimitMinutes = profile.dailyLimitMinutes,
            nowMillis = now,
            lastThresholdShownAtMillis = preferences
                .getLong(KEY_LAST_SHOWN_AT, NO_TIMESTAMP)
                .takeUnless { it == NO_TIMESTAMP },
            lastPatternShownAtMillis = preferences
                .getLong(KEY_LAST_PATTERN_AT, NO_TIMESTAMP)
                .takeUnless { it == NO_TIMESTAMP },
            patternInterventionsToday = patternInterventionsToday(preferences, now),
        )
        val trigger = decision.trigger
            ?: return debugState(
                "waiting: ${decision.reason}; usage=$usageMinutes/${profile.dailyLimitMinutes}m",
            )

        val hypothesis = NeedInferenceCalibration.filter(
            hypothesis = NeedInference.infer(
                NeedSignals.of(
                    pattern = snapshot,
                    trigger = trigger,
                    hourOfDay = Instant.ofEpochMilli(now)
                        .atZone(ZoneId.systemDefault())
                        .hour,
                    isCharging = usageStatsReader.isCharging(),
                    usageMinutes = usageMinutes,
                    dailyLimitMinutes = profile.dailyLimitMinutes,
                ),
            ),
            records = repository.loadRecords(),
        )

        debugState(
            "eligible: trigger=${trigger.storageValue} (${decision.reason}); " +
                "guess=${hypothesis?.stateId ?: "none"}; " +
                "usage=$usageMinutes/${profile.dailyLimitMinutes}m",
        )
        ContextCompat.getMainExecutor(this).execute {
            if (overlayController.show(profile, usageMinutes, trigger, hypothesis)) {
                recordShown(preferences, trigger, now)
                debugState("overlay shown; cooldown started")
            } else {
                debugState("overlay show failed")
            }
        }
    }

    /**
     * Pattern interventions are capped per local day, so the counter resets when the date changes
     * rather than on a rolling window the user cannot predict.
     */
    private fun patternInterventionsToday(
        preferences: SharedPreferences,
        nowMillis: Long,
    ): Int {
        val today = localDateKey(nowMillis)
        if (preferences.getString(KEY_PATTERN_COUNT_DATE, null) != today) return 0
        return preferences.getInt(KEY_PATTERN_COUNT, 0)
    }

    private fun recordShown(
        preferences: SharedPreferences,
        trigger: InterventionTrigger,
        nowMillis: Long,
    ) {
        val editor = preferences.edit()
        if (trigger.isPattern) {
            val today = localDateKey(nowMillis)
            val shownToday = patternInterventionsToday(preferences, nowMillis)
            editor
                .putLong(KEY_LAST_PATTERN_AT, nowMillis)
                .putString(KEY_PATTERN_COUNT_DATE, today)
                .putInt(KEY_PATTERN_COUNT, shownToday + 1)
        } else {
            editor.putLong(KEY_LAST_SHOWN_AT, nowMillis)
        }
        editor.apply()
    }

    private fun localDateKey(nowMillis: Long): String =
        Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun isScreenAvailableForIntervention(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        return powerManager.isInteractive && !keyguardManager.isKeyguardLocked
    }

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.monitor_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mola_notification)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setMonitoringEnabled(enabled: Boolean) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MONITORING_ENABLED, enabled)
            .apply()
    }

    private fun debugState(message: String) {
        if (!isDebuggable || lastDebugState == message) return
        lastDebugState = message
        Log.d(TAG, message)
    }

    companion object {
        private const val TAG = "MolaUsageMonitor"
        private const val CHANNEL_ID = "mola_usage_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val INITIAL_DELAY_SECONDS = 2L
        private const val POLL_INTERVAL_SECONDS = 60L
        private const val PREFERENCES_NAME = "mola_monitor"
        private const val KEY_LAST_SHOWN_AT = "last_overlay_at"
        private const val KEY_LAST_PATTERN_AT = "last_pattern_overlay_at"
        private const val KEY_PATTERN_COUNT_DATE = "pattern_overlay_date"
        private const val KEY_PATTERN_COUNT = "pattern_overlay_count"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val NO_TIMESTAMP = -1L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UsageMonitorService::class.java),
            )
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MONITORING_ENABLED, true)
                .apply()
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MONITORING_ENABLED, false)
                .apply()
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }

        fun isMonitoringEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MONITORING_ENABLED, false)

        fun resetCooldown(context: Context) {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_SHOWN_AT)
                .remove(KEY_LAST_PATTERN_AT)
                .remove(KEY_PATTERN_COUNT_DATE)
                .remove(KEY_PATTERN_COUNT)
                .apply()
        }
    }
}
