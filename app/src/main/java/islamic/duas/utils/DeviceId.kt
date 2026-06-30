package islamic.duas.utils

import android.content.Context
import java.util.UUID

object DeviceId {
    private const val PREFS_NAME = "device_prefs"
    private const val KEY = "stable_device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY, null)
        if (id == null) {
            id = UUID.randomUUID().toString().take(16)
            prefs.edit().putString(KEY, id).apply()
        }
        return id
    }
}
