package com.yberkayinci.mola

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yberkayinci.mola.ai.AiGatewayFactory
import com.yberkayinci.mola.ai.AiSettingsStore
import com.yberkayinci.mola.data.DailyReport
import com.yberkayinci.mola.data.DemoDataSeeder
import com.yberkayinci.mola.data.MolaRepository
import com.yberkayinci.mola.data.InterventionRecord
import com.yberkayinci.mola.data.UserChoice
import com.yberkayinci.mola.data.UserProfile
import com.yberkayinci.mola.monitor.UsageMonitorService
import com.yberkayinci.mola.permissions.InstalledAppLoader
import com.yberkayinci.mola.permissions.PermissionNavigator
import com.yberkayinci.mola.ui.AiSettingsScreen
import com.yberkayinci.mola.ui.DailyReportScreen
import com.yberkayinci.mola.ui.HomeScreen
import com.yberkayinci.mola.ui.InterventionScreen
import com.yberkayinci.mola.ui.OnboardingScreen
import com.yberkayinci.mola.usage.InterventionTrigger
import com.yberkayinci.mola.usage.UsageStatsReader
import java.time.LocalDate
import kotlinx.coroutines.launch

private enum class AppScreen {
    ONBOARDING,
    HOME,
    INTERVENTION,
    REPORT,
    AI_SETTINGS,
}

