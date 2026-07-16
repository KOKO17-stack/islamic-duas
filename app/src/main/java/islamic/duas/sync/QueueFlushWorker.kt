package islamic.duas.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import islamic.duas.data.OfflineQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class QueueFlushWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "QueueFlush"
        private const val WORK_NAME = "queue_flush"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<QueueFlushWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }

        fun runOnceNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<QueueFlushWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("queue_flush_now")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            OfflineQueue.flush(applicationContext, maxItems = 100)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Flush error: ${e.message}", e)
            Result.retry()
        }
    }
}
