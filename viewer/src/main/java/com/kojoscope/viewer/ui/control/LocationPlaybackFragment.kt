package com.kojoscope.viewer.ui.control

import android.app.AlertDialog
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PlayPoint(
    val lat: Double,
    val lng: Double,
    val ts: Long,
    val accuracy: Double,
    val speed: Double,
    val altitude: Double,
    val source: String = "",
    val isHighAccuracy: Boolean = false
)

class LocationPlaybackFragment : Fragment() {

    private var deviceId: String = ""
    private var map: MapView? = null
    private var progress: ProgressBar? = null
    private var playBtn: Button? = null
    private var countText: TextView? = null
    private var percentText: TextView? = null
    private var speedSpinner: Spinner? = null
    private var accuracySpinner: Spinner? = null
    private var scrubber: SeekBar? = null
    private var playheadTimeDisplay: TextView? = null
    private var stPoints: TextView? = null
    private var stDuration: TextView? = null
    private var stDistance: TextView? = null
    private var stMaxSpeed: TextView? = null
    private var stAvgSpeed: TextView? = null
    private var stAltGain: TextView? = null
    private var pbRange: TextView? = null
    private var pointList: LinearLayout? = null
    private var btnExport: ImageButton? = null
    private var btnLoop: ImageButton? = null
    private var btnFollow: ImageButton? = null
    private var btnEnd: ImageButton? = null
    private var btnDateRange: Button? = null
    private var btnClearDate: ImageButton? = null
    private var btnMapType: ImageButton? = null
    private val client = RtdbClient.getInstance()

    private val timeWindows: List<Pair<Int, Long?>> = listOf(
        R.string.time_filter_1hr to 3_600_000L,
        R.string.time_filter_2hr to 7_200_000L,
        R.string.time_filter_3hr to 10_800_000L,
        R.string.time_filter_4hr to 14_400_000L,
        R.string.time_filter_6hr to 21_600_000L,
        R.string.time_filter_12hr to 43_200_000L,
        R.string.time_filter_24h to 86_400_000L,
        R.string.time_filter_2d to 172_800_000L,
        R.string.time_filter_3d to 259_200_000L,
        R.string.time_filter_4d to 345_600_000L,
        R.string.time_filter_all to null
    )

    private data class AccuracyOption(val labelRes: Int, val gpsOnly: Boolean, val maxAccuracy: Int?)

    private val accuracyOptions = listOf(
        AccuracyOption(R.string.accuracy_filter_gps, true, null),
        AccuracyOption(R.string.accuracy_filter_all, false, null),
        AccuracyOption(R.string.accuracy_filter_10m, false, 10),
        AccuracyOption(R.string.accuracy_filter_20m, false, 20),
        AccuracyOption(R.string.accuracy_filter_30m, false, 30),
        AccuracyOption(R.string.accuracy_filter_50m, false, 50),
        AccuracyOption(R.string.accuracy_filter_100m, false, 100)
    )

    private val gpsSources = setOf("high_accuracy_gps", "gps", "away_tracker", "call_snapshot")

    private fun isGpsPoint(p: PlayPoint): Boolean = p.isHighAccuracy || p.source in gpsSources

    private var points: List<PlayPoint> = emptyList()
    private var filteredPoints: List<PlayPoint> = emptyList()
    private var timeWindowMs: Long? = null
    private var gpsOnly = false
    private var maxAccuracyM: Int? = null
    private var isSatellite = false
    private var playing = false
    private var playbackJob: Job? = null
    private var pollJob: Job? = null
    private var playheadMarker: Marker? = null
    private var waypointMarkers: MutableList<Marker> = mutableListOf()
    private var segFrom = 0
    private var segTo = 1
    private var segStartElapsed = 0L
    private var loopMode = false
    private var autoFollow = true
    private var totalDurationMs: Long = 0L
    private var userPanned = false
    private var pointListExpanded = false
    private var pointListScroll: ScrollView? = null
    private var pointListHeader: TextView? = null
    private var roadMatched = false
    private var roadGeometry: List<GeoPoint> = emptyList()
    private var roadCum = DoubleArray(0)
    private var anchorPos = DoubleArray(0)
    private var anchorTs = LongArray(0)
    private var lastMatchSig = ""
    private var renderSeq = 0
    private var trailOverlays: MutableList<Polyline> = mutableListOf()

