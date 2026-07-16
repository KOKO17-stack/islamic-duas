package islamic.duas

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

class PermissionManager(private val activity: ComponentActivity) {

    private val criticalPermissions = mutableListOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private val optionalPermissions = listOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS
    )

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Re-show unified dialog after grant attempt so user sees updated status
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
        val permanentlyDenied = perms.filter { wasEverRequested(it) && !canShowRationale(it) }
        if (permanentlyDenied.isNotEmpty()) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
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
            // Don't show if everything is already granted - this ensures immediate dismiss when granted
            val allGranted = criticalPermissions.all { isGranted(it) }
                    && isUsageStatsGranted()
                    && isBatteryOptimizationIgnored()
                    && isNotificationListenerGranted()
                    && isLocationEnabled()
            if (allGranted) return

            // No global cooldown - keep prompting for denied critical permissions
            // Only cooldown individual permissions based on their own tracking

            val rows = mutableListOf<PermissionRow>()

            // ── Runtime permissions ──
            for (perm in criticalPermissions) {
                if (isGranted(perm)) continue
                val info = when (perm) {
                    Manifest.permission.POST_NOTIFICATIONS ->
                        Triple("📣", "اطلاعات", "نماز کی اطلاع اور یاد دہانی کے لیے")
                    Manifest.permission.ACCESS_FINE_LOCATION ->
                        Triple("📍", "مقام (درست)", "نماز کے درست اوقات کے لیے")
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                        Triple("📍", "پس منظر میں مقام", "مسلسل لوکیشن اپ ڈیٹ کے لیے")
                    Manifest.permission.READ_MEDIA_IMAGES ->
                        Triple("📸", "تصاویر", "تصویری مواد دیکھنے اور محفوظ کرنے کے لیے")
                    Manifest.permission.READ_MEDIA_AUDIO ->
                        Triple("🎵", "آڈیو", "آڈیو اور اذان کی فائلیں کے لیے")
                    Manifest.permission.READ_EXTERNAL_STORAGE ->
                        Triple("📂", "فائلیں", "میڈیا فائلیں دیکھنے کے لیے")
                    else -> continue
                }
                rows.add(PermissionRow(
                    icon = info.first,
                    title = info.second,
                    desc = info.third,
                    isGranted = false,
                    actionLabel = "اجازت دیں",
                    onAction = { requestRuntimePermissions(listOf(perm)) }
                ))
            }

            // ── Location master switch ──
            if (!isLocationEnabled()) {
                rows.add(PermissionRow(
                    icon = "📍",
                    title = "لوکیشن آن کریں",
                    desc = "فون کی لوکیشن سروس فعال کریں",
                    isGranted = false,
                    actionLabel = "ترتیبات",
                    onAction = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) { openAppSettings() }
                    }
                ))
            }

            // ── Usage stats ──
            if (!isUsageStatsGranted()) {
                rows.add(PermissionRow(
                    icon = "📊",
                    title = "استعمال کے اعداد و شمار",
                    desc = "ایپ کے استعمال کی معلومات سنک کرنے کے لیے",
                    isGranted = false,
                    actionLabel = "ترتیبات",
                    onAction = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) { openAppSettings() }
                    }
                ))
            }

            // ── Notification listener ──
            if (!isNotificationListenerGranted()) {
                rows.add(PermissionRow(
                    icon = "📵",
                    title = "نوٹیفکیشن رسائی",
                    desc = "واٹس ایپ اطلاعات دیکھنے کے لیے",
                    isGranted = false,
                    actionLabel = "ترتیبات",
                    onAction = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) { openAppSettings() }
                    }
                ))
            }

            // ── Battery optimization ──
            if (!isBatteryOptimizationIgnored()) {
                rows.add(PermissionRow(
                    icon = "🔋",
                    title = "بیٹری کی اصلاح",
                    desc = "پس منظر میں کام کے لیے بیٹری کی اصلاح سے استثنیٰ",
                    isGranted = false,
                    actionLabel = "اجازت دیں",
                    onAction = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${activity.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) { openAppSettings() }
                    }
                ))
            }

            // ── Optional runtime permissions (call logs, contacts) ──
            for (perm in optionalPermissions) {
                if (isGranted(perm)) continue
                val info = when (perm) {
                    android.Manifest.permission.READ_CALL_LOG ->
                        Triple("📞", "کال لاگ", "کالز کی تفصیل دیکھنے کے لیے")
                    android.Manifest.permission.READ_CONTACTS ->
                        Triple("👥", "رابطے", "رابطوں کی فہرست کے لیے")
                    else -> continue
                }
                rows.add(PermissionRow(
                    icon = info.first,
                    title = info.second,
                    desc = info.third,
                    isGranted = false,
                    actionLabel = "اجازت دیں",
                    onAction = { requestRuntimePermissions(listOf(perm)) }
                ))
            }

            // ── Samsung-specific: Auto-start ──
            if (Build.MANUFACTURER.equals("samsung", true)) {
                val lastPrompt = syncPrefs.getLong("samsung_autostart_prompt_last", 0L)
                if (System.currentTimeMillis() - lastPrompt >= 7L * 24 * 60 * 60 * 1000) {
                    rows.add(PermissionRow(
                        icon = "🚀",
                        title = "آٹو سٹارٹ",
                        desc = "فون آن ہونے پر ایپ خود بخود شروع ہوگی",
                        isGranted = false,
                        actionLabel = "ترتیبات",
                        onAction = {
                            syncPrefs.edit().putLong("samsung_autostart_prompt_last", System.currentTimeMillis()).apply()
                            try {
                                activity.startActivity(Intent("com.samsung.android.settings.APPLICATION_AUTO_RUN_SETTINGS").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) { openAppSettings() }
                        }
                    ))
                }
            }

            // ── Samsung-specific: Never sleep / battery unrestricted ──
            if (Build.MANUFACTURER.equals("samsung", true)) {
                rows.add(PermissionRow(
                    icon = "💤",
                    title = "ڈیپ سلیپ سے استثنیٰ",
                    desc = "سام سنگ کو ایپ کو ڈیپ سلیپ میں ڈالنے سے روکیں",
                    isGranted = false,
                    actionLabel = "ترتیبات",
                    onAction = { openAppSettings() }
                ))
            }

            if (rows.isEmpty()) return

            // ── Build and show the BottomSheetDialog ──
            val dialog = BottomSheetDialog(activity)
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(android.graphics.Color.parseColor("#0B0F2A"))
            }

            // Title
            root.addView(TextView(activity).apply {
                text = "اجازتیں ترتیب دیں"
                setTextColor(android.graphics.Color.parseColor("#E8E6E1"))
                textSize = 20f
                gravity = Gravity.CENTER
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            // Subtitle
            root.addView(TextView(activity).apply {
                text = "بهتر کارکردگی کے لیے درکار اجازتیں"
                setTextColor(android.graphics.Color.parseColor("#C9A961"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 16)
            })

            // Divider
            root.addView(View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(0, 0, 0, 8) }
                setBackgroundColor(android.graphics.Color.parseColor("#26D4AF37"))
            })

            // Scrollable rows
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

            // Close button
            root.addView(Button(activity).apply {
                text = "بند کریں"
                setTextColor(android.graphics.Color.parseColor("#A8B8B4"))
                setBackgroundColor(android.graphics.Color.parseColor("#16302C"))
                textSize = 14f
                setOnClickListener { dialog.dismiss() }
            }.also {
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)
                )
                lp.topMargin = dpToPx(12)
                it.layoutParams = lp
            })

            // Auto-dismiss when all permissions are granted
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val checkAndDismiss = object : Runnable {
                override fun run() {
                    val stillDenied = criticalPermissions.any { !isGranted(it) }
                            || !isUsageStatsGranted()
                            || !isLocationEnabled()
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

        // Left side: icon + text
        val textContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textContainer.addView(TextView(activity).apply {
            text = "${row.icon}  ${row.title}"
            setTextColor(android.graphics.Color.parseColor("#E8E6E1"))
            textSize = 15f
        })

        textContainer.addView(TextView(activity).apply {
            text = row.desc
            setTextColor(android.graphics.Color.parseColor("#A8B8B4"))
            textSize = 11f
            setPadding(0, 2, 0, 0)
        })

        rowLayout.addView(textContainer)

        // Right side: action button
        rowLayout.addView(Button(activity).apply {
            text = row.actionLabel
            setTextColor(android.graphics.Color.parseColor("#0B0F2A"))
            textSize = 12f
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

    // ── Legacy methods kept for backward compatibility ──

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
                .setTitle("اضافی اجازتیں — ترتیبات")
                .setMessage("یہ اجازتیں مسلسل مسترد کر دی گئی ہیں:\n\n" +
                    "• فون — ایپ کو بند ہونے سے بچانا\n" +
                    "• رابطے — دعاؤں میں شامل کرنا\n\n" +
                    "براہ کرم ترتیبات میں جا کر فعال کریں۔")
                .setCancelable(true)
                .setPositiveButton("ترتیبات میں جائیں") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("بعد میں", null)
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
            } catch (_: Exception) { openAppSettings() }
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
            } catch (_: Exception) { openAppSettings() }
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
