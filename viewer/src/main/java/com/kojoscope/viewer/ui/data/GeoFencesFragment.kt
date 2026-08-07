package com.kojoscope.viewer.ui.data

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeofenceEntry(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radius: Int,
    var enabled: Boolean
)

class GeoFencesFragment : Fragment() {

    private var deviceId: String = ""
    private var homeInfo: TextView? = null
    private var homeRadiusSeek: SeekBar? = null
    private var homeRadiusVal: TextView? = null
    private var btnSetHome: TextView? = null
    private var btnRemoveHome: TextView? = null
    private var btnAddGeofence: TextView? = null
    private var geofenceList: LinearLayout? = null
    private var alertList: LinearLayout? = null
    private val client = RtdbClient.getInstance()
    private var homeConfig: JSONObject? = null
    private val geofences = mutableListOf<GeofenceEntry>()
    private var alertHistory = mutableListOf<String>()

    companion object {
        private const val PREFS_GEOFENCES = "geofences"
        private const val PREFS_ALERTS = "geofence_alerts"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_geo_fences, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeInfo = view.findViewById(R.id.homeInfo)
        homeRadiusSeek = view.findViewById(R.id.homeRadiusSeek)
        homeRadiusVal = view.findViewById(R.id.homeRadiusVal)
        btnSetHome = view.findViewById(R.id.btnSetHome)
        btnRemoveHome = view.findViewById(R.id.btnRemoveHome)
        btnAddGeofence = view.findViewById(R.id.btnAddGeofence)
        geofenceList = view.findViewById(R.id.geofenceList)
        alertList = view.findViewById(R.id.alertList)

        deviceId = DeviceRepo(requireContext()).getSelectedDeviceId()

        homeRadiusSeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
                val radius = 100 + progress * 100
                homeRadiusVal?.text = "${radius}m"
                if (fromUser) updateHomeRadius(radius)
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        btnSetHome?.setOnClickListener { setHomeFromLive() }
        btnRemoveHome?.setOnClickListener { removeHome() }
        btnAddGeofence?.setOnClickListener { showAddGeofenceDialog() }

        loadGeofences()
        loadAlertHistory()
        loadHomeConfig()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        homeInfo = null
        homeRadiusSeek = null
        homeRadiusVal = null
        btnSetHome = null
        btnRemoveHome = null
        btnAddGeofence = null
        geofenceList = null
        alertList = null
    }

    private fun prefs() = requireContext().getSharedPreferences("$PREFS_GEOFENCES-$deviceId", Context.MODE_PRIVATE)

