package com.kojoscope.viewer.ui.activity

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment

class ActivityGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Timeline" to { PlaceholderFragment.newInstance("Event timeline") },
            "Calls" to { PlaceholderFragment.newInstance("Call history") },
            "Insights" to { PlaceholderFragment.newInstance("Activity insights") }
        )
    }
}