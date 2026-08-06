package com.kojoscope.viewer.ui.player

import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kojoscope.viewer.R
import com.kojoscope.viewer.ui.media.MediaBitmaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_viewer)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val filePath = intent.getStringExtra("filePath") ?: run {
            showError("No image file")
            return
        }
        val name = intent.getStringExtra("name") ?: ""
        findViewById<TextView>(R.id.mediaTitle).text = name

        val image = findViewById<ImageView>(R.id.mediaImage)
        val progress = findViewById<ProgressBar>(R.id.progress)

        val file = File(filePath)
        if (!file.exists()) {
            showError("Image not found in cache")
            return
        }

        progress.visibility = android.view.View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val displayMetrics = resources.displayMetrics
            val reqSize = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val bmp = try {
                MediaBitmaps.decodeSampledFile(file, reqSize)
            } catch (_: Throwable) {
                null
            }
            withContext(Dispatchers.Main) {
                progress.visibility = android.view.View.GONE
                if (bmp != null) {
                    image.setImageBitmap(bmp)
                } else {
                    showError("Failed to decode image")
                }
            }
        }
    }

    private fun showError(msg: String) {
        findViewById<TextView>(R.id.mediaError)?.let {
            it.text = msg
            it.visibility = android.view.View.VISIBLE
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
