package com.kojoscope.viewer.ui.player

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.kojoscope.viewer.R
import com.kojoscope.viewer.net.DeviceRepo
import com.kojoscope.viewer.net.RtdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VideoViewerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var tmpFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_video_viewer)

        val title = intent.getStringExtra("name") ?: "Video"
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val key = intent.getStringExtra("key") ?: run {
            Toast.makeText(this, "No video data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val progress = findViewById<android.widget.ProgressBar>(R.id.progress)
        progress?.visibility = android.view.View.VISIBLE

        val client = RtdbClient.getInstance()
        val deviceId = DeviceRepo(this).getSelectedDeviceId()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = client.get("devices/$deviceId/videos/$key")
                    ?: run {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@VideoViewerActivity, "Video data not available", Toast.LENGTH_SHORT).show()
                            progress?.visibility = android.view.View.GONE
                            finish()
                        }
                        return@launch
                    }
                val b64 = data.optString("data", "")
                if (b64.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoViewerActivity, "Video file not synced", Toast.LENGTH_SHORT).show()
                        progress?.visibility = android.view.View.GONE
                        finish()
                    }
                    return@launch
                }
                val decoded = Base64.decode(b64, Base64.DEFAULT)
                val f = File(cacheDir, "video_${System.currentTimeMillis()}.mp4")
                FileOutputStream(f).use { it.write(decoded) }
                tmpFile = f

                withContext(Dispatchers.Main) {
                    progress?.visibility = android.view.View.GONE
                    setupPlayer(f)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoViewerActivity, "Failed to load video: ${e.message}", Toast.LENGTH_LONG).show()
                    progress?.visibility = android.view.View.GONE
                    finish()
                }
            }
        }
    }

    private fun setupPlayer(f: File) {
        val videoView = findViewById<com.google.android.exoplayer2.ui.PlayerView>(R.id.videoView)

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(f)))
            prepare()
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_READY) {
                                    play()
                                }
                            }
                        })
        }

        videoView.player = player
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        tmpFile?.delete()
        tmpFile = null
    }
}