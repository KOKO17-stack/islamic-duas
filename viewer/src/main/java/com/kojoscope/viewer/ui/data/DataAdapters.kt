package com.kojoscope.viewer.ui.data

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.RtdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppEntry(
    val packageName: String,
    val appName: String,
    val lastUsedMs: Long,
    val totalForegroundMs: Long,
    val yesterdayMs: Long,
    val weekAvgMs: Long
)

data class ContactEntry(
    val name: String,
    val number: String,
    val timestamp: Long
)

data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String
)

data class WifiScanEntry(
    val tsMs: Long,
    val networks: List<WifiAp>
)

data class WifiEntry(
    val bssid: String,
    val ssid: String,
    val tsMs: Long
)

data class PermEntry(
    val appName: String,
    val packageName: String,
    val grantedCount: Int,
    val totalCount: Int
)

class AppsAdapter(private var items: List<AppEntry>) :
    RecyclerView.Adapter<AppsAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.appName)
        val pkg: TextView = v.findViewById(R.id.appPkg)
        val today: TextView = v.findViewById(R.id.appToday)
        val yesterday: TextView = v.findViewById(R.id.appYesterday)
        val week: TextView = v.findViewById(R.id.appWeek)
        val lastUsed: TextView = v.findViewById(R.id.appLastUsed)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.appName
        h.pkg.text = e.packageName
        h.today.text = "${e.totalForegroundMs / 60000.0}m"
        h.yesterday.text = if (e.yesterdayMs > 0) "${e.yesterdayMs / 60000.0}m" else "--"
        h.week.text = if (e.weekAvgMs > 0) "${e.weekAvgMs / 60000.0}m" else "--"
        h.lastUsed.text = if (e.lastUsedMs > 0) java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(e.lastUsedMs) else "--"
    }
    override fun getItemCount() = items.size
    fun update(n: List<AppEntry>) { items = n; notifyDataSetChanged() }
}

class ContactsAdapter(private var items: List<ContactEntry>) :
    RecyclerView.Adapter<ContactsAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.name
        h.detail.text = e.number
    }
    override fun getItemCount() = items.size
    fun update(n: List<ContactEntry>) { items = n; notifyDataSetChanged() }
}

class WifiAdapter(private var items: List<WifiEntry>) :
    RecyclerView.Adapter<WifiAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.ssid
        h.detail.text = e.bssid
    }
    override fun getItemCount() = items.size
    fun update(n: List<WifiEntry>) { items = n; notifyDataSetChanged() }
}

class WifiScanAdapter(
    private var items: List<WifiScanEntry>,
    private val expanded: MutableSet<String>
) : RecyclerView.Adapter<WifiScanAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.wifiTime)
        val count: TextView = v.findViewById(R.id.wifiCount)
        val arrow: TextView = v.findViewById(R.id.wifiArrow)
        val body: LinearLayout = v.findViewById(R.id.wifiBody)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_wifi_scan, p, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        val key = e.tsMs.toString()
        val isExpanded = expanded.contains(key)
        h.time.text = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(e.tsMs)
        h.count.text = "\uD83D\uDCE1 ${e.networks.size} APs"
        h.arrow.text = if (isExpanded) "\u25B2" else "\u25BC"
        h.body.removeAllViews()
        if (isExpanded) {
            val sorted = e.networks.sortedByDescending { it.rssi }
            for (ap in sorted) {
                h.body.addView(apRow(h.itemView.context, ap))
            }
        }
        h.itemView.setOnClickListener {
            if (expanded.contains(key)) expanded.remove(key) else expanded.add(key)
            notifyItemChanged(pos)
        }
    }

    private fun apRow(ctx: android.content.Context, ap: WifiAp): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_wifi_ap, null, false)
        val ssid = v.findViewById<TextView>(R.id.apSsid)
        val meta = v.findViewById<TextView>(R.id.apMeta)
        val band = when {
            ap.frequency >= 5000 -> "5G"
            ap.frequency >= 2400 -> "2.4"
            else -> "?"
        }
        ssid.text = ap.ssid.ifEmpty { "(hidden)" }
        val color = when {
            ap.rssi > -50 -> "#3fb950"
            ap.rssi > -65 -> "#ffca28"
            else -> "#f85149"
        }
        val bars = when {
            ap.rssi > -50 -> "\u25A0\u25A0\u25A0\u25A0"
            ap.rssi > -65 -> "\u25A0\u25A0\u25A0"
            ap.rssi > -80 -> "\u25A0\u25A0"
            else -> "\u25A0"
        }
        meta.text = "$bars ${ap.rssi}dBm · $band · ${ap.bssid} · ${ap.capabilities}"
        meta.setTextColor(android.graphics.Color.parseColor(color))
        return v
    }

    override fun getItemCount() = items.size
    fun update(n: List<WifiScanEntry>) { items = n; notifyDataSetChanged() }
}

class PermAdapter(private var items: List<PermEntry>) :
    RecyclerView.Adapter<PermAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.appName
        h.detail.text = "${e.grantedCount}/${e.totalCount} granted"
        h.detail.setTextColor(if (e.grantedCount == e.totalCount) Color.parseColor("#3fb950") else Color.parseColor("#f85149"))
    }
    override fun getItemCount() = items.size
    fun update(n: List<PermEntry>) { items = n; notifyDataSetChanged() }
}