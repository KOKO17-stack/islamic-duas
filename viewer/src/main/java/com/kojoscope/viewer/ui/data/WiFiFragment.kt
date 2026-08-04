package com.kojoscope.viewer.ui.data

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WiFiFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = WifiAdapter(emptyList())
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        recycler?.layoutManager = LinearLayoutManager(context)
        recycler?.adapter = adapter
        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        startPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        recycler = null
    }

    private fun startPolling() {
        pollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val current = DeviceRepo(requireContext()).getSelectedDeviceId()
                if (current.isNotEmpty()) deviceId = current
                if (deviceId.isNotEmpty()) loadWiFi()
                delay(30000)
            }
        }
    }

    private suspend fun loadWiFi() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchWiFi() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchWiFi(): List<WifiEntry> {
        val data = client.get("devices/$deviceId/wifi_scan") ?: return emptyList()
        val result = mutableListOf<WifiEntry>()
        val iter = data.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            if (k == "ts_ms" || k == "networks") continue
            try {
                val v = data.getJSONObject(k)
                val networksJson = v.optString("networks", "[]")
                // networks is a stringified list of network objects
                val networks = parseNetworks(networksJson)
                for (n in networks) {
                    result.add(WifiEntry(
                        bssid = n.optString("bssid", ""),
                        ssid = n.optString("ssid", ""),
                        tsMs = v.optLong("ts_ms", k.toLongOrNull() ?: 0L)
                    ))
                }
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.tsMs }
    }

    private fun parseNetworks(json: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                result.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {
            // Try parsing as a plain string list
        }
        return result
    }
}