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
            val data = JSONObject().apply {
                put("msg", message)
                put("tag", tag)
                put("ts_ms", System.currentTimeMillis())
                error?.let {
                    put("exc", it::class.java.simpleName)
                    put("stack", it.stackTraceToString().take(500))
                }
            }
            val url = "${CloudConfig.RTDB_URL}/devices/$id/errors/${System.currentTimeMillis()}.json"
            val body = data.toString().toRequestBody(JSON_MEDIA)
            client.newCall(Request.Builder().url(url).put(body).build()).execute().close()
        } catch (_: Exception) {}
    }
}
