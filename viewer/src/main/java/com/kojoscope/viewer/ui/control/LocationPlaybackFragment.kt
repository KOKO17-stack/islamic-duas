package com.kojoscope.viewer.ui.control

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
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
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PlayPoint(val lat: Double, val lng: Double, val ts: Long, val accuracy: Double, val speed: Double)

class LocationPlaybackFragment : Fragment() {

    private var deviceId: String = ""
    private var map: MapView? = null
    private var progress: ProgressBar? = null
    private var playBtn: Button? = null
    private var countText: TextView? = null
    private var speedSpinner: Spinner? = null
    private var scrubber: SeekBar? = null
    private var stPoints: TextView? = null
    private var stDuration: TextView? = null
    private var stDistance: TextView? = null
    private var pbRange: TextView? = null
    private var pointList: LinearLayout? = null
    private val client = RtdbClient.getInstance()

    private var points: List<PlayPoint> = emptyList()
    private var playing = false
    private var playbackJob: Job? = null
    private var pollJob: Job? = null
    private var marker: Marker? = null
    private var trail: Polyline? = null
    private var segFrom = 0
    private var segTo = 1
    private var segStartElapsed = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osm_preferences", Context.MODE_PRIVATE)
        )
        return inflater.inflate(R.layout.fragment_location_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        map = view.findViewById(R.id.map)
        progress = view.findViewById(R.id.progress)
        playBtn = view.findViewById(R.id.playBtn)
        countText = view.findViewById(R.id.countText)
        speedSpinner = view.findViewById(R.id.speedSpinner)
        scrubber = view.findViewById(R.id.scrubber)
        stPoints = view.findViewById(R.id.stPoints)
        stDuration = view.findViewById(R.id.stDuration)
        stDistance = view.findViewById(R.id.stDistance)
        pbRange = view.findViewById(R.id.pbRange)
        pointList = view.findViewById(R.id.pointList)

        setupMap()
        val speeds = arrayOf("1x", "2x", "4x", "8x", "16x", "32x")
        speedSpinner?.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, speeds)
        speedSpinner?.setSelection(2)

        playBtn?.setOnClickListener { togglePlay() }
        scrubber?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && points.size >= 2) {
                    val totalSegs = points.size - 1
                    val targetSeg = (progress / 1000.0 * totalSegs).toInt().coerceIn(0, totalSegs)
                    segFrom = targetSeg
                    segTo = (targetSeg + 1).coerceAtMost(points.size - 1)
                    updateMarkerTo(points[segFrom])
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()
        CoroutineScope(Dispatchers.Main).launch { loadHistory() }
        startPolling()
    }

    private fun togglePlay() {
        if (points.size < 2) return
        playing = !playing
        if (playing) {
            if (segFrom >= points.size - 1) { segFrom = 0; segTo = 1 }
            segStartElapsed = SystemClock.elapsedRealtime()
            startPlaybackLoop()
            playBtn?.text = "\u23F8 Pause"
        } else {
            stopPlaybackLoop()
            playBtn?.text = "\u25B6 Play"
        }
    }

    private fun startPlaybackLoop() {
        stopPlaybackLoop()
        playbackJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && playing) {
                val speed = speedMultiplier()
                val elapsed = (SystemClock.elapsedRealtime() - segStartElapsed) * speed
                val pts = points
                if (pts.size < 2) break
                var gap = pts[segTo].ts - pts[segFrom].ts
                if (gap <= 0) gap = 1000
                if (gap > 3600000) gap = 3600000
                var t = elapsed.toDouble() / gap
                if (t >= 1) {
                    segFrom = segTo
                    segTo = (segTo + 1).coerceAtMost(pts.size - 1)
                    if (segFrom >= pts.size - 1) {
                        updatePlaybackDisplay(pts.last(), pts.size - 1, 1.0)
                        segFrom = 0; segTo = 1; segStartElapsed = SystemClock.elapsedRealtime(); t = 0.0
                        continue
                    }
                    segStartElapsed = SystemClock.elapsedRealtime()
                    t = 0.0
                }
                updatePlaybackDisplay(pts[segFrom], segFrom, t)
                delay(50)
            }
        }
    }

    private fun updatePlaybackDisplay(from: PlayPoint, idx: Int, frac: Double) {
        val pts = points
        val to = pts[(idx + 1).coerceAtMost(pts.size - 1)]
        val lat = lerp(from.lat, to.lat, frac)
        val lng = lerp(from.lng, to.lng, frac)
        val geo = GeoPoint(lat, lng)
        marker?.position = geo
        countText?.text = "${idx + 1}/${pts.size}"
        scrubber?.progress = ((idx + frac) / (pts.size - 1) * 1000).toInt()
        panIfNeeded(geo)
    }

    private fun updateMarkerTo(p: PlayPoint) {
        marker?.position = GeoPoint(p.lat, p.lng)
        countText?.text = "${points.indexOf(p) + 1}/${points.size}"
        val i = points.indexOf(p)
        if (i >= 0 && points.size > 1) scrubber?.progress = (i.toDouble() / (points.size - 1) * 1000).toInt()
        panIfNeeded(GeoPoint(p.lat, p.lng))
    }

    private fun panIfNeeded(geo: GeoPoint) {
        map?.controller?.animateTo(geo)
    }

    private fun speedMultiplier(): Long {
        val s = speedSpinner?.selectedItem?.toString()?.replace("x", "")?.toIntOrNull() ?: 4
        return s.toLong()
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun setupMap() {
        val m = map ?: return
        m.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        m.setMultiTouchControls(true)
        m.setBuiltInZoomControls(true)
        m.controller.setZoom(14.0)
    }

    private fun startPolling() {
        pollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val current = DeviceRepo(requireContext()).getSelectedDeviceId()
                if (current.isNotEmpty()) deviceId = current
                if (deviceId.isNotEmpty()) loadHistory()
                delay(120000)
            }
        }
    }

    private suspend fun loadHistory() {
        withContext(Dispatchers.Main) { progress?.visibility = View.VISIBLE }
        val pts = withContext(Dispatchers.IO) { fetchHistory() }
        withContext(Dispatchers.Main) {
            progress?.visibility = View.GONE
            points = pts
            renderPlayback()
        }
    }

    private fun renderPlayback() {
        val pts = points
        if (pts.isEmpty()) {
            countText?.text = "No history"
            pointList?.removeAllViews()
            return
        }
        stPoints?.text = "${pts.size} pts"
        stDuration?.text = formatDurHms(pts.last().ts - pts.first().ts)
        stDistance?.text = formatKm(totalDistance(pts))
        val first = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(pts.first().ts)
        val last = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(pts.last().ts)
        pbRange?.text = "$first \u2192 $last"

        drawTrail(pts)
        addMarker(pts.first())

        pointList?.removeAllViews()
        val dayFmt = java.text.SimpleDateFormat("MMM dd, EEE", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }
        var curDay = ""
        for (i in pts.indices) {
            val p = pts[i]
            val day = dayFmt.format(p.ts)
            if (day != curDay) {
                curDay = day
                val h = TextView(requireContext())
                h.text = "$day (${countInDay(pts, day, dayFmt)})"
                h.setTextColor(resources.getColor(R.color.accent))
                h.textSize = 12f
                h.setPadding(0, dp(8), 0, dp(2))
                pointList?.addView(h)
            }
            val row = TextView(requireContext())
            val time = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
            }.format(p.ts)
            row.text = buildString {
                append(time)
                if (p.speed > 0) append(" · ${String.format("%.1f", p.speed * 3.6)}km/h")
                if (p.accuracy > 0) append(" · ±${p.accuracy.toInt()}m")
            }
            row.setTextColor(resources.getColor(R.color.text_secondary))
            row.textSize = 12f
            row.setPadding(dp(4), dp(2), 0, dp(2))
            pointList?.addView(row)
        }
    }

    private fun countInDay(pts: List<PlayPoint>, day: String, fmt: java.text.SimpleDateFormat): Int {
        return pts.count { fmt.format(it.ts) == day }
    }

    private fun drawTrail(pts: List<PlayPoint>) {
        map?.overlays?.removeAll { it is Polyline && it === trail }
        val poly = Polyline().apply {
            setPoints(pts.map { GeoPoint(it.lat, it.lng) })
            color = android.graphics.Color.parseColor("#ffca28")
            width = 6f
        }
        trail = poly
        map?.overlays?.add(poly)
        map?.invalidate()
    }

    private fun addMarker(p: PlayPoint) {
        marker?.let { map?.overlays?.remove(it) }
        val mk = Marker(map).apply {
            position = GeoPoint(p.lat, p.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
        }
        marker = mk
        map?.overlays?.add(mk)
        map?.controller?.setCenter(GeoPoint(p.lat, p.lng))
        map?.invalidate()
    }

    private suspend fun fetchHistory(): List<PlayPoint> {
        val history = client.get("devices/$deviceId/location/history") ?: return emptyList()
        val pts = mutableListOf<PlayPoint>()
        collectLeafs(history, pts)
        val dedup = LinkedHashMap<String, PlayPoint>()
        for (p in pts.sortedBy { it.ts }) {
            val key = "${p.lat.toDouble().toString()}|${p.lng.toDouble().toString()}"
            dedup[key] = p
        }
        return dedup.values.sortedBy { it.ts }
    }

    private fun collectLeafs(obj: JSONObject, out: MutableList<PlayPoint>) {
        val iter = obj.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            try {
                val v = obj.opt(k)
                if (v is JSONObject) {
                    if (v.has("lat") && v.has("lng")) {
                        val ts = v.optLong("ts_ms", k.toLongOrNull() ?: 0L)
                        out.add(PlayPoint(
                            lat = v.getDouble("lat"),
                            lng = v.getDouble("lng"),
                            ts = ts,
                            accuracy = v.optDouble("accuracy", 0.0),
                            speed = v.optDouble("speed", 0.0)
                        ))
                    } else {
                        collectLeafs(v, out)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun totalDistance(pts: List<PlayPoint>): Double {
        var d = 0.0
        for (i in 1 until pts.size) {
            d += haversine(pts[i - 1].lat, pts[i - 1].lng, pts[i].lat, pts[i].lng)
        }
        return d
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun formatKm(m: Double): String {
        return if (m >= 1000) String.format("%.2f km", m / 1000) else "${m.toInt()} m"
    }

    private fun formatDurHms(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s % 60}s"
            else -> "${s}s"
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun stopPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playing = false
        stopPlaybackLoop()
        pollJob?.cancel()
        map?.overlays?.clear()
        map?.onDetach()
        map = null
    }
}