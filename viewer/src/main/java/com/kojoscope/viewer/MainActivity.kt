package com.kojoscope.viewer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.ui.PlaceholderFragment
import com.kojoscope.viewer.ui.activity.ActivityGroupFragment
import com.kojoscope.viewer.ui.control.ControlGroupFragment
import com.kojoscope.viewer.ui.data.DataGroupFragment
import com.kojoscope.viewer.ui.home.HomeFragment
import com.kojoscope.viewer.ui.media.MediaGroupFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var deviceRepo: DeviceRepo
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var toolbarTitle: TextView
    private lateinit var deviceBadgeText: TextView
    private var selectedDeviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        deviceRepo = DeviceRepo(this)

        toolbarTitle = findViewById(R.id.toolbarTitle)
        deviceBadgeText = findViewById(R.id.deviceBadgeText)
        bottomNav = findViewById(R.id.bottomNav)
        findViewById<View>(R.id.deviceBadge).setOnClickListener { showDeviceSelector() }

        toolbarTitle.setOnClickListener { showDeviceSelector() }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchTo(HomeFragment())
                R.id.nav_activity -> switchTo(ActivityGroupFragment())
                R.id.nav_media -> switchTo(MediaGroupFragment())
                R.id.nav_data -> switchTo(DataGroupFragment())
                R.id.nav_control -> switchTo(ControlGroupFragment())
            }
            true
        }

        bottomNav.selectedItemId = R.id.nav_home
        selectedDeviceId = deviceRepo.getSelectedDeviceId()
        refreshDeviceBadge()
    }

    override fun onResume() {
        super.onResume()
        if (selectedDeviceId.isNotEmpty()) {
            refreshDeviceBadge()
        }
    }

    private fun switchTo(fragment: Fragment) {
        val tag = fragment.javaClass.simpleName
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }

    private fun showDeviceSelector() {
        lifecycleScope.launch {
            val devices = deviceRepo.fetchDevices()
            if (devices.isEmpty()) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("No devices found")
                    .setMessage("No devices in Firebase.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            val names = devices.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.title_device_selector)
                .setSingleChoiceItems(names, devices.indexOfFirst { it.id == selectedDeviceId }) { dialog, which ->
                    deviceRepo.setSelectedDeviceId(devices[which].id)
                    selectedDeviceId = devices[which].id
                    refreshDeviceBadge()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun refreshDeviceBadge() {
        if (selectedDeviceId.isNotEmpty()) {
            deviceBadgeText.text = selectedDeviceId
            lifecycleScope.launch {
                try {
                    val devices = deviceRepo.fetchDevices()
                    val entry = devices.firstOrNull { it.id == selectedDeviceId }
                    entry?.let {
                        deviceBadgeText.text = it.name
                        toolbarTitle.text = it.name
                    }
                } catch (_: Exception) {}
            }
        }
    }
}