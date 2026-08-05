package com.kojoscope.viewer.ui.media

import android.util.Base64
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import com.kojoscope.viewer.ui.player.VideoViewerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoEntry(
    val tsMs: Long,
    val fileName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val source: String,
    val thumbB64: String,
    val dataB64: String,
    val key: String = ""
)

fun formatDurMs(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return if (m > 0) "${m}m ${sec}s" else "${sec}s"
}

class VideoAdapter(private var items: List<VideoEntry>) :
    RecyclerView.Adapter<VideoAdapter.Holder>() {

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.vidThumb)
        val name: TextView = view.findViewById(R.id.vidName)
        val meta: TextView = view.findViewById(R.id.vidMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val e = items[position]
        holder.name.text = e.fileName
        val dur = if (e.durationMs > 0) formatDurMs(e.durationMs) + " · " else ""
        holder.meta.text = "${dur}${e.sizeBytes / 1024}KB · ${e.width}x${e.height}"
        holder.itemView.setOnClickListener {
            val ctx = holder.itemView.context
            val intent = android.content.Intent(ctx, VideoViewerActivity::class.java)
            intent.putExtra("key", e.key)
            intent.putExtra("name", e.fileName)
            ctx.startActivity(intent)
        }
        if (e.thumbB64.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val decoded = Base64.decode(e.thumbB64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                if (bmp != null) withContext(Dispatchers.Main) { holder.thumb.setImageBitmap(bmp) }
            }
            holder.thumb.setBackgroundColor(android.graphics.Color.parseColor("#161b22"))
        } else {
            holder.thumb.setImageDrawable(null)
            holder.thumb.setBackgroundColor(android.graphics.Color.parseColor("#161b22"))
        }
    }

    override fun getItemCount(): Int = items.size
    fun update(newItems: List<VideoEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}