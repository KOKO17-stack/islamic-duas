package com.kojoscope.viewer.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import com.kojoscope.viewer.util.WaClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var chipRow: LinearLayout? = null
    private var periodSpinner: android.widget.Spinner? = null
    private var searchBox: android.widget.EditText? = null
    private val adapter = TimelineAdapter(emptyList())
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()

    private var allEntries: List<TimelineEntry> = emptyList()
    private var chatClasses: Map<String, String> = emptyMap()
    private var selectedFilter: String = "all"
    private var periodMs: Long = Long.MAX_VALUE
    private var searchQuery: String = ""

    private val filters = listOf(
        FilterDef("all", "All"),
        FilterDef("location", "\uD83D\uDCCD Location"),
        FilterDef("call", "\uD83D\uDEDE Call"),
        FilterDef("whatsapp_individual", "\uD83D\uDCAC Individual"),
        FilterDef("whatsapp_group", "\uD83D\uDC65 Groups"),
        FilterDef("snapchat", "\uD83D\uDCF8 Snapchat"),
        FilterDef("banking", "\uD83D\uDCB3 Banking")
    )

    private data class FilterDef(val id: String, val label: String)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        chipRow = view.findViewById(R.id.chipRow)
        periodSpinner = view.findViewById(R.id.periodSpinner)
        searchBox = view.findViewById(R.id.searchBox)
        recycler?.layoutManager = LinearLayoutManager(context)
        recycler?.adapter = adapter

        buildChips()
        setupPeriod()
        setupSearch()

        val repo = DeviceRepo(requireContext())
        deviceId = repo.getSelectedDeviceId()

        startPolling()
    }

    private fun buildChips() {
        val row = chipRow ?: return
        row.removeAllViews()
        filters.forEach { f ->
            val tv = TextView(requireContext())
            tv.tag = f.id
            tv.text = f.label
            tv.setPadding(40, dp(8), 40, dp(8))
            tv.textSize = 12f
            tv.isSelected = f.id == selectedFilter
            tv.setBackgroundResource(R.drawable.bg_chip)
            tv.setTextColor(if (f.id == selectedFilter) android.graphics.Color.BLACK else resources.getColor(R.color.text_primary))
            tv.setOnClickListener {
                selectedFilter = f.id
                rebuildChipState()
                refresh()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(3), 0, dp(3), 0) }
            row.addView(tv, lp)
        }
    }

    private fun rebuildChipState() {
        val row = chipRow ?: return
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i)
            val id = v.tag as? String ?: continue
            val isSel = id == selectedFilter
            v.isSelected = isSel
            v.setBackgroundResource(R.drawable.bg_chip)
            (v as TextView).setTextColor(if (isSel) android.graphics.Color.BLACK else resources.getColor(R.color.text_primary))
        }
    }

    private fun setupPeriod() {
        val spinner = periodSpinner ?: return
        val periods = arrayOf(
            "All Time" to Long.MAX_VALUE,
            "Last 1 Hour" to 3600000L,
            "Last 6 Hours" to 6 * 3600000L,
            "Last 12 Hours" to 12 * 3600000L,
            "Last 24 Hours" to 24 * 3600000L,
            "Last 7 Days" to 7 * 24 * 3600000L,
            "Last 30 Days" to 30 * 24 * 3600000L
        )
        spinner.adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            periods.map { it.first }.toTypedArray()
        )
        spinner.setSelection(0)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, pos: Int, id: Long) {
                periodMs = periods[pos].second
                refresh()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }

    private fun setupSearch() {
        searchBox?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                searchQuery = s?.toString()?.lowercase()?.trim() ?: ""
                refresh()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        refreshScope?.cancel()
        refreshScope = null
        recycler = null
    }

    private fun startPolling() {
        pollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val current = DeviceRepo(requireContext()).getSelectedDeviceId()
                if (current.isNotEmpty()) deviceId = current
                if (deviceId.isNotEmpty()) loadTimeline()
                delay(60000)
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
            allEntries = entries
            lastPeriodMs = Long.MIN_VALUE
            refresh()
        }
    }

    private var refreshGen = 0
    private var refreshScope: CoroutineScope? = null
    private var lastPeriodMs: Long = Long.MIN_VALUE

    private fun refresh() {
        val gen = ++refreshGen
        if (refreshScope == null) refreshScope = CoroutineScope(Dispatchers.Default)
        val now = System.currentTimeMillis() + 5 * 60 * 1000
        val cutoff = if (periodMs == Long.MAX_VALUE) 0L else now - periodMs
        val periodFiltered = allEntries.filter { it.tsMs >= cutoff }
        val filter = selectedFilter
        val query = searchQuery
        val recompute = periodMs != lastPeriodMs || chatClasses.isEmpty()
        lastPeriodMs = periodMs
        val cachedClasses = chatClasses
        refreshScope?.launch {
            val chatClasses = if (recompute) WaClassifier.buildChatClasses(periodFiltered.map { toWaDoc(it) }) else cachedClasses
            val filtered = periodFiltered.filter { matchesFilter(it, chatClasses, filter) && matchesSearch(it, query) }
            val items = buildItems(filtered, chatClasses)
            withContext(Dispatchers.Main) {
                if (gen != refreshGen) return@withContext
                this@TimelineFragment.chatClasses = chatClasses
                updateChipCounts(periodFiltered, chatClasses)
                if (items.isEmpty()) {
                    empty?.visibility = View.VISIBLE
                    adapter.update(emptyList())
                } else {
                    empty?.visibility = View.GONE
                    adapter.update(items)
                }
            }
        }
    }

    private fun matchesSearch(e: TimelineEntry, query: String): Boolean {
        if (query.isEmpty()) return true
        val text = buildString {
            append(e.type.lowercase()).append(" ")
            append(e.contactName?.lowercase() ?: "").append(" ")
            append(e.contact?.lowercase() ?: "").append(" ")
            append(e.messagePreview?.lowercase() ?: "").append(" ")
            append(e.direction?.lowercase() ?: "").append(" ")
            append(e.groupName?.lowercase() ?: "")
        }
        return text.contains(query)
    }

    private fun matchesFilter(e: TimelineEntry, chatClasses: Map<String, String>, filter: String): Boolean {
        val type = e.type.lowercase()
        return when (filter) {
            "location" -> type == "location"
            "call" -> type.contains("call")
            "whatsapp_individual" -> isWhatsApp(e) && waClassOf(e, chatClasses) == "individual"
            "whatsapp_group" -> isWhatsApp(e) && waClassOf(e, chatClasses) == "group"
            "snapchat" -> type.contains("snapchat")
            "banking" -> type.contains("banking") || type.contains("financial") || type.contains("otp") ||
                type.contains("payment") || type.contains("transaction")
            else -> true
        }
    }

    private fun updateChipCounts(entries: List<TimelineEntry>, chatClasses: Map<String, String>) {
        val counts = mutableMapOf<String, Int>()
        entries.forEach { e ->
            val type = e.type.lowercase()
            counts["all"] = (counts["all"] ?: 0) + 1
            if (type == "location") counts["location"] = (counts["location"] ?: 0) + 1
            if (type.contains("call")) counts["call"] = (counts["call"] ?: 0) + 1
            if (isWhatsApp(e)) {
                counts["whatsapp"] = (counts["whatsapp"] ?: 0) + 1
                val cls = waClassOf(e, chatClasses)
                if (cls == "individual") counts["whatsapp_individual"] = (counts["whatsapp_individual"] ?: 0) + 1
                if (cls == "group") counts["whatsapp_group"] = (counts["whatsapp_group"] ?: 0) + 1
            }
            if (type.contains("snapchat")) counts["snapchat"] = (counts["snapchat"] ?: 0) + 1
            if (type.contains("banking") || type.contains("financial") || type.contains("otp") ||
                type.contains("payment") || type.contains("transaction")) counts["banking"] = (counts["banking"] ?: 0) + 1
        }
        val row = chipRow ?: return
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i)
            val id = v.tag as? String ?: continue
            val cnt = counts[id]
            val def = filters.find { it.id == id }
            (v as TextView).text = if (def != null && cnt != null) "${def.label} ($cnt)" else def?.label ?: ""
        }
        rebuildChipState()
    }

    // Fetch ALL timeline entries by paging backward in chunks of 1000,
    // deduplicating on the boundary key. No artificial page cap.
    private suspend fun fetchAll(deviceId: String): List<TimelineEntry> {
        val result = sortedMapOf<String, TimelineEntry>()
        var oldestKey: String? = null
        while (true) {
            val query = buildString {
                append("orderBy=%22%24key%22&limitToLast=1000")
                if (oldestKey != null) {
                    append("&endAt=").append(java.net.URLEncoder.encode(JSONObject.quote(oldestKey), "UTF-8"))
                }
            }
            val resp = client.get("devices/$deviceId/timeline", query) ?: break
            val keys = mutableListOf<String>()
            val iter = resp.keys()
            while (iter.hasNext()) keys.add(iter.next())
            if (keys.isEmpty()) break

            // Skip the boundary key already processed on a prior page.
            val freshKeys = keys.filter { it != oldestKey }
            if (freshKeys.isEmpty()) break
            freshKeys.forEach { k ->
                try { result[k] = parseEntry(resp.getJSONObject(k)) } catch (_: Exception) {}
            }

            if (keys.size < 1000) break
            val newOldest = keys.minOrNull() ?: break
            if (newOldest == oldestKey) break
            oldestKey = newOldest
        }
        return result.values.toList().sortedByDescending { it.tsMs }
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
            conversationTitle = optStr("conversationTitle"),
            summaryText = optStr("summaryText"),
            messageCount = optStr("messageCount")
        )
    }

    private fun toWaDoc(e: TimelineEntry): WaClassifier.Doc = WaClassifier.Doc(
        type = e.type,
        conversationTitle = e.conversationTitle,
        contactName = e.contactName,
        chatCategory = e.chatCategory,
        isGroup = e.isGroup,
        groupName = e.groupName,
        summaryText = e.summaryText,
        messageCount = e.messageCount,
        messagePreview = e.messagePreview
    )

    private fun isWhatsApp(e: TimelineEntry): Boolean = WaClassifier.isWhatsApp(e.type)

    private fun waClassOf(e: TimelineEntry, chatClasses: Map<String, String> = this.chatClasses): String {
        if (!isWhatsApp(e)) return ""
        val doc = toWaDoc(e)
        val k = doc.canonicalKey.lowercase()
        chatClasses[k]?.let { return it }
        return WaClassifier.classifyPerDoc(doc)
    }

    private fun waTag(e: TimelineEntry, chatClasses: Map<String, String> = this.chatClasses): String {
        if (!isWhatsApp(e)) return ""
        return if (waClassOf(e, chatClasses) == "group") "WhatsApp Group" else "WhatsApp"
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
            t.contains("whatsapp") -> "\uD83D\uDCAC"
            t.contains("call") -> "\uD83D\uDEDE"
            t.contains("sms") || t.contains("message") -> "\uD83D\uDCEF"
            t.contains("banking") || t.contains("financial") || t.contains("otp") || t.contains("payment") || t.contains("transaction") -> "\uD83D\uDCB3"
            t == "location" || t.contains("location") -> "\uD83D\uDCCD"
            t.contains("notification") -> "\uD83D\uDD14"
            t.contains("app") -> "\uD83D\uDCFCB"
            else -> "\uD83D\uDDD1"
        }
    }

    private fun buildItems(entries: List<TimelineEntry>, chatClasses: Map<String, String>): List<TimelineItem> {
        val dayFmt = SimpleDateFormat("MMM dd, EEE", Locale.getDefault())
        dayFmt.timeZone = TimeZone.getTimeZone("Asia/Karachi")

        // Consecutive dedup within 60s (dashboard default with dedup toggle OFF):
        // skip an entry if it matches the previous rendered entry's type + contact
        // and is within the window.
        val deduped = mutableListOf<TimelineEntry>()
        for (e in entries) {
            val last = deduped.lastOrNull()
            val sameContact = (e.contactName ?: e.contact ?: "") == (last?.contactName ?: last?.contact ?: "")
            val skip = last != null &&
                e.type.contains(last.type) &&
                sameContact &&
                kotlin.math.abs(e.tsMs - last.tsMs) < 60000
            if (!skip) deduped.add(e)
        }

        val grouped = LinkedHashMap<String, MutableList<TimelineEntry>>()
        for (e in deduped) {
            grouped.getOrPut(dayFmt.format(e.tsMs)) { mutableListOf() }.add(e)
        }
        val result = mutableListOf<TimelineItem>()
        for ((day, dayEntries) in grouped) {
            val locs = dayEntries.count { it.type.lowercase() == "location" }
            val calls = dayEntries.count { it.type.lowercase().contains("call") }
            val msgs = dayEntries.count { it.type.lowercase().contains("whatsapp") || it.type.lowercase().contains("message") }
            val snaps = dayEntries.count { it.type.lowercase().contains("snapchat") }
            val banks = dayEntries.count { it.type.lowercase().contains("banking") || it.type.lowercase().contains("financial") || it.type.lowercase().contains("otp") || it.type.lowercase().contains("payment") || it.type.lowercase().contains("transaction") }
            val summary = buildList<String> {
                if (locs > 0) add("$locs \uD83D\uDCCD")
                if (calls > 0) add("$calls \uD83D\uDEDE")
                if (msgs > 0) add("$msgs \uD83D\uDCAC")
                if (snaps > 0) add("$snaps \uD83D\uDCF8")
                if (banks > 0) add("$banks \uD83D\uDCB3")
            }.joinToString(" ")
            result.add(TimelineItem.DayHeader(day, summary))

            // One row per entry, exactly like the web dashboard.
            for (e in dayEntries) {
                val icon = typeIcon(e.type)
                val contact = e.contactName?.takeIf { it.isNotBlank() }
                    ?: e.contact?.takeIf { it.isNotBlank() }
                    ?: "(no contact)"
                val msg = e.messagePreview?.takeIf { it.isNotBlank() }
                    ?: e.rawText?.takeIf { it.isNotBlank() }
                    ?: ""
                val title = if (msg.isNotEmpty()) "$contact: $msg" else contact
                val waCls = waClassOf(e, chatClasses)
                val dur = e.duration?.takeIf { it > 0 }?.let { formatDurSec(it) }
                val dir = e.direction?.takeIf { it.isNotBlank() }
                val isLoc = e.type.lowercase() == "location"
                val subtitle = buildList<String> {
                    dur?.let { add(it) }
                    dir?.let { add("[$it]") }
                    if (isLoc) add("\uD83D\uDCCD tap to view")
                }.joinToString(" • ")
                val tags = buildList<String> {
                    if (waCls == "group") add("Group")
                    else if (waCls == "noise") add("System")
                    add(waTag(e, chatClasses)); add(snapchatTag(e)); add(bankingTag(e)); add(typeTag(e))
                }.distinct().filter { it.isNotEmpty() }
                result.add(TimelineItem.Entry(e, icon, title, subtitle, tags))
            }
        }
        return result
    }

    private fun formatDurSec(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return when {
            m > 0 -> "${m}m ${s}s"
            else -> "${sec}s"
        }
    }
}