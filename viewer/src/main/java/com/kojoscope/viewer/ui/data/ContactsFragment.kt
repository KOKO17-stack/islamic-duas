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

class ContactsFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = ContactsAdapter(emptyList())
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
                if (deviceId.isNotEmpty()) loadContacts()
                delay(30000)
            }
        }
    }

    private suspend fun loadContacts() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchContacts() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchContacts(): List<ContactEntry> {
        val data = client.get("devices/$deviceId/contacts/all") ?: return emptyList()
        val result = mutableListOf<ContactEntry>()
        val iter = data.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            if (k == "syncedAt" || k == "ts_ms") continue
            try {
                if (k == "contacts") {
                    val arr = data.getJSONArray(k)
                    for (i in 0 until arr.length()) {
                        val v = arr.getJSONObject(i)
                        result.add(ContactEntry(
                            name = v.optString("name", v.optString("displayName", "")),
                            number = v.optString("number", v.optString("phone", "")),
                            timestamp = v.optLong("ts_ms", 0L)
                        ))
                    }
                } else {
                    val v = data.getJSONObject(k)
                    result.add(ContactEntry(
                        name = v.optString("displayName", v.optString("name", k)),
                        number = v.optString("number", v.optString("phone", "")),
                        timestamp = v.optLong("timestamp", 0L)
                    ))
                }
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.timestamp }
    }
}