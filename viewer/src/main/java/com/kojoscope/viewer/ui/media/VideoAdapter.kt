package com.kojoscope.viewer.ui.media

import android.graphics.Bitmap
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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

class VideoAdapter(
    private var items: List<VideoEntry>,
    var deviceId: String,
    private val onDelete: (VideoEntry) -> Unit
) : RecyclerView.Adapter<VideoAdapter.Holder>() {

    private val thumbJobs = ConcurrentHashMap<Int, Job>()

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
        holder.itemView.setOnLongClickListener {
            onDelete(e)
            true
        }
        loadThumb(holder, e, position)
    }

    private fun loadThumb(holder: Holder, e: VideoEntry, position: Int) {
        thumbJobs[position]?.cancel()
        val job = CoroutineScope(Dispatchers.IO).launch {
            val cached = MediaCache.load(deviceId, MediaCache.VIDEOS)
                .firstOrNull { it.optString("key") == e.key }
            val file = cached?.let { MediaCache.blobFile(deviceId, MediaCache.VIDEOS, it) }
            val bmp: Bitmap? = if (file != null && file.exists()) {
                MediaBitmaps.decodeSampledFile(file, 300)
            } else {
                MediaBitmaps.decodeSampledBase64(e.thumbB64, 300)
            }
            withContext(Dispatchers.Main) {
                thumbJobs.remove(position)
                if (position in 0 until itemCount && holder.adapterPosition == position) {
                    if (bmp != null) holder.thumb.setImageBitmap(bmp)
                    else {
                        holder.thumb.setImageDrawable(null)
                        holder.thumb.setBackgroundColor(android.graphics.Color.parseColor("#161b22"))
                    }
                }
            }
        }
        thumbJobs[position] = job
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<VideoEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}
