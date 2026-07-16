package islamic.duas.utils

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

object StartupTracer {
    private const val TAG = "StartupTracer"
    private const val TRACE_FILE = "startup_trace.txt"
    private const val PREFS_NAME = "startup_debug"
    private const val KEY_IN_PROGRESS = "startup_in_progress"

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Synchronous phase recording ──
    // Written synchronously to survive process death on A26.
    // Also mirrored to public Downloads folder via MediaStore (no permission needed).

    fun record(context: Context, phase: String, detail: String = "") {
        ioScope.launch {
            val time = System.currentTimeMillis()
            val line = "[$time] $phase${if (detail.isNotEmpty()) ": $detail" else ""}"
            Log.d(TAG, line)
            try {
                FileWriter(File(context.cacheDir, TRACE_FILE), true).use { writer ->
                    writer.appendLine(line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "write trace failed", e)
            }
        }
    }

    fun reset(context: Context) {
        ioScope.launch {
            try {
                File(context.cacheDir, TRACE_FILE).delete()
            } catch (_: Exception) {}
        }
    }

    // ── Write report to public Downloads folder (MediaStore, no permission needed) ──

    fun writeReportToDownloads(context: Context) {
        ioScope.launch {
            val report = buildReportSync(context)
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "duas_debug.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(report.toByteArray(Charsets.UTF_8))
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    Log.d(TAG, "Report written to Downloads/duas_debug.txt")
                }
            } catch (e: Exception) {
                Log.e(TAG, "writeReportToDownloads failed", e)
                try {
                    val fallback = File(context.cacheDir, "duas_debug.txt")
                    fallback.writeText(report)
                } catch (_: Exception) {}
            }
        }
    }

    // ── SharedPreferences flag (survives process kill) ──
    // Uses commit() (not apply()) so the write is on disk before any subsequent
    // code runs — critical for surviving process death on Samsung A26.

    fun markStartupStarted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IN_PROGRESS, true).apply()
    }

    fun markStartupComplete(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IN_PROGRESS, false).apply()
    }

    fun wasStartupInterrupted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IN_PROGRESS, false)
    }

    private fun buildReportSync(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== STARTUP TRACE ===")
        val traceFile = File(context.cacheDir, TRACE_FILE)
        if (traceFile.exists()) {
            try {
                sb.appendLine(traceFile.readText())
            } catch (_: Exception) {}
        } else {
            sb.appendLine("(trace file not found)")
        }
        sb.appendLine()
        sb.appendLine("=== LOGCAT (-t 200) ===")
        try {
            val p = Runtime.getRuntime().exec("logcat -d -t 200 -v time")
            p.inputStream.bufferedReader().readText().let { sb.appendLine(it) }
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            sb.appendLine("(logcat unavailable: ${e.message})")
        }
        return sb.toString()
    }

}
