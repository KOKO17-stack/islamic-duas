package com.kojoscope.viewer.net

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRepo(context: Context) {
    private val prefs = context.getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var devicesCache: List<DeviceEntry> = emptyList()
    private var lastFetchTime = 0L
    private val cacheDuration = 30000L

    data class DeviceInfo(
        val manufacturer: String? = null,
        val deviceModel: String? = null,
        val lastSyncMs: String? = null,
        val tsMs: Long? = null,
        val offlineQueueSize: String? = null
    ) {
        fun toDeviceEntry(deviceId: String): DeviceEntry {
            return DeviceEntry(
                id = deviceId,
                name = when {
                    !manufacturer.isNullOrEmpty() && !deviceModel.isNullOrEmpty() -> "$manufacturer $deviceModel"
                    !manufacturer.isNullOrEmpty() -> manufacturer
                    !deviceModel.isNullOrEmpty() -> deviceModel
                    else -> deviceId.take(8)
                },
                manufacturer = manufacturer,
                model = deviceModel,
                lastSeenTs = (lastSyncMs?.toLongOrNull() ?: 0L)
                    .coerceAtLeast(tsMs ?: 0L)
            )
        }
    }

    data class DeviceEntry(
        val id: String,
        val name: String,
        val manufacturer: String? = null,
        val model: String? = null,
        val lastSeenTs: Long = 0L
    ) {
        fun formatLastSeen(ts: String?): String {
            if (ts.isNullOrEmpty()) return "--"
            try {
                val now = System.currentTimeMillis()
                val tsLong = ts.toLongOrNull()
                if (tsLong == null) return "--"
                val diff = now - tsLong
                if (diff < 60000) return "Just now"
                if (diff < 3600000) return "${diff / 60000}m ago"
                if (diff < 86400000) return "${diff / 3600000}h ago"
                return "${diff / 86400000}d ago"
            } catch (_: Exception) {}
            return "--"
        }
    }

    suspend fun fetchDevices(): List<DeviceEntry> = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - lastFetchTime < cacheDuration && devicesCache.isNotEmpty()) {
            return@withContext devicesCache
        }

        val keysJson = RtdbClient.getInstance().get("devices", "shallow=true")?.toString() ?: "{}"
        if (keysJson.isNullOrEmpty()) {
            devicesCache = emptyList()
            lastFetchTime = System.currentTimeMillis()
            return@withContext emptyList()
        }

        try {
            val jsonMap = gson.fromJson(keysJson, Map::class.java) as Map<String, Any>
            val ids = jsonMap.keys.toList()
            val deviceEntries = mutableListOf<DeviceEntry>()
            for (id in ids) {
                val deviceEntry = fetchDeviceInfo(id)
                deviceEntries.add(deviceEntry)
            }
            devicesCache = deviceEntries
            lastFetchTime = System.currentTimeMillis()
            deviceEntries
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchDeviceInfo(id: String): DeviceEntry {
        val info = RtdbClient.getInstance().get("devices/$id/info")
        val metrics = RtdbClient.getInstance().get("devices/$id/metrics/latest")

        val infoJson = if (info?.has("manufacturer") == true) info else null
        val metricsJson = if (metrics?.has("manufacturer") == true) metrics else null

        val deviceInfo = when {
            infoJson != null -> DeviceInfo(
                manufacturer = infoJson.optString("manufacturer", null),
                deviceModel = infoJson.optString("deviceModel", null),
                lastSyncMs = infoJson.optString("lastSyncMs", null),
                offlineQueueSize = infoJson.optString("offlineQueueSize", null)
            )
            metricsJson != null -> DeviceInfo(
                manufacturer = metricsJson.optString("manufacturer", null),
                deviceModel = metricsJson.optString("deviceModel", null),
                lastSyncMs = metricsJson.optString("ts_ms", null),
                offlineQueueSize = metricsJson.optString("storageFreeGb", null)
            )
             else -> DeviceInfo()
        }

        return deviceInfo.toDeviceEntry(id)
    }

    suspend fun selectMostRecentIfNeeded(): String {
        val current = getSelectedDeviceId()
        if (current.isNotEmpty()) return current
        val devices = fetchDevices()
        if (devices.isEmpty()) return ""
        val newest = devices.maxByOrNull { it.lastSeenTs } ?: devices.first()
        setSelectedDeviceId(newest.id)
        return newest.id
    }


    fun getSelectedDeviceId(): String {
        return prefs.getString("selected_device", "") ?: ""
    }

    fun setSelectedDeviceId(deviceId: String) {
        prefs.edit().putString("selected_device", deviceId).apply()
    }
}