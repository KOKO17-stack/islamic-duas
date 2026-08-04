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

data class BrowserEntry(
    val url: String,
    val title: String,
    val timestamp: Long
)

data class SMSEntry(
    val address: String,
    val body: String,
    val date: Long,
    val type: String
)

class BrowserAdapter(private var items: List<BrowserEntry>) :
    RecyclerView.Adapter<BrowserAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.title.ifEmpty { e.url }
        h.detail.text = e.url
    }
    override fun getItemCount() = items.size
    fun update(n: List<BrowserEntry>) { items = n; notifyDataSetChanged() }
}

class SMSAdapter(private var items: List<SMSEntry>) :
    RecyclerView.Adapter<SMSAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.address
        h.detail.text = "${e.type} · ${e.body.take(60)}"
    }
    override fun getItemCount() = items.size
    fun update(n: List<SMSEntry>) { items = n; notifyDataSetChanged() }
}

class BrowserFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = BrowserAdapter(emptyList())
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
                if (deviceId.isNotEmpty()) loadBrowser()
                delay(30000)
            }
        }
    }

    private suspend fun loadBrowser() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchBrowser() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchBrowser(): List<BrowserEntry> {
        val data = client.get("devices/$deviceId/browser_history") ?: return emptyList()
        val result = mutableListOf<BrowserEntry>()
        val iter = data.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = data.getJSONObject(k)
                result.add(BrowserEntry(
                    url = v.optString("url", ""),
                    title = v.optString("title", ""),
                    timestamp = v.optLong("timestamp", 0L)
                ))
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.timestamp }
    }
}

class SMSFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = SMSAdapter(emptyList())
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
                if (deviceId.isNotEmpty()) loadSMS()
                delay(30000)
            }
        }
    }

    private suspend fun loadSMS() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchSMS() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchSMS(): List<SMSEntry> {
        val data = client.get("devices/$deviceId/sms") ?: return emptyList()
        val result = mutableListOf<SMSEntry>()
        val iter = data.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = data.getJSONObject(k)
                result.add(SMSEntry(
                    address = v.optString("address", ""),
                    body = v.optString("body", ""),
                    date = v.optLong("date", 0L),
                    type = v.optString("type", "inbox")
                ))
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.date }
    }
}