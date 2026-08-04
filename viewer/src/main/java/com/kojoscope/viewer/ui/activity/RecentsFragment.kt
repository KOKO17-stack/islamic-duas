package com.kojoscope.viewer.ui.activity

import android.graphics.Color
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

data class AppSnapshot(
    val appName: String,
    val packageName: String,
    val batteryPct: Int,
    val isCharging: Boolean,
    val networkType: String,
    val wifiSsid: String,
    val screenOn: Boolean,
    val phoneTsMs: Long,
    val dashboardTsMs: Long,
    val hb: Boolean,
    val idleTimeMs: Long?,
    val screenOffMs: Long?
)

fun formatHms(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h ${m}m ${s}s"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

private fun pkt12(ts: Long): String = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).apply {
    timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
}.format(ts)

sealed class RecentsRow {
    data class Snap(val s: AppSnapshot) : RecentsRow()
    data class ScreenOff(val wakeTs: Long, val offMs: Long) : RecentsRow()
}

class AppSnapshotAdapter(private var items: List<RecentsRow>) :
    RecyclerView.Adapter<AppSnapshotAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
        val time: TextView = v.findViewById(R.id.itemTime)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        when (val item = items[pos]) {
            is RecentsRow.Snap -> bindSnap(h, item.s)
            is RecentsRow.ScreenOff -> bindOff(h, item)
        }
    }
    private fun bindSnap(h: VH, e: AppSnapshot) {
        val rowTs = if (e.hb) e.dashboardTsMs else e.phoneTsMs
        h.name.text = e.appName
        h.time.text = pkt12(rowTs)
        val status = buildString {
            if (e.batteryPct > 0) append("\uD83D\uDD0B ${e.batteryPct}%")
            if (e.isCharging) append(" \u26A1")
            append(" · ${e.networkType}")
            if (e.wifiSsid.isNotEmpty()) append(" (${e.wifiSsid})")
            if (!e.screenOn) append(" \uD83D\uDC64")
            if (e.idleTimeMs != null && e.idleTimeMs > 0) append(" · \u23F3 ${formatHms(e.idleTimeMs)} idle")
            if (e.hb && e.screenOn) append(" · \uD83D\uDD11 alive")
        }
        h.detail.text = status
        h.detail.setTextColor(if (e.screenOn) Color.parseColor("#3fb950") else Color.parseColor("#8b949e"))
        h.itemView.setBackgroundResource(R.drawable.bg_card)
    }
    private fun bindOff(h: VH, e: RecentsRow.ScreenOff) {
        val offStartTs = e.wakeTs - e.offMs
        h.name.text = "\uD83D\uDCF4 Screen Off"
        h.name.setTextColor(Color.parseColor("#ffca28"))
        h.time.text = pkt12(e.wakeTs)
        h.detail.text = "off since ${pkt12(offStartTs)} · \u23F8 ${formatHms(e.offMs)}"
        h.detail.setTextColor(Color.parseColor("#8b949e"))
        h.itemView.setBackgroundResource(R.drawable.bg_card_alt)
    }
    override fun getItemCount() = items.size
    fun update(n: List<RecentsRow>) { items = n; notifyDataSetChanged() }
}

class RecentsFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = AppSnapshotAdapter(emptyList())
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
                if (deviceId.isNotEmpty()) loadRecents()
                delay(30000)
            }
        }
    }

    private suspend fun loadRecents() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchRecents() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchRecents(): List<RecentsRow> {
        val data = client.get("devices/$deviceId/appSnapshots") ?: return emptyList()
        val result = mutableListOf<AppSnapshot>()
        val iter = data.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = data.getJSONObject(k)
                result.add(AppSnapshot(
                    appName = v.optString("appName", ""),
                    packageName = v.optString("packageName", ""),
                    batteryPct = v.optString("batteryPct", "0").toIntOrNull() ?: 0,
                    isCharging = v.optString("isCharging", "false").toBoolean(),
                    networkType = v.optString("networkType", ""),
                    wifiSsid = v.optString("wifiSsid", ""),
                    screenOn = v.optString("screenOn", "false").toBoolean(),
                    phoneTsMs = v.optLong("phoneTsMs", 0L),
                    dashboardTsMs = v.optLong("dashboardTsMs", 0L),
                    hb = v.optString("hb", "false").toBoolean(),
                    idleTimeMs = (v.opt("idleTimeMs") as? Number)?.toLong(),
                    screenOffMs = (v.opt("screenOffMs") as? Number)?.toLong()
                ))
            } catch (_: Exception) {}
        }
        val sorted = result.sortedByDescending { if (it.hb) it.dashboardTsMs else it.phoneTsMs }
        val rows = mutableListOf<RecentsRow>()
        for (s in sorted) {
            rows.add(RecentsRow.Snap(s))
            val offMs = s.screenOffMs ?: 0
            if (offMs > 0) {
                val wakeTs = if (s.hb) s.dashboardTsMs else s.phoneTsMs
                rows.add(RecentsRow.ScreenOff(wakeTs, offMs))
            }
        }
        return rows
    }
}