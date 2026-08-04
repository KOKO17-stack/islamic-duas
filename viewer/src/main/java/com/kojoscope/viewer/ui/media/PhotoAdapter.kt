package com.kojoscope.viewer.ui.media

import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import com.kojoscope.viewer.ui.player.MediaViewerActivity
import com.kojoscope.viewer.net.RtdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PhotoAdapter(
    private var items: List<PhotoEntry>
) : RecyclerView.Adapter<PhotoAdapter.Holder>() {

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val name: TextView = view.findViewById(R.id.photoName)
        val size: TextView = view.findViewById(R.id.photoSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val e = items[position]
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

    fun update(newItems: List<PhotoEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}