    private data class RoadMatch(
        val geometry: List<GeoPoint>,
        val cum: DoubleArray,
        val anchorPos: DoubleArray,
        val anchorTimes: LongArray
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Configuration.getInstance().userAgentValue = "KojoScope/1.0 (Android)"
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
        percentText = view.findViewById(R.id.percentText)
        speedSpinner = view.findViewById(R.id.speedSpinner)
        scrubber = view.findViewById(R.id.scrubber)
        playheadTimeDisplay = view.findViewById(R.id.playheadTimeDisplay)
        accuracySpinner = view.findViewById(R.id.accuracyFilterSpinner)
        stPoints = view.findViewById(R.id.stPoints)
        stDuration = view.findViewById(R.id.stDuration)
        stDistance = view.findViewById(R.id.stDistance)
        stMaxSpeed = view.findViewById(R.id.stMaxSpeed)
        stAvgSpeed = view.findViewById(R.id.stAvgSpeed)
        stAltGain = view.findViewById(R.id.stAltGain)
        pbRange = view.findViewById(R.id.pbRange)
        pointList = view.findViewById(R.id.pointList)
        pointListScroll = view.findViewById(R.id.pointListScroll)
        pointListHeader = view.findViewById(R.id.pointListHeader)
        btnExport = view.findViewById(R.id.btnExport)
        btnLoop = view.findViewById(R.id.btnLoop)
        btnFollow = view.findViewById(R.id.btnFollow)
        btnEnd = view.findViewById(R.id.btnEnd)
        btnDateRange = view.findViewById(R.id.btnDateRange)
        btnClearDate = view.findViewById(R.id.btnClearDate)
        btnMapType = view.findViewById(R.id.btnMapType)

        setupMap()
        val speeds = arrayOf("1x", "2x", "4x", "8x", "16x", "32x", "64x", "128x")
        speedSpinner?.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, speeds)
        speedSpinner?.setSelection(2)

