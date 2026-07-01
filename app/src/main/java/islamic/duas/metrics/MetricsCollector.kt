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
            if (mode != AppOpsManager.MODE_ALLOWED) return usageList

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
            if (mode != AppOpsManager.MODE_ALLOWED) return result
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 24 * 60 * 60 * 1000L
            val cal = Calendar.getInstance()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            ) ?: return result
            for (stat in stats) {
                if (stat.totalTimeInForeground <= 0) continue
                cal.timeInMillis = stat.firstTimeStamp
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val pkg = stat.packageName ?: continue
                result.getOrPut(pkg) { mutableMapOf() }[hour] =
                    (result[pkg]?.get(hour) ?: 0L) + stat.totalTimeInForeground
            }
        } catch (_: Exception) {}
        return result
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
                val ssid = info.ssid ?: "unknown"
                if (ssid == "<unknown ssid>") "not_connected" else ssid.trim('"')
            } else "wifi_disabled"
        } catch (_: Exception) { "unknown" }
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
