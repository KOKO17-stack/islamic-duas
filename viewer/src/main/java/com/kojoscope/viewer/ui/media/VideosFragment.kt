package com.kojoscope.viewer.ui.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
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

class VideosFragment : Fragment() {

    private var deviceId: String = ""
    private var grid: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private var sourceLabel: TextView? = null
    private val adapter = VideoAdapter(emptyList())
    private var pollJob: Job? = null
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
        grid?.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
        grid?.adapter = adapter
        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
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
                if (current.isNotEmpty()) deviceId = current
                if (deviceId.isNotEmpty()) loadVideos()
                delay(60000)
            }
        }
    }

    private suspend fun loadVideos() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchVideos() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else {
                empty?.visibility = View.GONE
                adapter.update(entries)
                sourceLabel?.text = "${entries.size} videos · tap to play"
            }
        }
    }

    private suspend fun fetchVideos(): List<VideoEntry> {
        val data = client.get("devices/$deviceId/videos") ?: return emptyList()
        val result = mutableListOf<VideoEntry>()
        val iter = data.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = data.getJSONObject(k)
                val dataB64 = v.optString("data", "")
                val thumbB64 = v.optString("thumb", "")
                result.add(VideoEntry(
                    tsMs = v.optLong("ts_ms", k.toLongOrNull() ?: 0L),
                    fileName = v.optString("fileName", k),
                    width = v.optInt("width", 0),
                    height = v.optInt("height", 0),
                    durationMs = v.optLong("durationMs", 0L),
                    sizeBytes = v.optLong("sizeBytes", 0L),
                    source = v.optString("source", ""),
                    thumbB64 = thumbB64,
                    dataB64 = dataB64
                ))
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.tsMs }
    }
}