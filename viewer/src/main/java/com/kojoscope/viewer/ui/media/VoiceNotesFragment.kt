package com.kojoscope.viewer.ui.media

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import java.io.File
import java.io.FileOutputStream
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class VoiceNote(
    val tsMs: Long,
    val fileName: String,
    val sourceApp: String,
    val durationMs: Long,
    val sizeBytes: String,
    val audioData: String
)

private class VoiceNoteAdapter(
    private var items: List<VoiceNote>,
    private val onPlay: (Int) -> Unit
) : RecyclerView.Adapter<VoiceNoteAdapter.VH>() {

    var playingPosition = -1

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
        val time: TextView = v.findViewById(R.id.itemTime)
        val playing: View = v.findViewById(R.id.playingIndicator)
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
        h.playing.visibility = if (pos == playingPosition) View.VISIBLE else View.GONE
        h.itemView.setOnClickListener { onPlay(pos) }
    }

    override fun getItemCount() = items.size
    fun update(n: List<VoiceNote>) { items = n; notifyDataSetChanged() }
    fun updatePlaying(pos: Int) {
        val old = playingPosition
        playingPosition = pos
        if (old >= 0 && old < itemCount) notifyItemChanged(old)
        if (pos >= 0 && pos < itemCount) notifyItemChanged(pos)
    }
}

class VoiceNotesFragment : androidx.fragment.app.Fragment() {

    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private var playbackBar: View? = null
    private var playbackTitle: TextView? = null
    private var playbackSeek: SeekBar? = null
    private var playbackTime: TextView? = null
    private var btnPlayPause: ImageButton? = null
    private var btnStop: ImageButton? = null
    private val notes = mutableListOf<VoiceNote>()
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()
    private var player: ExoPlayer? = null
    private var playingPos = -1
    private var seekBarTracking = false
    private var updateJob: Job? = null
    private val adapter = VoiceNoteAdapter(emptyList()) { pos -> playAt(pos) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_voice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        playbackBar = view.findViewById(R.id.playbackBar)
        playbackTitle = view.findViewById(R.id.playbackTitle)
        playbackSeek = view.findViewById(R.id.playbackSeek)
        playbackTime = view.findViewById(R.id.playbackTime)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnStop = view.findViewById(R.id.btnStop)
        recycler?.layoutManager = LinearLayoutManager(context)
        recycler?.adapter = adapter
        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        startPolling()

        playbackSeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && player != null) {
                    player?.seekTo(progress.toLong() * 1000)
                }
            }
            override fun onStartTrackingTouch(seek: SeekBar?) { seekBarTracking = true }
            override fun onStopTrackingTouch(seek: SeekBar?) { seekBarTracking = false }
        })

        btnPlayPause?.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
                btnPlayPause?.setImageResource(android.R.drawable.ic_media_play)
            } else {
                player?.play()
                btnPlayPause?.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        btnStop?.setOnClickListener {
            stopPlayer()
            playbackBar?.visibility = View.GONE
            adapter.updatePlaying(-1)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        updateJob?.cancel()
        stopPlayer()
        recycler = null
        playbackBar = null
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
                val bytes = Base64.decode(note.audioData, Base64.DEFAULT)
                val f = File(activity.cacheDir, "vn_${System.currentTimeMillis()}.m4a")
                FileOutputStream(f).use { it.write(bytes) }

                withContext(Dispatchers.Main) {
                    player = ExoPlayer.Builder(activity).build().apply {
                        playWhenReady = true
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(f)))
                        prepare()
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_READY) {
                                    playbackBar?.visibility = View.VISIBLE
                                    playbackTitle?.text = note.fileName
                                    playbackSeek?.max = (note.durationMs / 1000).toInt()
                                    btnPlayPause?.setImageResource(android.R.drawable.ic_media_pause)
                                    adapter.updatePlaying(pos)
                                    startSeekUpdate()
                                }
                            }
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                requireActivity().runOnUiThread {
                                    btnPlayPause?.setImageResource(
                                        if (isPlaying) android.R.drawable.ic_media_pause
                                        else android.R.drawable.ic_media_play
                                    )
                                }
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startSeekUpdate() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && player != null && player!!.isPlaying) {
                if (!seekBarTracking) {
                    val pos = player?.currentPosition ?: 0
                    playbackSeek?.progress = (pos / 1000).toInt()
                    val dur = player?.duration ?: 0
                    playbackTime?.text = "${formatMs(pos)} / ${formatMs(dur)}"
                }
                delay(500)
            }
        }
    }

    private fun stopPlayer() {
        updateJob?.cancel()
        player?.let { runCatching { it.stop() } }
        player?.let { runCatching { it.release() } }
        player = null
    }

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        return String.format("%02d:%02d", m, sec)
    }
}