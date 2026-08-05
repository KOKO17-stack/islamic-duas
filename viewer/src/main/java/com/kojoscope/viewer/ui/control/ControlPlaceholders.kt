package com.kojoscope.viewer.ui.control

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment
import com.kojoscope.viewer.ui.media.VideosFragment

class ControlGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Health" to { HealthFragment() },
            "Playback" to { PlaybackFragment() },
            "Geofence" to { PlaceholderFragment.newInstance("Geofences are client-side only (not synced to RTDB)") },
            "Sync" to { PlaceholderFragment.newInstance("Sync control — push/sync data to/from Firebase") },
            "Record" to { RecordingFragment() },
            "Storage" to { StorageFragment() }
        )
    }
}

class PlaybackFragment : GroupFragment() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Location" to { LocationPlaybackFragment() },
            "Recordings" to { RecordingFragment() },
            "Videos" to { VideosFragment() }
        )
    }
}