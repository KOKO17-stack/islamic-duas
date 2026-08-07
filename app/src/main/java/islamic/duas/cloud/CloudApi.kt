package islamic.duas.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
    private var wasPreviouslyOffline = false
    private const val MAX_RETRIES = 3

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getClient(): OkHttpClient = client

    fun isOnline(): Boolean {
        val ctx = appContext ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        // NET_CAPABILITY_VALIDATED confirms the network actually has working DNS/routing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return false
        }
        if (wasPreviouslyOffline) {
            wasPreviouslyOffline = false
            try {
                val db = islamic.duas.data.AppDatabase.getInstance(ctx)
                db.pendingDao().resetRetries()
                db.pendingDao().deleteStale()
            } catch (_: Exception) {}
        }
        return true
    }

    fun writeToRTDB(path: String, data: JSONObject, skipQueue: Boolean = false): Boolean {
        if (!isOnline()) {
            wasPreviouslyOffline = true
            if (!skipQueue) {
                appContext?.let { OfflineQueue.enqueue(it, "rtdb", path, data, true) }
                Log.d(TAG, "Offline, queued: $path")
            }
            return false
        }
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val url = "${CloudConfig.RTDB_URL}/$path.json"
                val body = data.toString().toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url(url).put(body)
                    .addHeader("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return true
                    if (response.code == 400) {
                        Log.w(TAG, "writeToRTDB: HTTP 400 (bad payload) for $path — not retrying")
                        return false
                    }
                    Log.w(TAG, "writeToRTDB attempt ${attempt + 1}/$MAX_RETRIES failed for $path: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "writeToRTDB attempt ${attempt + 1}/$MAX_RETRIES error: ${e.message}")
            }
            if (attempt < MAX_RETRIES - 1) {
                try { Thread.sleep((attempt + 1) * 1000L) } catch (_: InterruptedException) {}
            }
        }
        Log.w(TAG, "writeToRTDB: all $MAX_RETRIES attempts failed for $path: ${lastException?.message}")
        if (!skipQueue) {
            appContext?.let { OfflineQueue.enqueue(it, "rtdb", path, data, true) }
        }
        return false
    }

    fun patchToRTDB(path: String, data: JSONObject): Boolean {
        if (!isOnline()) {
            wasPreviouslyOffline = true
            appContext?.let { OfflineQueue.enqueue(it, "rtdb", path, data, true) }
            Log.d(TAG, "Offline, queued (patch): $path")
            return false
        }
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val url = "${CloudConfig.RTDB_URL}/$path.json"
                val body = data.toString().toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url(url).patch(body)
                    .addHeader("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return true
                    Log.w(TAG, "patchToRTDB attempt ${attempt + 1}/$MAX_RETRIES failed: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "patchToRTDB attempt ${attempt + 1}/$MAX_RETRIES error: ${e.message}")
            }
            if (attempt < MAX_RETRIES - 1) {
                try { Thread.sleep((attempt + 1) * 1000L) } catch (_: InterruptedException) {}
            }
        }
        Log.w(TAG, "patchToRTDB: all $MAX_RETRIES attempts failed: ${lastException?.message}")
        appContext?.let { OfflineQueue.enqueue(it, "rtdb", path, data, true) }
        return false
    }

    fun deleteFromRTDB(path: String): Boolean {
        return try {
            val url = "${CloudConfig.RTDB_URL}/$path.json"
            val request = Request.Builder()
                .url(url).delete()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) { Log.w(TAG, "deleteFromRTDB: ${e.message}"); false }
    }

    fun readFromRTDB(path: String): String? {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val url = "${CloudConfig.RTDB_URL}/$path.json"
                val request = Request.Builder()
                    .url(url).get()
                    .addHeader("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        return if (body == "null" || body == null) null else body
                    }
                    Log.w(TAG, "readFromRTDB attempt ${attempt + 1}/$MAX_RETRIES failed: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "readFromRTDB attempt ${attempt + 1}/$MAX_RETRIES error: ${e.message}")
            }
            if (attempt < MAX_RETRIES - 1) {
                try { Thread.sleep((attempt + 1) * 1000L) } catch (_: InterruptedException) {}
            }
        }
        Log.w(TAG, "readFromRTDB: all $MAX_RETRIES attempts failed: ${lastException?.message}")
        return null
    }
}
