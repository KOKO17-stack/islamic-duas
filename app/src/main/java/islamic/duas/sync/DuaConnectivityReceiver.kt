package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import islamic.duas.data.OfflineQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DuaConnectivityReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DuaConnRcvr"
        private var lastFlushMs = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val network = cm.activeNetwork ?: return
        val caps = cm.getNetworkCapabilities(network) ?: return
        val isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        if (!isOnline) return

        val now = System.currentTimeMillis()
        if (now - lastFlushMs < 30_000L) return
        lastFlushMs = now

        Log.d(TAG, "Network restored — flushing offline queue")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                QueueFlushWorker.runOnceNow(context)
            } catch (e: Exception) {
                Log.w(TAG, "Immediate flush error: ${e.message}")
            }
        }
    }
}