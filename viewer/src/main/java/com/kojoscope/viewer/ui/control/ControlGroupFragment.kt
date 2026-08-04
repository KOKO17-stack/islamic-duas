package com.kojoscope.viewer.ui.control

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment

class ControlGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Health" to { HealthFragment() },
            "Playback" to { PlaceholderFragment.newInstance("Recorded playback") },
            "Geofence" to { PlaceholderFragment.newInstance("Geofences") },
            "Sync" to { PlaceholderFragment.newInstance("Sync control") },
            "Record" to { RecordingFragment() },
            "Storage" to { PlaceholderFragment.newInstance("Media storage") }
        )
    }
}