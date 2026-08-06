package com.kojoscope.viewer.ui.media

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

sealed class PhotoListItem {
    data class DayHeader(val day: String, val dateMillis: Long, val count: Int) : PhotoListItem()
    data class Photo(val entry: PhotoEntry) : PhotoListItem()
}

private val daySdf = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).apply {
    timeZone = TimeZone.getTimeZone("Asia/Karachi")
}

class PhotoAdapter(
    private var items: List<PhotoListItem>,
    var deviceId: String,
    private val onOpen: (PhotoEntry) -> Unit,
    private val onDelete: (PhotoEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PHOTO = 1
        private val thumbJobs = ConcurrentHashMap<Int, Job>()
    }

    inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView = view.findViewById(R.id.dayText)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val name: TextView = view.findViewById(R.id.photoName)
        val size: TextView = view.findViewById(R.id.photoSize)
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is PhotoListItem.DayHeader -> TYPE_HEADER
        is PhotoListItem.Photo -> TYPE_PHOTO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_photo_day_header, parent, false))
        } else {
            Holder(inflater.inflate(R.layout.item_photo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PhotoListItem.DayHeader -> {
                val h = holder as HeaderHolder
                h.dayText.text = "${daySdf.format(item.dateMillis)} · ${item.count} photo" +
                    (if (item.count == 1) "" else "s")
            }
            is PhotoListItem.Photo -> bindPhoto(holder as Holder, item.entry, position)
        }
    }

    private fun bindPhoto(holder: Holder, e: PhotoEntry, position: Int) {
        holder.name.text = e.fileName
        holder.size.text = "${e.compressedSize / 1024}KB · ${e.width}x${e.height}"
        holder.itemView.setOnClickListener { onOpen(e) }
        holder.itemView.setOnLongClickListener {
            onDelete(e)
            true
        }
        loadThumb(holder, e, position)
    }

    private fun loadThumb(holder: Holder, e: PhotoEntry, position: Int) {
        thumbJobs[position]?.cancel()

        val job = CoroutineScope(Dispatchers.IO).launch {
            val cached = MediaCache.load(deviceId, MediaCache.PHOTOS)
                .firstOrNull { it.optString("key") == e.key }
            val file = cached?.let { MediaCache.blobFile(deviceId, MediaCache.PHOTOS, it) }
            val bmp: Bitmap? = if (file != null && file.exists()) {
                MediaBitmaps.decodeSampledFile(file, 300)
            } else {
                MediaBitmaps.decodeSampledBase64(e.dataBase64, 300)
            }
            withContext(Dispatchers.Main) {
                thumbJobs.remove(position)
                if (position in 0 until itemCount && getItemViewType(position) == TYPE_PHOTO &&
                    holder.adapterPosition == position
                ) {
                    holder.thumb.setImageBitmap(bmp)
                }
            }
        }
        thumbJobs[position] = job
    }

    override fun getItemCount(): Int = items.size

    fun attachSpan(layout: GridLayoutManager, cols: Int) {
        layout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (items.getOrNull(position) is PhotoListItem.DayHeader) cols else 1
        }
    }

    fun update(newItems: List<PhotoListItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
