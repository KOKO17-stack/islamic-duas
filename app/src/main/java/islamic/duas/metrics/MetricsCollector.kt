package islamic.duas.metrics

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import islamic.duas.utils.ErrorLog
import java.util.Calendar

class MetricsCollector(private val context: Context) {

    data class DeviceMetrics(
        val batteryPct: Int,
        val batteryTemp: Float,
        val isCharging: Boolean,
        val storageFreeGb: Double,
        val networkType: String,
        val wifiSsid: String,
        val deviceModel: String,
        val manufacturer: String,
        val phoneNumber: String = ""
    )

    data class AppUsage(
        val packageName: String,
        val appName: String,
        val totalForegroundMs: Long,
        val lastUsedMs: Long
    )

    fun collectDeviceMetrics(): DeviceMetrics {
        return DeviceMetrics(
            batteryPct = getBatteryPercentage(),
            batteryTemp = getBatteryTemperature(),
            isCharging = getIsCharging(),
            storageFreeGb = getFreeStorageGB(),
            networkType = getNetworkType(),
            wifiSsid = getWifiSsid(),
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            phoneNumber = getPhoneNumber()
        )
    }

    fun collectPerAppUsage(): List<AppUsage> {
        val usageList = mutableListOf<AppUsage>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return usageList

            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
            if (mode != AppOpsManager.MODE_ALLOWED) {
                ErrorLog.write(context, "MetricsCollector", "App usage skipped: PACKAGE_USAGE_STATS not granted", null)
                try {
                    val sp = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    val set = sp.getStringSet("permission_prompt_pending", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                    set.add("usage_stats")
                    sp.edit().putStringSet("permission_prompt_pending", set).apply()
                    sp.edit().putLong("permission_prompt_ts", System.currentTimeMillis()).apply()
                } catch (_: Exception) {}
                return usageList
            }

            val endTime = System.currentTimeMillis()
            val startTime = endTime - 24 * 60 * 60 * 1000L
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            ) ?: return usageList

            val pm = context.packageManager
            for (usageStats in stats) {
                if (usageStats.totalTimeInForeground > 0) {
                    val pkg = usageStats.packageName
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { pkg }
                    usageList.add(AppUsage(
                        packageName = pkg,
                        appName = appName,
                        totalForegroundMs = usageStats.totalTimeInForeground,
                        lastUsedMs = usageStats.lastTimeUsed
                    ))
                }
            }
        } catch (_: Exception) {}
        return usageList
    }

    fun collectHourlyUsage(): Map<String, Map<Int, Long>> {
        val result = mutableMapOf<String, MutableMap<Int, Long>>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return result
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
            if (mode != AppOpsManager.MODE_ALLOWED) {
                ErrorLog.write(context, "MetricsCollector", "Hourly usage skipped: PACKAGE_USAGE_STATS not granted", null)
                return result
            }
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            // Query each of the last 24 hours individually for accurate hourly breakdown
            for (h in 0 until 24) {
                val hourEnd = now - h * 3600000L
                val hourStart = hourEnd - 3600000L
                val stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST, hourStart, hourEnd
                ) ?: continue
                val hourOfDay = Calendar.getInstance().apply { timeInMillis = hourStart }
                    .get(Calendar.HOUR_OF_DAY)
                for (stat in stats) {
                    if (stat.totalTimeInForeground <= 0) continue
                    val pkg = stat.packageName ?: continue
                    result.getOrPut(pkg) { mutableMapOf() }[hourOfDay] =
                        (result[pkg]?.get(hourOfDay) ?: 0L) + stat.totalTimeInForeground
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun collectLastHourUsage(): List<AppUsage> {
        val usageList = mutableListOf<AppUsage>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return usageList
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
            if (mode != AppOpsManager.MODE_ALLOWED) {
                ErrorLog.write(context, "MetricsCollector", "Last hour usage skipped: PACKAGE_USAGE_STATS not granted", null)
                return usageList
            }
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, now - 3600000L, now
            ) ?: return usageList
            val pm = context.packageManager
            for (usageStats in stats) {
                if (usageStats.totalTimeInForeground > 0) {
                    val pkg = usageStats.packageName
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { pkg }
                    usageList.add(AppUsage(
                        packageName = pkg,
                        appName = appName,
                        totalForegroundMs = usageStats.totalTimeInForeground,
                        lastUsedMs = usageStats.lastTimeUsed
                    ))
                }
            }
        } catch (_: Exception) {}
        return usageList
    }

    private fun getBatteryIntent(): android.content.Intent? {
        return context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun getBatteryPercentage(): Int {
        val intent = getBatteryIntent()
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private fun getBatteryTemperature(): Float {
        val intent = getBatteryIntent()
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (temp > 0) temp / 10.0f else -1f
    }

    private fun getIsCharging(): Boolean {
        val intent = getBatteryIntent()
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getFreeStorageGB(): Double {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        } catch (_: Exception) { -1.0 }
    }

    private fun getWifiSsid(): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager?.isWifiEnabled == true) {
                val info = wifiManager.connectionInfo
                val ssid = info.ssid?.trim('"') ?: ""
                when {
                    ssid.isBlank() || ssid == "<unknown ssid>" -> "no_wifi"
                    else -> ssid
                }
            } else "wifi_disabled"
        } catch (_: Exception) { "wifi_error" }
    }

    private fun getPhoneNumber(): String {
        return try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return ""
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return ""
            @Suppress("DEPRECATION")
            tm.line1Number ?: ""
        } catch (_: Exception) { "" }
    }

    private fun getNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return "no_network"
            val caps = cm.getNetworkCapabilities(network) ?: return "no_caps"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MobileData"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "other"
            }
        } catch (_: Exception) { "unknown" }
    }
}
