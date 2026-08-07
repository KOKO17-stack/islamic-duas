package com.kojoscope.viewer.ui.data

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncFragment : Fragment() {

    private var deviceId: String = ""
    private var btnTriggerSync: TextView? = null
    private var btnAutoSync: TextView? = null
    private var lastTrigger: TextView? = null
    private var fcmTokenStatus: TextView? = null
    private var queueSize: TextView? = null
    private val client = RtdbClient.getInstance()
    private var autoSyncEnabled = false
    private var autoSyncJob: Job? = null
    private var pollJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_sync, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnTriggerSync = view.findViewById(R.id.btnTriggerSync)
        btnAutoSync = view.findViewById(R.id.btnAutoSync)
        lastTrigger = view.findViewById(R.id.lastTrigger)
        fcmTokenStatus = view.findViewById(R.id.fcmTokenStatus)
        queueSize = view.findViewById(R.id.queueSize)

        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()

        btnTriggerSync?.setOnClickListener { triggerSync() }
        btnAutoSync?.setOnClickListener { toggleAutoSync() }

        startPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoSyncJob?.cancel()
        pollJob?.cancel()
        btnTriggerSync = null
        btnAutoSync = null
        lastTrigger = null
        fcmTokenStatus = null
        queueSize = null
    }

    private fun startPolling() {
        pollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val current = DeviceRepo(requireContext()).getSelectedDeviceId()
                if (current.isNotEmpty()) deviceId = current
                if (deviceId.isNotEmpty()) {
                    pollFcmToken()
                    pollQueueSize()
                }
                delay(30000)
            }
        }
    }

    private fun triggerSync() {
        if (deviceId.isEmpty()) return
        val now = System.currentTimeMillis()
        val body = JSONObject().apply {
            put("requested", now)
            put("ts", now)
        }
        CoroutineScope(Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) {
                client.put("devices/$deviceId/fcm/sync_request", body)
            }
            if (ok) {
                val timeStr = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(now))
                lastTrigger?.text = "Last trigger: $timeStr"
                Toast.makeText(context, "Sync triggered", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to trigger sync", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleAutoSync() {
        autoSyncEnabled = !autoSyncEnabled
        if (autoSyncEnabled) {
            btnAutoSync?.text = "⏱ Auto-Sync: ON"
            autoSyncJob?.cancel()
            autoSyncJob = CoroutineScope(Dispatchers.Main).launch {
                while (isActive && autoSyncEnabled) {
                    triggerSync()
                    delay(300000)
                }
            }
            Toast.makeText(context, "Auto-sync enabled (every 5 min)", Toast.LENGTH_SHORT).show()
        } else {
            btnAutoSync?.text = "⏱ Auto-Sync: OFF"
            autoSyncJob?.cancel()
            Toast.makeText(context, "Auto-sync disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun pollFcmToken() {
        val data = withContext(Dispatchers.IO) {
            client.get("devices/$deviceId/fcm/token")
        }
        if (data != null) {
            val token = data.optString("fcmToken", "")
            if (token.isNotEmpty()) {
                fcmTokenStatus?.text = "✅ FCM token registered"
                fcmTokenStatus?.setTextColor(requireContext().getColor(R.color.success))
            } else {
                fcmTokenStatus?.text = "No FCM token registered yet"
                fcmTokenStatus?.setTextColor(requireContext().getColor(R.color.text_secondary))
            }
        } else {
            fcmTokenStatus?.text = "No FCM token registered yet"
            fcmTokenStatus?.setTextColor(requireContext().getColor(R.color.text_secondary))
        }
    }

    private suspend fun pollQueueSize() {
        val info = withContext(Dispatchers.IO) {
            client.get("devices/$deviceId/info")
        }
        if (info != null) {
            val size = info.optInt("offlineQueueSize", 0)
            queueSize?.text = size.toString()
            val color = when {
                size > 50 -> R.color.danger
                size > 10 -> R.color.warning
                else -> R.color.success
            }
            queueSize?.setTextColor(requireContext().getColor(color))
        } else {
            queueSize?.text = "--"
            queueSize?.setTextColor(requireContext().getColor(R.color.text_secondary))
        }
    }
}
