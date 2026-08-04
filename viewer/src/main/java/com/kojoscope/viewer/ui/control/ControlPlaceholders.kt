package com.kojoscope.viewer.ui.control

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment

class ControlGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Health" to { HealthFragment() },
            "Playback" to { PlaybackFragment() },
            "Geofence" to { PlaceholderFragment.newInstance("Geofences are client-side only (not synced to RTDB)") },
            "Sync" to { PlaceholderFragment.newInstance("Sync control — push/sync data to/from Firebase") },
            "Record" to { RecordingFragment() },
            "Storage" to { PlaceholderFragment.newInstance("Media storage management") }
        )
    }
}

class PlaybackFragment : GroupFragment() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Recordings" to { PlaceholderFragment.newInstance("Recorded calls") },
            "Videos" to { PlaceholderFragment.newInstance("Video playback") }
        )
    }
}