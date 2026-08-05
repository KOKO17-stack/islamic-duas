package com.kojoscope.viewer.ui.control

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
import androidx.fragment.app.Fragment
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
import java.io.FileOutputStream
import java.util.TimeZone

data class HealthEntry(
    val date: String,
    val steps: Int,
    val goal: Int,
    val tsMs: Long
)

data class RecordingEntry(
    val id: String,
    val durationSec: String,
    val format: String,
    val status: String,
    val startedAtMs: Long,
    val sizeBytes: String,
    val parts: List<String> = emptyList()
)

class HealthAdapter(private var items: List<HealthEntry>) :
    RecyclerView.Adapter<HealthAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.itemName)
        val detail: TextView = v.findViewById(R.id.itemDetail)
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_list, p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.name.text = e.date
        h.detail.text = "${e.steps}/${e.goal} steps"
    }
    override fun getItemCount() = items.size
    fun update(n: List<HealthEntry>) { items = n; notifyDataSetChanged() }
}

class RecordingAdapter(var items: List<RecordingEntry>, private val onPlay: (Int) -> Unit) :
    RecyclerView.Adapter<RecordingAdapter.VH>() {

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
        h.name.text = "Recording ${e.id.take(8)}…"
        h.time.text = if (e.startedAtMs > 0) java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(e.startedAtMs) else ""
        val durSec = e.durationSec.toIntOrNull() ?: 0
        val sizeMb = if (e.sizeBytes.isNotEmpty()) (e.sizeBytes.toLongOrNull() ?: 0) / 1024 / 1024 else 0
        h.detail.text = "${durSec}s · ${e.format} · ${e.status}" +
            (if (sizeMb > 0) " · ${sizeMb}MB" else "") +
            (if (e.parts.isNotEmpty()) " · ${e.parts.size} part(s)" else "")
        h.itemView.setOnClickListener { onPlay(pos) }
    }
    override fun getItemCount() = items.size
    fun update(n: List<RecordingEntry>) { items = n; notifyDataSetChanged() }
}

class HealthFragment : Fragment() {
    private var deviceId: String = ""
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var empty: TextView? = null
    private val adapter = HealthAdapter(emptyList())
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        recycler?.layoutManager = LinearLayoutManager(context)
        recycler?.adapter = adapter
        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        startPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        recycler = null
    }

    private fun startPolling() {
        pollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val current = DeviceRepo(requireContext()).getSelectedDeviceId()
                if (current.isNotEmpty()) deviceId = current
                if (deviceId.isNotEmpty()) loadHealth()
                delay(30000)
            }
        }
    }

    private suspend fun loadHealth() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchHealth() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchHealth(): List<HealthEntry> {
        val result = mutableListOf<HealthEntry>()
        val cal = java.util.Calendar.getInstance()
        for (i in 0 until 7) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = sdf.format(cal.time)
            val steps = client.get("devices/$deviceId/steps/$date")
            if (steps != null && steps.has("steps")) {
                result.add(HealthEntry(
                    date = date,
                    steps = steps.optInt("steps", 0),
                    goal = steps.optInt("goal", 8000),
                    tsMs = steps.optLong("ts_ms", 0L)
                ))
            }
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return result.sortedByDescending { it.date }
    }
}

class RecordingFragment : Fragment() {
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
    private val adapter = RecordingAdapter(emptyList()) { pos -> playAt(pos) }
    private var pollJob: Job? = null
    private val client = RtdbClient.getInstance()
    private var player: ExoPlayer? = null
    private var seekBarTracking = false
    private var updateJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_recordings, container, false)
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
        }
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
                if (deviceId.isNotEmpty()) loadRecordings()
                delay(30000)
            }
        }
    }

    private suspend fun loadRecordings() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE; empty?.visibility = View.GONE }
        val entries = withContext(Dispatchers.IO) { fetchRecordings() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            if (entries.isEmpty()) { empty?.visibility = View.VISIBLE } else { adapter.update(entries) }
        }
    }

    private suspend fun fetchRecordings(): List<RecordingEntry> {
        val data = client.get("devices/$deviceId/recordings") ?: return emptyList()
        val result = mutableListOf<RecordingEntry>()
        val iter = data.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = data.getJSONObject(k)
                val parts = fetchParts(k)
                result.add(RecordingEntry(
                    id = k,
                    durationSec = v.optString("durationSec", "0"),
                    format = v.optString("format", "mp4"),
                    status = v.optString("status", "unknown"),
                    startedAtMs = v.optLong("startedAtMs", v.optLong("createdAt", 0L)),
                    sizeBytes = v.optString("sizeBytes", "0"),
                    parts = parts
                ))
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.startedAtMs }
    }

    private suspend fun fetchParts(recId: String): List<String> {
        val partsData = client.get("devices/$deviceId/recordings/$recId/parts") ?: return emptyList()
        val out = mutableListOf<String>()
        val iter = partsData.keys()
        while (iter.hasNext()) {
            val pk = iter.next()
            try {
                val p = partsData.optJSONObject(pk) ?: continue
                val d = p.optString("data", "")
                if (d.isNotEmpty()) out.add(d)
            } catch (_: Exception) {}
        }
        return out
    }

    private fun playAt(pos: Int) {
        val entry = adapter.items.getOrNull(pos) ?: return
        stopPlayer()
        val act = requireActivity()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tmp = File(act.cacheDir, "rec_${System.currentTimeMillis()}.${entry.format}")
                if (entry.parts.size == 1) {
                    val decoded = Base64.decode(entry.parts[0], Base64.DEFAULT)
                    FileOutputStream(tmp).use { it.write(decoded) }
                } else if (entry.parts.size > 1) {
                    val fos = FileOutputStream(tmp)
                    for (part in entry.parts) {
                        val decoded = Base64.decode(part, Base64.DEFAULT)
                        fos.write(decoded)
                    }
                    fos.close()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No audio data available", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    player = ExoPlayer.Builder(act).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(tmp)))
                        prepare()
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_READY) {
                                    playbackBar?.visibility = View.VISIBLE
                                    playbackTitle?.text = "Recording ${entry.id.take(8)}…"
                                    playbackSeek?.max = (entry.durationSec.toIntOrNull() ?: 0)
                                    play()
                                    btnPlayPause?.setImageResource(android.R.drawable.ic_media_pause)
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
}