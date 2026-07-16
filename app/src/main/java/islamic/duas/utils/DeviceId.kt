package islamic.duas.utils

import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceId {
    private const val PREFS_NAME = "device_prefs"
    private const val KEY = "stable_device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY, null)
        if (id == null) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) { null }
            val aospDefault = "9774d56d682e549c"
            id = if (!androidId.isNullOrBlank() && androidId != aospDefault) {
                androidId.take(16)
            } else {
                UUID.randomUUID().toString().take(16)
            }
            prefs.edit().putString(KEY, id).apply()
        }
        return id
    }
}