    private fun loadGeofences() {
        geofences.clear()
        val json = prefs().getString("list", "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                geofences.add(GeofenceEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    lat = o.getDouble("lat"),
                    lng = o.getDouble("lng"),
                    radius = o.getInt("radius"),
                    enabled = o.getBoolean("enabled")
                ))
            }
        } catch (_: Exception) {}
        renderGeofences()
    }

    private fun saveGeofences() {
        val arr = JSONArray()
        for (g in geofences) {
            val o = JSONObject()
            o.put("id", g.id)
            o.put("name", g.name)
            o.put("lat", g.lat)
            o.put("lng", g.lng)
            o.put("radius", g.radius)
            o.put("enabled", g.enabled)
            arr.put(o)
        }
        prefs().edit().putString("list", arr.toString()).apply()
    }

    private fun loadAlertHistory() {
        alertHistory.clear()
        val raw = prefs().getString("alerts", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                alertHistory.add(arr.getString(i))
            }
        } catch (_: Exception) {}
    }

    private fun saveAlert(entry: String) {
        alertHistory.add(0, entry)
        if (alertHistory.size > 50) alertHistory = alertHistory.take(50).toMutableList()
        val arr = JSONArray()
        for (s in alertHistory) arr.put(s)
        prefs().edit().putString("alerts", arr.toString()).apply()
    }

    private fun renderGeofences() {
        geofenceList?.removeAllViews()
        if (geofences.isEmpty()) {
            geofenceList?.addView(emptyNote("No geofences. Tap + Add to create one."))
            return
        }
        val inflater = LayoutInflater.from(requireContext())
        for (g in geofences) {
            val row = inflater.inflate(R.layout.item_geofence, geofenceList, false)
            row.findViewById<TextView>(R.id.gfName).text = g.name
            row.findViewById<TextView>(R.id.gfDetail).text =
                "%.4f, %.4f · ${g.radius}m radius".format(g.lat, g.lng)
            val toggle = row.findViewById<Switch>(R.id.gfToggle)
            toggle.isChecked = g.enabled
            toggle.setOnCheckedChangeListener { _, checked ->
                g.enabled = checked
                saveGeofences()
            }
            row.findViewById<ImageButton>(R.id.gfDelete).setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Geofence")
                    .setMessage("Delete \"${g.name}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        geofences.remove(g)
                        saveGeofences()
                        renderGeofences()
                        Toast.makeText(context, "Geofence deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            geofenceList?.addView(row)
        }
    }

    private fun renderAlerts() {
        alertList?.removeAllViews()
        if (alertHistory.isEmpty()) {
            alertList?.addView(emptyNote("No alerts yet"))
            return
        }
        val inflater = LayoutInflater.from(requireContext())
        for (entry in alertHistory.take(20)) {
            val tv = inflater.inflate(R.layout.item_list, alertList, false) as LinearLayout
            tv.findViewById<TextView>(R.id.itemName).text = entry
            tv.findViewById<TextView>(R.id.itemTime).text = ""
            tv.findViewById<TextView>(R.id.itemDetail).text = ""
            alertList?.addView(tv)
        }
    }

    private fun loadHomeConfig() {
        if (deviceId.isEmpty()) return
        CoroutineScope(Dispatchers.Main).launch {
            val data = withContext(Dispatchers.IO) {
                client.get("devices/$deviceId/config/home")
            }
            homeConfig = data
            renderHome()
        }
    }

    private fun renderHome() {
        val cfg = homeConfig
        if (cfg == null || cfg.length() == 0) {
            homeInfo?.text = "No home location set"
            homeRadiusSeek?.progress = 9
            homeRadiusVal?.text = "1000m"
            return
        }
        val lat = cfg.optDouble("lat", 0.0)
        val lng = cfg.optDouble("lng", 0.0)
        val radius = cfg.optInt("radiusM", 1000)
        val setAt = cfg.optLong("setAt", 0L)
        val label = cfg.optString("label", "Home")
        val dateStr = if (setAt > 0) {
            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(setAt))
        } else "unknown date"
        homeInfo?.text = "$label\n%.4f, %.4f · Set: %s".format(lat, lng, dateStr)
        val progress = ((radius - 100) / 100).coerceIn(0, 29)
        homeRadiusSeek?.progress = progress
        homeRadiusVal?.text = "${radius}m"
    }

    private fun setHomeFromLive() {
        if (deviceId.isEmpty()) return
        CoroutineScope(Dispatchers.Main).launch {
            val live = withContext(Dispatchers.IO) {
                client.get("devices/$deviceId/location/latest")
            }
            if (live == null) {
                Toast.makeText(context, "No live location available", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val lat = live.optDouble("lat", 0.0)
            val lng = live.optDouble("lng", 0.0)
            val radius = 100 + (homeRadiusSeek?.progress ?: 9) * 100
            val body = JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
                put("radiusM", radius)
                put("label", "Home")
                put("setAt", System.currentTimeMillis())
                put("accuracy", live.optDouble("accuracy", 0.0))
            }
            val ok = withContext(Dispatchers.IO) {
                client.put("devices/$deviceId/config/home", body)
            }
            if (ok) {
                homeConfig = body
                renderHome()
                Toast.makeText(context, "Home location set", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to set home", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateHomeRadius(radius: Int) {
        val cfg = homeConfig ?: return
        if (cfg.length() == 0) return
        val updated = JSONObject(cfg.toString())
        updated.put("radiusM", radius)
        CoroutineScope(Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) {
                client.put("devices/$deviceId/config/home", updated)
            }
            if (ok) {
                homeConfig = updated
            }
        }
    }

    private fun removeHome() {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Home")
            .setMessage("Remove home location?")
            .setPositiveButton("Remove") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val ok = withContext(Dispatchers.IO) {
                        client.delete("devices/$deviceId/config/home")
                    }
                    if (ok) {
                        homeConfig = null
                        renderHome()
                        Toast.makeText(context, "Home removed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to remove home", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddGeofenceDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = "Name (e.g. Work)"
            setSingleLine()
        }
        val latInput = EditText(requireContext()).apply {
            hint = "Latitude (e.g. 33.6844)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine()
        }
        val lngInput = EditText(requireContext()).apply {
            hint = "Longitude (e.g. 73.0479)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine()
        }
        layout.addView(nameInput)
        layout.addView(latInput)
        layout.addView(lngInput)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Geofence")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val lat = latInput.text.toString().toDoubleOrNull()
                val lng = lngInput.text.toString().toDoubleOrNull()
                if (name.isEmpty() || lat == null || lng == null) {
                    Toast.makeText(context, "Invalid input", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val gf = GeofenceEntry(
                    id = "gf_${System.currentTimeMillis()}",
                    name = name,
                    lat = lat,
                    lng = lng,
                    radius = 100,
                    enabled = true
                )
                geofences.add(gf)
                saveGeofences()
                renderGeofences()
                Toast.makeText(context, "Geofence added", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun emptyNote(text: String): View {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setTextColor(requireContext().getColor(R.color.text_secondary))
        tv.textSize = 12f
        tv.setPadding(8, 8, 8, 8)
        return tv
    }
}
