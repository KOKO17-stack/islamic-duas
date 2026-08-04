package com.kojoscope.viewer.ui.media

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoiceNote(
    val tsMs: Long,
    val fileName: String,
    val sourceApp: String,
    val durationMs: Long,
    val sizeBytes: String,
    val audioData: String
)

private class VoiceNoteAdapter(private var items: List<VoiceNote>, private val onPlay: (Int) -> Unit) :
    RecyclerView.Adapter<VoiceNoteAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
        val time: TextView = v.findViewById(R.id.itemTime)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.fileName
        h.time.text = if (e.tsMs > 0) java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Karachi")
        }.format(e.tsMs) else ""
        val sec = e.durationMs / 1000
        val sizeMb = if (e.sizeBytes.isNotEmpty()) (e.sizeBytes.toLongOrNull() ?: 0) / 1024 / 1024 else 0
        h.detail.text = "${sec}s · ${e.sourceApp}"
            .let { if (sizeMb > 0) "$it · ${sizeMb}MB" else it }
        h.itemView.setOnClickListener { onPlay(pos) }
    }

    override fun getItemCount() = items.size
    fun update(n: List<VoiceNote>) { items = n; notifyDataSetChanged() }
}

class VoiceNotesFragment : androidx.fragment.app.Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val notes = mutableListOf<VoiceNote>()
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()
    private var player: android.media.MediaPlayer? = null

    private val adapter = VoiceNoteAdapter(emptyList()) { pos -> playAt(pos) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: android.os.Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        recycler?.layoutManager = LinearLayoutManager(context)
        recycler?.adapter = adapter
        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        startPolling()
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        stopPlayer()
        recycler = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayer()
    }

    private fun startPolling() {
        pollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val current = DeviceRepo(requireContext()).getSelectedDeviceId()
                if (current.isNotEmpty()) deviceId = current
                if (deviceId.isNotEmpty()) load()
                delay(60000)
            }
        }
    }

    private suspend fun load() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val result = withContext(Dispatchers.IO) { fetch() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (result.isEmpty()) {
                empty?.visibility = View.VISIBLE
                adapter.update(emptyList())
            } else {
                empty?.visibility = View.GONE
                adapter.update(result)
            }
        }
    }

    private suspend fun fetch(): List<VoiceNote> {
        val tree = client.get("devices/$deviceId/voice_notes") ?: return emptyList()
        val out = mutableListOf<VoiceNote>()
        tree.keys().forEach { batch ->
            val b = tree.optJSONObject(batch) ?: return@forEach
            val audio = b.optString("audioData")
            if (audio.isEmpty()) return@forEach
            val ts = b.optLong("ts_ms", b.optLong("captureDateMs", b.optLong("dateAdded", 0L)))
            var file = b.optString("fileName")
            if (file.isEmpty()) file = "Voice ${(ts % 100000)}.m4a"
            out.add(VoiceNote(
                tsMs = ts,
                fileName = file,
                sourceApp = b.optString("sourceApp").ifEmpty { "voice" },
                durationMs = b.optLong("durationMs", 0L),
                sizeBytes = b.optString("sizeBytes"),
                audioData = audio
            ))
        }
        notes.clear()
        notes.addAll(out.sortedByDescending { it.tsMs })
        return notes
    }

    private fun playAt(pos: Int) {
        val note = notes.getOrNull(pos) ?: return
        stopPlayer()
        val activity = activity ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = android.util.Base64.decode(note.audioData, android.util.Base64.DEFAULT)
                val f = java.io.File(activity.cacheDir, "vn_${System.currentTimeMillis()}.m4a")
                f.writeBytes(bytes)
                withContext(Dispatchers.Main) {
                    val mp = android.media.MediaPlayer()
                    player = mp
                    mp.setOnErrorListener { p, _, _ -> p.reset(); true }
                    mp.setDataSource(f.absolutePath)
                    mp.prepare()
                    mp.start()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopPlayer() {
        player?.let { runCatching { it.stop() } }
        player?.let { runCatching { it.release() } }
        player = null
    }
}