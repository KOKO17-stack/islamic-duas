package com.kojoscope.viewer.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.kojoscope.viewer.R

class MediaViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_viewer)
        val b64 = intent.getStringExtra("data") ?: return
        val decoded = Base64.decode(b64, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        findViewById<ImageView>(R.id.mediaImage).setImageBitmap(bmp)
    }
}