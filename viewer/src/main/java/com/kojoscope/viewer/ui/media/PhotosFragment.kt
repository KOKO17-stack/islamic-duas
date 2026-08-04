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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PhotosFragment : Fragment() {

    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = PhotoAdapter(emptyList())
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_photos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.photoGrid)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        recycler?.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
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
                if (deviceId.isNotEmpty()) loadPhotos()
                delay(30000)
            }
        }
    }

    private suspend fun loadPhotos() {
        withContext(Dispatchers.Main) {
            progress?.visibility = View.VISIBLE
            empty?.visibility = View.GONE
        }
        val entries = withContext(Dispatchers.IO) { fetchRecentPhotos() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) {
                empty?.visibility = View.VISIBLE
            } else {
                adapter.update(entries)
            }
        }
    }

    private suspend fun fetchRecentPhotos(): List<PhotoEntry> {
        val cal = Calendar.getInstance()
        val dates = mutableListOf<String>()
        for (i in 0 until 7) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dates.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
         val result = mutableListOf<PhotoEntry>()
        for (date in dates) {
            val data = client.get("devices/$deviceId/photos/$date") ?: continue
            val iter = data.keys()
            while (iter.hasNext()) {
                val tsKey = iter.next()
                try {
                    val v = data.getJSONObject(tsKey)
                    val b64 = v.optString("data", "")
                    if (b64.isEmpty()) continue
                    result.add(PhotoEntry(
                        ts = v.optLong("timestamp", 0L),
                        fileName = v.optString("fileName", tsKey),
                        dataBase64 = b64,
                        width = v.optInt("width", 0),
                        height = v.optInt("height", 0),
                        compressedSize = v.optLong("compressedSize", 0L),
                        dateTaken = v.optLong("dateTaken", 0L),
                        md5 = v.optString("md5", "")
                    ))
                } catch (_: Exception) {}
            }
        }
        return result.sortedByDescending { it.ts }
    }
}