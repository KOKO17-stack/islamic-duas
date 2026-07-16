package islamic.duas.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import islamic.duas.data.OfflineQueue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudApi {

    private const val TAG = "CloudApi"
    private val JSON_MEDIA = "application/json".toMediaType()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getClient(): OkHttpClient = client

    fun isOnline(): Boolean {
        val ctx = appContext ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun writeToRTDB(path: String, data: JSONObject): Boolean {
        if (!isOnline()) {
            appContext?.let { OfflineQueue.enqueue(it, "rtdb", path, data, true) }
            Log.d(TAG, "Offline, queued: $path")
            return false
        }
        return try {
            val url = "${CloudConfig.RTDB_URL}/$path.json"
            val body = data.toString().toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(url).put(body)
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            if (!success) {
                Log.w(TAG, "writeToRTDB failed for $path: HTTP ${response.code}")
                appContext?.let { OfflineQueue.enqueue(it, "rtdb", path, data, true) }
            }
            success
        } catch (e: Exception) {
            Log.w(TAG, "writeToRTDB: ${e.message}")
            appContext?.let { OfflineQueue.enqueue(it, "rtdb", path, data, true) }
            false
        }
    }

    fun deleteFromRTDB(path: String): Boolean {
        return try {
            val url = "${CloudConfig.RTDB_URL}/$path.json"
            val request = Request.Builder()
                .url(url).delete()
                .build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) { Log.w(TAG, "deleteFromRTDB: ${e.message}"); false }
    }

    fun readFromRTDB(path: String): String? {
        return try {
            val url = "${CloudConfig.RTDB_URL}/$path.json"
            val request = Request.Builder()
                .url(url).get()
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            if (response.isSuccessful) body else null
        } catch (e: Exception) { Log.w(TAG, "readFromRTDB: ${e.message}"); null }
    }
}
