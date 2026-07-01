package islamic.duas.utils

import android.content.Context
import islamic.duas.cloud.CloudConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ErrorLog {
    private val JSON_MEDIA = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun write(context: Context, tag: String, message: String, error: Throwable? = null) {
        try {
            val id = DeviceId.get(context)
            val ts = System.currentTimeMillis()
            val data = JSONObject().apply {
                put("msg", message)
                put("tag", tag)
                put("ts_ms", ts)
                error?.let {
                    put("exc", it::class.java.simpleName)
                    put("stack", it.stackTraceToString().take(500))
                }
            }
            val url = "${CloudConfig.RTDB_URL}/devices/$id/errors/$ts.json"
            val body = data.toString().toRequestBody(JSON_MEDIA)
            client.newCall(Request.Builder().url(url).put(body).build()).execute().close()

            // Keep only last 100 errors per device
            val listUrl = "${CloudConfig.RTDB_URL}/devices/$id/errors.json?shallow=true"
            val listResp = client.newCall(Request.Builder().url(listUrl).get().build()).execute()
            val listBody = listResp.body?.string()
            listResp.close()
            if (listBody != null && listBody != "null") {
                val keys = JSONObject(listBody).keys().asSequence().toList().sorted()
                if (keys.size > 100) {
                    val toDelete = keys.take(keys.size - 100)
                    for (key in toDelete) {
                        try {
                            client.newCall(Request.Builder()
                                .url("${CloudConfig.RTDB_URL}/devices/$id/errors/$key.json")
                                .delete().build()).execute().close()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
