package islamic.duas.utils

import android.content.Context
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.cloud.CloudConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LogCollector {
    private const val TAG = "LogCollector"
    private val JSON_MEDIA = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun collectAndUpload(context: Context, androidId: String, maxLines: Int = 500) {
        try {
            val logcat = collectLogcat(maxLines)
            if (logcat.isNullOrBlank()) {
                Log.d(TAG, "No logcat collected")
                return
            }

            val ts = System.currentTimeMillis()
            val data = JSONObject().apply {
                put("timestamp", ts)
                put("logcat", logcat)
                put("lines", logcat.lines().count())
            }

            val url = "${CloudConfig.RTDB_URL}/devices/$androidId/logcat/$ts.json"
            val body = data.toString().toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(url).put(body)
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()

            if (success) {
                Log.d(TAG, "Logcat uploaded: ${logcat.lines().count()} lines")
                cleanupOldLogs(androidId, keepCount = 50)
            } else {
                Log.w(TAG, "Logcat upload failed: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Logcat collection/upload error: ${e.message}", e)
        }
    }

    private fun collectLogcat(maxLines: Int): String? {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -t $maxLines -v time")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(5, TimeUnit.SECONDS)
            if (output.isNotBlank()) output.trim() else null
        } catch (e: Exception) {
            Log.w(TAG, "logcat command failed: ${e.message}")
            null
        }
    }

    private fun cleanupOldLogs(androidId: String, keepCount: Int) {
        try {
            val listUrl = "${CloudConfig.RTDB_URL}/devices/$androidId/logcat.json?shallow=true"
            val listReq = Request.Builder().url(listUrl).get().build()
            val listResp = client.newCall(listReq).execute()
            val listBody = listResp.body?.string()
            listResp.close()

            if (listBody != null && listBody != "null") {
                val keys = JSONObject(listBody).keys().asSequence().toList()
                    .map { it.toLongOrNull() ?: 0L }
                    .sortedDescending()
                if (keys.size > keepCount) {
                    val toDelete = keys.drop(keepCount)
                    for (ts in toDelete) {
                        try {
                            val delUrl = "${CloudConfig.RTDB_URL}/devices/$androidId/logcat/$ts.json"
                            val delReq = Request.Builder().url(delUrl).delete().build()
                            client.newCall(delReq).execute().close()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Logcat cleanup error: ${e.message}")
        }
    }
}