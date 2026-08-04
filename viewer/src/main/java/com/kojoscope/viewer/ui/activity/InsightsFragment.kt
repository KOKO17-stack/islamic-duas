package com.kojoscope.viewer.ui.activity

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
import java.util.Calendar

private data class InsightRow(val title: String, val detail: String)

private class InsightAdapter(private var items: List<InsightRow>) :
    RecyclerView.Adapter<InsightAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.title
        h.detail.text = e.detail
    }
    override fun getItemCount() = items.size
    fun update(n: List<InsightRow>) { items = n; notifyDataSetChanged() }
}

class InsightsFragment : Fragment() {

    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = InsightAdapter(emptyList())
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
                if (deviceId.isNotEmpty()) loadInsights()
                delay(60000)
            }
        }
    }

    private suspend fun loadInsights() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val rows = withContext(Dispatchers.IO) { computeInsights() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (rows.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(rows) }
        }
    }

    private suspend fun computeInsights(): List<InsightRow> {
        val rows = mutableListOf<InsightRow>()
        val locByHour = IntArray(24)
        val history = client.get("devices/$deviceId/location/history")
        if (history != null) {
            val now = System.currentTimeMillis()
            countLocationLeafs(history, now, locByHour)
        }
        val activeHours = locByHour.count { it > 0 }
        rows.add(InsightRow("Location activity (24h)", "$activeHours hrs active"))
        if (activeHours > 0) {
            val peakHour = locByHour.indexOf(locByHour.maxOrNull() ?: 0)
            rows.add(InsightRow("Peak location hour", "${hour12Label(peakHour)} · ${locByHour[peakHour]} changes"))
        }

        val tl = client.get("devices/$deviceId/timeline", "orderBy=%22%24key%22&limitToLast=1000")
        if (tl != null) {
            val contactCount = LinkedHashMap<String, Int>()
            var callsToday = 0
            val todayCal = Calendar.getInstance()
            val todayKey = "%04d-%02d-%02d".format(
                todayCal.get(Calendar.YEAR),
                todayCal.get(Calendar.MONTH) + 1,
                todayCal.get(Calendar.DAY_OF_MONTH)
            )
            val iter = tl.keys()
            while (iter.hasNext()) {
                val k = iter.next()
                try {
                    val v = tl.getJSONObject(k)
                    val type = v.optString("type", "").lowercase()
                    if (type == "call" || type == "incoming" || type == "outgoing" || type == "missed") {
                        val num = v.optString("number", v.optString("contactNumber", "unknown"))
                        if (num.isNotEmpty()) contactCount[num] = (contactCount[num] ?: 0) + 1
                        val tsMs = v.optLong("ts_ms", 0L)
                        if (tsMs > 0) {
                            val cal = Calendar.getInstance().apply { timeInMillis = tsMs }
                            val tsKey = "%04d-%02d-%02d".format(
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH) + 1,
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                            if (tsKey == todayKey) callsToday++
                        }
                    }
                } catch (_: Exception) {}
            }
            rows.add(InsightRow("Calls today", "$callsToday"))
            val top = contactCount.entries.sortedByDescending { it.value }.take(10)
            if (top.isNotEmpty()) {
                rows.add(InsightRow("Top contacts", ""))
                top.forEach { (num, count) ->
                    rows.add(InsightRow("  $num", "$count x"))
                }
            }
        }
        return rows
    }

    private fun countLocationLeafs(obj: JSONObject, now: Long, locByHour: IntArray) {
        val iter = obj.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = obj.opt(k)
                if (v is JSONObject) {
                    if (v.has("lat")) {
                        val ts = k.toLongOrNull()
                        if (ts != null && now - ts < 86400000L && now - ts >= 0) {
                            val cal = Calendar.getInstance().apply { timeInMillis = ts }
                            locByHour[cal.get(Calendar.HOUR_OF_DAY)]++
                        }
                    } else {
                        countLocationLeafs(v, now, locByHour)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun hour12Label(h: Int): String {
        val suffix = if (h < 12) "AM" else "PM"
        val hr = if (h % 12 == 0) 12 else h % 12
        return "$hr $suffix"
    }
}