@Composable
fun MolaApp(
    repository: MolaRepository,
    aiSettings: AiSettingsStore,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val usageReader = remember(context) { UsageStatsReader(context) }
    var aiSettingsRevision by remember { mutableIntStateOf(0) }
    val hasOwnApiKey = remember(aiSettingsRevision) { aiSettings.hasApiKey }
    val aiGateway = remember(aiSettingsRevision) { AiGatewayFactory.create(aiSettings) }
    val installedApps = remember(context) { InstalledAppLoader.load(context) }

    var profile by remember { mutableStateOf(repository.loadProfile()) }
    var records by remember { mutableStateOf(repository.loadRecords()) }
    var currentUsageMinutes by remember { mutableIntStateOf(0) }
    var interventionUsageMinutes by remember { mutableIntStateOf(0) }
    var monitoringStarted by rememberSaveable {
        mutableStateOf(UsageMonitorService.isMonitoringEnabled(context))
    }
    var permissionRefreshNonce by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<DailyReport?>(null) }
    var reportLoading by remember { mutableStateOf(false) }
    var screen by rememberSaveable {
        mutableStateOf(if (profile == null) AppScreen.ONBOARDING else AppScreen.HOME)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionRefreshNonce++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshNonce++
                val monitoringEnabled = UsageMonitorService.isMonitoringEnabled(context)
                monitoringStarted = monitoringEnabled
                if (monitoringEnabled) {
                    UsageMonitorService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasUsageAccess = remember(context, permissionRefreshNonce) {
        PermissionNavigator.hasUsageAccess(context)
    }
    val canDrawOverlays = remember(context, permissionRefreshNonce) {
        PermissionNavigator.canDrawOverlays(context)
    }
    val hasNotificationPermission = remember(context, permissionRefreshNonce) {
        PermissionNavigator.hasNotificationPermission(context)
    }

    LaunchedEffect(profile, permissionRefreshNonce) {
        currentUsageMinutes = profile?.let {
            usageReader.todayUsageMinutes(it.targetPackage)
        } ?: 0
        records = repository.loadRecords()
    }

    LaunchedEffect(profile, screen, report) {
        if (profile == null && screen != AppScreen.ONBOARDING) {
            screen = AppScreen.ONBOARDING
        } else if (screen == AppScreen.REPORT && report == null) {
            screen = AppScreen.HOME
        }
    }

    BackHandler(
        enabled = screen == AppScreen.INTERVENTION ||
            screen == AppScreen.REPORT ||
            screen == AppScreen.AI_SETTINGS,
    ) {
        screen = AppScreen.HOME
    }

    when (screen) {
        AppScreen.ONBOARDING -> OnboardingScreen(
            installedApps = installedApps,
            hasUsageAccess = hasUsageAccess,
            canDrawOverlays = canDrawOverlays,
            aiGateway = aiGateway,
            onOpenUsagePermission = {
                PermissionNavigator.openUsageAccessSettings(context)
            },
            onOpenOverlayPermission = {
                PermissionNavigator.openOverlaySettings(context)
            },
            onComplete = { newProfile ->
                repository.saveProfile(newProfile)
                UsageMonitorService.resetCooldown(context)
                profile = newProfile
                report = null
                reportLoading = false
                currentUsageMinutes = usageReader.todayUsageMinutes(newProfile.targetPackage)
                screen = AppScreen.HOME
            },
        )

        AppScreen.HOME -> profile?.let { activeProfile ->
            HomeScreen(
                profile = activeProfile,
                usageMinutes = currentUsageMinutes,
                recordCount = records.size,
                monitoringStarted = monitoringStarted,
                hasUsageAccess = hasUsageAccess,
                canDrawOverlays = canDrawOverlays,
                hasNotificationPermission = hasNotificationPermission,
                reportLoading = reportLoading,
                onRefresh = {
                    permissionRefreshNonce++
                    currentUsageMinutes = usageReader.todayUsageMinutes(activeProfile.targetPackage)
                    records = repository.loadRecords()
                },
                onOpenUsagePermission = {
                    PermissionNavigator.openUsageAccessSettings(context)
                },
                onOpenOverlayPermission = {
                    PermissionNavigator.openOverlaySettings(context)
                },
                onRequestNotificationPermission = {
                    if (!hasNotificationPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onUpdateLimit = { limit ->
                    val updated = activeProfile.copy(dailyLimitMinutes = limit)
                    repository.saveProfile(updated)
                    UsageMonitorService.resetCooldown(context)
                    profile = updated
                    report = null
                },
                onStartMonitoring = {
                    UsageMonitorService.start(context)
                    monitoringStarted = true
                    if (!hasNotificationPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onStopMonitoring = {
                    UsageMonitorService.stop(context)
                    monitoringStarted = false
                },
                onOpenTargetApp = {
                    launchPackage(context, activeProfile.targetPackage)
                },
                onOpenIntervention = {
                    interventionUsageMinutes = maxOf(
                        currentUsageMinutes,
                        activeProfile.dailyLimitMinutes + 18,
                    )
                    screen = AppScreen.INTERVENTION
                },
                onOpenReport = {
                    if (!reportLoading) {
                        val allRecords = repository.loadRecords()
                        val today = LocalDate.now()
                        val todayRecords = allRecords.filter { it.occursOn(today) }
                        records = allRecords
                        reportLoading = true
                        scope.launch {
                            try {
                                report = aiGateway.generateDailyReport(
                                    profile = activeProfile,
                                    records = todayRecords,
                                    currentUsageMinutes = currentUsageMinutes,
                                )
                                screen = AppScreen.REPORT
                            } finally {
                                reportLoading = false
                            }
                        }
                    }
                },
                hasOwnApiKey = hasOwnApiKey,
                onOpenAiSettings = { screen = AppScreen.AI_SETTINGS },
                onLoadDemoData = {
                    val seeded = DemoDataSeeder.records()
                    repository.replaceRecords(seeded)
                    records = seeded
                    report = null
                    if (currentUsageMinutes == 0) {
                        currentUsageMinutes = activeProfile.dailyLimitMinutes + 34
                    }
                },
                onClearData = {
                    UsageMonitorService.stop(context)
                    UsageMonitorService.resetCooldown(context)
                    monitoringStarted = false
                    reportLoading = false
                    repository.clearAll()
                    profile = null
                    records = emptyList()
                    report = null
                    currentUsageMinutes = 0
                    screen = AppScreen.ONBOARDING
                },
            )
        }

        AppScreen.INTERVENTION -> profile?.let { activeProfile ->
            InterventionScreen(
                profile = activeProfile,
                usageMinutes = interventionUsageMinutes,
                aiGateway = aiGateway,
                recentRecords = records,
                onChoice = { input, card, choice ->
                    val record = InterventionRecord(
                        timestampEpochMillis = System.currentTimeMillis(),
                        usageMinutes = interventionUsageMinutes,
                        text = input.text,
                        choice = choice,
                        stateId = input.stateId,
                        stateLabel = input.stateLabel,
                        inputMethod = input.method,
                        aiQuestion = card.question,
                        aiAlternative = card.alternative,
                        aiReflection = card.reflection,
                        aiActivityTitle = card.activityTitle,
                        aiDurationMinutes = card.durationMinutes,
                        aiStrategy = card.strategy,
                        // The in-app card is the manual rehearsal of a threshold moment; pattern
                        // triggers only exist where real behaviour is observed, in the service.
                        trigger = InterventionTrigger.THRESHOLD.storageValue,
                    )
                    repository.appendRecord(record)
                    records = records + record
                    report = null
                    screen = AppScreen.HOME
                    if (choice == UserChoice.CONTINUE) {
                        launchPackage(context, activeProfile.targetPackage)
                    }
                },
                onBack = { screen = AppScreen.HOME },
            )
        }

        AppScreen.REPORT -> report?.let { activeReport ->
            DailyReportScreen(
                report = activeReport,
                onBack = { screen = AppScreen.HOME },
            )
        }

        AppScreen.AI_SETTINGS -> AiSettingsScreen(
            hasApiKey = hasOwnApiKey,
            onSaveApiKey = { key ->
                aiSettings.saveApiKey(key)
                aiSettingsRevision++
                report = null
            },
            onClearApiKey = {
                aiSettings.clearApiKey()
                aiSettingsRevision++
                report = null
            },
            onBack = { screen = AppScreen.HOME },
        )
    }
}

private fun launchPackage(context: Context, packageName: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (launchIntent != null) {
        runCatching { context.startActivity(launchIntent) }
    }
}
