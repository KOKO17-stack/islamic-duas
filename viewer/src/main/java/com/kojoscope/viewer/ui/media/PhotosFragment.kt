package com.kojoscope.viewer.ui.media

import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import com.kojoscope.viewer.ui.player.MediaViewerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PhotosFragment : Fragment() {

    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private var countText: TextView? = null
    private val adapter = PhotoAdapter(emptyList(), "", { openPhoto(it) }, { confirmDelete(it) })
    private var pollJob: Job? = null
    private var loadedOnce = false
    private val client = RtdbClient.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_photos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.photoGrid)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        countText = view.findViewById(R.id.photoCount)
        val layout = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
        adapter.attachSpan(layout, 2)
        recycler?.layoutManager = layout
        recycler?.adapter = adapter

        view.findViewById<View>(R.id.photoRefresh).setOnClickListener { refreshNow() }
        view.findViewById<View>(R.id.photoDeleteAll).setOnClickListener { confirmDeleteAll() }

        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        adapter.deviceId = deviceId
        renderCached()

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
                if (current != deviceId && current.isNotEmpty()) {
                    deviceId = current
                    adapter.deviceId = deviceId
                    renderCached()
                }
                if (deviceId.isNotEmpty()) refresh()
                delay(30000)
            }
        }
    }

    private fun refreshNow() {
        CoroutineScope(Dispatchers.Main).launch { refresh() }
    }

    private suspend fun refresh() {
        val showLoader = !loadedOnce
        if (showLoader) {
            withContext(Dispatchers.Main) {
                progress?.visibility = View.VISIBLE
                empty?.visibility = View.GONE
            }
        }
        val cached = withContext(Dispatchers.IO) { fetchAndCache() }
        withContext(Dispatchers.Main) {
            loadedOnce = true
            progress?.visibility = View.GONE
            renderCached(cached)
        }
    }

    private fun renderCached(cached: List<JSONObject>? = null) {
        val list = cached ?: MediaCache.load(deviceId, MediaCache.PHOTOS)
        buildItems(list)
    }

    private fun buildItems(list: List<JSONObject>) {
        val photos = list.map(::metaToPhoto).sortedByDescending { it.ts }
        if (photos.isEmpty()) {
            adapter.update(emptyList())
            countText?.text = "0 photos"
            empty?.visibility = View.VISIBLE
            return
        }
        empty?.visibility = View.GONE
        val byDay = LinkedHashMap<String, MutableList<PhotoEntry>>()
        for (p in photos) {
            byDay.getOrPut(p.date) { mutableListOf() }.add(p)
        }
        val items = mutableListOf<PhotoListItem>()
        for ((date, dayList) in byDay.entries.sortedByDescending { it.key }) {
            val dateMillis = try {
                headerSdf.parse(date)?.time ?: 0L
            } catch (_: Exception) { 0L }
            items.add(PhotoListItem.DayHeader(date, dateMillis, dayList.size))
            items.addAll(dayList.map { PhotoListItem.Photo(it) })
        }
        adapter.update(items)
        countText?.text = "${photos.size} photo" + (if (photos.size == 1) "" else "s")
    }

    private fun metaToPhoto(m: JSONObject): PhotoEntry = PhotoEntry(
        ts = m.optLong("ts", 0L),
        fileName = m.optString("fileName", m.optString("key")),
        dataBase64 = "",
        width = m.optInt("width", 0),
        height = m.optInt("height", 0),
        compressedSize = m.optLong("compressedSize", 0L),
        dateTaken = m.optLong("dateTaken", 0L),
        md5 = m.optString("md5", ""),
        date = m.optString("date"),
        key = m.optString("key")
    )

    /**
     * Loads cache instantly, then downloads ONLY dates not cached yet (plus the
     * newest cached date to pick up fresh uploads). Within a re-checked date only
     * brand-new photo keys are downloaded. Already-cached photos are never
     * re-downloaded.
     */
    private suspend fun fetchAndCache(): List<JSONObject> {
        if (deviceId.isEmpty()) return MediaCache.load(deviceId, MediaCache.PHOTOS)
        val cached = MediaCache.load(deviceId, MediaCache.PHOTOS)
        val knownKeysByDate = HashMap<String, MutableSet<String>>()
        for (m in cached) {
            val d = m.optString("date")
            val k = m.optString("key")
            if (d.isNotEmpty() && k.isNotEmpty()) {
                knownKeysByDate.getOrPut(d) { mutableSetOf() }.add(k)
            }
        }
        val knownDates = knownKeysByDate.keys

        val datesShallow = client.get("devices/$deviceId/photos", "shallow=true") ?: return cached
        val allDates = mutableListOf<String>()
        val dateIter = datesShallow.keys()
        while (dateIter.hasNext()) {
            val d = dateIter.next()
            if (d != "_index" && d != "_meta") allDates.add(d)
        }
        val newDates = allDates.filter { it !in knownDates }

        val maxCachedDate = if (knownDates.isEmpty()) "" else knownDates.maxOrNull() ?: ""
        val toFetch = LinkedHashSet<String>()
        toFetch.addAll(newDates)
        if (maxCachedDate.isNotEmpty() && maxCachedDate in allDates) toFetch.add(maxCachedDate)

        for (date in toFetch) {
            if (date in knownDates) {
                // incremental: only fetch keys we don't already have
                val shallow = client.get("devices/$deviceId/photos/$date", "shallow=true") ?: continue
                val newKeys = buildList {
                    val it = shallow.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        if (k !in knownKeysByDate[date].orEmpty()) add(k)
                    }
                }
                for (tsKey in newKeys) {
                    try {
                        val v = client.get("devices/$deviceId/photos/$date/$tsKey") ?: continue
                        savePhotoEntry(date, tsKey, v)
                    } catch (_: Exception) {}
                }
            } else {
                val data = client.get("devices/$deviceId/photos/$date") ?: continue
                val keys = data.keys()
                while (keys.hasNext()) {
                    val tsKey = keys.next()
                    try {
                        savePhotoEntry(date, tsKey, data.getJSONObject(tsKey))
                    } catch (_: Exception) {}
                }
            }
        }
        return MediaCache.load(deviceId, MediaCache.PHOTOS)
    }

    private fun savePhotoEntry(date: String, tsKey: String, v: JSONObject) {
        val b64 = v.optString("data", "")
        if (b64.isEmpty()) return
        val bytes = try { Base64.decode(b64, Base64.DEFAULT) } catch (_: Exception) { null } ?: return
        val meta = JSONObject()
        meta.put("ts", v.optLong("timestamp", 0L))
        meta.put("fileName", v.optString("fileName", tsKey))
        meta.put("width", v.optInt("width", 0))
        meta.put("height", v.optInt("height", 0))
        meta.put("compressedSize", v.optLong("compressedSize", 0L))
        meta.put("dateTaken", v.optLong("dateTaken", 0L))
        meta.put("md5", v.optString("md5", ""))
        meta.put("date", date)
        MediaCache.saveItem(deviceId, MediaCache.PHOTOS, tsKey, meta, bytes)
    }

    private fun openPhoto(e: PhotoEntry) {
        val cached = MediaCache.load(deviceId, MediaCache.PHOTOS)
            .firstOrNull { it.optString("key") == e.key }
        val file = cached?.let { MediaCache.blobFile(deviceId, MediaCache.PHOTOS, it) }
        if (file != null && file.exists()) {
            launchViewer(file.path, e.fileName)
            return
        }
        // fallback: download from RTDB, cache, then open
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val path = if (e.date.isNotEmpty() && e.key.isNotEmpty()) {
                    "devices/$deviceId/photos/${e.date}/${e.key}"
                } else {
                    "devices/$deviceId/photos"
                }
                val data = client.get(path) ?: return@launch
                val b64 = data.optString("data", "")
                if (b64.isEmpty()) return@launch
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val meta = JSONObject()
                meta.put("ts", e.ts)
                meta.put("fileName", e.fileName)
                meta.put("width", e.width)
                meta.put("height", e.height)
                meta.put("compressedSize", e.compressedSize)
                meta.put("dateTaken", e.dateTaken)
                meta.put("md5", e.md5)
                meta.put("date", e.date)
                MediaCache.saveItem(deviceId, MediaCache.PHOTOS, e.key, meta, bytes)
                val cached = MediaCache.load(deviceId, MediaCache.PHOTOS)
                    .firstOrNull { it.optString("key") == e.key }
                val f = cached?.let { MediaCache.blobFile(deviceId, MediaCache.PHOTOS, it) }
                if (f != null && f.exists()) launchViewer(f.path, e.fileName)
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun launchViewer(filePath: String, name: String) {
        val intent = android.content.Intent(requireContext(), MediaViewerActivity::class.java)
        intent.putExtra("filePath", filePath)
        intent.putExtra("name", name)
        startActivity(intent)
    }

    private fun confirmDelete(e: PhotoEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete photo")
            .setMessage("Delete \"${e.fileName}\" from the device and the local cache?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val path = if (e.date.isNotEmpty() && e.key.isNotEmpty()) {
                        "devices/$deviceId/photos/${e.date}/${e.key}"
                    } else ""
                    if (path.isNotEmpty()) client.delete(path)
                    MediaCache.deleteItem(deviceId, MediaCache.PHOTOS, e.key)
                    withContext(Dispatchers.Main) { renderCached() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete all photos")
            .setMessage("Delete ALL photos of this device from RTDB and the local cache? This cannot be undone.")
            .setPositiveButton("Delete all") { _, _ ->
                lifecycleScope.launch {
                    client.delete("devices/$deviceId/photos")
                    MediaCache.deleteAll(deviceId, MediaCache.PHOTOS)
                    withContext(Dispatchers.Main) { renderCached() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private val headerSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Karachi")
        }
    }
}
