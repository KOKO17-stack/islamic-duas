package islamic.duas.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class WifiScanner(private val context: Context) {

    companion object {
        private const val TAG = "WifiScanner"
    }

    fun scanAndCollect(lat: Double? = null, lng: Double? = null): JSONArray {
        val networks = JSONArray()
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return networks

            if (wifiManager.isWifiEnabled) {
                wifiManager.startScan()
                val scanResults = wifiManager.scanResults
                val ts = System.currentTimeMillis()

                for (result in scanResults) {
                    networks.put(JSONObject().apply {
                        put("ssid", result.SSID)
                        put("bssid", result.BSSID)
                        put("rssi", result.level)
                        put("frequency", result.frequency)
                        put("capabilities", result.capabilities)
                        put("channelWidth", result.channelWidth)
                        put("centerFreq0", result.centerFreq0)
                        put("timestamp", ts)
                        if (lat != null) put("lat", lat)
                        if (lng != null) put("lng", lng)
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scan error: ${e.message}", e)
        }
        return networks
    }
}
