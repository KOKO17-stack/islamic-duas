package islamic.duas.cloud

import android.content.Context
import android.util.Log
import islamic.duas.cloud.CloudAuth
import islamic.duas.data.OfflineQueue
import islamic.duas.utils.ErrorLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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

    fun writeToRTDB(path: String, data: JSONObject): Boolean {
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

    fun writeToCloud(collection: String, data: JSONObject, docId: String? = null, debugRtdbPath: String? = null): Boolean {
        return try {
            val token = CloudAuth.getFirebaseToken()
            val path = if (docId != null) "$collection/$docId" else collection
            val url = "${CloudConfig.FIRESTORE_BASE_URL}/$path"
            val payload = toCloudDocument(data).toString()
            val body = payload.toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(url)
                .apply { if (docId != null) patch(body) else post(body) }
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            if (!success && debugRtdbPath != null) {
                val err = response.body?.string() ?: ""
                writeToRTDB(debugRtdbPath, JSONObject().apply {
                    put("error", "HTTP ${response.code}")
                    put("detail", err.take(500))
                })
            }
            response.close()
            if (!success) {
                appContext?.let { OfflineQueue.enqueue(it, collection, (docId ?: collection), data, false) }
            }
            success
        } catch (e: Exception) {
            Log.w(TAG, "writeToCloud: ${e.message}")
            appContext?.let { OfflineQueue.enqueue(it, collection, (docId ?: collection), data, false) }
            if (debugRtdbPath != null) {
                writeToRTDB(debugRtdbPath, JSONObject().apply {
                    put("error", e.message?.take(200) ?: "unknown")
                    put("exception", e::class.java.simpleName)
                })
            }
            false
        }
    }

    fun writePhotoToCloud(collection: String, docId: String, data: JSONObject): Boolean {
        return try {
            val token = CloudAuth.getFirebaseToken()
            val url = "${CloudConfig.FIRESTORE_BASE_URL}/$collection?documentId=$docId"
            val payload = toCloudDocument(data).toString()
            val body = payload.toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(url).patch(body)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) { Log.w(TAG, "writePhotoToCloud: ${e.message}"); false }
    }

    fun deleteFromCloud(path: String): Boolean {
        return try {
            val token = CloudAuth.getFirebaseToken()
            val url = "${CloudConfig.FIRESTORE_BASE_URL}/$path"
            val request = Request.Builder()
                .url(url).delete()
                .addHeader("Authorization", "Bearer $token")
                .build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) { Log.w(TAG, "deleteFromCloud: ${e.message}"); false }
    }

    fun queryCloud(collection: String, field: String, op: String, value: Any, limit: Int = 50): List<JSONObject> {
        return try {
            val token = CloudAuth.getFirebaseToken()
            val url = "${CloudConfig.FIRESTORE_BASE_URL}:runQuery"
            val query = buildCloudStructuredQuery(collection, field, op, value, limit)
            val body = query.toString().toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(url).post(body)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            response.close()

            val results = mutableListOf<JSONObject>()
            val arr = JSONArray(responseBody)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.has("document")) {
                    results.add(item.getJSONObject("document"))
                }
            }
            results
        } catch (e: Exception) { Log.w(TAG, "queryCloud: ${e.message}"); emptyList() }
    }

    fun updateCloudDocument(path: String, data: JSONObject): Boolean {
        return try {
            val token = CloudAuth.getFirebaseToken()
            val url = "${CloudConfig.FIRESTORE_BASE_URL}/$path"
            val payload = JSONObject().apply {
                put("fields", toCloudFields(data))
            }.toString()
            val body = payload.toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(url).patch(body)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            if (!success) {
                val err = response.body?.string() ?: ""
                writeToRTDB("devices/${extractAndroidId(path)}/debug/cloud_patch",
                    JSONObject().apply {
                        put("error", "HTTP ${response.code}")
                        put("detail", err.take(500))
                        put("path", path)
                    })
            }
            response.close()
            success
        } catch (e: Exception) {
            writeToRTDB("devices/debug/cloud_patch",
                JSONObject().apply {
                    put("error", e.message?.take(200) ?: "unknown")
                    put("path", path)
                })
            false
        }
    }

    private fun extractAndroidId(path: String): String {
        val parts = path.split("/")
        return if (parts.size >= 2) parts[1] else "unknown"
    }

    private fun toCloudDocument(json: JSONObject): JSONObject {
        return JSONObject().apply { put("fields", toCloudFields(json)) }
    }

    private fun toCloudFields(json: JSONObject): JSONObject {
        val fields = JSONObject()
        for (key in json.keys()) {
            fields.put(key, jsonValueToCloud(json.get(key)))
        }
        return fields
    }

    private fun jsonValueToCloud(value: Any?): JSONObject {
        return when (value) {
            is String -> JSONObject().put("stringValue", value)
            is Int -> JSONObject().put("integerValue", value.toString())
            is Long -> JSONObject().put("integerValue", value.toString())
            is Double -> JSONObject().put("doubleValue", value)
            is Float -> JSONObject().put("doubleValue", value.toDouble())
            is Boolean -> JSONObject().put("booleanValue", value)
            is JSONObject -> JSONObject().put("mapValue", JSONObject().put("fields", toCloudFields(value)))
            is JSONArray -> {
                val values = mutableListOf<JSONObject>()
                for (i in 0 until value.length()) {
                    values.add(jsonValueToCloud(value.opt(i)))
                }
                JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(values)))
            }
            null -> JSONObject().put("nullValue", JSONObject.NULL)
            else -> JSONObject().put("stringValue", value.toString())
        }
    }

    private fun buildCloudStructuredQuery(collection: String, field: String, op: String, value: Any, limit: Int): JSONObject {
        val fieldValue = when (value) {
            is Int -> JSONObject().put("integerValue", value.toString())
            is Long -> JSONObject().put("integerValue", value.toString())
            is Double -> JSONObject().put("doubleValue", value)
            is String -> JSONObject().put("stringValue", value)
            is Boolean -> JSONObject().put("booleanValue", value)
            else -> JSONObject().put("stringValue", value.toString())
        }

        return JSONObject().apply {
            put("structuredQuery", JSONObject().apply {
                put("from", JSONArray().put(JSONObject().apply {
                    put("collectionId", collection.split("/").last())
                }))
                put("where", JSONObject().apply {
                    put("fieldFilter", JSONObject().apply {
                        put("field", JSONObject().apply { put("fieldPath", field) })
                        put("op", op)
                        put("value", fieldValue)
                    })
                })
                put("orderBy", JSONArray().put(JSONObject().apply {
                    put("field", JSONObject().apply { put("fieldPath", field) })
                    put("direction", "ASCENDING")
                }))
                put("limit", limit)
            })
        }
    }
}
