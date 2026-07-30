package islamic.duas.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallDetector private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: CallDetector? = null

        fun getInstance(context: Context): CallDetector {
            return instance ?: synchronized(this) {
                instance ?: CallDetector(context.applicationContext).also { instance = it }
            }
        }
    }

    private lateinit var telephonyManager: TelephonyManager
    private var cachedIncomingNumber: String? = null
    private var wasRinging: Boolean = false
    private var callListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null
    private val bgScope = CoroutineScope(Dispatchers.IO)

    fun startDetection() {
        try {
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state, null)
                    }
                }
                telephonyCallback = callback
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                Log.d("CallFix", "startDetection: registered TelephonyCallback (API 31+)")
            } else {
                @Suppress("DEPRECATION")
                callListener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                        handleCallState(state, incomingNumber)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.d("CallFix", "startDetection: registered PhoneStateListener (legacy)")
            }
        } catch (e: Exception) {
            Log.e("CallFix", "startDetection failed: ${e.message}", e)
        }
    }

    private fun handleCallState(state: Int, incomingNumber: String?) {
        Log.d("CallFix", "onCallState: state=$state number=$incomingNumber")
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                cachedIncomingNumber = incomingNumber
                wasRinging = true
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val number = if (wasRinging) {
                    cachedIncomingNumber ?: "unknown"
                } else {
                    ""
                }
                wasRinging = false

                Log.d("CallFix", "CALL_STATE_OFFHOOK → startCall(number=$number)")
                CallRecorder.getInstance(context).startCall(
                    type = "phone",
                    number = number,
                    name = "",
                    direction = "incoming"
                )

                bgScope.launch {
                    delay(1500)
                    val outgoingNumber = queryOutgoingNumber()
                    if (outgoingNumber != null) {
                        cachedIncomingNumber = outgoingNumber
                    }
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d("CallFix", "CALL_STATE_IDLE → endCall()")
                cachedIncomingNumber = null
                wasRinging = false
                CallRecorder.getInstance(context).endCall("phone")
            }
        }
    }

    private fun queryOutgoingNumber(): String? {
        try {
            if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                return null
            }
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}",
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(CallLog.Calls.NUMBER)
                    if (idx >= 0) return it.getString(idx)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun stopDetection() {
        try {
            Log.d("CallFix", "stopDetection()")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                callListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
            callListener = null
            telephonyCallback = null
        } catch (e: Exception) {
            Log.e("CallFix", "stopDetection error: ${e.message}", e)
        }
    }
}
