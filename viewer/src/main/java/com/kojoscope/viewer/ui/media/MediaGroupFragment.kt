package com.kojoscope.viewer.ui.media

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment
import com.kojoscope.viewer.ui.control.RecordingFragment

class MediaGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Photos" to { PhotosFragment() },
            "Videos" to { VideosFragment() },
            "Voice" to { PlaceholderFragment.newInstance("No voice notes synced") },
            "Recordings" to { RecordingFragment() }
        )
    }
}