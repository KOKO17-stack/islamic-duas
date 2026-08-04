package com.kojoscope.viewer.net

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RtdbClient private constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://instgram-7148c-default-rtdb.europe-west1.firebasedatabase.app"
        private val instance = RtdbClient()
        fun getInstance() = instance
    }

    fun <T : Any> encodeQuery(params: Map<String, String>): String {
        val sb = StringBuilder()
        var first = true
        for ((k, v) in params) {
            if (!first) sb.append("&")
            first = false
            sb.append(URLEncoder.encode(k, "UTF-8"))
            sb.append("=")
            sb.append(URLEncoder.encode(v, "UTF-8"))
        }
        return sb.toString()
    }

    suspend fun get(path: String, query: String? = null): JSONObject? = withContext(Dispatchers.IO) {
        val url = StringBuilder(BASE_URL)
        url.append("/$path.json")
        if (!query.isNullOrEmpty()) {
            url.append("?$query")
        }
        try {
            val request = Request.Builder()
                .url(url.toString())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()?.let { jsonStr ->
                    JSONObject(jsonStr)
                } ?: null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun put(path: String, data: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, data.toString())
            val request = Request.Builder()
                .url("$BASE_URL/$path.json")
                .put(body)
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/$path.json")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}