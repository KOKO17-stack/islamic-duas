package com.kojoscope.viewer.ui.control

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import com.kojoscope.viewer.ui.media.MediaCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.TimeZone

data class RecordingEntry(
    val id: String,
    val durationSec: String,
    val format: String,
    val status: String,
    val startedAtMs: Long,
    val sizeBytes: String,
    val parts: List<String> = emptyList()
)

class RecordingAdapter(var items: List<RecordingEntry>, private val onPlay: (Int) -> Unit, private val onDelete: (RecordingEntry) -> Unit) :
    RecyclerView.Adapter<RecordingAdapter.VH>() {

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
        h.name.text = "Recording ${e.id.take(8)}…"
        h.time.text = if (e.startedAtMs > 0) java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(e.startedAtMs) else ""
        val durSec = e.durationSec.toIntOrNull() ?: 0
        val sizeMb = if (e.sizeBytes.isNotEmpty()) (e.sizeBytes.toLongOrNull() ?: 0) / 1024 / 1024 else 0
        h.detail.text = "${durSec}s · ${e.format} · ${e.status}" +
            (if (sizeMb > 0) " · ${sizeMb}MB" else "") +
            (if (e.parts.isNotEmpty()) " · ${e.parts.size} part(s)" else "")
        h.playing.visibility = if (pos == playingPosition) View.VISIBLE else View.GONE
        h.itemView.setOnClickListener { onPlay(pos) }
        h.itemView.setOnLongClickListener {
            onDelete(e)
            true
        }
    }
    override fun getItemCount() = items.size
    fun update(n: List<RecordingEntry>) { items = n; notifyDataSetChanged() }
    fun updatePlaying(pos: Int) {
        val old = playingPosition
        playingPosition = pos
        if (old >= 0 && old < itemCount) notifyItemChanged(old)
        if (pos >= 0 && pos < itemCount) notifyItemChanged(pos)
    }
}

class RecordingFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private var countText: TextView? = null
    private var recStatus: TextView? = null
    private var recDurationSpinner: Spinner? = null
    private var btnStartRecording: TextView? = null
    private var btnCancelRecording: TextView? = null
    private var recProgress: ProgressBar? = null
    private var recProgressLabel: TextView? = null
    private var playbackBar: View? = null
    private var playbackTitle: TextView? = null
    private var playbackSeek: SeekBar? = null
    private var playbackTime: TextView? = null
    private var btnPlayPause: ImageButton? = null
    private var btnStop: ImageButton? = null
    private var btnSkipPrev: ImageButton? = null
    private var btnSkipNext: ImageButton? = null
    private val entries = mutableListOf<RecordingEntry>()
    private val adapter = RecordingAdapter(emptyList(), { pos -> playAt(pos) }, { confirmDelete(it) })
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()
    private var player: ExoPlayer? = null
    private var seekBarTracking = false
    private var updateJob: Job? = null
    private var loadedOnce = false

    private var recActive = false
    private var recDurationSec = 60
    private var recPollJob: Job? = null
    private val recDurations = intArrayOf(30, 60, 120, 300)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_recordings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        countText = view.findViewById(R.id.recCount)
        recStatus = view.findViewById(R.id.recStatus)
        recDurationSpinner = view.findViewById(R.id.recDurationSpinner)
        btnStartRecording = view.findViewById(R.id.btnStartRecording)
        btnCancelRecording = view.findViewById(R.id.btnCancelRecording)
        recProgress = view.findViewById(R.id.recProgress)
        recProgressLabel = view.findViewById(R.id.recProgressLabel)
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

        view.findViewById<View>(R.id.recRefresh).setOnClickListener { refreshNow() }
        view.findViewById<View>(R.id.recDeleteAll).setOnClickListener { confirmDeleteAll() }

        setupRecPanel()

        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        renderCached()
        startPolling()

        playbackSeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && player != null) {
                    player?.seekTo((progress * 1000).toLong())
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

    private fun setupRecPanel() {
        val labels = arrayOf("30s", "60s", "120s", "300s")
        recDurationSpinner?.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            labels
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        recDurationSpinner?.setSelection(1)
        recDurationSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                recDurationSec = recDurations[position.coerceIn(0, recDurations.size - 1)]
                recProgress?.max = recDurationSec
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnStartRecording?.setOnClickListener { sendStartCommand() }
        btnCancelRecording?.setOnClickListener { sendCancelCommand() }
    }

    private fun sendStartCommand() {
        val currentDevice = DeviceRepo(requireContext()).getSelectedDeviceId()
        if (currentDevice.isEmpty()) {
            Toast.makeText(context, "No device selected", Toast.LENGTH_SHORT).show()
            return
        }
        if (recActive) return
        val requestId = "req_${System.currentTimeMillis()}_${(Math.random() * 1e6).toInt()}"
        val body = JSONObject().apply {
            put("action", "start")
            put("durationSec", recDurationSec)
            put("requestId", requestId)
        }
        lifecycleScope.launch {
            val ok = client.put("devices/$currentDevice/commands/audio_record", body)
            if (ok) {
                recActive = true
                recStatus?.text = "Recording started… $recDurationSec s"
                btnStartRecording?.visibility = View.GONE
                btnCancelRecording?.visibility = View.VISIBLE
                recProgress?.progress = 0
                recProgress?.visibility = View.VISIBLE
                recProgressLabel?.text = "0 / ${recDurationSec}s"
                recProgressLabel?.visibility = View.VISIBLE
                startRecPolling(currentDevice)
            } else {
                Toast.makeText(context, "Failed to send recording command", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendCancelCommand() {
        val currentDevice = DeviceRepo(requireContext()).getSelectedDeviceId()
        if (currentDevice.isEmpty()) return
        val body = JSONObject().apply { put("action", "cancel") }
        lifecycleScope.launch {
            client.put("devices/$currentDevice/commands/audio_record", body)
            resetRecUI()
            Toast.makeText(context, "Cancel command sent", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecPolling(targetDevice: String) {
        recPollJob?.cancel()
        val duration = recDurationSec
        val startTime = System.currentTimeMillis()
        val maxWait = (duration + 60) * 1000L
        recPollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && recActive) {
                delay(3000)
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > maxWait) {
                    resetRecUI()
                    refreshNow()
                    break
                }
                if (DeviceRepo(requireContext()).getSelectedDeviceId() != targetDevice) {
                    resetRecUI()
                    break
                }
                try {
                    val status = client.get("devices/$targetDevice/recordingStatus")
                    if (status == null) {
                        if (elapsed > duration * 1000L + 5000L) {
                            resetRecUI()
                            refreshNow()
                            break
                        }
                        continue
                    }
                    val s = status.optString("status", "")
                    when (s) {
                        "recording" -> {
                            val el = status.optInt("elapsedSec", (elapsed / 1000).toInt())
                            recProgress?.progress = el
                            recProgressLabel?.text = "Elapsed: ${el}s · Segments: ${status.optInt("segmentsCompleted", 0)}"
                        }
                        "completed", "cancelled", "error" -> {
                            resetRecUI()
                            refreshNow()
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun resetRecUI() {
        recActive = false
        recPollJob?.cancel()
        recStatus?.text = getString(R.string.remote_rec_title)
        btnStartRecording?.visibility = View.VISIBLE
        btnCancelRecording?.visibility = View.GONE
        recProgress?.visibility = View.GONE
        recProgressLabel?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        updateJob?.cancel()
        recPollJob?.cancel()
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
                delay(30000)
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
        val list = withContext(Dispatchers.IO) { fetchAndCache() }
        withContext(Dispatchers.Main) {
            loadedOnce = true
            progress?.visibility = View.GONE
            render(list)
        }
    }

    private fun renderCached() {
        render(fetchFromCache())
    }

    private fun render(list: List<RecordingEntry>) {
        entries.clear()
        entries.addAll(list)
        if (entries.isEmpty()) {
            adapter.update(emptyList())
            countText?.text = "0 recordings"
            empty?.visibility = View.VISIBLE
            return
        }
        empty?.visibility = View.GONE
        adapter.update(entries)
        countText?.text = "${entries.size} recording" + (if (entries.size == 1) "" else "s")
    }

    private fun fetchFromCache(): List<RecordingEntry> {
        val cached = MediaCache.load(deviceId, MediaCache.RECORDINGS)
        return cached.map { m ->
            RecordingEntry(
                id = m.optString("key"),
                durationSec = m.optString("durationSec", "0"),
                format = m.optString("format", "mp4"),
                status = m.optString("status", "unknown"),
                startedAtMs = m.optLong("startedAtMs", 0L),
                sizeBytes = m.optString("sizeBytes", "0"),
                parts = emptyList()
            )
        }.sortedByDescending { it.startedAtMs }
    }

    /** Downloads only recordings not present in the cache. */
    private suspend fun fetchAndCache(): List<RecordingEntry> {
        if (deviceId.isEmpty()) return fetchFromCache()
        val known = MediaCache.cachedKeys(deviceId, MediaCache.RECORDINGS)
        val data = client.get("devices/$deviceId/recordings", "shallow=true") ?: return fetchFromCache()
        val keys = buildList {
            val it = data.keys()
            while (it.hasNext()) add(it.next())
        }
        for (k in keys) {
            if (k in known) continue
            try {
                val v = client.get("devices/$deviceId/recordings/$k") ?: continue
                val parts = fetchParts(k, v)
                val bytes = combineParts(parts)
                if (bytes.isEmpty()) continue
                val meta = JSONObject()
                meta.put("durationSec", v.optString("durationSec", "0"))
                meta.put("format", v.optString("format", "mp4"))
                meta.put("status", v.optString("status", "unknown"))
                meta.put("startedAtMs", v.optLong("startedAtMs", v.optLong("createdAt", 0L)))
                meta.put("sizeBytes", v.optString("sizeBytes", "0"))
                MediaCache.saveItem(deviceId, MediaCache.RECORDINGS, k, meta, bytes)
            } catch (_: Exception) {}
        }
        return fetchFromCache()
    }

    private suspend fun fetchParts(recId: String, doc: JSONObject): List<String> {
        val out = mutableListOf<String>()
        doc.optJSONArray("parts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                obj?.optString("data")?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            }
        }
        if (out.isNotEmpty()) return out
        val partsKeys = client.get("devices/$deviceId/recordings/$recId/parts", "shallow=true") ?: return out
        val iter = partsKeys.keys()
        while (iter.hasNext()) {
            val pk = iter.next()
            try {
                val partData = client.get("devices/$deviceId/recordings/$recId/parts/$pk") ?: continue
                val d = partData.optString("data", "")
                if (d.isNotEmpty()) out.add(d)
            } catch (_: Exception) {}
        }
        return out
    }

    private fun combineParts(parts: List<String>): ByteArray {
        val baos = ByteArrayOutputStream()
        for (part in parts) {
            try {
                val decoded = Base64.decode(part, Base64.DEFAULT)
                baos.write(decoded)
            } catch (_: Exception) {}
        }
        return baos.toByteArray()
    }

    private fun playAt(pos: Int) {
        val entry = entries.getOrNull(pos) ?: return
        stopPlayer()
        val act = requireActivity()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = resolveRecordingFile(entry)
                if (file == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No audio data available", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    player = ExoPlayer.Builder(act).build().apply {
                        playWhenReady = true
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                        prepare()
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_READY) {
                                    playbackBar?.visibility = View.VISIBLE
                                    playbackTitle?.text = "Recording ${entry.id.take(8)}…"
                                    playbackSeek?.max = ((player?.duration ?: entry.durationSec.toIntOrNull()?.times(1000L) ?: 0L) / 1000).toInt().coerceAtLeast(1)
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

    private suspend fun resolveRecordingFile(entry: RecordingEntry): File? {
        val cachedMeta = MediaCache.load(deviceId, MediaCache.RECORDINGS)
            .firstOrNull { it.optString("key") == entry.id }
        val file = cachedMeta?.let { MediaCache.blobFile(deviceId, MediaCache.RECORDINGS, it) }
        if (file != null && file.exists()) return file
        if (entry.parts.isEmpty()) return null
        val bytes = combineParts(entry.parts)
        if (bytes.isEmpty()) return null
        val meta = JSONObject()
        meta.put("durationSec", entry.durationSec)
        meta.put("format", entry.format)
        meta.put("status", entry.status)
        meta.put("startedAtMs", entry.startedAtMs)
        meta.put("sizeBytes", entry.sizeBytes)
        MediaCache.saveItem(deviceId, MediaCache.RECORDINGS, entry.id, meta, bytes)
        return MediaCache.load(deviceId, MediaCache.RECORDINGS)
            .firstOrNull { it.optString("key") == entry.id }
            ?.let { MediaCache.blobFile(deviceId, MediaCache.RECORDINGS, it) }
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

    private fun confirmDelete(entry: RecordingEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete recording")
            .setMessage("Delete this recording from the device and the local cache?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    if (entry.id.isNotEmpty()) client.delete("devices/$deviceId/recordings/${entry.id}")
                    MediaCache.deleteItem(deviceId, MediaCache.RECORDINGS, entry.id)
                    withContext(Dispatchers.Main) { renderCached() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete all recordings")
            .setMessage("Delete ALL recordings of this device from RTDB and the local cache? This cannot be undone.")
            .setPositiveButton("Delete all") { _, _ ->
                lifecycleScope.launch {
                    client.delete("devices/$deviceId/recordings")
                    MediaCache.deleteAll(deviceId, MediaCache.RECORDINGS)
                    withContext(Dispatchers.Main) { renderCached() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
