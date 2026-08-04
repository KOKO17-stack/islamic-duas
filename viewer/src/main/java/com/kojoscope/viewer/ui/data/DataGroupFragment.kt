package com.kojoscope.viewer.ui.data

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.PlaceholderFragment

class DataGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Apps" to { PlaceholderFragment.newInstance("Installed apps") },
            "Browser" to { PlaceholderFragment.newInstance("Browser history") },
            "Contacts" to { PlaceholderFragment.newInstance("Contacts") },
            "WiFi" to { PlaceholderFragment.newInstance("WiFi scans") },
            "SMS" to { PlaceholderFragment.newInstance("SMS logs") },
            "Permissions" to { PlaceholderFragment.newInstance("App permissions") }
        )
    }
}