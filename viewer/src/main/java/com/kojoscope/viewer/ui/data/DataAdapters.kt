package com.kojoscope.viewer.ui.data

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    val totalForegroundMs: Long
)

data class ContactEntry(
    val name: String,
    val number: String,
    val timestamp: Long
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
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.appName
        h.detail.text = "${e.packageName} · ${(e.totalForegroundMs / 1000)}s"
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