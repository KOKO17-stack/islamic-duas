package islamic.duas.whatsapp

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ring buffer of WhatsApp voice-message notification events.
 *
 * Live notifications (DuaNotificationService) record when a voice message arrives
 * together with whether the chat was individual or group. The voice-note sync
 * (DuaSyncWorker) then matches each newly-seen voice-note file to one of these
 * events by timestamp so group voices can be excluded and individual voices kept.
 *
 * WhatsApp stores every voice note in flat folders (no per-chat/group marker),
 * so this live event stream is the only reliable per-chat classification signal.
 */
object VoiceEventStore {

    const val MATCH_WINDOW_MS = 5 * 60 * 1000L
    private const val MAX_EVENTS = 300
    private const val EXPIRY_MS = 7L * 24L * 60L * 60L * 1000L
    private const val KEY = "voice_events"

    fun recordEvent(prefs: SharedPreferences, tsMs: Long, isGroup: Boolean, chatCategory: String, chatName: String) {
        try {
            val now = System.currentTimeMillis()
            val arr = load(prefs)
            arr.put(JSONObject().apply {
                put("ts", tsMs)
                put("isGroup", isGroup)
                put("chatCategory", chatCategory)
                put("chatName", chatName)
                put("recorded", now)
            })
            // Trim to a bounded newest-N window
            while (arr.length() > MAX_EVENTS) arr.remove(0)
            save(prefs, arr)
        } catch (_: Exception) {}
    }

    /**
     * Returns true when the file matches a GROUP voice event (skip sync),
     * false when it matches an INDIVIDUAL chat event, or null when no event matches.
     * Consumes the matched event so each one drives at most one note.
     */
    fun matchAndConsume(prefs: SharedPreferences, fileTsMs: Long): Boolean? {
        val arr = load(prefs)
        if (arr.length() == 0) return null
        val now = System.currentTimeMillis()

        var bestIdx = -1
        var bestDelta = Long.MAX_VALUE
        var bestGroup = false
        var i = 0
        while (i < arr.length()) {
            val ev = arr.optJSONObject(i)
            if (ev == null) {
                arr.remove(i)
                continue
            }
            val evTs = ev.optLong("ts", 0L)
            if (evTs <= 0 || now - ev.optLong("recorded", evTs) > EXPIRY_MS) {
                arr.remove(i)
                continue
            }
            val delta = kotlin.math.abs(evTs - fileTsMs)
            if (delta <= MATCH_WINDOW_MS && delta < bestDelta) {
                bestDelta = delta
                bestIdx = i
                bestGroup = ev.optBoolean("isGroup", false)
            }
            i++
        }
        if (bestIdx < 0) {
            save(prefs, arr)
            return null
        }
        arr.remove(bestIdx)
        save(prefs, arr)
        return bestGroup
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(KEY).apply()
    }

    private fun load(prefs: SharedPreferences): JSONArray {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun save(prefs: SharedPreferences, arr: JSONArray) {
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}