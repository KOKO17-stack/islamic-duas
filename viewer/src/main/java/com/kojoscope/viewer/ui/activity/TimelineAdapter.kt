package com.kojoscope.viewer.ui.activity

import com.kojoscope.viewer.R

data class TimelineEntry(
    val tsMs: Long,
    val type: String,
    val contactName: String?,
    val contact: String?,
    val messagePreview: String?,
    val duration: Long?,
    val direction: String?,
    val groupName: String?,
    val rawText: String?,
    val packageName: String?,
    val isIncoming: String?
)

sealed class TimelineItem {
    data class DayHeader(val day: String, val summary: String) : TimelineItem()
    data class Entry(val entry: TimelineEntry, val icon: String, val title: String, val subtitle: String) : TimelineItem()
}

class TimelineAdapter(
    private var items: List<TimelineItem>
) : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_DAY = 0
        const val TYPE_ENTRY = 1
    }

    inner class DayHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val dayText: android.widget.TextView = view.findViewById(R.id.dayText)
        val summaryText: android.widget.TextView = view.findViewById(R.id.summaryText)
    }

    inner class EntryHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val icon: android.widget.TextView = view.findViewById(R.id.entryIcon)
        val time: android.widget.TextView = view.findViewById(R.id.entryTime)
        val title: android.widget.TextView = view.findViewById(R.id.entryTitle)
        val subtitle: android.widget.TextView = view.findViewById(R.id.entrySubtitle)
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is TimelineItem.DayHeader -> TYPE_DAY
        is TimelineItem.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        val inflater = android.view.LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DAY) {
            DayHolder(inflater.inflate(R.layout.item_timeline_day_header, parent, false))
        } else {
            EntryHolder(inflater.inflate(R.layout.item_timeline_entry, parent, false))
        }
    }

    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is DayHolder && item is TimelineItem.DayHeader) {
            holder.dayText.text = item.day
            holder.summaryText.text = item.summary
        } else if (holder is EntryHolder && item is TimelineItem.Entry) {
            holder.icon.text = item.icon
            holder.title.text = item.title
            holder.subtitle.text = item.subtitle
            holder.time.text = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
            }.format(item.entry.tsMs)
        }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<TimelineItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}