package com.kojoscope.viewer.ui.activity

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment

class ActivityGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Timeline" to { TimelineFragment() },
            "Recents" to { RecentsFragment() },
            "Calls" to { CallsFragment() },
            "Insights" to { InsightsFragment() }
        )
    }
}