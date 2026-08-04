package com.kojoscope.viewer.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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

data class CallEntry(
    val tsMs: Long,
    val contactName: String,
    val number: String,
    val direction: String,
    val duration: String,
    val location: String
)

class CallsAdapter(private var items: List<CallEntry>) :
    RecyclerView.Adapter<CallsAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(com.kojoscope.viewer.R.id.itemName)
        val detail: TextView = v.findViewById(com.kojoscope.viewer.R.id.itemDetail)
        val time: TextView = v.findViewById(com.kojoscope.viewer.R.id.itemTime)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(com.kojoscope.viewer.R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        val dirIcon = when (e.direction.lowercase()) {
            "incoming", "in" -> "\u2193"
            "outgoing", "out" -> "\u2191"
            "missed" -> "\u2715"
            else -> "\uD83D\uDEDE"
        }
        h.name.text = buildString {
            append(dirIcon).append(" ")
            if (e.contactName.isNotEmpty()) append(e.contactName)
            if (e.number.isNotEmpty()) append(" (").append(e.number).append(")")
        }
        h.time.text = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(e.tsMs)
        h.detail.text = buildString {
            append(e.direction.ifEmpty { "unknown" })
            if (e.duration.isNotEmpty()) append(" · ").append(e.duration)
            if (e.location.isNotEmpty()) append(" · ").append(e.location)
        }
    }
    override fun getItemCount() = items.size
    fun update(n: List<CallEntry>) { items = n; notifyDataSetChanged() }
}

class CallsFragment : Fragment() {

    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = CallsAdapter(emptyList())
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
                if (deviceId.isNotEmpty()) loadCalls()
                delay(30000)
            }
        }
    }

    private suspend fun loadCalls() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchCalls() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE; empty?.text = getString(R.string.no_call_data); adapter.update(emptyList()) }
            else { empty?.visibility = View.GONE; adapter.update(entries) }
        }
    }

    private suspend fun fetchCalls(): List<CallEntry> {
        val result = mutableListOf<CallEntry>()
        val timeline = client.get("devices/$deviceId/timeline") ?: return result
        val iter = timeline.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = timeline.getJSONObject(k)
                val type = v.optString("type", "").lowercase()
                if (!type.contains("call")) continue
                val ts = v.optString("ts_ms", k).toLongOrNull() ?: 0L
                result.add(CallEntry(
                    tsMs = ts,
                    contactName = v.optString("contactName", ""),
                    number = v.optString("contactNumber", ""),
                    direction = v.optString("direction", ""),
                    duration = v.optString("duration", "0"),
                    location = v.optString("location", "")
                ))
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.tsMs }
    }
}