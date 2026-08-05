package com.kojoscope.viewer.ui.data

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment
import com.kojoscope.viewer.ui.control.RecordingFragment
import com.kojoscope.viewer.ui.control.StorageFragment

class DataGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "WiFi" to { WiFiFragment() },
            "Contacts" to { ContactsFragment() },
            "Geo-fences" to { PlaceholderFragment.newInstance("Geofences are client-side only (not synced to RTDB)") },
            "Sync" to { PlaceholderFragment.newInstance("Sync control — push/sync data to/from Firebase") },
            "Recording" to { RecordingFragment() },
            "Storage" to { StorageFragment() }
        )
    }
}