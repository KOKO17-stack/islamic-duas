package com.kojoscope.viewer.ui.media

import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import com.kojoscope.viewer.ui.player.MediaViewerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

sealed class PhotoListItem {
    data class DayHeader(val day: String, val dateMillis: Long, val count: Int) : PhotoListItem()
    data class Photo(val entry: PhotoEntry) : PhotoListItem()
}

private val daySdf = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).apply {
    timeZone = TimeZone.getTimeZone("Asia/Karachi")
}

class PhotoAdapter(
    private var items: List<PhotoListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PHOTO = 1
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
            is PhotoListItem.Photo -> bindPhoto(holder as Holder, item.entry)
        }
    }

    private fun bindPhoto(holder: Holder, e: PhotoEntry) {
        holder.name.text = e.fileName
        holder.size.text = "${e.compressedSize / 1024}KB · ${e.width}x${e.height}"
        holder.itemView.setOnClickListener {
            val ctx = holder.itemView.context
            val intent = android.content.Intent(ctx, MediaViewerActivity::class.java)
            intent.putExtra("data", e.dataBase64)
            intent.putExtra("name", e.fileName)
            ctx.startActivity(intent)
        }
        CoroutineScope(Dispatchers.IO).launch {
            val decoded = Base64.decode(e.dataBase64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            withContext(Dispatchers.Main) {
                holder.thumb.setImageBitmap(bmp)
            }
        }
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