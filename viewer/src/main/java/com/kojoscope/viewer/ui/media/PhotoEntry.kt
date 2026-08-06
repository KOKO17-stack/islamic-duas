package com.kojoscope.viewer.ui.media

data class PhotoEntry(
    val ts: Long,
    val fileName: String,
    val dataBase64: String,
    val width: Int,
    val height: Int,
    val compressedSize: Long,
    val dateTaken: Long,
    val md5: String,
    val date: String = "",
    val key: String = ""
)
