package com.kojoscope.viewer.ui.data

import com.kojoscope.viewer.ui.GroupFragment
import com.kojoscope.viewer.ui.control.StorageFragment

class DataGroupFragment : GroupFragment() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = listOf(
            "WiFi" to { WiFiFragment() },
            "Contacts" to { ContactsFragment() },
            "Geo-fences" to { GeoFencesFragment() },
            "Sync" to { SyncFragment() },
            "Storage" to { StorageFragment() }
        )
    }
}