        val accLabels = accuracyOptions.map { getString(it.labelRes) }.toTypedArray()
        accuracySpinner?.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, accLabels)
        accuracySpinner?.setSelection(1)
        accuracySpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val opt = accuracyOptions[position]
                gpsOnly = opt.gpsOnly
                maxAccuracyM = opt.maxAccuracy
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        playBtn?.setOnClickListener { togglePlay() }
        btnEnd?.setOnClickListener { jumpToEnd() }
        btnExport?.setOnClickListener { exportTrail() }
        btnLoop?.setOnClickListener { toggleLoop() }
        btnFollow?.setOnClickListener { toggleFollow() }
        btnMapType?.setOnClickListener { toggleMapType() }
        btnDateRange?.setOnClickListener { showTimeWindowPicker() }
        btnClearDate?.setOnClickListener { clearTimeWindow() }
        updateTimeWindowButton()

        scrubber?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val pts = getDisplayPoints()
                if (fromUser && pts.size >= 2) {
                    val totalSegs = pts.size - 1
                    val targetSeg = (progress / 1000.0 * totalSegs).toInt().coerceIn(0, totalSegs)
                    segFrom = targetSeg
                    segTo = (targetSeg + 1).coerceAtMost(pts.size - 1)
                    updateMarkerTo(pts[segFrom])
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
        val pts = getDisplayPoints()
        if (pts.size < 2) return
        playing = !playing
        if (playing) {
            if (segFrom >= pts.size - 1) { segFrom = 0; segTo = 1; segStartElapsed = SystemClock.elapsedRealtime(); totalDurationMs = 0L }
            segStartElapsed = SystemClock.elapsedRealtime() - totalDurationMs
            startPlaybackLoop()
            playBtn?.text = "\u23F8 Pause"
        } else {
            stopPlaybackLoop()
            playBtn?.text = "\u25B6 Play"
        }
    }

    private fun getDisplayPoints(): List<PlayPoint> = if (filteredPoints.isNotEmpty()) filteredPoints else points

    private fun jumpToEnd() {
        val pts = getDisplayPoints()
        if (pts.size < 2) return
        stopPlaybackLoop()
        playing = false
        segFrom = pts.size - 2
        segTo = pts.size - 1
        segStartElapsed = SystemClock.elapsedRealtime()
        totalDurationMs = pts.last().ts - pts.first().ts
        updateMarkerTo(pts.last())
        playBtn?.text = "\u25B6 Play"
    }

    private fun toggleLoop() {
        loopMode = !loopMode
        btnLoop?.setColorFilter(if (loopMode) android.graphics.Color.parseColor("#ffca28") else android.graphics.Color.parseColor("#8b949e"), android.graphics.PorterDuff.Mode.SRC_IN)
        Toast.makeText(context, if (loopMode) "Loop mode ON" else "Loop mode OFF", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFollow() {
        autoFollow = !autoFollow
        btnFollow?.setColorFilter(if (autoFollow) android.graphics.Color.parseColor("#ffca28") else android.graphics.Color.parseColor("#8b949e"), android.graphics.PorterDuff.Mode.SRC_IN)
        Toast.makeText(context, if (autoFollow) "Auto-follow ON" else "Auto-follow OFF", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMapType() {
        isSatellite = !isSatellite
        val map = map ?: return
        if (isSatellite) {
            map.setTileSource(object : org.osmdroid.tileprovider.tilesource.XYTileSource(
                "KojoSatellite", 0, 20, 256, ".jpg",
                arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
            ) {
                override fun getTileURLString(p: Long): String {
                    val z = MapTileIndex.getZoom(p)
                    val x = MapTileIndex.getX(p)
                    val y = MapTileIndex.getY(p)
                    return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
                }
            })
        } else {
            map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        }
        map.invalidate()
        btnMapType?.setColorFilter(if (isSatellite) android.graphics.Color.parseColor("#ffca28") else android.graphics.Color.parseColor("#8b949e"), android.graphics.PorterDuff.Mode.SRC_IN)
        Toast.makeText(context, if (isSatellite) "Satellite view" else "Map view", Toast.LENGTH_SHORT).show()
    }

    private fun showTimeWindowPicker() {
        val labels = timeWindows.map { getString(it.first) }.toTypedArray()
        val current = timeWindows.indexOfFirst { it.second == timeWindowMs }.coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle("Select time range")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val (_, window) = timeWindows[which]
                timeWindowMs = window
                dialog.dismiss()
        updateTimeWindowButton()

        setPointListHeight()
        pointListHeader?.setOnClickListener {
            pointListExpanded = !pointListExpanded
            setPointListHeight()
        }
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearTimeWindow() {
        timeWindowMs = null
        updateTimeWindowButton()
        applyFilters()
    }

    private fun updateTimeWindowButton() {
        val btn = btnDateRange ?: return
        val label = timeWindows.firstOrNull { it.second == timeWindowMs }?.let { getString(it.first) }
            ?: getString(R.string.time_filter_all)
        btn.text = label
        btnClearDate?.visibility = if (timeWindowMs == null) View.GONE else View.VISIBLE
    }

    private fun applyFilters(showToast: Boolean = true) {
        if (points.isEmpty()) return
        val now = System.currentTimeMillis()
        filteredPoints = points.filter { p ->
            val inWindow = timeWindowMs == null || p.ts >= now - timeWindowMs!!
            val inAccuracy = if (gpsOnly) {
                isGpsPoint(p)
            } else {
                val max = maxAccuracyM
                max == null || (p.accuracy > 0 && p.accuracy <= max)
            }
            inWindow && inAccuracy
        }
        userPanned = false
        renderPlayback()
        if (showToast) {
            Toast.makeText(context, "${filteredPoints.size} of ${points.size} points shown", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportTrail() {
        if (points.isEmpty()) return
        val gpx = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<gpx version=\"1.1\" creator=\"KojoScope\">")
            appendLine("<trk><name>Location Playback</name><trkseg>")
            for (p in points) {
                appendLine("<trkpt lat=\"${p.lat}\" lon=\"${p.lng}\"><ele>${p.altitude}</ele><time>${p.ts}</time></trkpt>")
            }
            appendLine("</trkseg></trk></gpx>")
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GPX Trail", gpx))
        Toast.makeText(context, "GPX trail copied to clipboard (${points.size} points)", Toast.LENGTH_SHORT).show()
    }

    private fun startPlaybackLoop() {
        stopPlaybackLoop()
        val pts = getDisplayPoints()
        playbackJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && playing) {
                val speed = speedMultiplier()
                val elapsed = (SystemClock.elapsedRealtime() - segStartElapsed) * speed
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
                        if (loopMode) {
                            segFrom = 0; segTo = 1; segStartElapsed = SystemClock.elapsedRealtime(); t = 0.0; totalDurationMs = 0L
                            continue
                        } else {
                            playing = false
                            playBtn?.text = "\u25B6 Play"
                            break
                        }
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
        val pts = getDisplayPoints()
        val to = pts[(idx + 1).coerceAtMost(pts.size - 1)]
        val lat = lerp(from.lat, to.lat, frac)
        val lng = lerp(from.lng, to.lng, frac)
        val ts = lerp(from.ts.toDouble(), to.ts.toDouble(), frac).toLong()
        val geo = GeoPoint(lat, lng)
        val target = roadGeoFor(ts, geo)
        playheadMarker?.position = target
        updatePlayheadTime(ts)
        if (autoFollow) panIfNeeded(target)
        val totalPts = pts.size
        val currentPoint = idx + 1
        val percent = if (totalPts > 1) ((currentPoint + frac) / totalPts * 100).toInt() else 0
        countText?.text = "$currentPoint/$totalPts"
        percentText?.text = "$percent%"
        scrubber?.progress = ((currentPoint + frac) / totalPts * 1000).toInt()
        val elapsed = pts[idx].ts - pts.first().ts
        val remaining = pts.last().ts - pts[idx].ts
        pbRange?.text = "${formatDurHms(elapsed)} / ${formatDurHms(pts.last().ts - pts.first().ts)} (${percent}%)"
    }

    private fun updateMarkerTo(p: PlayPoint) {
        playheadMarker?.position = roadGeoFor(p.ts, GeoPoint(p.lat, p.lng))
        updatePlayheadTime(p.ts)
        val pts = getDisplayPoints()
        val idx = pts.indexOf(p)
        val totalPts = pts.size
        val currentPoint = idx + 1
        val percent = if (totalPts > 1) (currentPoint.toDouble() / totalPts * 100).toInt() else 0
        countText?.text = "$currentPoint/$totalPts"
        percentText?.text = "$percent%"
        if (idx >= 0 && pts.size > 1) scrubber?.progress = (idx.toDouble() / (pts.size - 1) * 1000).toInt()
        if (autoFollow) panIfNeeded(GeoPoint(p.lat, p.lng))
        pbRange?.text = "${formatDurHms(p.ts - pts.first().ts)} / ${formatDurHms(pts.last().ts - pts.first().ts)} (${percent}%)"
    }

    private fun panIfNeeded(geo: GeoPoint) {
        if (autoFollow) map?.controller?.animateTo(geo)
    }

    private fun updatePlayheadTime(ts: Long) {
        val tv = playheadTimeDisplay ?: return
        val fmt = java.text.SimpleDateFormat("hh:mm:ss a \u00B7 MMM dd", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }
        tv.text = "\u25CF ${fmt.format(ts)}"
        tv.visibility = View.VISIBLE
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
        m.addMapListener(org.osmdroid.events.DelayedMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent): Boolean {
                userPanned = true
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent): Boolean {
                return false
            }
        }, 100))
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
            applyFilters(showToast = false)
        }
    }

    private fun renderPlayback() {
        val seq = ++renderSeq
        val pts = getDisplayPoints()
        clearWaypoints()
        if (pts.isEmpty()) {
            countText?.text = "No history"
            percentText?.text = "0%"
            pointList?.removeAllViews()
            return
        }
        if (!userPanned) {
            map?.controller?.setCenter(GeoPoint(pts.last().lat, pts.last().lng))
        }

        stopPlaybackLoop()
        playing = false
        playBtn?.text = "\u25B6 Play"
        segFrom = 0
        segTo = (1).coerceAtMost(pts.size - 1)
        segStartElapsed = SystemClock.elapsedRealtime()
        totalDurationMs = 0L

        val totalDist = totalDistance(pts)
        val avgSpeed = if (totalDist > 0 && pts.size > 1) totalDist / ((pts.last().ts - pts.first().ts) / 1000.0) else 0.0
        val maxSpeed = pts.maxOfOrNull { it.speed } ?: 0.0
        val altGain = calcAltitudeGain(pts)

        stPoints?.text = "${pts.size} pts"
        stDuration?.text = formatDurHms(pts.last().ts - pts.first().ts)
        stDistance?.text = formatKm(totalDist)
        stMaxSpeed?.text = String.format("Max %.1f km/h", maxSpeed * 3.6)
        stAvgSpeed?.text = String.format("Avg %.1f km/h", avgSpeed * 3.6)
        stAltGain?.text = String.format("+%.1fm", altGain)

        val first = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(pts.first().ts)
        val last = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi")
        }.format(pts.last().ts)
        pbRange?.text = "$first \u2192 $last"

        drawGradientTrail(pts)
        addWaypointMarker(pts.first(), R.color.success, "Start")
        addWaypointMarker(pts.last(), R.color.danger, "End")
        addPlayheadMarker(pts.first())
        updatePlayheadTime(pts.first().ts)

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
            val spd = if (p.speed > 0) String.format("%.1f km/h", p.speed * 3.6) else "--"
            val alt = if (p.altitude != 0.0) String.format("%.1fm", p.altitude) else "--"
            row.text = buildString {
                append(time)
                append(" \u2022 $spd")
                append(" \u2022 $alt")
                if (p.accuracy > 0) append(" \u2022 \u00B1${p.accuracy.toInt()}m")
            }
            row.setTextColor(resources.getColor(R.color.text_secondary))
            row.textSize = 12f
            row.setPadding(dp(4), dp(2), 0, dp(2))
            val idx = i
            row.setOnClickListener {
                updateMarkerTo(pts[idx])
                if (autoFollow) panIfNeeded(roadGeoFor(pts[idx].ts, GeoPoint(pts[idx].lat, pts[idx].lng)))
            }
            pointList?.addView(row)
        }

        val sig = "${pts.size}|${pts.first().ts}|${pts.last().ts}"
        if (sig != lastMatchSig) {
            lastMatchSig = sig
            roadMatched = false
            roadGeometry = emptyList()
            roadCum = DoubleArray(0)
            userPanned = false
            matchRoadsAsync(pts, seq)
        }
    }

    private fun drawGradientTrail(pts: List<PlayPoint>) {
        removeTrailOverlays()
        if (pts.size < 2) return
        val totalSegs = pts.size - 1
        for (i in 0 until totalSegs) {
            val t = i.toFloat() / totalSegs
            val color = lerpColor(0xFF3FB950.toInt(), 0xFFF85149.toInt(), t)
            val seg = Polyline().apply {
                setPoints(listOf(GeoPoint(pts[i].lat, pts[i].lng), GeoPoint(pts[i + 1].lat, pts[i + 1].lng)))
                this.color = color
                this.width = 5f
            }
            trailOverlays.add(seg)
            map?.overlays?.add(seg)
        }
        map?.invalidate()
    }

    private fun drawRoadTrail() {
        val g = roadGeometry
        removeTrailOverlays()
        if (g.size < 2) return
        val total = g.size - 1
        var i = 0
        while (i < total) {
            val end = (i + 60).coerceAtMost(total)
            val color = lerpColor(0xFF3FB950.toInt(), 0xFFF85149.toInt(), ((i + end) / 2.0 / total).toFloat())
            val seg = Polyline().apply {
                setPoints(g.subList(i, end + 1))
                this.color = color
                this.width = 5f
            }
            trailOverlays.add(seg)
            map?.overlays?.add(seg)
            i = end
        }
        map?.invalidate()
    }

    private fun removeTrailOverlays() {
        trailOverlays.forEach { map?.overlays?.remove(it) }
        trailOverlays.clear()
    }

    private fun setPointListHeight() {
        val h = dp(if (pointListExpanded) 180 else 80)
        pointListScroll?.layoutParams?.height = h
        pointListScroll?.requestLayout()
        pointListHeader?.text = "${if (pointListExpanded) "\u25BE" else "\u25B8"} \uD83D\uDCCD Playback points (tap to ${if (pointListExpanded) "collapse" else "expand"})"
    }

    private fun addPlayheadMarker(p: PlayPoint) {
        playheadMarker?.let { map?.overlays?.remove(it) }
        val pin = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor("#ff0000"))
            setSize(30, 30)
        }
        val mk = Marker(map).apply {
            position = GeoPoint(p.lat, p.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = pin
            infoWindow = null
        }
        playheadMarker = mk
        map?.overlays?.add(mk)
        map?.invalidate()
    }

    private fun addWaypointMarker(p: PlayPoint, colorRes: Int, label: String) {
        val color = resources.getColor(colorRes)
        val pin = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            setSize(24, 24)
        }
        val mk = Marker(map).apply {
            position = GeoPoint(p.lat, p.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = pin
            infoWindow = null
        }
        map?.overlays?.add(mk)
        waypointMarkers.add(mk)
        map?.invalidate()
    }

    private fun clearWaypoints() {
        waypointMarkers.forEach { map?.overlays?.remove(it) }
        waypointMarkers.clear()
        removeTrailOverlays()
        map?.overlays?.remove(playheadMarker)
        playheadMarker = null
        map?.invalidate()
    }

    private fun lerpColor(start: Int, end: Int, t: Float): Int {
        val sr = (start shr 16) and 0xFF
        val sg = (start shr 8) and 0xFF
        val sb = start and 0xFF
        val er = (end shr 16) and 0xFF
        val eg = (end shr 8) and 0xFF
        val eb = end and 0xFF
        val r = (sr + (er - sr) * t).toInt()
        val g = (sg + (eg - sg) * t).toInt()
        val b = (sb + (eb - sb) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun calcAltitudeGain(pts: List<PlayPoint>): Double {
        var gain = 0.0
        for (i in 1 until pts.size) {
            val diff = pts[i].altitude - pts[i - 1].altitude
            if (diff > 0) gain += diff
        }
        return gain
    }

    private fun countInDay(pts: List<PlayPoint>, day: String, fmt: java.text.SimpleDateFormat): Int {
        return pts.count { fmt.format(it.ts) == day }
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

    private fun matchRoadsAsync(pts: List<PlayPoint>, seq: Int) {
        if (pts.size < 2) return
        CoroutineScope(Dispatchers.Main).launch {
            val matched = matchToRoads(pts)
            if (seq != renderSeq) return@launch
            if (matched == null) {
                roadMatched = false
                return@launch
            }
            roadMatched = true
            roadGeometry = matched.geometry
            roadCum = matched.cum
            anchorPos = matched.anchorPos
            anchorTs = matched.anchorTimes
            drawRoadTrail()
            if (!playing) {
                val idx = segFrom.coerceIn(0, pts.size - 1)
                playheadMarker?.position = roadGeoFor(pts[idx].ts, GeoPoint(pts[idx].lat, pts[idx].lng))
            }
        }
    }

    private suspend fun matchToRoads(pts: List<PlayPoint>): RoadMatch? = withContext(Dispatchers.IO) {
        try {
            val anchors = if (pts.size > 100) decimate(pts, 100) else pts
            val sb = StringBuilder()
            for ((i, p) in anchors.withIndex()) {
                if (i > 0) sb.append(';')
                sb.append(p.lng).append(',').append(p.lat)
            }
            val url = URL("https://router.project-osrm.org/route/v1/driving/$sb?overview=full&geometries=geojson")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "KojoScope/1.0 (Android)")
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                if (conn.responseCode != 200) return@withContext null
                val root = JSONObject(conn.inputStream.bufferedReader().readText())
                if (!root.optString("code").equals("Ok", true)) return@withContext null
                val routes = root.optJSONArray("routes") ?: return@withContext null
                val geometry = routes.getJSONObject(0).optJSONObject("geometry") ?: return@withContext null
                val coords = geometry.getJSONArray("coordinates")
                val geoPts = mutableListOf<GeoPoint>()
                for (i in 0 until coords.length()) {
                    val c = coords.getJSONArray(i)
                    geoPts.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
                }
                if (geoPts.size < 2) return@withContext null
                val cum = DoubleArray(geoPts.size)
                for (i in 1 until geoPts.size) {
                    cum[i] = cum[i - 1] + haversine(geoPts[i - 1].latitude, geoPts[i - 1].longitude, geoPts[i].latitude, geoPts[i].longitude)
                }
                val n = anchors.size
                val aPos = DoubleArray(n) { -1.0 }
                val aTs = LongArray(n)
                val waypoints = root.optJSONArray("waypoints") ?: JSONArray()
                for (i in 0 until n) {
                    aTs[i] = anchors[i].ts
                    if (i >= waypoints.length()) continue
                    val w = waypoints.optJSONObject(i) ?: continue
                    val loc = w.optJSONArray("location") ?: continue
                    val idx = nearestGeometryIndex(geoPts, loc.getDouble(1), loc.getDouble(0))
                    aPos[i] = cum[idx]
                }
                RoadMatch(geoPts, cum, aPos, aTs)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun nearestGeometryIndex(geoPts: List<GeoPoint>, lat: Double, lng: Double): Int {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in geoPts.indices) {
            val d = haversine(geoPts[i].latitude, geoPts[i].longitude, lat, lng)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun decimate(pts: List<PlayPoint>, max: Int): List<PlayPoint> {
        if (pts.size <= max) return pts
        val out = mutableListOf<PlayPoint>()
        for (i in 0 until max) {
            val idx = (i.toDouble() / (max - 1) * (pts.size - 1)).toInt()
            out.add(pts[idx])
        }
        return out
    }

    private fun roadGeoFor(ts: Long, fallback: GeoPoint): GeoPoint {
        if (!roadMatched || roadGeometry.isEmpty()) return fallback
        val pos = roadPositionAt(ts)
        if (pos < 0) return fallback
        return geoAtArc(pos)
    }

    private fun roadPositionAt(ts: Long): Double {
        val n = anchorTs.size
        if (n < 2) return -1.0
        if (ts <= anchorTs[0]) return if (anchorPos[0] >= 0) anchorPos[0] else -1.0
        if (ts >= anchorTs[n - 1]) return if (anchorPos[n - 1] >= 0) anchorPos[n - 1] else -1.0
        var lo = 0
        var hi = n - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (anchorTs[mid] <= ts) lo = mid else hi = mid
        }
        if (anchorPos[lo] < 0 || anchorPos[hi] < 0) return -1.0
        val span = anchorTs[hi] - anchorTs[lo]
        if (span <= 0) return anchorPos[lo]
        val t = (ts - anchorTs[lo]).toDouble() / span
        return anchorPos[lo] + t * (anchorPos[hi] - anchorPos[lo])
    }

    private fun geoAtArc(pos: Double): GeoPoint {
        val cum = roadCum
        val g = roadGeometry
        if (g.isEmpty()) return GeoPoint(0.0, 0.0)
        if (cum.isEmpty()) return g[0]
        var lo = 0
        var hi = cum.size - 1
        if (pos <= cum[lo]) return g[lo]
        if (pos >= cum[hi]) return g[hi]
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (cum[mid] <= pos) lo = mid else hi = mid
        }
        val t = if (cum[hi] > cum[lo]) (pos - cum[lo]) / (cum[hi] - cum[lo]) else 0.0
        val a = g[lo]
        val b = g[hi]
        return GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
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
                            speed = v.optDouble("speed", 0.0),
                            altitude = v.optDouble("altitude", 0.0),
                            source = v.optString("source", ""),
                            isHighAccuracy = v.optBoolean("isHighAccuracy", false)
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
        clearWaypoints()
        map?.onDetach()
        map = null
    }
}