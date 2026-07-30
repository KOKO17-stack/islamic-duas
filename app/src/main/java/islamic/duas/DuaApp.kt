package islamic.duas

import android.app.Application
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.StrictMode
import islamic.duas.cloud.CloudApi
import islamic.duas.logs.CrashCollector
import islamic.duas.sync.DuaConnectivityReceiver

class DuaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashCollector.install(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build())
        }
        CloudApi.init(this)
        CrashCollector.uploadPendingCrashes(this)
        try {
            registerReceiver(
                DuaConnectivityReceiver(),
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            )
        } catch (_: Exception) {}
        try { StepCounterService.start(this) } catch (_: Exception) {}
    }
}
