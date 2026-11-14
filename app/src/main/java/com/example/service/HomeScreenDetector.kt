package com.example.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

class HomeScreenDetector(private val context: Context) {

    private var cachedLauncherPackages: Set<String> = emptySet()
    private var lastLauncherCacheTime: Long = 0L
    private var lastKnownForegroundPackage: String? = null

    private val ignoredSystemPackages = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.inputmethod.latin"
    )

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isCurrentlyOnHomeScreen(): Boolean {
        if (!hasUsageStatsPermission()) {
            return false
        }
        val foregroundPkg = detectForegroundPackage() ?: lastKnownForegroundPackage ?: return false
        lastKnownForegroundPackage = foregroundPkg
        val launchers = getLauncherPackages()
        return launchers.contains(foregroundPkg)
    }

    private fun detectForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 180_000L

        var latestPkg: String? = null
        var latestTs = 0L

        runCatching {
            val events = usm.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (pkg in ignoredSystemPackages) continue
                val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (isForegroundEvent && event.timeStamp >= latestTs) {
                    latestTs = event.timeStamp
                    latestPkg = pkg
                }
            }
        }

        if (latestPkg != null) return latestPkg

        return runCatching {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                endTime - 300_000L,
                endTime
            )
            stats
                ?.filter { it.packageName !in ignoredSystemPackages }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
        }.getOrNull()
    }

    private fun getLauncherPackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (cachedLauncherPackages.isNotEmpty() && now - lastLauncherCacheTime < 60_000L) {
            return cachedLauncherPackages
        }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val packages = mutableSetOf<String>()

        runCatching {
            val defaultFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(defaultFlags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, defaultFlags)
            }.forEach { info ->
                info.activityInfo?.packageName?.let { packages.add(it) }
            }

            val resolvedDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolvedDefault?.activityInfo?.packageName?.let { packages.add(it) }
        }
        cachedLauncherPackages = packages
        lastLauncherCacheTime = now
        return packages
    }
}
