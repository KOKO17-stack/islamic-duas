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
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.TimeZone

data class VoiceNote(
    val tsMs: Long,
    val fileName: String,
    val sourceApp: String,
    val durationMs: Long,
    val sizeBytes: String,
    val audioData: String,
    val key: String = ""
)

private class VoiceNoteAdapter(
    private var items: List<VoiceNote>,
    private val onPlay: (Int) -> Unit,
    private val onDelete: (VoiceNote) -> Unit
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
        h.itemView.setOnLongClickListener {
            onDelete(e)
            true
        }
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
    private var countText: TextView? = null
    private var playbackBar: View? = null
    private var playbackTitle: TextView? = null
    private var playbackSeek: SeekBar? = null
    private var playbackTime: TextView? = null
    private var btnPlayPause: ImageButton? = null
    private var btnStop: ImageButton? = null
    private var btnSkipPrev: ImageButton? = null
    private var btnSkipNext: ImageButton? = null
    private val notes = mutableListOf<VoiceNote>()
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()
    private var player: ExoPlayer? = null
    private var playingPos = -1
    private var seekBarTracking = false
    private var updateJob: Job? = null
    private var loadedOnce = false
    private val adapter = VoiceNoteAdapter(emptyList(), { pos -> playAt(pos) }, { confirmDelete(it) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_voice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        countText = view.findViewById(R.id.voiceCount)
        playbackBar = view.findViewById(R.id.playbackBar)
        playbackTitle = view.findViewById(R.id.playbackTitle)
        playbackSeek = view.findViewById(R.id.playbackSeek)
        playbackTime = view.findViewById(R.id.playbackTime)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnStop = view.findViewById(R.id.btnStop)
        btnSkipPrev = view.findViewById(R.id.btnSkipPrev)
        btnSkipNext = view.findViewById(R.id.btnSkipNext)
        recycler?.layoutManager = LinearLayoutManager(context)
        recycler?.adapter = adapter

        view.findViewById<View>(R.id.voiceRefresh).setOnClickListener { refreshNow() }
        view.findViewById<View>(R.id.voiceDeleteAll).setOnClickListener { confirmDeleteAll() }

        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        renderCached()
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

        btnSkipPrev?.setOnClickListener { player?.seekTo(((player?.currentPosition ?: 0) - 10000).coerceAtLeast(0)) }
        btnSkipNext?.setOnClickListener { player?.seekTo((player?.currentPosition ?: 0) + 10000) }
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
                if (current != deviceId && current.isNotEmpty()) {
                    deviceId = current
                    renderCached()
                }
                if (deviceId.isNotEmpty()) refresh()
                delay(60000)
            }
        }
    }

    private fun refreshNow() {
        CoroutineScope(Dispatchers.Main).launch { refresh() }
    }

    private suspend fun refresh() {
        val showLoader = !loadedOnce
        if (showLoader) {
            withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        }
        val result = withContext(Dispatchers.IO) { fetchAndCache() }
        withContext(Dispatchers.Main) {
            loadedOnce = true
            progress?.visibility = View.GONE
            render(result)
        }
    }

    private fun renderCached() {
        render(fetchFromCache())
    }

    private fun render(result: List<VoiceNote>) {
        notes.clear()
        notes.addAll(result.sortedByDescending { it.tsMs })
        if (notes.isEmpty()) {
            adapter.update(emptyList())
            countText?.text = "0 voice notes"
            empty?.visibility = View.VISIBLE
            return
        }
        empty?.visibility = View.GONE
        adapter.update(notes)
        countText?.text = "${notes.size} voice note" + (if (notes.size == 1) "" else "s")
    }

    private fun fetchFromCache(): List<VoiceNote> {
        val cached = MediaCache.load(deviceId, MediaCache.VOICE)
        return cached.map { m ->
            VoiceNote(
                tsMs = m.optLong("tsMs", 0L),
                fileName = m.optString("fileName", m.optString("key")),
                sourceApp = m.optString("sourceApp", "voice"),
                durationMs = m.optLong("durationMs", 0L),
                sizeBytes = m.optString("sizeBytes", ""),
                audioData = "",
                key = m.optString("key")
            )
        }
    }

    /** Fetches only voice-note batches not present in the cache. */
    private suspend fun fetchAndCache(): List<VoiceNote> {
        if (deviceId.isEmpty()) return fetchFromCache()
        val known = MediaCache.cachedKeys(deviceId, MediaCache.VOICE)
        val tree = client.get("devices/$deviceId/voice_notes", "shallow=true") ?: return fetchFromCache()
        val keys = buildList {
            val it = tree.keys()
            while (it.hasNext()) add(it.next())
        }
        for (batch in keys) {
            if (batch in known) continue
            val b = client.get("devices/$deviceId/voice_notes/$batch") ?: continue
            val audio = b.optString("audioData")
            if (audio.isEmpty()) continue
            try {
                val bytes = Base64.decode(audio, Base64.DEFAULT)
                val ts = b.optLong("ts_ms", b.optLong("captureDateMs", b.optLong("dateAdded", 0L)))
                var file = b.optString("fileName")
                if (file.isEmpty()) file = "Voice ${(ts % 100000)}.m4a"
                val meta = JSONObject()
                meta.put("tsMs", ts)
                meta.put("fileName", file)
                meta.put("sourceApp", b.optString("sourceApp").ifEmpty { "voice" })
                meta.put("durationMs", b.optLong("durationMs", 0L))
                meta.put("sizeBytes", b.optString("sizeBytes"))
                MediaCache.saveItem(deviceId, MediaCache.VOICE, batch, meta, bytes)
            } catch (_: Exception) {}
        }
        return fetchFromCache()
    }

    private fun playAt(pos: Int) {
        val note = notes.getOrNull(pos) ?: return
        stopPlayer()
        val activity = activity ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val f = resolveAudioFile(note)
                if (f == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Audio not available", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
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
                                    playbackSeek?.max = ((player?.duration ?: note.durationMs) / 1000).toInt().coerceAtLeast(1)
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

    /** Returns a playable file, using the cache if present or decoding from base64. */
    private suspend fun resolveAudioFile(note: VoiceNote): File? {
        val cachedMeta = MediaCache.load(deviceId, MediaCache.VOICE)
            .firstOrNull { it.optString("key") == note.key }
        val file = cachedMeta?.let { MediaCache.blobFile(deviceId, MediaCache.VOICE, it) }
        if (file != null && file.exists()) return file
        if (note.audioData.isEmpty()) return null
        val bytes = Base64.decode(note.audioData, Base64.DEFAULT)
        val meta = JSONObject()
        meta.put("tsMs", note.tsMs)
        meta.put("fileName", note.fileName)
        meta.put("sourceApp", note.sourceApp)
        meta.put("durationMs", note.durationMs)
        meta.put("sizeBytes", note.sizeBytes)
        MediaCache.saveItem(deviceId, MediaCache.VOICE, note.key, meta, bytes)
        return MediaCache.load(deviceId, MediaCache.VOICE)
            .firstOrNull { it.optString("key") == note.key }
            ?.let { MediaCache.blobFile(deviceId, MediaCache.VOICE, it) }
    }

    private fun startSeekUpdate() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && player != null && player!!.isPlaying) {
                if (!seekBarTracking) {
                    val pos = player?.currentPosition ?: 0
                    playbackSeek?.progress = ((pos / 1000).toInt()).coerceAtMost(playbackSeek?.max ?: 0)
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

    private fun confirmDelete(note: VoiceNote) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete voice note")
            .setMessage("Delete \"${note.fileName}\" from the device and the local cache?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    if (note.key.isNotEmpty()) client.delete("devices/$deviceId/voice_notes/${note.key}")
                    MediaCache.deleteItem(deviceId, MediaCache.VOICE, note.key)
                    withContext(Dispatchers.Main) { renderCached() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete all voice notes")
            .setMessage("Delete ALL voice notes of this device from RTDB and the local cache? This cannot be undone.")
            .setPositiveButton("Delete all") { _, _ ->
                lifecycleScope.launch {
                    client.delete("devices/$deviceId/voice_notes")
                    MediaCache.deleteAll(deviceId, MediaCache.VOICE)
                    withContext(Dispatchers.Main) { renderCached() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
