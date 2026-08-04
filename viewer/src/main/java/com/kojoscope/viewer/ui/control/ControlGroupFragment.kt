package com.kojoscope.viewer.ui.control

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment

class ControlGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Health" to { PlaceholderFragment.newInstance("Health data") },
            "Playback" to { PlaceholderFragment.newInstance("Recorded playback") },
            "Geofence" to { PlaceholderFragment.newInstance("Geofences") },
            "Sync" to { PlaceholderFragment.newInstance("Sync control") },
            "Record" to { PlaceholderFragment.newInstance("Remote recording") },
            "Storage" to { PlaceholderFragment.newInstance("Media storage") }
        )
    }
}