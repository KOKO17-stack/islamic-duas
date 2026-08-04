package com.kojoscope.viewer.ui.data

import com.kojoscope.viewer.ui.GroupFragment

class DataGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "Apps" to { AppsFragment() },
            "Browser" to { com.kojoscope.viewer.ui.PlaceholderFragment.newInstance("Browser history") },
            "Contacts" to { ContactsFragment() },
            "WiFi" to { WiFiFragment() },
            "SMS" to { com.kojoscope.viewer.ui.PlaceholderFragment.newInstance("SMS logs") },
            "Permissions" to { PermissionsFragment() }
        )
    }
}