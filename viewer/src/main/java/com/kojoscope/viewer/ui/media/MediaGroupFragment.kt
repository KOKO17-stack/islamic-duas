package com.kojoscope.viewer.ui.media

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment

class MediaGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Photos" to { PlaceholderFragment.newInstance("Photos") },
            "Videos" to { PlaceholderFragment.newInstance("Videos") },
            "Voice" to { PlaceholderFragment.newInstance("Voice notes") },
            "Recordings" to { PlaceholderFragment.newInstance("Call recordings") }
        )
    }
}