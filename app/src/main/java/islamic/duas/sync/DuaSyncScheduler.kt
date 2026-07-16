package islamic.duas.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlin.random.Random
import java.util.concurrent.TimeUnit

object DuaSyncScheduler {

    enum class Mode {
        HOME,
        AWAY,
        CHARGING
    }

    fun updateSchedule(context: Context, mode: Mode) {
        cancelCurrent(context)

        when (mode) {
            Mode.HOME -> scheduleHome(context)
            Mode.AWAY -> scheduleAway(context)
            Mode.CHARGING -> scheduleCharging(context)
        }
    }

    fun onBoot(context: Context) {
        val batteryStatus = getBatteryStatus(context)
        val mode = if (batteryStatus) Mode.CHARGING else Mode.HOME
        updateSchedule(context, mode)
        DuaLocationWorker.schedule(context)
    }

    fun onChargingStateChanged(context: Context, isCharging: Boolean) {
        if (isCharging) {
            updateSchedule(context, Mode.CHARGING)
        } else {
            updateSchedule(context, Mode.HOME)
        }
    }

    private fun jitter(baseMinutes: Long): Long {
        val delta = (baseMinutes * 0.3).toLong()
        return baseMinutes + Random.nextLong(-delta, delta + 1)
    }

    private fun scheduleHome(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val interval = jitter(20)
        val request = PeriodicWorkRequestBuilder<DuaSyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag("sync_home")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_home",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
        scheduleHeartbeat(context)
    }

    private fun scheduleAway(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val interval = jitter(15)
        val request = PeriodicWorkRequestBuilder<DuaSyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag("sync_away")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_away",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
        scheduleHeartbeat(context)
    }

    private fun scheduleCharging(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val interval = jitter(15)
        val request = PeriodicWorkRequestBuilder<DuaSyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag("sync_charging")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_charging",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
        scheduleHeartbeat(context)
    }

    private fun scheduleHeartbeat(context: Context) {
        val heartbeatInterval = jitter(30)
        val request = PeriodicWorkRequestBuilder<DuaLegacyWorker>(heartbeatInterval, TimeUnit.MINUTES)
            .addTag("sync_heartbeat")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_heartbeat",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    fun runOnceNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DuaSyncWorker>()
            .setConstraints(constraints)
            .addTag("sync_onetime")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun cancelCurrent(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("sync_home")
        WorkManager.getInstance(context).cancelAllWorkByTag("sync_away")
        WorkManager.getInstance(context).cancelAllWorkByTag("sync_charging")
    }

    private fun getBatteryStatus(context: Context): Boolean {
        val intent = context.registerReceiver(null, android.content.IntentFilter(
            android.content.Intent.ACTION_BATTERY_CHANGED
        ))
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }
}
