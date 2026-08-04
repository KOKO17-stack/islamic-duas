package com.kojoscope.viewer.ui.home

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LiveFragment : Fragment() {

    private var deviceId: String = ""
    private var map: MapView? = null
    private var marker: org.osmdroid.views.overlay.Marker? = null
    private var accuracyPoly: Polygon? = null
    private var locationTrail = mutableListOf<GeoPoint>()

    private lateinit var statusDot: TextView
    private lateinit var updatedVal: TextView
    private lateinit var batText: TextView
    private lateinit var storageText: TextView
    private lateinit var stepsText: TextView
    private lateinit var wifiText: TextView
    private lateinit var signalText: TextView
    private lateinit var fgsText: TextView
    private lateinit var activeAppVal: TextView
    private lateinit var networkVal: TextView
    private lateinit var latVal: TextView
    private lateinit var lngVal: TextView
    private lateinit var accVal: TextView
    private lateinit var spdVal: TextView
    private lateinit var seenVal: TextView
    private lateinit var coordBox: TextView

    private var locPollJob: Job? = null
    private var metaPollJob: Job? = null
    private val client = RtdbClient.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osm_preferences", Context.MODE_PRIVATE)
        )
        return inflater.inflate(R.layout.fragment_live, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusDot = view.findViewById(R.id.statusDot)
        updatedVal = view.findViewById(R.id.updatedVal)
        batText = view.findViewById(R.id.batText)
        storageText = view.findViewById(R.id.storageText)
        stepsText = view.findViewById(R.id.stepsText)
        wifiText = view.findViewById(R.id.wifiText)
        signalText = view.findViewById(R.id.signalText)
        fgsText = view.findViewById(R.id.fgsText)
        activeAppVal = view.findViewById(R.id.activeAppVal)
        networkVal = view.findViewById(R.id.networkVal)
        latVal = view.findViewById(R.id.latVal)
        lngVal = view.findViewById(R.id.lngVal)
        accVal = view.findViewById(R.id.accVal)
        spdVal = view.findViewById(R.id.spdVal)
        seenVal = view.findViewById(R.id.seenVal)
        coordBox = view.findViewById(R.id.coordBox)
        map = view.findViewById(R.id.map)

        setupMap()
        startPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locPollJob?.cancel()
        metaPollJob?.cancel()
        map?.onDetach()
        map = null
    }

    private fun setupMap() {
        val m = map ?: return
        m.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        m.setMultiTouchControls(true)
        m.setBuiltInZoomControls(true)
        m.controller.setZoom(15.0)
    }

    private fun startPolling() {
        val repo = DeviceRepo(requireContext())
        locPollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val current = repo.getSelectedDeviceId()
                if (current.isNotEmpty() && current != deviceId) deviceId = current
                if (deviceId.isNotEmpty()) fetchLocation()
                delay(3000)
            }
        }
        metaPollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val current = repo.getSelectedDeviceId()
                if (current.isNotEmpty() && current != deviceId) deviceId = current
                if (deviceId.isNotEmpty()) {
                    fetchMetrics()
                    fetchFgs()
                    fetchActiveApp()
                }
                delay(30000)
            }
        }
    }

    private suspend fun fetchLocation() {
        val loc = withContext(Dispatchers.IO) {
            client.get("devices/$deviceId/location/latest")
        } ?: return
        if (!loc.has("lat")) return
        val lat = loc.optDouble("lat", 0.0)
        val lng = loc.optDouble("lng", 0.0)
        if (lat == 0.0 && lng == 0.0) return

        val point = GeoPoint(lat, lng)
        val accuracy = if (loc.has("accuracy")) loc.optDouble("accuracy") else 0.0
        val speed = if (loc.has("speed")) loc.optDouble("speed") else -1.0
        val ts = if (loc.has("ts_ms")) loc.optLong("ts_ms") else 0L
        val isHighAcc = loc.optBoolean("isHighAccuracy", false)

        withContext(Dispatchers.Main) {
            latVal.text = String.format("%.6f°", lat)
            lngVal.text = String.format("%.6f°", lng)
            accVal.text = if (accuracy > 0) "${accuracy.toInt()}m" else "--"
            spdVal.text = if (speed >= 0) String.format("%.1f km/h", speed * 3.6) else "--"
            seenVal.text = if (ts > 0) pkt(ts) else "--"
            coordBox.text = String.format("%.6f, %.6f", lat, lng)

            ensureMarker()
            marker?.position = point
            val pinColor = if (isHighAcc) Color.parseColor("#3fb950") else Color.parseColor("#ffca28")
            setMarkerColor(pinColor)

            if (accuracy > 0) {
                val circlePts = Polygon.pointsAsCircle(point, accuracy)
                val poly = Polygon()
                poly.points = circlePts
                val ringColor = if (isHighAcc) Color.parseColor("#3fb950") else Color.parseColor("#ffca28")
                poly.fillColor = (0x30 shl 24) or (ringColor and 0x00FFFFFF)
                poly.strokeColor = ringColor
                poly.strokeWidth = 2f
                accuracyPoly?.let { map?.overlayManager?.remove(it) }
                accuracyPoly = poly
                map?.overlays?.add(poly)
            }

            addTrailPoint(point)
            redrawTrail()

            val firstFix = marker?.position == null
            if (firstFix || locationTrail.size <= 1) {
                map?.controller?.animateTo(point)
            }
        }
    }

    private suspend fun fetchMetrics() {
        val m = withContext(Dispatchers.IO) {
            client.get("devices/$deviceId/metrics/latest")
        } ?: return
        withContext(Dispatchers.Main) {
            val bat = m.optInt("batteryPct")
            val charging = m.optBoolean("isCharging")
            batText.text = buildString {
                append("\uD83D\uDD0B ")
                if (bat > 0) { append(bat).append("%"); if (charging) append(" ⚡") } else append("--")
            }
            val freeGb = if (m.has("storageFreeGb")) m.optDouble("storageFreeGb") else 0.0
            storageText.text = if (freeGb > 0) "\uD83D\uDCBE ${String.format("%.1f", freeGb)}GB free" else "\uD83D\uDCBE --"
            val steps = if (m.has("stepsToday")) m.optInt("stepsToday") else 0
            val goal = if (m.has("stepsGoal")) m.optInt("stepsGoal") else 8000
            stepsText.text = if (steps > 0) "\uD83D\uDC5F ${steps}/${goal}" else "\uD83D\uDC5F --/--"
            val ssid = if (m.has("wifiSsid")) m.optString("wifiSsid", "") else ""
            wifiText.text = if (ssid.isNotEmpty()) "\uD83D\uDCE1 $ssid" else "\uD83D\uDCE1 Mobile"
            val rssi = when {
                m.has("signalStrengthDbm") -> m.optInt("signalStrengthDbm")
                m.has("signalStrength") -> m.optInt("signalStrength")
                m.has("rssi") -> m.optInt("rssi")
                m.has("wifiRssi") -> m.optInt("wifiRssi")
                else -> -100
            }
            val ntype = if (m.has("networkType")) m.optString("networkType", "--") else "--"
            val bars = if (rssi > -60) 4 else if (rssi > -70) 3 else if (rssi > -80) 2 else if (rssi > -90) 1 else 0
            val barChars = StringBuilder()
            for (i in 0 until 4) barChars.append(if (i < bars) "▆" else "▁")
            signalText.text = "$barChars $ntype"
            networkVal.text = buildList<String> {
                if (m.has("simCountryIso")) { val iso = m.optString("simCountryIso", "").uppercase(); if (iso.isNotEmpty()) add(iso) }
                if (m.has("networkOperatorName")) { val v = m.optString("networkOperatorName", ""); if (v.isNotEmpty()) add(v) }
                if (m.has("networkOperator")) { val v = m.optString("networkOperator", ""); if (v.isNotEmpty()) add(v) }
                if (m.has("networkTypeDetail")) { val v = m.optString("networkTypeDetail", ""); if (v.isNotEmpty()) add(v) }
                if (isEmpty()) add("--")
            }.joinToString(" ")
            val ts = if (m.has("ts_ms")) m.optLong("ts_ms") else 0L
            updatedVal.text = if (ts > 0) "Updated ${pkt(ts)} (phone)" else "Updated --"
        }
    }

    private suspend fun fetchFgs() {
        val beat = withContext(Dispatchers.IO) {
            client.get("devices/$deviceId/metrics/fgsAlive")
        } ?: return
        val beatTs = if (beat.has("ts_ms")) beat.optLong("ts_ms") else 0L
        withContext(Dispatchers.Main) {
            if (beatTs == 0L) {
                fgsText.text = "\u2699 FGS --"
                fgsText.setTextColor(Color.parseColor("#8b949e"))
            } else {
                val ageMin = ((System.currentTimeMillis() - beatTs) / 60000).toInt()
                if (ageMin <= 3) {
                    fgsText.text = "\u2699 FGS alive"
                    fgsText.setTextColor(Color.parseColor("#3fb950"))
                } else {
                    fgsText.text = "\u2699 FGS dead ${ageMin}m"
                    fgsText.setTextColor(Color.parseColor("#f85149"))
                }
            }
        }
    }

    private suspend fun fetchActiveApp() {
        val aa = withContext(Dispatchers.IO) {
            client.get("devices/$deviceId/activeApp")
        } ?: return
        withContext(Dispatchers.Main) {
            if (aa.has("appName")) {
                val appName = aa.optString("appName", "")
                val lastUsed = if (aa.has("lastUsedMs")) aa.optLong("lastUsedMs") else 0L
                val since = if (lastUsed > 0) " · ${pkt(lastUsed)}" else ""
                activeAppVal.text = "$appName$since"
            } else {
                activeAppVal.text = "--"
            }
        }
    }

    private fun ensureMarker() {
        val m = map ?: return
        if (marker == null) {
            val pin = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#ffca28"))
                setSize(26, 26)
            }
            marker = org.osmdroid.views.overlay.Marker(m).apply {
                icon = pin
                setAnchor(0.5f, 0.5f)
            }
            m.overlays.add(marker)
        }
    }

    private fun setMarkerColor(color: Int) {
        val mk = marker ?: return
        val pin = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            setSize(26, 26)
        }
        mk.icon = pin
    }

    private fun addTrailPoint(p: GeoPoint) {
        locationTrail.add(p)
        if (locationTrail.size > 500) locationTrail.removeAt(0)
    }

    private fun redrawTrail() {
        val m = map ?: return
        m.overlayManager.forEach { if (it is Polyline) m.overlayManager.remove(it) }
        if (locationTrail.size < 2) return
        val colors = listOf(
            Color.parseColor("#1a3a5c"), Color.parseColor("#2a5a7c"),
            Color.parseColor("#3a7a9c"), Color.parseColor("#4a9abc"),
            Color.parseColor("#5ababc"), Color.parseColor("#7acc7a"),
            Color.parseColor("#aadd55"), Color.parseColor("#ddee44"),
            Color.parseColor("#ffca28"), Color.parseColor("#ff8c00")
        )
        val segs = minOf(colors.size, locationTrail.size - 1)
        val chunkSize = maxOf(1, locationTrail.size / segs)
        for (i in 0 until locationTrail.size - 1 step chunkSize) {
            val end = minOf(i + chunkSize + 1, locationTrail.size)
            if (end - i < 2) continue
            val ci = minOf(i / chunkSize, colors.size - 1)
            val seg = Polyline().apply {
                width = 3.0f
                color = colors[ci]
                for (j in i until end) {
                    addPoint(locationTrail[j])
                }
            }
            m.overlays.add(seg)
        }
    }

    private fun pkt(ts: Long): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Karachi")
        return sdf.format(ts)
    }

    companion object {
        fun newInstance(id: String): LiveFragment {
            val f = LiveFragment()
            f.deviceId = id
            return f
        }
    }
}