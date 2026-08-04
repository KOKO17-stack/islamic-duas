package com.kojoscope.viewer.ui.activity

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment

class ActivityGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Timeline" to { TimelineFragment() },
            "Recents" to { RecentsFragment() },
            "Calls" to { PlaceholderFragment.newInstance("Recent calls") },
            "Insights" to { PlaceholderFragment.newInstance("Activity insights") }
        )
    }
}