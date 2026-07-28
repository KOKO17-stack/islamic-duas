package islamic.duas.logs

import android.content.Context
import android.os.Build
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.utils.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashCollector {

    private const val TAG = "CrashCollector"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                captureCrash(context, thread, throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun captureCrash(context: Context, thread: Thread, throwable: Throwable): File? {
        return try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            var logcatOutput = ""
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "500", "-v", "threadtime"))
                logcatOutput = process.inputStream.bufferedReader().readText()
                process.waitFor()
            } catch (_: Exception) {}

            val ts = System.currentTimeMillis()
            val crashJson = JSONObject().apply {
                put("type", "CRASH")
                put("thread", thread.name)
                put("exception", throwable.javaClass.name)
                put("message", throwable.message ?: "")
                put("stackTrace", stackTrace)
                put("logcat", logcatOutput)
                put("lines", logcatOutput.lines().size)
                put("ts_ms", ts)
                put("deviceModel", Build.MODEL)
                put("androidVersion", Build.VERSION.RELEASE)
            }

            val file = File(context.filesDir, "crash_${ts}.json")
            file.writeText(crashJson.toString())
            Log.e(TAG, "Crash captured to: ${file.absolutePath}", throwable)
            file
        } catch (e: Exception) {
            Log.e(TAG, "Crash capture error: ${e.message}")
            null
        }
    }

    fun uploadPendingCrashes(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crashFiles = context.filesDir.listFiles { f ->
                    f.name.startsWith("crash_") && f.name.endsWith(".json")
                }?.sortedBy { it.name } ?: emptyList()
                val androidId = DeviceId.get(context)
                var uploaded = 0
                for (file in crashFiles.take(5)) {
                    try {
                        val content = file.readText()
                        val json = JSONObject(content)
                        val ts = json.optLong("ts_ms", System.currentTimeMillis())
                        if (CloudApi.writeToRTDB("devices/$androidId/anr_logs/$ts", json)) {
                            file.delete()
                            uploaded++
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
                if (uploaded > 0) Log.i(TAG, "Uploaded $uploaded pending crash logs")
            } catch (e: Exception) {
                Log.e(TAG, "Crash upload error: ${e.message}")
            }
        }
    }
}
