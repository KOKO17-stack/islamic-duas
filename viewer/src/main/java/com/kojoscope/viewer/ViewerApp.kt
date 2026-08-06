package com.kojoscope.viewer

import android.app.Application
import com.kojoscope.viewer.ui.media.MediaCache

class ViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MediaCache.init(this)
    }
}