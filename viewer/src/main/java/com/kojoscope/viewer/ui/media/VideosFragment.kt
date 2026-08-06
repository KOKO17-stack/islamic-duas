package com.kojoscope.viewer.ui.media

import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
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

class VideosFragment : Fragment() {

    private var deviceId: String = ""
    private var grid: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private var sourceLabel: TextView? = null
    private var countText: TextView? = null
    private val adapter = VideoAdapter(emptyList(), "", { confirmDelete(it) })
    private var pollJob: Job? = null
    private var loadedOnce = false
    private val client = RtdbClient.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_videos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        grid = view.findViewById(R.id.videoGrid)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        sourceLabel = view.findViewById(R.id.sourceLabel)
        countText = view.findViewById(R.id.videoCount)
        grid?.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
        grid?.adapter = adapter

        view.findViewById<View>(R.id.videoRefresh).setOnClickListener { refreshNow() }
        view.findViewById<View>(R.id.videoDeleteAll).setOnClickListener { confirmDeleteAll() }

        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        adapter.deviceId = deviceId
        renderCached()
        startPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        grid = null
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
                delay(60000)
            }
        }
    }

    private fun refreshNow() {
        CoroutineScope(Dispatchers.Main).launch { refresh() }
    }

    private suspend fun refresh() {
        val showLoader = !loadedOnce
        if (showLoader) {
            withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        }
        val entries = withContext(Dispatchers.IO) { fetchAndCache() }
        withContext(Dispatchers.Main) {
            loadedOnce = true
            progress?.visibility = View.GONE
            render(entries)
        }
    }

    private fun renderCached() {
        render(fetchFromCache())
    }

    private fun fetchFromCache(): List<VideoEntry> {
        val cached = MediaCache.load(deviceId, MediaCache.VIDEOS)
        val out = cached.map { m ->
            VideoEntry(
                tsMs = m.optLong("tsMs", 0L),
                fileName = m.optString("fileName", m.optString("key")),
                width = m.optInt("width", 0),
                height = m.optInt("height", 0),
                durationMs = m.optLong("durationMs", 0L),
                sizeBytes = m.optLong("sizeBytes", 0L),
                source = m.optString("source", ""),
                thumbB64 = "",
                dataB64 = "",
                key = m.optString("key")
            )
        }
        return out.sortedByDescending { it.tsMs }
    }

    private fun render(entries: List<VideoEntry>) {
        if (entries.isEmpty()) {
            adapter.update(emptyList())
            countText?.text = "0 videos"
            empty?.visibility = View.VISIBLE
            sourceLabel?.text = "No videos synced"
            return
        }
        empty?.visibility = View.GONE
        adapter.update(entries)
        countText?.text = "${entries.size} video" + (if (entries.size == 1) "" else "s")
        sourceLabel?.text = "${entries.size} videos · tap to play · long-press to delete"
    }

    /** Only downloads videos whose keys are not yet cached. */
    private suspend fun fetchAndCache(): List<VideoEntry> {
        if (deviceId.isEmpty()) return fetchFromCache()
        val known = MediaCache.cachedKeys(deviceId, MediaCache.VIDEOS)

        val candidateKeys = LinkedHashSet<String>()
        val shallow = client.get("devices/$deviceId/videos", "shallow=true")
        if (shallow != null) {
            val iter = shallow.keys()
            while (iter.hasNext()) {
                val k = iter.next()
                if (k != "_index") candidateKeys.add(k)
            }
        }
        // Some devices nest videos under an _index object
        if (shallow != null && shallow.has("_index")) {
            val idx = client.get("devices/$deviceId/videos/_index")
            if (idx != null) {
                val keys = idx.keys()
                while (keys.hasNext()) candidateKeys.add(keys.next())
            }
        }

        val newKeys = candidateKeys.filter { it !in known }
        for (k in newKeys) {
            val v = client.get("devices/$deviceId/videos/$k") ?: continue
            try {
                val thumbB64 = v.optString("thumb", "")
                val thumbBytes = if (thumbB64.isNotEmpty()) {
                    try { Base64.decode(thumbB64, Base64.DEFAULT) } catch (_: Exception) { null }
                } else null
                val meta = JSONObject()
                meta.put("tsMs", v.optLong("ts_ms", k.toLongOrNull() ?: 0L))
                meta.put("fileName", v.optString("fileName", k))
                meta.put("width", v.optInt("width", 0))
                meta.put("height", v.optInt("height", 0))
                meta.put("durationMs", v.optLong("durationMs", 0L))
                meta.put("sizeBytes", v.optLong("sizeBytes", 0L))
                meta.put("source", v.optString("source", ""))
                MediaCache.saveItem(deviceId, MediaCache.VIDEOS, k, meta, thumbBytes)
            } catch (_: Exception) {}
        }
        return fetchFromCache()
    }

    private fun confirmDelete(e: VideoEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete video")
            .setMessage("Delete \"${e.fileName}\" from the device and the local cache?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    if (e.key.isNotEmpty()) client.delete("devices/$deviceId/videos/${e.key}")
                    MediaCache.deleteItem(deviceId, MediaCache.VIDEOS, e.key)
                    withContext(Dispatchers.Main) { renderCached() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete all videos")
            .setMessage("Delete ALL videos of this device from RTDB and the local cache? This cannot be undone.")
            .setPositiveButton("Delete all") { _, _ ->
                lifecycleScope.launch {
                    client.delete("devices/$deviceId/videos")
                    MediaCache.deleteAll(deviceId, MediaCache.VIDEOS)
                    withContext(Dispatchers.Main) { renderCached() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
