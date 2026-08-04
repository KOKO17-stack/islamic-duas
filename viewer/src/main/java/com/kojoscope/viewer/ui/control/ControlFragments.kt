package com.kojoscope.viewer.ui.control

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

data class HealthEntry(
    val date: String,
    val steps: Int,
    val goal: Int,
    val tsMs: Long
)

data class RecordingEntry(
    val id: String,
    val durationSec: String,
    val format: String,
    val status: String,
    val startedAtMs: Long,
    val sizeBytes: String
)

class HealthAdapter(private var items: List<HealthEntry>) :
    RecyclerView.Adapter<HealthAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.date
        h.detail.text = "${e.steps}/${e.goal} steps"
    }
    override fun getItemCount() = items.size
    fun update(n: List<HealthEntry>) { items = n; notifyDataSetChanged() }
}

class RecordingAdapter(private var items: List<RecordingEntry>) :
    RecyclerView.Adapter<RecordingAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
        val time: TextView = v.findViewById(R.id.itemTime)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = "Recording ${e.id.take(8)}…"
        h.time.text = if (e.startedAtMs > 0) java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(e.startedAtMs) else ""
        h.detail.text = "${e.durationSec}s · ${e.format} · ${e.status}" +
            (if (e.sizeBytes.isNotEmpty() && e.sizeBytes != "0") " · ${(e.sizeBytes.toLongOrNull() ?: 0) / 1024 / 1024}MB" else "")
    }
    override fun getItemCount() = items.size
    fun update(n: List<RecordingEntry>) { items = n; notifyDataSetChanged() }
}

class HealthFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = HealthAdapter(emptyList())
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
                if (deviceId.isNotEmpty()) loadHealth()
                delay(30000)
            }
        }
    }

    private suspend fun loadHealth() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchHealth() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchHealth(): List<HealthEntry> {
        val result = mutableListOf<HealthEntry>()
        // Fetch steps for last 7 days
        val cal = java.util.Calendar.getInstance()
        for (i in 0 until 7) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = sdf.format(cal.time)
            val steps = client.get("devices/$deviceId/steps/$date")
            if (steps != null && steps.has("steps")) {
                result.add(HealthEntry(
                    date = date,
                    steps = steps.optInt("steps", 0),
                    goal = steps.optInt("goal", 8000),
                    tsMs = steps.optLong("ts_ms", 0L)
                ))
            }
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return result.sortedByDescending { it.date }
    }
}

class RecordingFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = RecordingAdapter(emptyList())
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
                if (deviceId.isNotEmpty()) loadRecordings()
                delay(30000)
            }
        }
    }

    private suspend fun loadRecordings() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchRecordings() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchRecordings(): List<RecordingEntry> {
        val data = client.get("devices/$deviceId/recordings") ?: return emptyList()
        val result = mutableListOf<RecordingEntry>()
        val iter = data.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = data.getJSONObject(k)
                result.add(RecordingEntry(
                    id = k,
                    durationSec = v.optString("durationSec", "0"),
                    format = v.optString("format", "mp4"),
                    status = v.optString("status", "unknown"),
                    startedAtMs = v.optLong("startedAtMs", v.optLong("createdAt", 0L)),
                    sizeBytes = v.optString("sizeBytes", "0")
                ))
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.startedAtMs }
    }
}