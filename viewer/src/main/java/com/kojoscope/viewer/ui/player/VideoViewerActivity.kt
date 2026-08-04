package com.kojoscope.viewer.ui.player

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.kojoscope.viewer.R
import java.io.File
import java.io.FileOutputStream

class VideoViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_video_viewer)

        val title = intent.getStringExtra("name") ?: "Video"
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val b64 = intent.getStringExtra("data") ?: return
        val videoView = findViewById<VideoView>(R.id.videoView)
        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)

        try {
            val decoded = Base64.decode(b64, Base64.DEFAULT)
            val tmp = File(cacheDir, "preview_${System.currentTimeMillis()}.mp4")
            FileOutputStream(tmp).use { it.write(decoded) }
            videoView.setVideoURI(Uri.fromFile(tmp))
            videoView.setOnPreparedListener { it.isLooping = false; videoView.start() }
        } catch (_: Exception) {
            videoView.stopPlayback()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}