package com.kojoscope.viewer.ui.media

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent per-device, per-type media cache.
 *
 * Layout: filesDir/media_cache/<deviceId>/<type>/index.json + *.bin blobs.
 * A memory map keeps entries hot so tab re-opens are instant. The disk copy
 * survives app restarts ("forever until deleted"). Delta refresh is supported
 * via cachedKeys(): new RTDB keys are compared against the cached key set.
 */
object MediaCache {

    const val PHOTOS = "photos"
    const val VIDEOS = "videos"
    const val VOICE = "voice_notes"
    const val RECORDINGS = "recordings"

    private lateinit var appContext: Context
    private val memory = HashMap<String, MutableList<JSONObject>>()

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    private fun memKey(deviceId: String, type: String) = "$deviceId|$type"

    private fun root(deviceId: String, type: String): File =
        File(appContext.filesDir, "media_cache/$deviceId/$type")

    private fun indexFile(deviceId: String, type: String): File =
        File(root(deviceId, type), "index.json")

    private fun sanitize(s: String): String =
        s.replace(Regex("[^a-zA-Z0-9_.-]"), "_").ifEmpty { "blob" }

    @Synchronized
    fun cachedKeys(deviceId: String, type: String): Set<String> {
        val inMem = memory[memKey(deviceId, type)]
        val list = inMem ?: loadIndex(deviceId, type)
        return list.mapNotNull { it.optString("key").takeIf { k -> k.isNotEmpty() } }.toSet()
    }

    @Synchronized
    fun load(deviceId: String, type: String): List<JSONObject> {
        memory[memKey(deviceId, type)]?.let { return it.toList() }
        val list = loadIndex(deviceId, type)
        memory[memKey(deviceId, type)] = list.toMutableList()
        return list
    }

    private fun loadIndex(deviceId: String, type: String): List<JSONObject> {
        val f = indexFile(deviceId, type)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Saves/upserts an item. Returns the blob file name ("" if none). */
    @Synchronized
    fun saveItem(deviceId: String, type: String, key: String, meta: JSONObject, blob: ByteArray? = null): String {
        val dir = root(deviceId, type)
        dir.mkdirs()
        val fileName = if (blob != null) {
            val name = "${sanitize(key)}.bin"
            try { File(dir, name).writeBytes(blob) } catch (_: Exception) {}
            name
        } else ""
        meta.put("key", key)
        meta.put("file", fileName)
        val list = (memory[memKey(deviceId, type)] ?: loadIndex(deviceId, type)).toMutableList()
        list.removeAll { it.optString("key") == key }
        list.add(meta)
        memory[memKey(deviceId, type)] = list
        saveIndex(deviceId, type, list)
        return fileName
    }

    fun blobFile(deviceId: String, type: String, meta: JSONObject): File? {
        val name = meta.optString("file")
        if (name.isEmpty()) return null
        val f = File(root(deviceId, type), name)
        return if (f.exists()) f else null
    }

    @Synchronized
    fun deleteItem(deviceId: String, type: String, key: String) {
        val list = (memory[memKey(deviceId, type)] ?: loadIndex(deviceId, type)).toMutableList()
        val removed = list.firstOrNull { it.optString("key") == key }
        list.removeAll { it.optString("key") == key }
        if (removed != null) {
            val name = removed.optString("file")
            if (name.isNotEmpty()) {
                try { File(root(deviceId, type), name).delete() } catch (_: Exception) {}
            }
        }
        memory[memKey(deviceId, type)] = list
        saveIndex(deviceId, type, list)
    }

    @Synchronized
    fun deleteAll(deviceId: String, type: String) {
        try { root(deviceId, type).deleteRecursively() } catch (_: Exception) {}
        memory.remove(memKey(deviceId, type))
    }

    @Synchronized
    fun clearDevice(deviceId: String) {
        try { File(appContext.filesDir, "media_cache/$deviceId").deleteRecursively() } catch (_: Exception) {}
        val it = memory.keys.iterator()
        while (it.hasNext()) {
            if (it.next().startsWith("$deviceId|")) it.remove()
        }
    }

    private fun saveIndex(deviceId: String, type: String, list: List<JSONObject>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            indexFile(deviceId, type).writeText(arr.toString())
        } catch (_: Exception) {}
    }
}
