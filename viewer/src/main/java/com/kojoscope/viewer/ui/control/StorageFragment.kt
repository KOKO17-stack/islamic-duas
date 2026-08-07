package com.kojoscope.viewer.ui.control

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

data class StorageCat(
    val icon: String,
    val name: String,
    val count: Int,
    val bytes: Long
)

class StorageFragment : Fragment() {
    private var deviceId: String = ""
    private var header: TextView? = null
    private var categoryList: LinearLayout? = null
    private var largestList: LinearLayout? = null
    private var storageRefresh: TextView? = null
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()

    private var btnDeletePhotos: TextView? = null
    private var btnDeleteVideos: TextView? = null
    private var btnDeleteVoiceNotes: TextView? = null
    private var btnDeleteRecordings: TextView? = null
    private var btnClearRecStatus: TextView? = null
    private var btnDeleteTimeline: TextView? = null
    private var btnDeleteApps: TextView? = null
    private var btnDeleteLocation: TextView? = null
    private var btnDeleteBrowser: TextView? = null
    private var btnDeleteWifi: TextView? = null
    private var btnDeleteEverything: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_storage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header = view.findViewById(R.id.storageHeader)
        categoryList = view.findViewById(R.id.categoryList)
        largestList = view.findViewById(R.id.largestList)
        storageRefresh = view.findViewById(R.id.storageRefresh)

        btnDeletePhotos = view.findViewById(R.id.btnDeletePhotos)
        btnDeleteVideos = view.findViewById(R.id.btnDeleteVideos)
        btnDeleteVoiceNotes = view.findViewById(R.id.btnDeleteVoiceNotes)
        btnDeleteRecordings = view.findViewById(R.id.btnDeleteRecordings)
        btnClearRecStatus = view.findViewById(R.id.btnClearRecStatus)
        btnDeleteTimeline = view.findViewById(R.id.btnDeleteTimeline)
        btnDeleteApps = view.findViewById(R.id.btnDeleteApps)
        btnDeleteLocation = view.findViewById(R.id.btnDeleteLocation)
        btnDeleteBrowser = view.findViewById(R.id.btnDeleteBrowser)
        btnDeleteWifi = view.findViewById(R.id.btnDeleteWifi)
        btnDeleteEverything = view.findViewById(R.id.btnDeleteEverything)

        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()

        storageRefresh?.setOnClickListener { loadStorage() }
        btnDeletePhotos?.setOnClickListener { confirmDelete("Delete All Photos", "devices/$deviceId/photos") }
        btnDeleteVideos?.setOnClickListener { confirmDelete("Delete Videos", "devices/$deviceId/videos") }
        btnDeleteVoiceNotes?.setOnClickListener { confirmDelete("Delete Voice Notes", "devices/$deviceId/voice_notes") }
        btnDeleteRecordings?.setOnClickListener { confirmDelete("Delete Remote Recordings", "devices/$deviceId/recordings") }
        btnClearRecStatus?.setOnClickListener { confirmDelete("Clear recordingStatus", "devices/$deviceId/recordingStatus") }
        btnDeleteTimeline?.setOnClickListener { confirmDelete("Delete Timeline", "devices/$deviceId/timeline") }
        btnDeleteApps?.setOnClickListener { confirmDelete("Delete App Usage", "devices/$deviceId/apps") }
        btnDeleteLocation?.setOnClickListener { confirmDelete("Delete Location History", "devices/$deviceId/location/history") }
        btnDeleteBrowser?.setOnClickListener { confirmDelete("Delete Browser History", "devices/$deviceId/browser_history") }
        btnDeleteWifi?.setOnClickListener { confirmDelete("Delete WiFi Scans", "devices/$deviceId/wifi_scan") }
        btnDeleteEverything?.setOnClickListener { confirmDeleteEverything() }

        startPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        categoryList = null
        largestList = null
        header = null
        storageRefresh = null
        btnDeletePhotos = null
        btnDeleteVideos = null
        btnDeleteVoiceNotes = null
        btnDeleteRecordings = null
        btnClearRecStatus = null
        btnDeleteTimeline = null
        btnDeleteApps = null
        btnDeleteLocation = null
        btnDeleteBrowser = null
        btnDeleteWifi = null
        btnDeleteEverything = null
    }

    private fun startPolling() {
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val current = DeviceRepo(requireContext()).getSelectedDeviceId()
                if (current.isNotEmpty()) deviceId = current
                if (deviceId.isNotEmpty()) loadStorage()
                delay(60000)
            }
        }
    }

    private fun loadStorage() {
        if (deviceId.isEmpty()) return
        header?.text = "Scanning storage for device…"
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetchStorage() }
            if (isAdded) render(result)
        }
    }

    private data class LargestRow(val name: String, val time: String, val detail: String)

    private data class StorageResult(
        val cats: List<StorageCat>,
        val largest: List<LargestRow>,
        val freeGb: String
    )

    private suspend fun fetchStorage(): StorageResult {
        val devicePath = "devices/$deviceId"
        val cats: List<StorageCat> = coroutineScope {
            val photos = async { estimatePhotos("$devicePath/photos") }
            val videos = async { estimateVideos("$devicePath/videos") }
            val voice = async { estimateNodeSize("$devicePath/voice_notes", 2) }
            val recordings = async { estimateNodeSize("$devicePath/recordings", 2) }
            val timeline = async { estimateNodeSize("$devicePath/timeline", 3) }
            val contacts = async { estimateNodeSize("$devicePath/contacts", 1) }
            val apps = async { estimateNodeSize("$devicePath/apps", 1) }
            val location = async { estimateLocation("$devicePath/location") }
            val browser = async { estimateNodeSize("$devicePath/browser_history", 1) }
            val wifi = async { estimateNodeSize("$devicePath/wifi_scan", 1) }

            val p = photos.await()
            val v = videos.await()
            val vo = voice.await()
            val rc = recordings.await()
            val tl = timeline.await()
            val ct = contacts.await()
            val ap = apps.await()
            val lc = location.await()
            val br = browser.await()
            val wf = wifi.await()

            listOf(
                StorageCat("📸", "Photos", p.first, p.second),
                StorageCat("🎬", "Videos", v.first, v.second),
                StorageCat("🎤", "Voice Notes", vo.first, vo.second),
                StorageCat("🎙️", "Remote Recordings", rc.first, rc.second),
                StorageCat("📞", "Timeline", tl.first, tl.second),
                StorageCat("👥", "Contacts", ct.first, ct.second),
                StorageCat("📊", "App Usage", ap.first, ap.second),
                StorageCat("📍", "Location", lc.first, lc.second),
                StorageCat("🌐", "Browser History", br.first, br.second),
                StorageCat("📡", "WiFi Scans", wf.first, wf.second)
            )
        }
        val largest = fetchLargest(devicePath)
        val metrics = client.get("devices/$deviceId/metrics/latest")
        val freeGb = metrics?.optDouble("storageFreeGb", -1.0)
            ?.takeIf { it > 0 }?.let { String.format(Locale.getDefault(), "%.1f", it) }.orEmpty()
        return StorageResult(cats, largest, freeGb)
    }

    private fun keysOf(obj: JSONObject, skipUnderscore: Boolean = true): List<String> {
        val names = obj.keys().asSequence()
            .filter { !skipUnderscore || !it.startsWith("_") }
            .toList()
        return names
    }

    private fun rawSize(obj: JSONObject): Long =
        obj.toString().toByteArray(Charsets.UTF_8).size.toLong()

    private suspend fun estimateNodeSize(path: String, sampleSize: Int): Pair<Int, Long> {
        val keys = client.get(path, "shallow=true") ?: return 0 to 0L
        val names = keysOf(keys)
        if (names.isEmpty()) return 0 to 0L
        var totalBytes = 0L
        var sampled = 0
        for (k in names.shuffled().take(sampleSize)) {
            val obj = client.get("$path/$k")
            if (obj != null) { totalBytes += rawSize(obj); sampled++ }
        }
        val avg = if (sampled > 0) totalBytes / sampled else 0L
        return names.size to names.size * avg
    }

    private suspend fun estimatePhotos(path: String): Pair<Int, Long> {
        val dates = client.get(path, "shallow=true") ?: return 0 to 0L
        val dateKeys = dates.keys().asSequence().filter { !it.startsWith("_") }.toList()
        if (dateKeys.isEmpty()) return 0 to 0L
        var count = 0
        val samples = mutableListOf<Pair<String, String>>()
        for (d in dateKeys.take(10)) {
            val sub = client.get("$path/$d", "shallow=true") ?: continue
            val ts = keysOf(sub)
            count += ts.size
            for (t in ts.take(2)) if (samples.size < 4) samples.add(d to t)
        }
        if (count == 0) return 0 to 0L
        var totalBytes = 0L
        var sampled = 0
        for ((d, t) in samples.shuffled().take(3)) {
            val obj = client.get("$path/$d/$t")
            if (obj != null) { totalBytes += rawSize(obj); sampled++ }
        }
        val avg = if (sampled > 0) totalBytes / sampled else 0L
        return count to count * avg
    }

    private suspend fun estimateVideos(path: String): Pair<Int, Long> {
        val idx = client.get("$path/_index", "shallow=true")
        if (idx != null) {
            val names = keysOf(idx)
            if (names.isEmpty()) return 0 to 0L
            var totalBytes = 0L
            var sampled = 0
            for (k in names.shuffled().take(3)) {
                val e = client.get("$path/_index/$k")
                if (e != null) {
                    val sb = e.optLong("sizeBytes", -1L)
                    totalBytes += if (sb > 0) sb else rawSize(e)
                    sampled++
                }
            }
            val avg = if (sampled > 0) totalBytes / sampled else 0L
            return names.size to names.size * avg
        }
        val raw = client.get(path, "shallow=true") ?: return 0 to 0L
        val names = keysOf(raw)
        if (names.isEmpty()) return 0 to 0L
        var totalBytes = 0L
        var sampled = 0
        for (k in names.shuffled().take(2)) {
            val e = client.get("$path/$k")
            if (e != null) {
                val sb = e.optLong("sizeBytes", -1L)
                totalBytes += if (sb > 0) sb else rawSize(e)
                sampled++
            }
        }
        val avg = if (sampled > 0) totalBytes / sampled else 0L
        return names.size to names.size * avg
    }

    private suspend fun estimateLocation(path: String): Pair<Int, Long> {
        val keys = client.get(path, "shallow=true") ?: return 0 to 0L
        val names = keysOf(keys)
        if (names.isEmpty()) return 0 to 0L

        var dayCount = 0
        val daySamples = mutableListOf<String>()
        if ("history" in names) {
            val history = client.get("$path/history", "shallow=true")
            if (history != null) {
                for (y in keysOf(history)) {
                    val months = client.get("$path/history/$y", "shallow=true") ?: continue
                    for (m in keysOf(months)) {
                        val days = client.get("$path/history/$y/$m", "shallow=true") ?: continue
                        val ds = keysOf(days)
                        dayCount += ds.size
                        for (day in ds.take(2)) if (daySamples.size < 4) daySamples.add("$path/history/$y/$m/$day")
                    }
                }
            }
        }
        val topLevelCount = names.size
        val count = if (dayCount > 0) dayCount else topLevelCount

        var totalBytes = 0L
        var sampled = 0
        for (k in names) {
            if (k == "history") continue
            val obj = client.get("$path/$k")
            if (obj != null) { totalBytes += rawSize(obj); sampled++ }
        }
        for (dayPath in daySamples.shuffled().take(2)) {
            val obj = client.get(dayPath)
            if (obj != null) { totalBytes += rawSize(obj); sampled++ }
        }
        val avg = if (sampled > 0) totalBytes / sampled else 0L
        return count to count * avg
    }

    private suspend fun fetchLargest(devicePath: String): List<LargestRow> {
        val rows = mutableListOf<LargestRow>()

        val vnKeys = client.get("$devicePath/voice_notes", "shallow=true")
        if (vnKeys != null) {
            val keys = keysOf(vnKeys)
            val chosen = keys.shuffled().take(5)
            val notes = mutableListOf<Triple<String, Long, String>>()
            for (k in chosen) {
                val e = client.get("$devicePath/voice_notes/$k") ?: continue
                val size = e.optLong("sizeBytes", 0L)
                val name = e.optString("fileName", "")
                    .ifEmpty { "voice note" }
                val durMs = e.optLong("durationMs", 0L)
                val dur = if (durMs > 0) "${durMs / 1000}s" else ""
                notes.add(Triple(name, size, dur))
            }
            notes.sortedByDescending { it.second }.take(5).forEach { (n, s, dur) ->
                rows.add(LargestRow("🎤 $n", formatBytes(s), if (dur.isNotEmpty()) "Voice note · $dur" else "Voice note"))
            }
        }

        val recKeys = client.get("$devicePath/recordings", "shallow=true")
        if (recKeys != null) {
            val keys = keysOf(recKeys)
            val chosen = keys.shuffled().take(5)
            val recs = mutableListOf<Triple<String, Long, Long>>()
            for (k in chosen) {
                val e = client.get("$devicePath/recordings/$k") ?: continue
                val size = e.optLong("sizeBytes", 0L)
                val dur = e.optLong("durationSec", 0L)
                recs.add(Triple(k, size, dur))
            }
            recs.sortedByDescending { it.second }.take(5).forEach { (id, s, dur) ->
                rows.add(LargestRow("🎙️ ${id.take(8)}…", formatBytes(s), "Recording · ${dur}s"))
            }
        }

        return rows
    }

    private fun render(result: StorageResult) {
        val cats = result.cats
        val largest = result.largest
        header?.text = if (result.freeGb.isNotEmpty())
            "Sizes estimated from sampled entries · free: ${result.freeGb} GB"
        else
            "Sizes estimated from sampled entries"

        categoryList?.removeAllViews()
        val maxBytes = cats.map { it.bytes }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val inflater = LayoutInflater.from(requireContext())
        for (cat in cats) {
            val row = inflater.inflate(R.layout.item_storage, categoryList, false)
            row.findViewById<TextView>(R.id.itemName).text = "${cat.icon} ${cat.name}"
            row.findViewById<TextView>(R.id.itemTime).text = if (cat.count == 0) "—" else formatBytes(cat.bytes)
            row.findViewById<TextView>(R.id.itemDetail).text =
                if (cat.count == 0) "No entries" else "${cat.count} ${if (cat.count == 1) "entry" else "entries"} · ${formatBytes(cat.bytes)}"
            val bar = row.findViewById<ProgressBar>(R.id.itemBar)
            bar.progress = if (cat.count == 0) 0 else ((cat.bytes.toDouble() / maxBytes * 100)).toInt().coerceIn(1, 100)
            categoryList?.addView(row)
        }

        largestList?.removeAllViews()
        if (largest.isEmpty()) {
            largestList?.addView(emptyNote("No media items found"))
        } else {
            for (row in largest) {
                val v = inflater.inflate(R.layout.item_list, largestList, false)
                v.findViewById<TextView>(R.id.itemName).text = row.name
                v.findViewById<TextView>(R.id.itemTime).text = row.time
                v.findViewById<TextView>(R.id.itemDetail).text = row.detail
                largestList?.addView(v)
            }
        }
    }

    private fun emptyNote(text: String): View {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setTextColor(requireContext().getColor(R.color.text_secondary))
        tv.textSize = 12f
        tv.setPadding(8, 8, 8, 8)
        return tv
    }

    private fun confirmDelete(title: String, path: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage("This will permanently delete this data from RTDB. Continue?")
            .setPositiveButton("Delete") { _, _ ->
                executeDelete(path, title)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteEverything() {
        val input = EditText(requireContext()).apply {
            hint = "Type DELETE to confirm"
            setSingleLine()
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ DELETE EVERYTHING")
            .setMessage("This will wipe ALL device data from RTDB. This cannot be undone. Type DELETE below to confirm.")
            .setView(layout)
            .setPositiveButton("DELETE") { _, _ ->
                if (input.text.toString() == "DELETE") {
                    executeDelete("devices/$deviceId", "DELETE EVERYTHING")
                } else {
                    Toast.makeText(context, "Confirmation failed — type DELETE", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeDelete(path: String, label: String) {
        if (deviceId.isEmpty()) return
        val ctx = requireContext()
        Toast.makeText(ctx, "Deleting…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                client.delete(path)
            }
            if (isAdded) {
                if (ok) {
                    Toast.makeText(ctx, "$label — deleted", Toast.LENGTH_SHORT).show()
                    loadStorage()
                } else {
                    Toast.makeText(ctx, "Failed to delete $label", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var u = 0
        while (value >= 1024 && u < units.size - 1) { value /= 1024; u++ }
        return if (u == 0) "${bytes} B" else String.format(Locale.getDefault(), "%.1f %s", value, units[u])
    }
}
