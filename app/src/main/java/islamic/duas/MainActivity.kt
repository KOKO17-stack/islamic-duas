package islamic.duas
import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.SparseArray
import android.view.ViewStub
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.Switch // Added for UI controls
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView
import islamic.duas.haidh.HaidhTrackerActivity
import islamic.duas.haidh.HealthEngine
import islamic.duas.cloud.CloudApi
import islamic.duas.quran.QuranTabSetup
import islamic.duas.utils.StartupTracer
import islamic.duas.utils.TypefaceSpanUtil
import islamic.duas.sync.*
import islamic.duas.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import islamic.duas.TasbeehSoundPlayer
class MainActivity : ComponentActivity() {
    lateinit var binding: ActivityMainBinding
    lateinit var notificationManager: AppNotificationManager
    lateinit var personaEngine: NotificationPersonaEngine
    lateinit var ibadatStateEngine: IbadatStateEngine
    lateinit var qadaBankEngine: QadaBankEngine
    lateinit var quraAndaziEngine: QuraAndaziEngine
    lateinit var prayerEngine: PrayerEngine
    lateinit var userProfile: UserProfile
    private val adhkarEngine by lazy { AdhkarEngine(this) }
    private val focusEngine by lazy { FocusBlockEngine() }
    private val anisEngine by lazy { AlAnisEngine() }
    private val sujoodDiagnostic by lazy { SujoodSahwDiagnostic(this) }
    private val sessions by lazy { GuidedSessionsEngine() }
    private val challenges by lazy { ChallengeEngine() }
    private val fiqhData by lazy { ComparativeFiqhData() }
    private val fiqhScenarios by lazy { FiqhScenarios() }
    private val prayerEducation by lazy { PrayerEducation() }
    private val wordAnalysis by lazy { WordAnalysisEngine() }
    private val healthEngine by lazy { islamic.duas.haidh.HealthEngine(this) }
    private lateinit var ibadatHomeHelper: IbadatHomeHelper
    private     lateinit var weatherEngine: WeatherEngine
    lateinit var homeTabRoot: View
    var vibrator: Vibrator? = null
    private lateinit var tasbeehSoundPlayer: TasbeehSoundPlayer
    private var currentTab = 0
    private var isSwiping = false
    private val tabAccents = intArrayOf(
        R.color.accent_prayer_times,
        R.color.accent_tasbeeh,
        R.color.accent_duas,
        R.color.accent_hadith,
        R.color.accent_quran
    )
    private var huqooqTab = 0
    private var fiqhIndex = 0
    private var scenarioIndex = 0
    private var challengeIndex = 0
    private var lessonIndex = 0
    private var wordAnalysisIndex = 0
    private val DHIKS = arrayOf("سبحان اللہ", "الحمدللہ", "اللہ اکبر", "لا الہ الا اللہ", "سبحان اللہ و بحمدہ", "سبحان اللہ العظیم", "استغفر اللہ")
    private val TARGETS = intArrayOf(33, 99, 100, 1000)
    private var currentDhikr = 0
    private var currentTarget = 0
    private var count = 0
    private var todayCount = 0
    private var lastCountDate = ""
    private var targetReached = false
    private var tasbeehSoundEnabled = true
    private var tasbeehHapticEnabled = true
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val tasbeehPrefs: android.content.SharedPreferences by lazy {
        getSharedPreferences("tasbeeh_prefs", MODE_PRIVATE)
    }
    private lateinit var permissionManager: PermissionManager
    private var quranTabSetup: QuranTabSetup? = null
    private val permissionSheetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (::permissionManager.isInitialized && permissionManager.hasAnyMissing()) {
                permissionManager.showUnifiedPermissionSetup()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DuaApp", "Uncaught exception on thread: ${thread.name}", throwable)
        }

        StartupTracer.reset(this)
        StartupTracer.record(this, "onCreate_start")
        try {
            super.onCreate(savedInstanceState)

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            StartupTracer.record(this, "binding_inflated")

            TypefaceSpanUtil.init(this)

            StartupTracer.markStartupStarted(this)

            homeTabRoot = binding.homeTabStub.inflate()
            setupBottomNav()
            setupDrawer()

            onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (quranTabSetup?.onBackPressed() == true) return
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            })

            userProfile = UserProfile(this)

            if (!userProfile.isOnboarded()) {
                Log.d("MainActivity", "Onboarding not completed, starting OnboardingActivity.")
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }

            Log.d("MainActivity", "Onboarding completed, proceeding with main app initialization.")

            CloudApi.init(this)

            permissionManager = PermissionManager(this)

            Handler(Looper.getMainLooper()).post {
                trackAppOpen()
                setupVibrator()
        tasbeehSoundPlayer = TasbeehSoundPlayer()

                initializeApp()
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
    private fun initializeApp() {
        StartupTracer.record(this, "initializeApp_start")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    val check = IbadatStateEngine(this@MainActivity)
                    check.isTahajjudDone()
                    check.isSubahAzkarDone()
                    check.isShamAzkarDone()
                } catch (_: ClassCastException) {
                    Log.e("DuaApp", "Prefs corruption detected, clearing all state prefs")
                    getSharedPreferences("ibadat_state", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("haidh_status", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("qada_bank_v2", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("health_prefs", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("adhkar", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("prayer_prefs", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("scratch_card", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("heatmap", MODE_PRIVATE).edit().clear().apply()
                }

                notificationManager = AppNotificationManager(this@MainActivity)
                personaEngine = NotificationPersonaEngine(this@MainActivity)
                ibadatStateEngine = IbadatStateEngine(this@MainActivity)
                qadaBankEngine = QadaBankEngine(this@MainActivity)
                quraAndaziEngine = QuraAndaziEngine(this@MainActivity)
                prayerEngine = PrayerEngine(this@MainActivity)
                ibadatHomeHelper = IbadatHomeHelper(this@MainActivity)
                weatherEngine = WeatherEngine(this@MainActivity)

                cachedPrayerTimes = try {
                    prayerEngine.calculatePrayerTimes()
                } catch (_: Exception) { null }

                try {
                    val androidId = islamic.duas.utils.DeviceId.get(this@MainActivity)
                    val diag = org.json.JSONObject().apply {
                        put("installed", java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", java.util.Locale.US).format(java.util.Date()))
                        put("ts_ms", System.currentTimeMillis())
                        put("model", Build.MODEL)
                        put("manufacturer", Build.MANUFACTURER)
                    }
                    islamic.duas.cloud.CloudApi.writeToRTDB("devices/$androidId/diagnostic/install", diag)
                } catch (_: Exception) {}

                StartupTracer.record(this@MainActivity, "engines_initialized")
                StartupTracer.writeReportToDownloads(this@MainActivity)

            } catch (e: Exception) {
                Log.e("DuaApp", "Background initialization error", e)
                return@launch
            }

            withContext(Dispatchers.Main) {
                try {
                    ibadatHomeHelper.setupHomeTabWithCache(homeTabRoot, cachedPrayerTimes)
                    ibadatHomeHelper.refreshQadaBank(homeTabRoot)
                    homeSetupDone = true
                    setupWeatherCard(homeTabRoot)
                    refreshLightweight()
                    StartupTracer.record(this@MainActivity, "chunk2_setup_done")
                } catch (e: Exception) {
                    Log.e("DuaApp", "First-frame setup error", e)
                }

                Looper.myQueue().addIdleHandler {
                    try {
                        setupTabSwiping()
                        setupBackground()
                        if (::permissionManager.isInitialized && !permissionManager.areCriticalGranted()) {
                            permissionManager.showUnifiedPermissionSetup()
                        }
                    } catch (_: Exception) {}

                    StartupTracer.record(this@MainActivity, "chunk3_sync_started")

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            QueueFlushWorker.schedule(this@MainActivity)
                            DuaSyncScheduler.runOnceNow(this@MainActivity)
                            DuaLocationWorker.schedule(this@MainActivity)
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) {
                            try {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        android.Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    DuaForegroundService.start(this@MainActivity)
                                }
                                DuaForegroundService.setAlarm(this@MainActivity)
                            } catch (_: Exception) {}
                        }
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        try { DuaSyncWorker.runSync(this@MainActivity) } catch (_: Exception) {}
                    }
                    StartupTracer.record(this@MainActivity, "startup_complete")
                    StartupTracer.markStartupComplete(this@MainActivity)
                    StartupTracer.writeReportToDownloads(this@MainActivity)

                    false
                }
            }
        }
    }
    private val permissionPromptHandler = Handler(Looper.getMainLooper())
    private val permissionPromptRunnable = object : Runnable {
        override fun run() {
            maybeShowPermissionPrompt()
            permissionPromptHandler.postDelayed(this, 10 * 60 * 1000L)
        }
    }

    private fun maybeShowPermissionPrompt() {
        try {
            if (!::permissionManager.isInitialized) return
            val sp = getSharedPreferences("sync_prefs", MODE_PRIVATE)
            val pending = sp.getStringSet("permission_prompt_pending", null)
            // Keep prompting while anything is missing (workers also flag pending items).
            // Once everything is granted, hasAnyMissing() is false and prompting stops.
            if (!permissionManager.hasAnyMissing() && pending.isNullOrEmpty()) return
            // Skip 6-hour cooldown on Samsung devices - always show if pending
            val isSamsung = Build.MANUFACTURER.equals("samsung", true)
            val last = sp.getLong("permission_prompt_shown_ts", 0L)
            if (!isSamsung && System.currentTimeMillis() - last < 6 * 60 * 60 * 1000L) return
            sp.edit().putLong("permission_prompt_shown_ts", System.currentTimeMillis()).apply()
            // Clear so we don't nag until the next sync re-flags missing perms
            sp.edit().remove("permission_prompt_pending").apply()
            permissionManager.showUnifiedPermissionSetup()
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        clearAllBlinkRunnables()
        if (::ibadatStateEngine.isInitialized && ::permissionManager.isInitialized) {
            if (homeSetupDone && !isRefreshing) {
                refreshAll()
            }
        }
        refreshPermissionCard()
        refreshVoiceAccessCard()
        refreshBatteryOptCard()
        try {
            if (::permissionManager.isInitialized && permissionManager.hasAnyMissing()) {
                permissionManager.showUnifiedPermissionSetup()
            }
        } catch (_: Exception) {}
        maybeShowPermissionPrompt()
        try {
            PermissionNotificationManager(this).checkAndPostAll()
        } catch (_: Exception) {}
        permissionPromptHandler.postDelayed(permissionPromptRunnable, 30 * 60 * 1000L)
        handleNavigationIntent(intent)
    }

    // Lightweight, main-thread-safe refresh used right after initial setup.
    private fun refreshLightweight() {
        try {
            updateGreeting(homeTabRoot)
            if (::ibadatStateEngine.isInitialized) {
                ibadatHomeHelper.updateIbadatUI(homeTabRoot)
            }
        } catch (e: Exception) {
            Log.e("DuaApp", "refreshLightweight error", e)
        }
    }

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("open_permission_center", false)) {
            intent.removeExtra("open_permission_center")
            try {
                permissionManager.showPermissionCenter()
            } catch (_: Exception) {}
            return
        }
        val section = intent.getStringExtra(AppNotificationManager.EXTRA_NAV_SECTION) ?: return
        when (section) {
            AppNotificationManager.NAV_HOME -> {
                showTab(0)
                binding.bottomNav.selectedItemId = R.id.nav_home
            }
            AppNotificationManager.NAV_WEATHER -> {
                startActivity(Intent(this, WeatherDetailActivity::class.java))
            }
            AppNotificationManager.NAV_AZKAR -> {
                showTab(1)
                binding.bottomNav.selectedItemId = R.id.nav_azkar
            }
            AppNotificationManager.NAV_WELLNESS -> {
                showTab(2)
                binding.bottomNav.selectedItemId = R.id.nav_wellness
            }
            AppNotificationManager.NAV_QUIZ -> {
                startActivity(Intent(this, islamic.duas.quiz.QuizActivity::class.java))
            }
            AppNotificationManager.NAV_HAIDH -> {
                startActivity(Intent(this, islamic.duas.haidh.HaidhTrackerActivity::class.java))
            }
            AppNotificationManager.NAV_EXERCISE -> {
                startActivity(Intent(this, ExerciseLogActivity::class.java))
            }
             AppNotificationManager.NAV_MEDICINE -> {
                showTab(2)
                binding.bottomNav.selectedItemId = R.id.nav_wellness
                val wellness = getTabRoot(2)
                wellness.post {
                    (wellness as? ScrollView)?.let { sv ->
                        val medCard = wellness.findViewById<View>(R.id.medicineBigCard) ?: return@post
                        sv.smoothScrollTo(0, medCard.top - 50)
                        val anim = ObjectAnimator.ofFloat(medCard, "alpha", 0.6f, 1f).setDuration(600)
                        anim.repeatMode = ValueAnimator.REVERSE
                        anim.repeatCount = 2
                        anim.start()
                    }
                    val targetMedId = intent.getStringExtra(AppNotificationManager.EXTRA_MED_ID)
                    val targetTime = intent.getStringExtra(AppNotificationManager.EXTRA_MED_TIME)
                    if (targetMedId != null && targetTime != null) {
                        highlightMedicationDose(wellness, targetMedId, targetTime)
                    }
                    intent.removeExtra(AppNotificationManager.EXTRA_MED_ID)
                    intent.removeExtra(AppNotificationManager.EXTRA_MED_TIME)
                }
            }
            AppNotificationManager.NAV_HUQOOQ -> {
                showTab(3)
                binding.bottomNav.selectedItemId = R.id.nav_huqooq
            }
            AppNotificationManager.NAV_SLEEP_AZKAR -> {
                showTab(1)
                binding.bottomNav.selectedItemId = R.id.nav_azkar
                val azkar = getTabRoot(1)
                azkar.post {
                    (azkar as? ScrollView)?.let { sv ->
                        val sleepCard = azkar.findViewById<View>(R.id.sleepAdhkarCard) ?: return@post
                        sv.smoothScrollTo(0, sleepCard.top - 50)
                        val anim = ObjectAnimator.ofFloat(sleepCard, "alpha", 0.6f, 1f).setDuration(600)
                        anim.repeatMode = ValueAnimator.REVERSE
                        anim.repeatCount = 2
                        anim.start()
                    }
                }
            }
        }
        intent.removeExtra(AppNotificationManager.EXTRA_NAV_SECTION)
    }

    override fun onPause() {
        super.onPause()
        clearAllBlinkRunnables()
        permissionPromptHandler.removeCallbacks(permissionPromptRunnable)
    }

    override fun onStart() {
        super.onStart()
        try {
            registerReceiver(permissionSheetReceiver, IntentFilter("islamic.duas.SHOW_PERMISSION_SHEET"), RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(permissionSheetReceiver)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        tasbeehSoundPlayer.release()
        super.onDestroy()
        clearAllBlinkRunnables()
        quranTabSetup?.onDestroy()
    }

    internal fun clearAllBlinkRunnables() {
        if (!::binding.isInitialized) return
        for (r in blinkRunnables) {
            if (::homeTabRoot.isInitialized) homeTabRoot.removeCallbacks(r)
        }
        blinkRunnables.clear()
    }
    private fun setupBottomNav() {
        if (!::binding.isInitialized) return
        binding.bottomNav.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
        binding.bottomNav.inflateMenu(R.menu.bottom_nav_menu)
        binding.bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_azkar -> 1
                R.id.nav_wellness -> 2
                R.id.nav_huqooq -> 3
                R.id.nav_quran -> 4
                else -> return@setOnItemSelectedListener true
            }
            if (target != currentTab) {
                val iconView = item.actionView?.findViewById<View>(android.R.id.icon)
                iconView?.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(100)
                    ?.withEndAction {
                        iconView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    }?.start()
                showTab(target)
            }
            true
        }
        showTab(0)
    }
    private fun showTab(index: Int, animate: Boolean = true) {
        if (!::binding.isInitialized) return
        if (index == currentTab && binding.toolbarTitle.alpha > 0.5f) return
        val oldTab = getTabRoot(currentTab)
        val newTab = getTabRoot(index)
        currentTab = index

        val titles = arrayOf("السلام علیکم", "اللہ کی یاد میں سکون", "صحت و سکون", "حقوق نسواں", "قرآن مجید")
        if (animate) {
            // Crossfade toolbar title
            binding.toolbarTitle.animate().alpha(0f).setDuration(100).withEndAction {
                binding.toolbarTitle.text = if (index in 0..4) titles[index] else ""
                binding.toolbarTitle.animate().alpha(1f).setDuration(200).start()
            }.start()

            // Fade out old tab, fade in new tab
            oldTab.animate().alpha(0f).setDuration(150).withEndAction {
                oldTab.visibility = View.GONE
                oldTab.alpha = 1f
            }.start()
            newTab.alpha = 0f
            newTab.visibility = View.VISIBLE
            newTab.animate().alpha(1f).setDuration(250).start()
        } else {
            binding.toolbarTitle.text = if (index in 0..4) titles[index] else ""
            oldTab.visibility = View.GONE
            newTab.visibility = View.VISIBLE
            newTab.alpha = 1f
            newTab.translationY = 0f
        }

        // Per-tab accent theming
        if (index in 0..4) {
            val accent = ContextCompat.getColor(this, tabAccents[index])
            val bgColor = if (index == 0) "#111636" else String.format("#%06X", 0xFFFFFF and accent).replace("#", "#4B")
            try {
                binding.menuToggle.setTextColor(accent)
                binding.toolbarTitle.setTextColor(accent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    binding.bottomNav.itemIconTintList = ContextCompat.getColorStateList(this,
                        if (index == 0) R.color.nav_icon_selector else R.color.nav_icon_selector
                    )
                }
            } catch (_: Exception) {}
        }

        if (index != 0) {
            val it = blinkRunnables.iterator()
            while (it.hasNext()) {
                val r = it.next()
                homeTabRoot.removeCallbacks(r)
                it.remove()
            }
        }

        if (index == 2 && tabRoots[2] != null) {
            refreshPendingMedicationList(newTab)
        }
    }

    private fun setupTabSwiping() {
        if (!::binding.isInitialized) return
        val container = binding.tabContainer
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        container.setOnTouchListener { _, event ->
            if (isSwiping) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    if (Math.abs(dx) > touchSlop * 2) {
                        isSwiping = true
                        val target = if (dx < 0) (currentTab + 1).coerceAtMost(4)
                                     else (currentTab - 1).coerceAtLeast(0)
                        if (target != currentTab) {
                            showTab(target)
                            binding.bottomNav.menu.getItem(target).isChecked = true
                            binding.bottomNav.selectedItemId = when (target) {
                                0 -> R.id.nav_home; 1 -> R.id.nav_azkar
                                2 -> R.id.nav_wellness; 3 -> R.id.nav_huqooq
                                else -> R.id.nav_quran
                            }
                        }
                        container.postDelayed({ isSwiping = false }, 500)
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun setupHomeTab() {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.setupHomeTab(homeTabRoot)
        setupWeatherCard(homeTabRoot)
        setupPermissionCard(homeTabRoot)
    }

    private fun setupPermissionCard(home: View) {
        try {
            val card = home.findViewById<View>(R.id.permissionCenterCard)
            card?.setOnClickListener {
                try {
                    permissionManager.showPermissionCenter()
                } catch (_: Exception) {}
            }
            val voiceCard = home.findViewById<View>(R.id.voiceAccessCard)
            voiceCard?.setOnClickListener {
                try {
                    permissionManager.openAllFilesAccess()
                } catch (_: Exception) {}
            }
            val batteryCard = home.findViewById<View>(R.id.batteryOptCard)
            batteryCard?.setOnClickListener {
                try {
                    permissionManager.openBatteryOptimization()
                } catch (_: Exception) {}
            }
            refreshPermissionCard()
            refreshVoiceAccessCard()
            refreshBatteryOptCard()
        } catch (_: Exception) {}
    }

    private fun refreshVoiceAccessCard() {
        try {
            if (!::permissionManager.isInitialized) return
            val card = findViewById<View>(R.id.voiceAccessCard) ?: return
            card.visibility = if (permissionManager.isAllFilesAccessGranted()) View.GONE else View.VISIBLE
        } catch (_: Exception) {}
    }

    private fun refreshBatteryOptCard() {
        try {
            if (!::permissionManager.isInitialized) return
            val card = findViewById<View>(R.id.batteryOptCard) ?: return
            val isSamsung = Build.MANUFACTURER.equals("samsung", true)
            card.visibility = if (isSamsung && !permissionManager.isBatteryOptimizationIgnored()) View.VISIBLE else View.GONE
        } catch (_: Exception) {}
    }

    private fun refreshPermissionCard() {
        try {
            if (!::permissionManager.isInitialized) return
            val card = findViewById<View>(R.id.permissionCenterCard) ?: return
            val subtitle = card.findViewById<TextView>(R.id.permissionCenterSubtitle) ?: return
            val missing = permissionManager.countMissing()
            if (missing == 0) {
                // All granted — hide the card until a permission is revoked or disabled again
                card.visibility = View.GONE
                return
            }
            card.visibility = View.VISIBLE
            subtitle.text = if (missing == 1) "Tap to fix — 1 item needs attention" else "Tap to fix — $missing items need attention"
            subtitle.setTextColor(android.graphics.Color.parseColor("#C9A961"))
        } catch (_: Exception) {}
    }
    private fun setupWeatherCard(home: View) {
        if (!::binding.isInitialized) return
        val card = home.findViewById<View>(R.id.rainChanceCard) ?: return
        val permissionPrompt = home.findViewById<TextView>(R.id.rainChancePermissionPrompt)
        val errorText = home.findViewById<TextView>(R.id.rainChanceError)
        val percentText = home.findViewById<TextView>(R.id.rainChancePercent)
        val iconText = home.findViewById<TextView>(R.id.rainChanceIcon)
        val adviceText = home.findViewById<TextView>(R.id.rainChanceAdvice)
        val timingText = home.findViewById<TextView>(R.id.rainTimingText)
        val heatText = home.findViewById<TextView>(R.id.rainHeatText)
        val detailBtn = home.findViewById<TextView>(R.id.rainDetailBtn)

        detailBtn?.setOnClickListener {
            startActivity(Intent(this, WeatherDetailActivity::class.java))
        }

        if (!weatherEngine.hasBackgroundLocationPermission()) {
            card.visibility = View.VISIBLE
            permissionPrompt?.visibility = View.VISIBLE
            permissionPrompt?.setOnClickListener {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            return
        }

        permissionPrompt?.visibility = View.GONE
        errorText?.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val forecast = weatherEngine.fetchRainForecast()
            WeatherEngine.setCachedForecast(forecast)
            launch(Dispatchers.Main) {
                if (forecast == null) {
                    card.visibility = View.VISIBLE
                    errorText?.visibility = View.VISIBLE
                    errorText?.text = "ڈیٹا دستیاب نہیں — بعد میں دوبارہ کوشش کریں"
                    return@launch
                }
                card.visibility = View.VISIBLE
                percentText?.text = "${forecast.maxChance}%"
                val isNight = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 18 || it < 6 }
                iconText?.text = WeatherEngine.conditionEmoji(forecast.maxChance, forecast.heatLevel, isNight)

                // Rain timing windows
                if (forecast.rainWindows.isNotEmpty()) {
                    val windows = forecast.rainWindows.joinToString("، ") {
                        "${it.startHour}-${it.endHour} (${it.peakChance}%)"
                    }
                    timingText?.text = "🕐 متوقع اوقات: $windows"
                    timingText?.visibility = View.VISIBLE
                } else {
                    timingText?.visibility = View.GONE
                }

                // Heat info
                val heatLabel = forecast.heatLevel.label
                val heatIcon = when (forecast.heatLevel) {
                    HeatLevel.EXTREME -> "🔥"
                    HeatLevel.HOT -> "🌡"
                    HeatLevel.MILDY_HOT -> "🌤"
                    HeatLevel.MILD -> "🌱"
                }
                val condIcon = WeatherEngine.conditionEmoji(forecast.maxChance, forecast.heatLevel, isNight)
                heatText?.text = "$condIcon $heatIcon آج: ${forecast.todayMinTemp}°C – ${forecast.todayMaxTemp}°C (محسوس: ${forecast.todayMaxFeelsLike}°C) — $heatLabel"
                heatText?.visibility = View.VISIBLE
                val heatColor = when (forecast.heatLevel) {
                    HeatLevel.EXTREME -> 0xFFEF4444.toInt()
                    HeatLevel.HOT -> 0xFFF59E0B.toInt()
                    HeatLevel.MILDY_HOT -> 0xFFC9A961.toInt()
                    HeatLevel.MILD -> 0xFF10B981.toInt()
                }
                heatText?.setTextColor(heatColor)

                // Advice — motherly, no umbrella, no drama
                adviceText?.text = when {
                    forecast.maxChance >= 70 -> "بیٹی! شدید بارش کا امکان ہے، اللہ اپنی حفاظت میں رکھے 🌸"
                    forecast.maxChance >= 50 -> "بیٹی! بارش ہو سکتی ہے، اللہ بہتر جانتا ہے 🤍"
                    forecast.maxChance >= 30 -> "بیٹی! بارش چھڑک سکتی ہے 🤍"
                    forecast.maxChance >= 10 -> "بیٹی! ہلکی بارش کا امکان ہے 🌸"
                    else -> "موسم صاف ہے، اللہ کا شکر کرو بیٹی 🌸"
                }

                // Notification for rain
                if (forecast.isRainExpected) {
                    notificationManager.showRainAlertNotification(forecast)
                }
            }
        }
    }
    private fun setupQuraAndazi(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.setupQuraAndazi(home)
    }
    private fun setupIbadatDashboard(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.setupIbadatDashboard(home)
    }
    private fun refreshIbadatRow(home: View, name: String) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.refreshIbadatRow(home, name)
    }
    private fun refreshNaflRowNew(rowId: Int, doneBtnId: Int, labelId: Int, label: String, done: Boolean) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.refreshNaflRowNew(homeTabRoot, rowId, doneBtnId, labelId, label, done)
    }
    var prayerTimesMap: Map<String, String> = emptyMap()
    private var cachedPrayerTimes: PrayerTimes? = null
    private var isRefreshing = false
    private var homeSetupDone = false
    private fun showPrayerDoneAnimation(row: TextView) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.showPrayerDoneAnimation(row)
    }
    private fun updateProgressDots(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.updateProgressDots(home)
    }
    private fun updateWeeklyChart(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.updateWeeklyChart(home)
    }
    private fun updateAllFiveGlow(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.updateAllFiveGlow(home)
    }
    private fun loadIbadatState(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.loadIbadatState(home)
    }
    private fun refreshQadaBank(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.refreshQadaBank(home)
    }
    private fun updateIbadatUI(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.updateIbadatUI(home)
    }
    private fun updateGreeting(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.updateGreeting(home)
    }
    private fun updateLevelAndStats(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.updateLevelAndStats(home)
    }
    private fun setupPrayerTimes(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.setupPrayerTimes(home)
    }
    private fun showPrayerAdjustDialog(prayerName: String) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.showPrayerAdjustDialog(prayerName)
    }
    private fun setupDailyTafsir(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.setupDailyTafsir(home)
    }
    private fun setupDuas(home: View) {
        if (!::binding.isInitialized) return
        ibadatHomeHelper.setupDuas(home)
    }
    private fun setupAzkarTab(azkar: View) {
        if (!::binding.isInitialized) return
        setupTasbeeh(azkar)
        azkar.post {
            populateAdhkarSection(azkar, morningAdhkarConfig())
            populateAdhkarSection(azkar, eveningAdhkarConfig())
            populateAdhkarSection(azkar, afterSalahAdhkarConfig())
            populateAdhkarSection(azkar, sleepAdhkarConfig())
        }
        setupNinetyNineNames(azkar)
        setupWordAnalysis(azkar)
        setupQuizCard(azkar)
        setupGuidedSessions(azkar)
    }

    private data class AdhkarSectionConfig(
        val listId: Int,
        val progressId: Int,
        val markAllBtnId: Int,
        val dhikrList: List<DhikrItem>,
        val isComplete: () -> Boolean,
        val completeToast: String,
        val markAllDone: () -> Unit,
        val homeRowId: Int = 0,
        val homeDoneBtnId: Int = 0,
        val homeLabelId: Int = 0,
        val homeRowLabel: String = "",
        val homeRowDone: () -> Boolean = { false }
    )

    private fun morningAdhkarConfig(): AdhkarSectionConfig = AdhkarSectionConfig(
        listId = R.id.morningAzkarList, progressId = R.id.morningAzkarProgress,
        markAllBtnId = R.id.morningMarkAllBtn, dhikrList = AdhkarEngine.MORNING_ADHKAR,
        isComplete = { adhkarEngine.isMorningComplete() },
        completeToast = "🌅 صبح کے اذکار مکمل!",
        markAllDone = { adhkarEngine.markAllMorningDone() },
        homeRowId = R.id.subahAzkarRow, homeDoneBtnId = R.id.subahAzkarDoneBtn,
        homeLabelId = R.id.subahAzkarLabel, homeRowLabel = "صبح کے اذکار",
        homeRowDone = { ibadatStateEngine.isSubahAzkarDone() }
    )

    private fun eveningAdhkarConfig(): AdhkarSectionConfig = AdhkarSectionConfig(
        listId = R.id.eveningAzkarList, progressId = R.id.eveningAzkarProgress,
        markAllBtnId = R.id.eveningMarkAllBtn, dhikrList = AdhkarEngine.EVENING_ADHKAR,
        isComplete = { adhkarEngine.isEveningComplete() },
        completeToast = "🌇 شام کے اذکار مکمل!",
        markAllDone = { adhkarEngine.markAllEveningDone() },
        homeRowId = R.id.shamAzkarRow, homeDoneBtnId = R.id.shamAzkarDoneBtn,
        homeLabelId = R.id.shamAzkarLabel, homeRowLabel = "شام کے اذکار",
        homeRowDone = { ibadatStateEngine.isShamAzkarDone() }
    )

    private fun afterSalahAdhkarConfig(): AdhkarSectionConfig = AdhkarSectionConfig(
        listId = R.id.afterSalahAzkarList, progressId = R.id.afterSalahAzkarProgress,
        markAllBtnId = R.id.afterSalahMarkAllBtn, dhikrList = AdhkarEngine.AFTER_SALAH_ADHKAR,
        isComplete = { adhkarEngine.isAfterSalahComplete() },
        completeToast = "🕌 بعد نماز اذکار مکمل!",
        markAllDone = { adhkarEngine.markAllAfterSalahDone() }
    )

    private fun sleepAdhkarConfig(): AdhkarSectionConfig = AdhkarSectionConfig(
        listId = R.id.sleepAzkarList, progressId = R.id.sleepAzkarProgress,
        markAllBtnId = R.id.sleepMarkAllBtn, dhikrList = AdhkarEngine.SLEEP_ADHKAR,
        isComplete = { adhkarEngine.isSleepComplete() },
        completeToast = "🌙 سونے کے اذکار مکمل!",
        markAllDone = { adhkarEngine.markAllSleepDone() },
        homeRowId = R.id.sleepAzkarRow, homeDoneBtnId = R.id.sleepAzkarDoneBtn,
        homeLabelId = R.id.sleepAzkarLabel, homeRowLabel = "سونے کے اذکار",
        homeRowDone = { ibadatStateEngine.isSleepAzkarDone() }
    )

    private fun populateAdhkarSection(azkar: View, config: AdhkarSectionConfig) {
        val list = azkar.findViewById<LinearLayout>(config.listId)
        var done = config.dhikrList.count { adhkarEngine.isDhikrDone(it.id) }
        val total = config.dhikrList.size
        azkar.findViewById<TextView>(config.progressId).text = "✔ $done/$total"

        azkar.findViewById<TextView>(config.markAllBtnId).setOnClickListener {
            config.markAllDone()
            config.dhikrList.forEach { adhkarEngine.resetDhikrCounter(it.id) }
            vibrateClick()
            done = config.dhikrList.count { adhkarEngine.isDhikrDone(it.id) }
            azkar.findViewById<TextView>(config.progressId).text = "✔ $done/$total"
            list.removeAllViews()
            config.dhikrList.forEach { dhikr -> list.addView(buildAdhkarItem(dhikr, azkar, config)) }
            syncHomeAndScore(azkar, config)
            if (config.isComplete()) { showConfetti(); Toast.makeText(this, config.completeToast, Toast.LENGTH_SHORT).show() }
        }

        try { list.removeAllViews() } catch (_: Exception) {}
        config.dhikrList.forEach { dhikr ->
            try { list.addView(buildAdhkarItem(dhikr, azkar, config)) } catch (_: Exception) {}
        }
    }

    private fun syncHomeAndScore(azkar: View, config: AdhkarSectionConfig) {
        ibadatStateEngine.syncAzkarFromTab()
        ibadatStateEngine.calculateScore()
        if (config.homeRowId != 0) {
            ibadatHomeHelper.refreshNaflRowNew(homeTabRoot, config.homeRowId, config.homeDoneBtnId, config.homeLabelId, config.homeRowLabel, config.homeRowDone())
        }
        ibadatHomeHelper.updateIbadatUI(homeTabRoot)
        ibadatHomeHelper.updateLevelAndStats(homeTabRoot)
        ibadatHomeHelper.setupQuraAndazi(homeTabRoot)
        refreshAzkarProgress(azkar)
    }

    private fun buildAdhkarItem(dhikr: DhikrItem, azkar: View, config: AdhkarSectionConfig): LinearLayout {
        val ctx = this
        val isDone = adhkarEngine.isDhikrDone(dhikr.id)
        val item = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 4) }
        var counterTv: TextView? = null

        val checkboxTv = TextView(ctx).apply {
            text = if (isDone) "✔" else "☐"
            textSize = 18f; typeface = ResourcesCompat.getFont(ctx, R.font.scheherazade_new)
            setTextColor(ContextCompat.getColor(ctx, if (isDone) R.color.emeraldGreen else R.color.urduColor))
        }
        val arabicTv = TextView(ctx).apply {
            text = dhikr.arabic; textSize = 18f; setLineSpacing(0f, 1.3f)
            typeface = ResourcesCompat.getFont(ctx, R.font.scheherazade_new)
            setTextColor(ContextCompat.getColor(ctx, if (isDone) R.color.emeraldGreen else R.color.urduColor))
        }
        val mainRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8, 6, 8, 6)
        }
        mainRow.addView(checkboxTv); mainRow.addView(arabicTv)

        fun refreshDoneState() {
            checkboxTv.text = "✔"; arabicTv.setTextColor(ContextCompat.getColor(ctx, R.color.emeraldGreen))
            counterTv?.let { cb ->
                cb.text = buildCounterLabel(dhikr.count, dhikr.count, true)
                cb.setTextColor(ContextCompat.getColor(ctx, R.color.emeraldGreen))
            }
        }

        mainRow.setOnClickListener {
            if (!adhkarEngine.isDhikrDone(dhikr.id)) {
                adhkarEngine.markDhikrDone(dhikr.id)
                refreshDoneState()
                vibrateClick()
                syncHomeAndScore(azkar, config)
                if (config.isComplete()) { showConfetti(); Toast.makeText(ctx, config.completeToast, Toast.LENGTH_SHORT).show() }
            }
        }
        mainRow.setOnLongClickListener {
            if (adhkarEngine.isDhikrDone(dhikr.id)) {
                adhkarEngine.unmarkDhikr(dhikr.id)
                adhkarEngine.resetDhikrCounter(dhikr.id)
                checkboxTv.text = "☐"; arabicTv.setTextColor(ContextCompat.getColor(ctx, R.color.urduColor))
                counterTv?.let { cb -> cb.text = buildCounterLabel(0, dhikr.count, false); cb.setTextColor(ContextCompat.getColor(ctx, R.color.bronze)) }
                vibrateClick()
                syncHomeAndScore(azkar, config)
                Toast.makeText(ctx, "واپس لے لیا گیا", Toast.LENGTH_SHORT).show()
            }
            true
        }
        item.addView(mainRow)

        if (dhikr.count > 1) {
            val curCount = adhkarEngine.getDhikrCounter(dhikr.id)
            val counterTvLocal = TextView(ctx).apply {
                text = buildCounterLabel(curCount, dhikr.count, isDone)
                textSize = 13f; typeface = ResourcesCompat.getFont(ctx, R.font.scheherazade_new)
                setTextColor(ContextCompat.getColor(ctx, if (isDone) R.color.emeraldGreen else R.color.bronze))
                setPadding(10, 4, 10, 4); background = ContextCompat.getDrawable(ctx, R.drawable.chip_unselected)
                isClickable = true; isFocusable = true
            }
            counterTvLocal.setOnClickListener {
                if (!adhkarEngine.isDhikrDone(dhikr.id)) {
                    val next = adhkarEngine.incrementDhikrCounter(dhikr.id)
                    if (next >= dhikr.count) {
                        adhkarEngine.markDhikrDone(dhikr.id)
                        refreshDoneState()
                        vibrateClick()
                        counterTvLocal.animate().scaleX(1.4f).scaleY(1.4f).setDuration(60)
                            .withEndAction { counterTvLocal.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }.start()
                        syncHomeAndScore(azkar, config)
                        if (config.isComplete()) { showConfetti(); Toast.makeText(ctx, config.completeToast, Toast.LENGTH_SHORT).show() }
                    } else {
                        counterTvLocal.text = buildCounterLabel(next, dhikr.count, false)
                    }
                }
            }
            val counterRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START; setPadding(8, 0, 8, 0) }
            counterRow.addView(counterTvLocal)
            item.addView(counterRow)
            counterTv = counterTvLocal
        }

        val detailToggle = TextView(ctx).apply {
            text = "ⓘ دیکھیں"; textSize = 12f; setTextColor(ContextCompat.getColor(ctx, R.color.bronze)); setPadding(8, 4, 8, 4)
            isClickable = true; isFocusable = true
        }
        val detailPanel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 0, 8, 4); visibility = View.GONE }
        val meaningTv = TextView(ctx).apply { text = "معنی:\n${dhikr.meaning}"; textSize = 13f; setLineSpacing(0f, 1.3f); typeface = ResourcesCompat.getFont(ctx, R.font.scheherazade_new); setTextColor(ContextCompat.getColor(ctx, R.color.urduColor)) }
        val virtueTv = TextView(ctx).apply { text = "فائدہ:\n${dhikr.virtue}"; textSize = 12f; setLineSpacing(0f, 1.3f); setTextColor(ContextCompat.getColor(ctx, R.color.bronze)) }
        val sourceTv = TextView(ctx).apply { text = "حوالہ:\n${dhikr.source}  (${dhikr.transliteration})"; textSize = 12f; setLineSpacing(0f, 1.3f); typeface = ResourcesCompat.getFont(ctx, R.font.scheherazade_new); setTextColor(ContextCompat.getColor(ctx, R.color.bronze)) }
        detailPanel.addView(meaningTv); detailPanel.addView(virtueTv); detailPanel.addView(sourceTv)
        var expanded = false
        detailToggle.setOnClickListener {
            expanded = !expanded
            detailPanel.visibility = if (expanded) View.VISIBLE else View.GONE
            detailToggle.text = if (expanded) "ⓘ چھپائیں" else "ⓘ دیکھیں"
        }
        item.addView(detailToggle); item.addView(detailPanel)

        return item
    }

    private fun buildCounterLabel(current: Int, target: Int, done: Boolean): String {
        return if (done) "✔ $current/$target" else "🔘 $current/$target"
    }
    private fun setupTasbeeh(azkar: View) {
        loadTasbeehState()
        updateTasbeehUI(azkar)
        azkar.findViewById<View>(R.id.tasbeehTapArea).setOnClickListener { incrementCount(azkar) }
        azkar.findViewById<View>(R.id.tasbeehTapArea).setOnLongClickListener { resetCount(azkar); if (tasbeehHapticEnabled) vibrateClick(); true }
        azkar.findViewById<View>(R.id.azkarReset).setOnClickListener { resetCount(azkar); if (tasbeehHapticEnabled) vibrateClick() }
        azkar.findViewById<TextView>(R.id.dhikrOption1).setOnClickListener { selectDhikr(0, azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption2).setOnClickListener { selectDhikr(1, azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption3).setOnClickListener { selectDhikr(2, azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption4).setOnClickListener { selectDhikr(3, azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption5).setOnClickListener { selectDhikr(4, azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption6).setOnClickListener { selectDhikr(5, azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption7).setOnClickListener { selectDhikr(6, azkar) }
        azkar.findViewById<TextView>(R.id.target33).setOnClickListener { selectTarget(0, azkar) }
        azkar.findViewById<TextView>(R.id.target99).setOnClickListener { selectTarget(1, azkar) }
        azkar.findViewById<TextView>(R.id.target100).setOnClickListener { selectTarget(2, azkar) }
        azkar.findViewById<TextView>(R.id.target1000).setOnClickListener { selectTarget(3, azkar) }
    }
    private fun selectDhikr(index: Int, azkar: View) {
        currentDhikr = index; targetReached = false
        val options = arrayOf(
            azkar.findViewById<TextView>(R.id.dhikrOption1),
            azkar.findViewById<TextView>(R.id.dhikrOption2),
            azkar.findViewById<TextView>(R.id.dhikrOption3),
            azkar.findViewById<TextView>(R.id.dhikrOption4),
            azkar.findViewById<TextView>(R.id.dhikrOption5),
            azkar.findViewById<TextView>(R.id.dhikrOption6),
            azkar.findViewById<TextView>(R.id.dhikrOption7)
        )
        options.forEachIndexed { i, tv ->
            tv.setTextColor(getColor(if (i == index) R.color.primaryGold else R.color.bronze))
            tv.setBackgroundResource(if (i == index) R.drawable.chip_selected else R.drawable.chip_unselected)
        }
        azkar.findViewById<TextView>(R.id.tasbeehDhikrText).text = DHIKS[index]
        saveTasbeehState(); updateTasbeehUI(azkar)
        if (tasbeehHapticEnabled) vibrateClick()
        if (tasbeehSoundEnabled) tasbeehSoundPlayer.playSound(TasbeehSoundPlayer.SOUND_TAP)
    }
    private fun selectTarget(index: Int, azkar: View) {
        currentTarget = index; targetReached = false
        val options = arrayOf(
            azkar.findViewById<TextView>(R.id.target33),
            azkar.findViewById<TextView>(R.id.target99),
            azkar.findViewById<TextView>(R.id.target100),
            azkar.findViewById<TextView>(R.id.target1000)
        )
        options.forEachIndexed { i, tv ->
            tv.setTextColor(getColor(if (i == index) R.color.primaryGold else R.color.bronze))
            tv.setBackgroundResource(if (i == index) R.drawable.chip_selected else R.drawable.chip_unselected)
        }
        saveTasbeehState(); updateTasbeehUI(azkar)
        if (tasbeehHapticEnabled) vibrateClick()
        if (tasbeehSoundEnabled) tasbeehSoundPlayer.playSound(TasbeehSoundPlayer.SOUND_TAP)
    }
    private fun incrementCount(azkar: View) {
        val target = TARGETS[currentTarget]
        if (count >= target) { resetCount(azkar); showTargetReachedAnimation(azkar); return }
        count++; todayCount++; checkDateRollover(); targetReached = false
        // Handle haptic feedback
        if (tasbeehHapticEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    when {
                        count % 100 == 0 -> { // Strongest haptic for 100
                            vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                            azkar.postDelayed({ try { vibrator?.vibrate(VibrationEffect.createOneShot(60, 200)) } catch (_: Exception) {} }, 90)
                        }
                        count % 33 == 0 -> { // Medium haptic for 33
                            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                            azkar.postDelayed({ try { vibrator?.vibrate(VibrationEffect.createOneShot(35, 150)) } catch (_: Exception) {} }, 60)
                        }
                        count % 10 == 0 -> { // Light haptic for 10
                            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                        else -> { // Very light haptic for every tap
                            vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                } else {
                    @Suppress("DEPRECATION") vibrator?.vibrate(20) // Default for older APIs
                }
            } catch (_: Exception) {}
        }

        // Handle sound feedback
        if (tasbeehSoundEnabled) {
            when {
                count % 100 == 0 -> tasbeehSoundPlayer.playSound(TasbeehSoundPlayer.SOUND_MILESTONE)
                count % 33 == 0 -> tasbeehSoundPlayer.playSound(TasbeehSoundPlayer.SOUND_MILESTONE)
                count % 10 == 0 -> tasbeehSoundPlayer.playSound(TasbeehSoundPlayer.SOUND_TAP)
                else -> tasbeehSoundPlayer.playSound(TasbeehSoundPlayer.SOUND_TAP)
            }
        }
        val scale = when {
            count % 100 == 0 -> 1.5f
            count % 33 == 0 -> 1.35f
            count % 10 == 0 -> 1.25f
            else -> 1.15f
        }
        val dur = when {
            count % 100 == 0 -> 120L
            count % 33 == 0 -> 100L
            count % 10 == 0 -> 80L
            else -> 60L
        }
        azkar.findViewById<TextView>(R.id.tasbeehCountText).animate()
            .scaleX(scale).scaleY(scale).setDuration(dur)
            .withEndAction { azkar.findViewById<TextView>(R.id.tasbeehCountText).animate().scaleX(1f).scaleY(1f).setDuration(dur * 2).start() }.start()
        updateTasbeehUI(azkar); saveTasbeehState()
        if (count % 10 == 0 && count % 100 != 0) {
            azkar.findViewById<TextView>(R.id.tasbeehProgressText).setTextColor(0xFFD4AF37.toInt())
            azkar.postDelayed({ azkar.findViewById<TextView>(R.id.tasbeehProgressText).setTextColor(0xFF8B7355.toInt()) }, 400)
        }
        if (count >= target) {
            targetReached = true; showTargetReachedAnimation(azkar); showConfetti()
            ibadatStateEngine.addBonusScore(10)
            updateIbadatUI(homeTabRoot)
            if (tasbeehSoundEnabled) tasbeehSoundPlayer.playSound(TasbeehSoundPlayer.SOUND_TARGET_REACHED)
            if (tasbeehHapticEnabled) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                        azkar.postDelayed({ try { vibrator?.vibrate(VibrationEffect.createOneShot(100, 220)) } catch (_: Exception) {} }, 150)
                        azkar.postDelayed({ try { vibrator?.vibrate(VibrationEffect.createOneShot(150, 255)) } catch (_: Exception) {} }, 320)
                    } else {
                        @Suppress("DEPRECATION") vibrator?.vibrate(300)
                    }
                } catch (_: Exception) {}
            }
        }
    }
    private fun resetCount(azkar: View): Boolean {
        count = 0; targetReached = false; updateTasbeehUI(azkar); saveTasbeehState(); return true
    }
    private fun updateTasbeehUI(azkar: View) {
        val target = TARGETS[currentTarget]
        azkar.findViewById<TextView>(R.id.tasbeehCountText).text = count.toString()
        azkar.findViewById<TextView>(R.id.tasbeehProgressText).text = "$count / $target"
        azkar.findViewById<TextView>(R.id.tasbeehDhikrText).text = if (targetReached) "سُبْحَانَ اللَّهِ" else DHIKS[currentDhikr]
        azkar.findViewById<TextView>(R.id.azkarSession).text = "${Localization.tasbeehSession} $todayCount"
        val streak = tasbeehPrefs.getInt("tasbeeh_streak", 0)
        azkar.findViewById<TextView>(R.id.azkarStreaks).text = if (streak > 0) "🔥 $streak" else ""
        val ringBg = azkar.findViewById<View>(R.id.ringBg)
        val progress = ((count.toFloat() / target.toFloat()) * 100).toInt().coerceAtMost(100)
        val alpha = 0.3f + (progress / 100f) * 0.7f
        ringBg.alpha = alpha
    }
    private fun loadTasbeehState() {
        currentDhikr = tasbeehPrefs.getInt("current_dhikr", 0)
        currentTarget = tasbeehPrefs.getInt("current_target", 0)
        count = tasbeehPrefs.getInt("count", 0)
        todayCount = tasbeehPrefs.getInt("today_count", 0)
        lastCountDate = tasbeehPrefs.getString("last_count_date", "") ?: ""
        targetReached = tasbeehPrefs.getBoolean("target_reached", false)
        tasbeehSoundEnabled = tasbeehPrefs.getBoolean("tasbeeh_sound_enabled", true)
        tasbeehHapticEnabled = tasbeehPrefs.getBoolean("tasbeeh_haptic_enabled", true)
        val today = dateFormat.format(Date())
        if (lastCountDate != today) todayCount = 0
    }
    private fun saveTasbeehState() {
        tasbeehPrefs.edit().apply {
            putInt("current_dhikr", currentDhikr); putInt("current_target", currentTarget)
            putInt("count", count); putInt("today_count", todayCount)
            putString("last_count_date", lastCountDate); putBoolean("target_reached", targetReached)
            putBoolean("tasbeeh_sound_enabled", tasbeehSoundEnabled)
            putBoolean("tasbeeh_haptic_enabled", tasbeehHapticEnabled)
            apply()
        }
    }
    private fun checkDateRollover() {
        val today = dateFormat.format(Date())
        if (lastCountDate != today) { todayCount = count; lastCountDate = today }
    }
    fun refreshAzkarProgress(azkar: View) {
        val (mDone, mTotal) = adhkarEngine.getMorningProgress()
        azkar.findViewById<TextView>(R.id.morningAzkarProgress).text = "✔ $mDone/$mTotal"
        val (eDone, eTotal) = adhkarEngine.getEveningProgress()
        azkar.findViewById<TextView>(R.id.eveningAzkarProgress).text = "✔ $eDone/$eTotal"
        val (aDone, aTotal) = adhkarEngine.getAfterSalahProgress()
        azkar.findViewById<TextView>(R.id.afterSalahAzkarProgress).text = "✔ $aDone/$aTotal"
        val (sDone, sTotal) = adhkarEngine.getSleepProgress()
        azkar.findViewById<TextView>(R.id.sleepAzkarProgress).text = "✔ $sDone/$sTotal"
    }
    private fun setupNinetyNineNames(azkar: View) {
        val display = azkar.findViewById<TextView>(R.id.ninetyNineNamesDisplay)
        val meaning = azkar.findViewById<TextView>(R.id.ninetyNineNamesMeaning)
        var idx = 0
        display.setOnClickListener {
            val name = focusEngine.names.getOrNull(idx) ?: return@setOnClickListener
            display.text = name.text
            meaning.text = "${name.transliteration} — ${name.meaning}"
            idx = (idx + 1) % focusEngine.names.size
        }
        display.text = focusEngine.names.first().text
        meaning.text = "${focusEngine.names.first().transliteration} — ${focusEngine.names.first().meaning}"
    }
    private fun setupWordAnalysis(azkar: View) {
        val display = azkar.findViewById<TextView>(R.id.wordAnalysisDisplay)
        val meaning = azkar.findViewById<TextView>(R.id.wordAnalysisMeaning)
        val nextBtn = azkar.findViewById<TextView>(R.id.wordAnalysisNext)
        val all = wordAnalysis.getAll()
        if (all.isNotEmpty()) {
            display.text = all[0].phrase
            meaning.text = all[0].fullMeaning
        }
        nextBtn.setOnClickListener {
            wordAnalysisIndex = (wordAnalysisIndex + 1) % all.size
            display.text = all[wordAnalysisIndex].phrase
            meaning.text = all[wordAnalysisIndex].fullMeaning
        }
    }
    private fun setupQuizCard(azkar: View) {
        azkar.findViewById<TextView>(R.id.startQuizBtn).setOnClickListener {
            startActivity(Intent(this, islamic.duas.quiz.QuizActivity::class.java))
        }
    }
    private fun setupWellnessTab(wellness: View) {

        setupAnis(wellness)
        setupExerciseBenefits(wellness)
        setupParentingTips(wellness)
        setupTaharah(wellness)
        setupHaidhTracker(wellness)
        setupSujoodSahw(wellness)
        setupBedtime(wellness)
        setupExerciseLogCard(wellness)

        wellness.findViewById<TextView>(R.id.addMedicationBtn).setOnClickListener {
            showAddMedicationDialog(wellness)
        }
        refreshPendingMedicationList(wellness)

        wellness.findViewById<LinearLayout>(R.id.moreCard).setOnClickListener {
            val container = wellness.findViewById<LinearLayout>(R.id.moreContentContainer)
            if (container.tag == null) {
                layoutInflater.inflate(R.layout.more_card_sections, container, true)
                setupComparativeFiqh(container)
                setupFiqhScenarios(container)
                setupSeerah(container)
                setupFadail(container)
                setupDawah(container)
                setupEconomy(container)
                container.tag = true
            }
            container.visibility = if (container.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }
    private fun setupExerciseLogCard(wellness: View) {
        val minutesView = wellness.findViewById<TextView>(R.id.exerciseLogMinutes)
        val weeklyView = wellness.findViewById<TextView>(R.id.exerciseLogWeekly)
        val recordBtn = wellness.findViewById<TextView>(R.id.exerciseLogRecordBtn)
        val openBtn = wellness.findViewById<TextView>(R.id.exerciseLogOpenBtn)
        val card = wellness.findViewById<LinearLayout>(R.id.exerciseLogCard)

        fun refreshExerciseLogUI() {
            val todayMinutes = healthEngine.getTodayExerciseMinutes()
            val weeklyCount = healthEngine.getWeeklyExerciseCount()
            minutesView.text = "آج: $todayMinutes منٹ"
            weeklyView.text = "اس ہفتے: $weeklyCount/4 ورزشیں"
        }

        card?.setOnClickListener {
            startActivity(Intent(this, ExerciseLogActivity::class.java))
        }

        recordBtn.setOnClickListener {
            val durations = arrayOf("15 منٹ", "30 منٹ", "45 منٹ", "60 منٹ")
            val durationValues = intArrayOf(15, 30, 45, 60)
            AlertDialog.Builder(this)
                .setTitle("🏃 ورزش کا دورانیہ")
                .setItems(durations) { _, which ->
                    val mins = durationValues[which]
                    healthEngine.recordExercise(mins)
                    ibadatStateEngine.addBonusScore(5)
                    Toast.makeText(this, "🏃 $mins منٹ ریکارڈ ہوگئے — اللہ قبول فرمائے", Toast.LENGTH_SHORT).show()
                    refreshExerciseLogUI()
                }
                .setNegativeButton("منسوخ", null)
                .show()
        }

        openBtn.setOnClickListener {
            startActivity(Intent(this, ExerciseLogActivity::class.java))
        }

        refreshExerciseLogUI()
    }
    private fun showAddMedicationDialog(wellness: View) {
        val nameInput = android.widget.EditText(this).apply {
            setTextColor(0xFFE0DDD8.toInt())
            setHintTextColor(0xFF8B7355.toInt())
            hint = "دوا کا نام (مثلاً: پیناڈول)"
        }

        val subahCb = android.widget.CheckBox(this).apply {
            text = "صبح"
            setTextColor(0xFFE0DDD8.toInt())
        }
        val dopaharCb = android.widget.CheckBox(this).apply {
            text = "دوپہر"
            setTextColor(0xFFE0DDD8.toInt())
        }
        val shamCb = android.widget.CheckBox(this).apply {
            text = "شام"
            setTextColor(0xFFE0DDD8.toInt())
        }

        val headerLabel = TextView(this).apply {
            text = "وقت منتخب کریں"
            setTextColor(0xFFC9A961.toInt())
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        val customSection = buildCustomTimeSection(emptyList())
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(nameInput)
            addView(headerLabel)
            addView(subahCb)
            addView(dopaharCb)
            addView(shamCb)
            addView(customSection.first)
        }

        AlertDialog.Builder(this)
            .setTitle("💊 نئی دوا")
            .setView(layout)
            .setPositiveButton("شامل کریں") { _, _ ->
                val name = nameInput.text.toString().trim()
                val selected = mutableListOf<String>()
                if (subahCb.isChecked) selected.add("صبح")
                if (dopaharCb.isChecked) selected.add("دوپہر")
                if (shamCb.isChecked) selected.add("شام")
                selected.addAll(customSection.second)
                if (name.isEmpty()) {
                    Toast.makeText(this, "دوا کا نام درج کریں", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "کم از کم ایک وقت منتخب کریں", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val med = islamic.duas.haidh.Medication(
                    name = name,
                    dosage = "",
                    frequency = selected.size,
                    times = selected
                )
                healthEngine.saveMedication(med)
                try {
                    AppNotificationManager(this).scheduleMedicineReminder()
                } catch (_: Exception) {}
                refreshPendingMedicationList(wellness)
                Toast.makeText(this, "💊 $name شامل ہوگئی", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("منسوخ", null)
            .show()
    }

    private fun showEditMedicationDialog(wellness: View, med: islamic.duas.haidh.Medication) {
        val nameInput = android.widget.EditText(this).apply {
            setText(med.name)
            setTextColor(0xFFE0DDD8.toInt())
            setHintTextColor(0xFF8B7355.toInt())
            hint = "Medication name (e.g., Panadol)"
        }

        val morningCb = android.widget.CheckBox(this).apply {
            text = "Morning"
            isChecked = med.times.contains("صبح")
            setTextColor(0xFFE0DDD8.toInt())
        }
        val afternoonCb = android.widget.CheckBox(this).apply {
            text = "Afternoon"
            isChecked = med.times.contains("دوپہر")
            setTextColor(0xFFE0DDD8.toInt())
        }
        val eveningCb = android.widget.CheckBox(this).apply {
            text = "Evening"
            isChecked = med.times.contains("شام")
            setTextColor(0xFFE0DDD8.toInt())
        }

        val headerLabel = TextView(this).apply {
            text = "Select times"
            setTextColor(0xFFC9A961.toInt())
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        val customSection = buildCustomTimeSection(med.times)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(nameInput)
            addView(headerLabel)
            addView(morningCb)
            addView(afternoonCb)
            addView(eveningCb)
            addView(customSection.first)
        }

        AlertDialog.Builder(this)
            .setTitle("💊 Edit Medication")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val selected = mutableListOf<String>()
                if (morningCb.isChecked) selected.add("صبح")
                if (afternoonCb.isChecked) selected.add("دوپہر")
                if (eveningCb.isChecked) selected.add("شام")
                selected.addAll(customSection.second)
                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter medication name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "Select at least one time", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updated = med.copy(
                    name = name,
                    frequency = selected.size,
                    times = selected
                )
                healthEngine.saveMedication(updated)
                try {
                    AppNotificationManager(this).scheduleMedicineReminder()
                } catch (_: Exception) {}
                refreshPendingMedicationList(wellness)
                Toast.makeText(this, "💊 $name updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildCustomTimeSection(
        current: List<String>
    ): Pair<LinearLayout, MutableList<String>> {
        val customTimes = current
            .filter { it !in setOf("صبح", "دوپہر", "شام") }
            .toMutableList()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 0)
        }
        val addBtn = TextView(this).apply {
            text = "⏰ اپنا وقت منتخب کریں"
            textSize = 13f
            setTextColor(0xFF0B0F2A.toInt())
            setPadding(14, 8, 14, 8)
            setBackgroundColor(0xFFD4AF37.toInt())
            gravity = android.view.Gravity.CENTER
            isClickable = true
            isFocusable = true
        }
        fun render() {
            container.removeAllViews()
            for (t in customTimes) {
                val chip = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 4, 0, 4)
                }
                val label = TextView(this@MainActivity).apply {
                    text = "🕐 $t"
                    textSize = 14f
                    setTextColor(0xFFE0DDD8.toInt())
                }
                val remove = TextView(this@MainActivity).apply {
                    text = "✖"
                    textSize = 14f
                    setTextColor(0xFFEF4444.toInt())
                    setPadding(14, 0, 0, 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        customTimes.remove(t)
                        render()
                    }
                }
                chip.addView(label)
                chip.addView(remove)
                container.addView(chip)
            }
            container.addView(addBtn)
        }
        addBtn.setOnClickListener {
            android.app.TimePickerDialog(
                this@MainActivity,
                { _, h, m ->
                    val ampm = if (h < 12) "AM" else "PM"
                    val h12 = when (h % 12) { 0 -> 12 else -> h % 12 }
                    val tt = String.format("%d:%02d %s", h12, m, ampm)
                    if (!customTimes.contains(tt)) {
                        customTimes.add(tt)
                    }
                    render()
                },
                9, 0, false
            ).show()
        }
        render()
        return container to customTimes
    }

    private fun refreshPendingMedicationList(wellness: View) {
        val container = wellness.findViewById<LinearLayout>(R.id.medicationFullList) ?: return
        container.removeAllViews()
        val meds = healthEngine.getMedications().filter { it.isActive }
        if (meds.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "ابھی تک کوئی دوا شامل نہیں"
                textSize = 14f
                setTextColor(0xFF8B7355.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 24)
            }
            container.addView(emptyTv)
            return
        }

        val todayLog = healthEngine.getTodayMedicationLog()
        for (med in meds) {
            val pendingTimes = med.times.filter { time ->
                !todayLog.any { it.medicationId == med.id && it.time == time && it.taken }
            }
            if (pendingTimes.isEmpty()) continue

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.rounded_bg)
                setPadding(12, 10, 12, 10)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 6 }
            }

            val nameRow = TextView(this).apply {
                text = "💊 ${med.name}"
                textSize = 15f
                setTextColor(0xFFE0DDD8.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            card.addView(nameRow)

            val timeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 0)
            }
            for (time in pendingTimes) {
                val badge = TextView(this).apply {
                    text = time
                    textSize = 12f
                    setTextColor(0xFF0B0F2A.toInt())
                    setPadding(8, 3, 8, 3)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 6, 0) }
                    setBackgroundColor(0xFFD4AF37.toInt())
                    gravity = android.view.Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    tag = "${med.id}|$time"
                    setOnClickListener {
                        showMedicationDoseDialog(wellness, med.id, time)
                    }
                }
                timeRow.addView(badge)
            }
            card.addView(timeRow)

            val infoTv = TextView(this).apply {
                text = "معلومات دیکھیں ►"
                textSize = 11f
                setTextColor(0xFF8B7355.toInt())
                gravity = android.view.Gravity.END
                setPadding(0, 4, 0, 0)
            }
            card.addView(infoTv)

            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                setPadding(0, 8, 0, 0)
            }
            val editBtn = TextView(this).apply {
                text = "Edit"
                textSize = 12f
                setTextColor(0xFF0B0F2A.toInt())
                setPadding(14, 6, 14, 6)
                setBackgroundColor(0xFFD4AF37.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }
                isClickable = true
                isFocusable = true
                setOnClickListener { showEditMedicationDialog(wellness, med) }
            }
            val deleteBtn = TextView(this).apply {
                text = "Delete"
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(14, 6, 14, 6)
                setBackgroundColor(0xFFEF4444.toInt())
                gravity = android.view.Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    healthEngine.deleteMedication(med.id)
                    try {
                        AppNotificationManager(this@MainActivity).scheduleMedicineReminder()
                    } catch (_: Exception) {}
                    refreshPendingMedicationList(wellness)
                    Toast.makeText(this@MainActivity, "${med.name} deleted", Toast.LENGTH_SHORT).show()
                }
            }
            actionsRow.addView(editBtn)
            actionsRow.addView(deleteBtn)
            card.addView(actionsRow)

            card.setOnClickListener {
                val timesStr = pendingTimes.joinToString("، ")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("💊 ${med.name}")
                    .setMessage("اوقات: $timesStr\nحالت: زیر التواء")
                    .setPositiveButton("ٹھیک ہے", null)
                    .show()
            }

            container.addView(card)
        }

        if (container.childCount == 0) {
            val doneTv = TextView(this).apply {
                text = "✅ آج کی تمام دوائیں لے لی گئیں"
                textSize = 14f
                setTextColor(0xFF10B981.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 24)
            }
            container.addView(doneTv)
        }
    }

    private fun highlightMedicationDose(wellness: View, medId: String, time: String) {
        val key = "$medId|$time"
        val badge = wellness.findViewWithTag<View>(key) ?: return
        (wellness as? ScrollView)?.let { sv ->
            sv.smoothScrollTo(0, badge.top - 100)
        }
        val anim = ObjectAnimator.ofFloat(badge, "alpha", 0.4f, 1f).setDuration(700)
        anim.repeatMode = ValueAnimator.REVERSE
        anim.repeatCount = 2
        anim.start()
        showMedicationDoseDialog(wellness, medId, time)
    }

    private fun showMedicationDoseDialog(wellness: View, medId: String, time: String) {
        val med = healthEngine.getMedications().firstOrNull { it.id == medId } ?: return
        val todayLog = healthEngine.getTodayMedicationLog()
        val alreadyTaken = todayLog.any { it.medicationId == medId && it.time == time && it.taken }
        if (alreadyTaken) return
        AlertDialog.Builder(this)
            .setTitle("💊 ${med.name}")
            .setMessage("$time کا وقت — کیا دوا لے لی؟")
            .setPositiveButton("✅ لے لی") { _, _ ->
                markMedicationDose(wellness, medId, time, taken = true)
            }
            .setNegativeButton("🔔 بعد میں") { _, _ ->
                markMedicationDose(wellness, medId, time, taken = false)
            }
            .setNeutralButton("منسوخ", null)
            .show()
    }

    private fun markMedicationDose(wellness: View, medId: String, time: String, taken: Boolean) {
        if (taken) {
            healthEngine.logMedicationDose(medId, time, true)
            val i = Intent(this, NotificationReceiver::class.java).apply {
                action = AppNotificationManager.ACTION_MEDICINE_TAKEN
                putExtra(AppNotificationManager.EXTRA_MED_ID, medId)
                putExtra(AppNotificationManager.EXTRA_MED_TIME, time)
            }
            sendBroadcast(i)
        } else {
            val i = Intent(this, NotificationReceiver::class.java).apply {
                action = AppNotificationManager.ACTION_MEDICINE_SNOOZE
                putExtra(AppNotificationManager.EXTRA_MED_TIME, time)
            }
            sendBroadcast(i)
        }
        refreshPendingMedicationList(wellness)
    }

    @Suppress("unused")
    private fun showMedicationLogDialog() {
        // removed
    }
    private fun setupAnis(wellness: View) {
        wellness.findViewById<TextView>(R.id.anisSadBtn).setOnClickListener {
            val r = anisEngine.getResponseByMood("sad")
            wellness.findViewById<TextView>(R.id.anisResponse).text = r
            wellness.findViewById<TextView>(R.id.anisResponse).visibility = View.VISIBLE
            vibrateClick()
        }
        wellness.findViewById<TextView>(R.id.anisLonelyBtn).setOnClickListener {
            val r = anisEngine.getResponseByMood("lonely")
            wellness.findViewById<TextView>(R.id.anisResponse).text = r
            wellness.findViewById<TextView>(R.id.anisResponse).visibility = View.VISIBLE
            vibrateClick()
        }
        wellness.findViewById<TextView>(R.id.anisAnxiousBtn).setOnClickListener {
            val r = anisEngine.getResponseByMood("anxious")
            wellness.findViewById<TextView>(R.id.anisResponse).text = r
            wellness.findViewById<TextView>(R.id.anisResponse).visibility = View.VISIBLE
            vibrateClick()
        }
        wellness.findViewById<TextView>(R.id.anisGratefulBtn).setOnClickListener {
            val r = anisEngine.getResponseByMood("grateful")
            wellness.findViewById<TextView>(R.id.anisResponse).text = r
            wellness.findViewById<TextView>(R.id.anisResponse).visibility = View.VISIBLE
            vibrateClick()
        }
    }
    private var exerciseIndex = 0
    private val exerciseData = listOf(
        Triple("چہل قدمی (Walking)", "نبی کریم ﷺ نے فرمایا: تم میں سب سے بہتر وہ ہے جو اپنی دنیا اور آخرت دونوں کے لیے کام کرے۔ روزانہ 30 منٹ کی چہل قدمی دل کی صحت، وزن میں کمی، اور ذہنی سکون کے لیے مفید ہے۔", "صحت سے متعلق"),
        Triple("دوڑ (Jogging)", "روزانہ 15-20 منٹ کی دوڑ دل اور پھیپھڑوں کو مضبوط کرتی ہے، خون کی گردش بہتر کرتی ہے، اور کیلوریز جلانے میں مدد دیتی ہے۔", "قلبی صحت"),
        Triple("یوگا (Yoga)", "یوگا لچک، توازن، اور ذہنی سکون بڑھاتا ہے۔ نبی کریم ﷺ نے جسمانی ورزش کی ترغیب دی ہے۔", "لچک اور سکون"),
        Triple("تیراکی (Swimming)", "تیراکی پورے جسم کی ورزش ہے۔ نبی کریم ﷺ نے فرمایا: اپنے بچوں کو تیراکی سکھاؤ۔", "پورے جسم کی ورزش"),
        Triple("وزن اٹھانا (Strength Training)", "پٹھوں کی مضبوطی اور ہڈیوں کی صحت کے لیے وزن اٹھانا مفید ہے۔ روزانہ 15 منٹ کی طاقت کی ورزش کافی ہے۔", "طاقت اور مضبوطی")
    )
    var tafsirIndex = 0
    var isTafsirMode = true
    val blinkRunnables = mutableListOf<Runnable>()
    private val tabRoots = SparseArray<View>(5)

    private fun getTabRoot(index: Int): View {
        var root = tabRoots[index]
        if (root != null) return root
        val stub: ViewStub = when (index) {
            1 -> binding.azkarTab
            2 -> binding.wellnessTab
            3 -> binding.huqooqTab
            4 -> binding.quranTab
            else -> return homeTabRoot
        }
        root = stub.inflate()
        tabRoots[index] = root
        when (index) {
            1 -> setupAzkarTab(root)
            2 -> setupWellnessTab(root)
            3 -> setupHuqooqTab(root)
            4 -> setupQuranTab(root)
        }
        return root
    }
    val tafsirData = listOf(
        listOf("قُلْ هُوَ اللَّهُ أَحَدٌ", "اللہ ایک ہے — وہ بے نیاز ہے، نہ کسی سے پیدا ہوا نہ اس نے کسی کو پیدا کیا", "سورۃ الاخلاص پوری طرح توحید کا اعلان ہے۔ اہل حدیث کے نزدیک یہ سورۃ شرک کی ہر قسم کی نفی کرتی ہے۔ اللہ تعالیٰ اکیلا عبادت کے لائق ہے، اس کا کوئی شریک نہیں، نہ بیٹا نہ باپ۔ یہی توحید خالص ہے جس پر اہل حدیث کا عقیدہ ہے۔", "الاخلاص 112:1-4 — ابن کثیر"),
        listOf("إِنَّا أَعْطَيْنَاكَ الْكَوْثَرَ", "بے شک ہم نے آپ ﷺ کو کوثر (خیر کثیر) عطا کیا", "کوثر سے مراد جنت کی نہر ہے اور بہت سی خیر و برکت۔ اللہ نے نبی ﷺ کو علم، حکمت، اولاد، اور امت عطا کی۔ یہ آیت مایوس ہونے والوں کے لیے تسلی ہے۔", "الکوثر 108:1 — ابن کثیر"),
        listOf("وَالْعَصْرِ ﴿1﴾ إِنَّ الْإِنسَانَ لَفِي خُسْرٍ", "زمانے کی قسم — بے شک انسان خسارے میں ہے", "اللہ نے زمانے کی قسم کھا کر فرمایا کہ ہر انسان نقصان میں ہے سوائے ان چار لوگوں کے: ایمان والے، نیک عمل کرنے والے، حق کی وصیت کرنے والے، اور صبر کی وصیت کرنے والے۔", "العصر 103:1-3 — ابن کثیر"),
        listOf("لَا إِكْرَاهَ فِي الدِّينِ", "دین میں کوئی زبردستی نہیں", "یہ آیت اسلامی اصول ہے کہ کسی کو دین قبول کرنے پر مجبور نہیں کیا جا سکتا۔ اہل حدیث اس بات پر زور دیتے ہیں کہ ایمان دل کی بات ہے، زبردستی سے نہیں ہو سکتا۔", "البقرہ 2:256 — ابن کثیر"),
        listOf("فَاذْكُرُونِي أَذْكُرْكُمْ", "تم مجھے یاد کرو میں تمہیں یاد کروں گا", "اللہ کا ذکر کرنے والوں کو اللہ یاد کرتا ہے۔ یہ عظیم فضیلت ہے۔ اہل حدیث ہر وقت اللہ کے ذکر کو لازم سمجھتے ہیں — صبح و شام کے اذکار، نماز کے بعد کے اذکار، اور ہر حال میں اللہ کو یاد رکھنا۔", "البقرہ 2:152 — ابن کثیر"),
        listOf("إِنَّ مَعَ الْعُسْرِ يُسْرًا", "بے شک مشکل کے ساتھ آسانی ہے", "یہ آیت مومن کے لیے بشارت ہے کہ ہر مشکل کے بعد آسانی آتی ہے۔ اہل حدیث اس آیت سے صبر اور امید کا سبق لیتے ہیں۔ اللہ اپنے بندے پر اس کی طاقت سے زیادہ بوجھ نہیں ڈالتا۔", "الشرح 94:6 — ابن کثیر"),
        listOf("يَا أَيُّهَا الَّذِينَ آمَنُوا اتَّقُوا اللَّهَ وَكُونُوا مَعَ الصَّادِقِينَ", "اے ایمان والو! اللہ سے ڈرو اور سچوں کے ساتھ رہو", "یہ آیت اہل حدیث کے منہج کی بنیاد ہے — صدق (سچائی) اور حق پر قائم رہنا۔ اہل حدیث اسی لیے خود کو اہل الحدیث کہلاتے ہیں کیونکہ وہ حدیث میں سچائی کو مانتے ہیں۔", "التوبہ 9:119 — ابن کثیر"),
        listOf("وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ", "اور جو اللہ پر توکل کرے اللہ اس کے لیے کافی ہے", "توکل کا مطلب یہ نہیں کہ کام چھوڑ دیں بلکہ اسباب اختیار کرنے کے بعد اللہ پر بھروسہ کریں۔ اہل حدیث توکل کو ایمان کی علامت مانتے ہیں۔", "الطلاق 65:3 — ابن کثیر"),
        listOf("رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً", "اے ہمارے رب! ہمیں دنیا میں بھی بھلائی دے اور آخرت میں بھی بھلائی دے", "یہ جامع دعا ہے — جو شخص یہ دعا مانگے اسے دنیا اور آخرت دونوں کی بھلائی ملتی ہے۔ اہل حدیث اس دعا کو ہر وقت پڑھنے کی ترغیب دیتے ہیں۔", "البقرہ 2:201 — ابن کثیر"),
        listOf("إِنَّ اللَّهَ يَأْمُرُ بِالْعَدْلِ وَالْإِحْسَانِ", "بے شک اللہ عدل اور احسان کا حکم دیتا ہے", "یہ آیت اسلامی اخلاقیات کا خلاصہ ہے — عدل (انصاف) اور احسان (بھلائی) کرنا۔ اہل حدیث ہر معاملے میں انصاف اور بھلائی کو لازم سمجھتے ہیں۔", "النحل 16:90 — ابن کثیر")
    )
    val hadithData = listOf(
        listOf("خَيْرُكُمْ خَيْرُكُمْ لِأَهْلِهِ", "تم میں سب سے بہتر وہ ہے جو اپنے گھر والوں کے لیے بہترین ہو — سنن الترمذی: 3895", "یہ حدیث عورت کے ساتھ حسن سلوک کی بنیاد ہے۔ نبی ﷺ نے خود فرمایا کہ میں اپنے گھر والوں کے لیے سب سے بہتر ہوں۔ اہل حدیث عورتوں کے حقوق میں اس حدیث کو پیش پیش رکھتے ہیں۔"),
        listOf("طَلَبُ الْعِلْمِ فَرِيضَةٌ عَلَى كُلِّ مُسْلِمٍ", "علم حاصل کرنا ہر مسلمان مرد اور عورت پر فرض ہے — ابن ماجہ: 224", "یہ حدیث عورت کی تعلیم کی اہمیت کو واضح کرتی ہے۔ اہل حدیث کے نزدیک عورت کو دینی اور دنیاوی تعلیم حاصل کرنے کا پورا حق ہے۔"),
        listOf("الدُّنْيَا مَتَاعٌ وَخَيْرُ مَتَاعِ الدُّنْيَا الْمَرْأَةُ الصَّالِحَةُ", "دنیا سامان ہے اور دنیا کا بہترین سامان نیک عورت ہے — صحیح مسلم: 1467", "نیک عورت کی قدر و قیمت بیان کی گئی ہے۔ یہ حدیث عورت کی عزت اور اہمیت کو واضح کرتی ہے۔"),
        listOf("لَا تُنْكَحُ الْأَيِّمُ حَتَّى تُسْتَأْمَرَ", "بیوہ عورت سے اس کی اجازت کے بغیر نکاح نہ کیا جائے — صحیح البخاری: 4846", "یہ حدیث عورت کو نکاح میں رضامندی کا حق دیتی ہے۔ اہل حدیث کے نزدیک عورت کی مرضی کے بغیر نکاح کرنا جائز نہیں۔"),
        listOf("جِهَادُكُنَّ الْحَجُّ", "عورتوں کا جہاد حج ہے — صحیح البخاری: 2875", "عورتوں کے لیے جہاد کا متبادل حج ہے۔ یہ حدیث عورتوں کے لیے آسانی اور ان کے مقام کی نشاندہی کرتی ہے۔"),
        listOf("أَكْمَلُ الْمُؤْمِنِينَ إِيمَانًا أَحْسَنُهُمْ خُلُقًا", "سب سے کامل ایمان والا سب سے اچھے اخلاق والا ہے — سنن الترمذی: 1162", "اخلاق کی اہمیت — اہل حدیث ایمان اور اخلاق کو ایک دوسرے سے منسلک سمجھتے ہیں۔"),
        listOf("لَا يُفْرِكْ مُؤْمِنٌ مُؤْمِنَةً", "کوئی مومن کسی مومنہ سے بغض نہ رکھے — صحیح مسلم: 1469", "ازدواجی زندگی میں برداشت اور محبت — یہ حدیث نکاح میں عورت کے تحفظ کی تعلیم دیتی ہے۔"),
        listOf("الرَّاحِمُونَ يَرْحَمُهُمُ الرَّحْمَنُ", "رحم کرنے والوں پر رحمٰن رحم کرتا ہے — سنن الترمذی: 1924", "رحم دلی کی فضیلت — اہل حدیث دوسروں پر رحم کرنے کو ایمان کی علامت مانتے ہیں۔"),
        listOf("مَنْ كَانَ يُؤْمِنُ بِاللَّهِ وَالْيَوْمِ الْآخِرِ فَلْيَقُلْ خَيْرًا أَوْ لِيَصْمُتْ", "جو اللہ اور آخرت پر ایمان رکھتا ہے وہ بھلائی کہے یا خاموش رہے — صحیح البخاری: 6018", "زبانی احتیاط — اہل حدیث فضول گفتگو سے بچنے کی تعلیم دیتے ہیں۔"),
        listOf("الْمُسْلِمُ مَنْ سَلِمَ الْمُسْلِمُونَ مِنْ لِسَانِهِ وَيَدِهِ", "مسلمان وہ ہے جس کی زبان اور ہاتھ سے دوسرے مسلمان محفوظ رہیں — صحیح البخاری: 10", "مسلمان کی پہچان — اہل حدیث دوسروں کو تکلیف نہ پہنچانے کو حقیقی ایمان کی علامت مانتے ہیں۔")
    )
    private val parentingData = listOf(
        listOf("ولادت کے بعد اذان", "بچے کی پیدائش کے فوراً بعد دائیں کان میں اذان اور بائیں میں اقامت کہنا سنت ہے۔", "سنن الترمذی"),
        listOf("عقیقہ", "بچے کی طرف سے ساتواں دن عقیقہ کرنا سنت ہے — لڑکے کی طرف سے دو بکریاں اور لڑکی کی طرف سے ایک۔", "صحیح البخاری"),
        listOf("نام رکھنا", "بچے کا اچھا نام رکھنا والدین کا حق ہے۔ ساتویں دن نام رکھنا سنت ہے۔ عبداللہ، عبدالرحمن، محمد پسندیدہ نام ہیں۔", "صحیح مسلم"),
        listOf("بچوں سے محبت", "نبی ﷺ بچوں سے بہت محبت کرتے تھے، انہیں گود میں لیتے، بوسہ دیتے اور ان کے ساتھ کھیلتے تھے۔", "صحیح البخاری"),
        listOf("نماز کی تربیت", "سات سال کی عمر سے بچوں کو نماز کا حکم دیں اور دس سال پر سختی کریں۔", "سنن ابوداؤد"),
        listOf("بچوں کے ساتھ انصاف", "والدین کو چاہیے کہ بچوں کے درمیان انصاف کریں — پیار اور تحفے میں برابر رکھیں۔", "صحیح البخاری"),
        listOf("بچوں کی دعا", "نبی ﷺ بچوں کے لیے دعا کرتے تھے: 'اللہم بارک فیه' (اے اللہ اس میں برکت دے)", "صحیح البخاری"),
        listOf("بددعا سے بچنا", "والدین کو چاہیے کہ بچوں کو بددعا نہ دیں، کیونکہ یہ قبول ہو سکتی ہے۔", "صحیح مسلم")
    )
    private val taharahData = listOf(
        listOf("استنجاء کا طریقہ", "پیشاب اور پاخانے کے بعد پانی سے استنجاء کرنا سنت ہے۔ پانی نہ ہو تو پتھر یا ٹشو سے صفائی کر سکتے ہیں۔ پیشاب کے چھینٹوں سے بچنا ضروری ہے کیونکہ قبر کا عذاب اکثر پیشاب کی وجہ سے ہوتا ہے۔", "صحیح البخاری"),
        listOf("مسواک کی فضیلت", "مسواک کرنا سنت ہے — منہ کی صفائی اور اللہ کی خوشنودی کا ذریعہ۔ نبی ﷺ ہر نماز سے پہلے مسواک کرتے تھے۔", "صحیح البخاری"),
        listOf("وضو کے فرائض", "وضو کے چار فرائض ہیں: (1) چہرہ دھونا (2) ہاتھ کہنیوں تک دھونا (3) سر کا مسح (4) پاؤں ٹخنوں تک دھونا۔", "المائدہ 5:6"),
        listOf("غسل کے فرائض", "غسل کے تین فرائض ہیں: (1) کلی کرنا (2) ناک میں پانی ڈالنا (3) پورے جسم پر پانی بہانا۔ غسل جنابت، حیض اور نفاس کے بعد فرض ہے۔", "صحیح البخاری"),
        listOf("تیمم کا طریقہ", "پانی نہ ہو یا بیماری ہو تو تیمم کیا جا سکتا ہے — صاف مٹی سے چہرہ اور ہاتھوں کا مسح۔ تیمم میں پانی کی طرح کے احکام ہیں۔", "النساء 4:43"),
        listOf("ناک میں پانی ڈالنا", "وضو میں ناک میں پانی ڈالنا (استنشاق) سنت ہے — تین بار دائیں ہاتھ سے پانی ڈال کر بائیں سے نکالیں۔ روزہ دار ایسا نہ کرے۔", "صحیح البخاری"),
        listOf("دائیں سے شروع کرنا", "طہارت میں دائیں طرف سے شروع کرنا سنت ہے — ہاتھ، پاؤں دہنی طرف سے دھوئیں۔", "صحیح البخاری"),
        listOf("وضو کے بعد کی دعا", "وضو کے بعد یہ دعا پڑھیں: 'أشهد أن لا إله إلا اللہ وأشهد أن محمدا عبده ورسوله' — اس کے لیے جنت کے آٹھ دروازے کھول دیے جاتے ہیں۔", "صحیح مسلم")
    )
    private val seerahData = listOf(
        listOf("نبی ﷺ کی ولادت", "نبی ﷺ کی ولادت عام الفیل (ہاتھی کے سال) میں مکہ میں ہوئی۔ آپ ﷺ کی پیدائش سے پہلے آپ کے والد عبداللہ کا انتقال ہو گیا تھا۔", "سیرت ابن ہشام"),
        listOf("نبوت سے پہلے کی زندگی", "آپ ﷺ امانت اور سچائی میں مشہور تھے۔ لوگ آپ کو 'الصادق الأمین' کہتے تھے۔ 25 سال کی عمر میں خدیجہ سے نکاح ہوا۔", "صحیح البخاری"),
        listOf("پہلی وحی", "40 سال کی عمر میں غار حرا میں پہلی وحی نازل ہوئی — 'اقْرَأْ بِاسْمِ رَبِّكَ الَّذِي خَلَقَ'۔ خدیجہ اور ورقة بن نوفل نے تصدیق کی۔", "صحیح البخاری"),
        listOf("ہجرت حبشہ", "مشرکین کے ظلم سے بچنے کے لیے صحابہ نے حبشہ کی طرف ہجرت کی۔ یہ پہلی ہجرت تھی جس میں عورتیں اور بچے شامل تھے۔", "سیرت ابن ہشام"),
        listOf("ہجرت مدینہ", "13 سال کی دعوت کے بعد اللہ نے مدینہ کی طرف ہجرت کا حکم دیا۔ آپ ﷺ نے مسجد قبا بنائی اور پھر مسجد نبوی تعمیر کی۔", "صحیح البخاری"),
        listOf("غزوہ بدر", "پہلا بڑا غزوہ — 313 مسلمانوں نے 1000 مشرکین کا مقابلہ کیا اور اللہ کی مدد سے فتح پائی۔", "صحیح البخاری"),
        listOf("فتح مکہ", "8 ہجری میں مکہ فتح ہوا — آپ ﷺ نے عام معافی کا اعلان کیا اور بتوں کو توڑا۔ یہ رحمت اور عفو کا عظیم دن تھا۔", "صحیح البخاری"),
        listOf("حجۃ الوداع", "10 ہجری میں آپ ﷺ نے آخری حج کیا — جس میں خطبہ حجۃ الوداع دیا اور عورتوں کے حقوق کی تاکید کی۔", "صحیح البخاری"),
        listOf("نبی ﷺ کا اخلاق", "نبی ﷺ کا اخلاق قرآن تھا۔ آپ ﷺ نہ کبھی غصے میں بدلہ لیتے تھے اور نہ کسی کو ذلیل کرتے۔", "صحیح مسلم"),
        listOf("نبی ﷺ کا انتقال", "11 ہجری میں 63 سال کی عمر میں نبی ﷺ کا انتقال ہوا۔ آپ ﷺ کی وفات سے پہلے قرآن مکمل ہو چکا تھا اور دین مکمل ہو چکا تھا۔", "صحیح البخاری")
    )
    private val fadailData = listOf(
        listOf("سورۃ الاخلاص کا ثواب", "سورۃ الاخلاص پڑھنا قرآن کے تہائی حصے کے برابر ہے — آپ ﷺ نے فرمایا: 'قُلْ هُوَ اللَّهُ أَحَدٌ قرآن کے تہائی حصے کے برابر ہے'", "صحیح البخاری: 5015"),
        listOf("سبحان اللہ وبحمده کا ثواب", "جو دن میں 100 بار 'سُبْحَانَ اللَّهِ وَبِحَمْدِهِ' پڑھے گا اس کے گناہ معاف کر دیے جائیں گے اگرچہ سمندر کے جھاگ کے برابر ہوں۔", "صحیح البخاری: 6405"),
        listOf("دس آیتیں پڑھنے کا ثواب", "جو رات میں سورۃ البقرہ کی آخری دو آیتیں پڑھے گا وہ اس کے لیے کافی ہوں گی — یہ بہت بڑی فضیلت ہے۔", "صحیح البخاری: 5008"),
        listOf("جماعت سے نماز کا ثواب", "جماعت سے نماز پڑھنے کا ثواب اکیلے پڑھنے سے 27 گنا زیادہ ہے۔", "صحیح البخاری: 645"),
        listOf("صدقہ کی فضیلت", "صدقہ مال کو کم نہیں کرتا بلکہ بڑھاتا ہے۔ نبی ﷺ نے فرمایا: صدقہ دینے والے کا مال کم نہیں ہوتا۔", "صحیح مسلم: 2588"),
        listOf("ذکر کی فضیلت", "نبی ﷺ نے فرمایا: اللہ کے ذکر کرنے والے اور نہ کرنے والے کی مثال زندہ اور مردہ جیسی ہے۔", "صحیح البخاری: 6407"),
        listOf("والدین کی خدمت", "نبی ﷺ نے فرمایا: والدین کی خدمت جہاد سے افضل ہے — ایک شخص نے والدین کی خدمت کی اجازت مانگی تو آپ ﷺ نے فرمایا: ان کی خدمت کرو۔", "صحیح البخاری: 5972"),
        listOf("صلہ رحمی", "نبی ﷺ نے فرمایا: رحمی تعلقات کو جوڑنا ایمان کی علامت ہے اور اس سے رزق میں وسعت اور عمر میں برکت ہوتی ہے۔", "صحیح البخاری: 5985"),
        listOf("درود کی فضیلت", "جو ایک بار مجھ پر درود بھیجے اللہ اس پر دس رحمتیں نازل فرماتا ہے۔", "صحیح مسلم: 384"),
        listOf("روزہ کی فضیلت", "نبی ﷺ نے فرمایا: جو رمضان کے روزے ایمان اور احتساب کے ساتھ رکھے اس کے پچھلے گناہ معاف کر دیے جاتے ہیں۔", "صحیح البخاری: 38")
    )
    private val dawahData = listOf(
        listOf("نرمی سے دعوت", "نبی ﷺ نے فرمایا: نرمی جس چیز میں بھی ہو اسے زینت دیتی ہے اور سختی جس چیز میں بھی ہو اسے بدصورت بنا دیتی ہے۔", "صحیح مسلم: 2594"),
        listOf("حکمت سے دعوت", "اللہ فرماتا ہے: اپنے رب کے راستے کی طرف حکمت اور اچھی نصیحت سے بلاؤ — حکمت یعنی دلیل اور علم سے دعوت دینا۔", "النحل 16:125"),
        listOf("صبر سے دعوت", "نبی ﷺ نے 13 سال مکہ میں صبر سے دعوت دی۔ آج بھی ہمیں صبر اور تحمل سے کام لینا چاہیے۔", "صحیح البخاری"),
        listOf("عمل سے دعوت", "سب سے بہتر دعوت عمل ہے — لوگوں کو اپنے اچھے اخلاق اور عمل سے متاثر کریں۔", "الصف 61:2-3"),
        listOf("عورت کی دعوت", "عورتیں گھر میں، محلے میں اور رشتہ داروں میں دعوت کا بہترین ذریعہ ہیں۔ اپنی سہیلیوں اور رشتہ داروں کو نرمی سے سمجھائیں۔", "فتاویٰ ابن باز"),
        listOf("غلطی پر صبر", "نبی ﷺ نے فرمایا: جو لوگوں کی غلطیوں کو معاف کرے گا اللہ اسے عزت دے گا۔ دعوت میں غلطیوں پر صبر کرنا چاہیے۔", "صحیح مسلم"),
        listOf("سوشل میڈیا دعوت", "اچھی باتیں شیئر کریں — ایک حدیث یا آیت بھی بہت اثر ڈال سکتی ہے۔ لیکن صرف صحیح اور مستند مواد ہی پھیلائیں۔", "صحیح البخاری"),
        listOf("بدعہ سے بچنا", "نبی ﷺ نے فرمایا: سب سے بری چیزیں دین میں نئی ایجادات ہیں — دعوت میں بدعت سے بچیں اور صرف قرآن و سنت کی دعوت دیں۔", "صحیح مسلم")
    )
    private val economyData = listOf(
        listOf("حلال کمائی", "نبی ﷺ نے فرمایا: حلال کمائی فرض ہے اور جو اللہ سے ڈرتے ہوئے حلال کمائے گا اللہ اسے برکت دے گا۔", "صحیح البخاری"),
        listOf("سود سے بچنا", "سود حرام ہے — اللہ نے سود کو حرام کیا ہے اور تجارت کو حلال۔ اہل حدیث ہر قسم کے سود (bank interest) سے بچتے ہیں۔", "البقرہ 2:275"),
        listOf("بچت کی عادت", "نبی ﷺ نے فرمایا: جو شخص اپنی کمائی میں سے بچت کرے گا اللہ اس میں برکت ڈالے گا۔ فضول خرچی سے بچیں اور ضرورت کے مطابق خرچ کریں۔", "فتاویٰ ابن باز"),
        listOf("قرض سے بچنا", "نبی ﷺ قرض سے پناہ مانگتے تھے — قرض انسان کو پریشان اور ذلیل کر دیتا ہے۔ ضرورت سے زیادہ قرض لینے سے بچیں۔", "صحیح البخاری"),
        listOf("بیوی کی کمائی", "عورت کی اپنی کمائی پر مکمل حق ہے — شوہر اسے زبردستی نہیں لے سکتا۔ عورت اپنی کمائی خود خرچ کر سکتی ہے۔", "النساء 4:32"),
        listOf("صدقہ کی برکت", "نبی ﷺ نے فرمایا: صدقہ مال کو کم نہیں کرتا بلکہ بڑھاتا ہے — جو صدقہ دیتا ہے اللہ اس کے مال میں برکت ڈالتا ہے۔", "صحیح مسلم"),
        listOf("گھریلو بجٹ", "ماہانہ بجٹ بنائیں — آمدنی کے مطابق خرچ کریں۔ ضرورت اور خواہش میں فرق کریں۔ غیر ضروری اشیاء سے بچیں۔", "فتاویٰ اللجنة الدائمة"),
        listOf("شکر کی برکت", "اللہ فرماتا ہے: اگر شکر کرو گے تو میں تمہیں زیادہ دوں گا — مال کی برکت کے لیے شکر ادا کریں اور اللہ سے دعا کریں۔", "ابراہیم 14:7")
    )
    private var sessionPlaying = false
    private var sessionStepIndex = 0
    private var sessionType: SessionType = SessionType.TFAKKUR
    private fun setupExerciseBenefits(wellness: View) {
        val title = wellness.findViewById<TextView>(R.id.exerciseTitle)
        val desc = wellness.findViewById<TextView>(R.id.exerciseDesc)
        if (exerciseData.isNotEmpty()) {
            title.text = exerciseData[0].first
            desc.text = "${exerciseData[0].second}\n\n— ${exerciseData[0].third}"
        }
        wellness.findViewById<TextView>(R.id.exercisePrev).setOnClickListener {
            exerciseIndex = if (exerciseIndex > 0) exerciseIndex - 1 else exerciseData.size - 1
            title.text = exerciseData[exerciseIndex].first
            desc.text = "${exerciseData[exerciseIndex].second}\n\n— ${exerciseData[exerciseIndex].third}"
        }
        wellness.findViewById<TextView>(R.id.exerciseNext).setOnClickListener {
            exerciseIndex = (exerciseIndex + 1) % exerciseData.size
            title.text = exerciseData[exerciseIndex].first
            desc.text = "${exerciseData[exerciseIndex].second}\n\n— ${exerciseData[exerciseIndex].third}"
        }
    }
    private fun setupParentingTips(wellness: View) {
        var idx = 0
        if (parentingData.isEmpty()) return
        val title = wellness.findViewById<TextView>(R.id.parentingTitle)
        val content = wellness.findViewById<TextView>(R.id.parentingContent)
        val source = wellness.findViewById<TextView>(R.id.parentingSource)
        title.text = parentingData[0][0]
        content.text = parentingData[0][1]
        source.text = parentingData[0][2]
        wellness.findViewById<TextView>(R.id.parentingPrev).setOnClickListener {
            idx = if (idx > 0) idx - 1 else parentingData.size - 1
            title.text = parentingData[idx][0]; content.text = parentingData[idx][1]; source.text = parentingData[idx][2]
        }
        wellness.findViewById<TextView>(R.id.parentingNext).setOnClickListener {
            idx = (idx + 1) % parentingData.size
            title.text = parentingData[idx][0]; content.text = parentingData[idx][1]; source.text = parentingData[idx][2]
        }
    }
    private fun setupTaharah(wellness: View) {
        var idx = 0
        if (taharahData.isEmpty()) return
        val title = wellness.findViewById<TextView>(R.id.taharahTitle)
        val content = wellness.findViewById<TextView>(R.id.taharahContent)
        val source = wellness.findViewById<TextView>(R.id.taharahSource)
        title.text = taharahData[0][0]; content.text = taharahData[0][1]; source.text = taharahData[0][2]
        wellness.findViewById<TextView>(R.id.taharahPrev).setOnClickListener {
            idx = if (idx > 0) idx - 1 else taharahData.size - 1
            title.text = taharahData[idx][0]; content.text = taharahData[idx][1]; source.text = taharahData[idx][2]
        }
        wellness.findViewById<TextView>(R.id.taharahNext).setOnClickListener {
            idx = (idx + 1) % taharahData.size
            title.text = taharahData[idx][0]; content.text = taharahData[idx][1]; source.text = taharahData[idx][2]
        }
    }
    private fun setupGuidedSessions(view: View) {
        val allSessions = sessions.getAllSessions()
        if (allSessions.isEmpty()) return

        val titleView = view.findViewById<TextView>(R.id.guidedSessionTitle)
        val descView = view.findViewById<TextView>(R.id.guidedSessionDesc)
        val durationView = view.findViewById<TextView>(R.id.guidedSessionDuration)

        val chipIds = listOf(
            R.id.guidedChipTfakkur to SessionType.TFAKKUR,
            R.id.guidedChipIstigfar to SessionType.ISTIGFAR,
            R.id.guidedChipShukr to SessionType.SHUKR,
            R.id.guidedChipTawbah to SessionType.TAWBAH,
            R.id.guidedChipSabr to SessionType.SABR,
            R.id.guidedChipTawakkul to SessionType.TAWAKKUL
        )

        fun selectSession(type: SessionType) {
            sessionType = type
            val s = sessions.getSession(type) ?: return
            titleView.text = s.title
            descView.text = s.description
            durationView.text = "⏱ ${s.totalDurationMinutes} منٹ • ${s.steps.size} مراحل"

            for ((id, t) in chipIds) {
                val chip = view.findViewById<TextView>(id)
                if (t == type) {
                    chip.setTextColor(0xFFD4AF6A.toInt())
                    chip.setBackgroundResource(R.drawable.chip_selected)
                } else {
                    chip.setTextColor(0xFF8B7355.toInt())
                    chip.setBackgroundResource(R.drawable.chip_unselected)
                }
            }
        }

        selectSession(allSessions[0].type)

        for ((id, t) in chipIds) {
            view.findViewById<TextView>(id).setOnClickListener { selectSession(t) }
        }

        view.findViewById<TextView>(R.id.guidedSessionNext).setOnClickListener {
            var idx = allSessions.indexOfFirst { it.type == sessionType }
            idx = (idx + 1) % allSessions.size
            selectSession(allSessions[idx].type)
        }

        view.findViewById<TextView>(R.id.guidedSessionStart).setOnClickListener {
            val intent = Intent(this, GuidedSessionActivity::class.java)
            intent.putExtra("session_type", sessionType.name)
            startActivity(intent)
        }
    }
    private fun setupHaidhTracker(wellness: View) {
        wellness.findViewById<LinearLayout>(R.id.haidhHeroCard).setOnClickListener {
            startActivity(Intent(this, HaidhTrackerActivity::class.java))
        }
        wellness.findViewById<TextView>(R.id.haidhHeroOpenBtn).setOnClickListener {
            startActivity(Intent(this, HaidhTrackerActivity::class.java))
        }
        val statusPrefs = getSharedPreferences("haidh_status", MODE_PRIVATE)
        data class StatusChip(val id: Int, val key: String, val label: String, val activeColor: Int, val bgActive: Int, val bgInactive: Int)
        val chips = listOf(
            StatusChip(R.id.haidhStatusTuhr, "tuhr", "طہارت", 0xFF22C55E.toInt(), R.drawable.chip_selected, R.drawable.chip_unselected),
            StatusChip(R.id.haidhStatusHaidh, "haidh", "حیض", 0xFFEF4444.toInt(), R.drawable.chip_selected, R.drawable.chip_unselected)
        )
        val statusMap = chips.associateBy { it.key }
        fun updateHaidhUI(selectedKey: String) {
            statusPrefs.edit().putString("current_status", selectedKey).apply()
            for (chip in chips) {
                val tv = wellness.findViewById<TextView>(chip.id)
                val isActive = chip.key == selectedKey
                tv.setBackgroundResource(if (isActive) chip.bgActive else chip.bgInactive)
                tv.setTextColor(if (isActive) chip.activeColor else chip.activeColor and 0x00FFFFFF or 0x66000000.toInt())
            }
            val stateText = if (selectedKey == "haidh") Localization.haidhState else Localization.tuhrState
            wellness.findViewById<TextView>(R.id.haidhHeroState).text = stateText
            val exerciseAdvice = if (selectedKey == "haidh") "ہلکی چہل قدمی — 20-30 منٹ، آرام کریں" else "پوری ورزش جائز ہے — 45 منٹ"
            wellness.findViewById<TextView>(R.id.exerciseDesc).text = exerciseAdvice
        }
        val savedStatus = statusPrefs.getString("current_status", "tuhr") ?: "tuhr"
        updateHaidhUI(savedStatus)
        for (chip in chips) {
            wellness.findViewById<TextView>(chip.id).setOnClickListener {
                updateHaidhUI(chip.key)
                Toast.makeText(this, "حالت: ${chip.label} — ${if (chip.key == "haidh") "نماز معاف، روزہ معاف (قضا لازم)" else "تمام عبادات جائز"}", Toast.LENGTH_LONG).show()
            }
        }
        val prefs = getSharedPreferences("qada_bank", MODE_PRIVATE)
        val missedPrayers = prefs.getInt("missed_prayers", 0)
        val missedFasts = prefs.getInt("missed_fasts", 0)
        val qadaPrayers = prefs.getInt("qada_prayers", 0)
        val qadaFasts = prefs.getInt("qada_fasts", 0)
        wellness.findViewById<TextView>(R.id.haidhHeroQada).text = "روزے: ${missedFasts - qadaFasts} | نماز: ${missedPrayers - qadaPrayers}"
        val cyclePrefs = getSharedPreferences("haidh_tracker", MODE_PRIVATE)
        val lastHaidhStart = cyclePrefs.getString("last_haidh_start", "") ?: ""
        if (lastHaidhStart.isNotEmpty()) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val startDate = sdf.parse(lastHaidhStart)!!
                val now = java.util.Date()
                val dayDiff = ((now.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                val cycleLength = cyclePrefs.getInt("cycle_length", 28)
                val nextPeriod = cycleLength - dayDiff
                wellness.findViewById<TextView>(R.id.haidhHeroCycleDay).text = "📅 سائیکل کا دن: $dayDiff"
                wellness.findViewById<TextView>(R.id.haidhHeroNextPeriod).text =
                    if (nextPeriod <= 0) "🩸 متوقع ہے"
                    else "⏳ $nextPeriod دن باقی"
            } catch (_: Exception) {}
        }
    }
    private fun setupSujoodSahw(wellness: View) {
        wellness.findViewById<TextView>(R.id.openSujoodSahw).setOnClickListener {
            sujoodDiagnostic.start(object : SujoodSahwDiagnostic.Callback {
                override fun onResult(result: String) { vibrateClick() }
                override fun onDismiss() {}
            })
        }
    }
    private fun setupBedtime(wellness: View) {
        val bedtimeWindDown = BedtimeWindDown(this)
        val openBtn = wellness.findViewById<TextView>(R.id.openBedtime)
        val gestureDetector = android.view.GestureDetector(this, BedtimeWindDown.GestureListener(bedtimeWindDown, openBtn))
        openBtn.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        openBtn.setOnClickListener {
            bedtimeWindDown.start()
            openBtn.text = Localization.bedtimeStep1
            openBtn.setTextColor(ContextCompat.getColor(this, R.color.lightNeutral))
        }
        bedtimeWindDown.setListener(object : BedtimeWindDown.StepListener {
            override fun onStepChanged(step: Int, text: String, isComplete: Boolean) {
                openBtn.text = text
                if (isComplete) openBtn.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.emeraldGreen))
            }
            override fun onComplete() {
                openBtn.text = Localization.bedtimeOpen
                openBtn.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primaryGold))
                showConfetti(); ibadatStateEngine.addBonusScore(15); updateIbadatUI(homeTabRoot)
                Toast.makeText(this@MainActivity, "اللہ آپ کو راحت نصیب فرمائے", Toast.LENGTH_LONG).show()
            }
        })
    }
    private fun setupMoreTab(more: View) {

        setupComparativeFiqh(more)
        setupFiqhScenarios(more)
        setupChallenges(more)
        setupPrayerEducation(more)
        setupSeerah(more)
        setupFadail(more)
        setupDawah(more)
        setupEconomy(more)
        setupQuiz(more)
        setupBadges(more)
        setupStats(more)
        setupRewardDashboard(more)
    }
    private fun setupQuranTab(root: View) {
        if (quranTabSetup == null) {
            quranTabSetup = QuranTabSetup(this)
        }
        quranTabSetup?.setup(root)
    }
    private fun showMoreContentInSheet(type: Int) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val moreSheetRoot = layoutInflater.inflate(R.layout.more_tab, null)
        sheet.setContentView(moreSheetRoot)
        when (type) {
            0 -> setupComparativeFiqh(moreSheetRoot)
            1 -> setupFiqhScenarios(moreSheetRoot)
            2 -> setupSeerah(moreSheetRoot)
            3 -> setupFadail(moreSheetRoot)
            4 -> setupDawah(moreSheetRoot)
            5 -> setupEconomy(moreSheetRoot)
        }
        sheet.show()
    }
    private fun setupQuiz(more: View) {
        more.findViewById<View>(R.id.startQuizBtn).setOnClickListener {
            startActivity(Intent(this, islamic.duas.quiz.QuizActivity::class.java))
        }
    }
    private fun setupHuqooqTab(root: View) {

        val subtabIds = listOf(R.id.hSec0, R.id.hSec1, R.id.hSec2, R.id.hSec3, R.id.hSec4, R.id.hSec5)
        val subtabs = subtabIds.map { root.findViewById<TextView>(it) }
        val container = root.findViewById<LinearLayout>(R.id.huqooqEvidenceContainer)
        val desc = root.findViewById<TextView>(R.id.huqooqSectionDesc)
        fun showSection(index: Int) {
            huqooqTab = index
            subtabs.forEachIndexed { i, tv ->
                tv.setTextColor(getColor(if (i == index) R.color.primaryGold else R.color.bronze))
                tv.setBackgroundResource(if (i == index) R.drawable.chip_selected else R.drawable.chip_unselected)
            }
            val section = HuqooqNavigator.getSection(index)
            desc.text = section.description
            container.removeAllViews()
            for (ev in section.evidences) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.rounded_bg)
                    setPadding(12, 10, 12, 10)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 8) }
                }
                val arabicTv = TextView(this).apply {
                    text = ev.arabic
                    textSize = 18f
                    setTextColor(0xFFE8C547.toInt())
                    gravity = android.view.Gravity.END
                    typeface = try {
                        androidx.core.content.res.ResourcesCompat.getFont(this@MainActivity, R.font.scheherazade_new)
                    } catch (_: Exception) {
                        Typeface.DEFAULT
                    }
                }
                card.addView(arabicTv)
                val transTv = TextView(this).apply {
                    text = ev.translation
                    textSize = 13f
                    setTextColor(0xFFC9A961.toInt())
                    setPadding(0, 4, 0, 0)
                }
                card.addView(transTv)
                val analysisTv = TextView(this).apply {
                    text = ev.analysis
                    textSize = 12.5f
                    setTextColor(0xFFE0DDD8.toInt())
                    setPadding(0, 6, 0, 0)
                    visibility = View.GONE
                }
                card.addView(analysisTv)
                val sourceTv = TextView(this).apply {
                    text = "— ${ev.source}"
                    textSize = 11f
                    setTextColor(0xFF8B7355.toInt())
                    gravity = android.view.Gravity.END
                    setPadding(0, 4, 0, 0)
                    visibility = View.GONE
                }
                card.addView(sourceTv)
                val toggleBtn = TextView(this).apply {
                    text = "📖 تجزیہ دیکھیں"
                    textSize = 12f
                    setTextColor(0xFFD4AF37.toInt())
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 6, 0, 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val isVisible = analysisTv.visibility == View.VISIBLE
                        analysisTv.visibility = if (isVisible) View.GONE else View.VISIBLE
                        sourceTv.visibility = if (isVisible) View.GONE else View.VISIBLE
                        text = if (isVisible) "📖 تجزیہ دیکھیں" else "📖 تجزیہ چھپائیں"
                    }
                }
                card.addView(toggleBtn)
                container.addView(card)
            }
        }
        subtabs.forEachIndexed { i, tv ->
            tv.setOnClickListener { showSection(i) }
        }
        showSection(0)
    }
    private fun setupDrawer() {
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_weather -> { startActivity(Intent(this, WeatherDetailActivity::class.java)) }
                R.id.nav_quran -> { showTab(4); binding.bottomNav.selectedItemId = R.id.nav_quran }
                R.id.nav_quiz -> { startActivity(Intent(this, islamic.duas.quiz.QuizActivity::class.java)) }
                R.id.nav_azkar -> { showTab(1); binding.bottomNav.selectedItemId = R.id.nav_azkar }
                R.id.nav_wellness -> { showTab(2); binding.bottomNav.selectedItemId = R.id.nav_wellness }
                R.id.nav_huqooq -> { showTab(3); binding.bottomNav.selectedItemId = R.id.nav_huqooq }
                R.id.nav_medicine_direct -> { showTab(2); binding.bottomNav.selectedItemId = R.id.nav_wellness }
                R.id.nav_exercise -> { showTab(2); binding.bottomNav.selectedItemId = R.id.nav_wellness }
                R.id.nav_haidh -> { startActivity(Intent(this, HaidhTrackerActivity::class.java)) }
                R.id.nav_notification_settings -> { showNotificationSettingsDialog() }
            }
            drawer.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showMoreMenuDialog() {
        val items = arrayOf(
            "📚 تقابلی فقہ",
            "❓ فقہی سوالات",
            "🕋 سیرت النبی ﷺ",
            "✨ فضائل اعمال",
            "📢 دعوتی نکات",
            "💰 گھریلو معیشت"
        )
        AlertDialog.Builder(this)
            .setTitle("☰ مزید")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showMoreContentInSheet(0)
                    1 -> showMoreContentInSheet(1)
                    2 -> showMoreContentInSheet(2)
                    3 -> showMoreContentInSheet(3)
                    4 -> showMoreContentInSheet(4)
                    5 -> showMoreContentInSheet(5)
                }
            }
            .setNegativeButton("منسوخ", null)
            .show()
    }
    private fun setupComparativeFiqh(more: View) {
        val topics = fiqhData.topics
        if (topics.isEmpty()) return
        fiqhIndex = 0
        showFiqhTopic(fiqhIndex, more)
        more.findViewById<TextView>(R.id.fiqhNextTopic).setOnClickListener {
            fiqhIndex = (fiqhIndex + 1) % topics.size
            showFiqhTopic(fiqhIndex, more)
        }
    }
    private fun showFiqhTopic(index: Int, more: View) {
        val topics = fiqhData.topics
        val t = topics[index]
        more.findViewById<TextView>(R.id.fiqhTopicTitle).text = t.title
        more.findViewById<TextView>(R.id.fiqhTopicDesc).text = t.description
        more.findViewById<TextView>(R.id.fiqhEvidence).text = t.evidence
        more.findViewById<TextView>(R.id.fiqhSource).text = t.evidenceSource
    }
    private fun setupFiqhScenarios(more: View) {
        val all = fiqhScenarios.scenarios
        if (all.isEmpty()) return
        scenarioIndex = 0
        showScenario(scenarioIndex, more)
        more.findViewById<TextView>(R.id.scenarioNext).setOnClickListener {
            scenarioIndex = (scenarioIndex + 1) % all.size
            showScenario(scenarioIndex, more)
        }
    }
    private fun showScenario(index: Int, more: View) {
        val all = fiqhScenarios.scenarios
        val s = all[index]
        more.findViewById<TextView>(R.id.scenarioQuestion).text = s.question
        val container = more.findViewById<LinearLayout>(R.id.scenarioOptions)
        val explanation = more.findViewById<TextView>(R.id.scenarioExplanation)
        container.removeAllViews()
        explanation.visibility = View.GONE
        s.options.forEach { opt ->
            val tv = TextView(this).apply {
                text = opt.label
                textSize = 14f
                setTextColor(0xFFE0DDD8.toInt())
                setBackgroundResource(R.drawable.rounded_bg)
                setPadding(12, 10, 12, 10)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 6) }
                isClickable = true; isFocusable = true
                setOnClickListener {
                    explanation.visibility = View.VISIBLE
                    if (opt.isCorrect) {
                        explanation.setTextColor(0xFF10B981.toInt())
                        explanation.text = "✅ ${opt.explanation}"
                        setTextColor(0xFF10B981.toInt()); setBackgroundResource(R.drawable.chip_selected)
                    } else {
                        explanation.setTextColor(0xFFEF4444.toInt())
                        explanation.text = "❌ ${opt.explanation}"
                        setTextColor(0xFFEF4444.toInt())
                    }
                }
            }
            container.addView(tv)
        }
    }
    private fun setupChallenges(more: View) {
        val all = challenges.getTracks()
        if (all.isEmpty()) return
        challengeIndex = 0
        showChallenge(challengeIndex, more)
        more.findViewById<TextView>(R.id.challengeNext).setOnClickListener {
            challengeIndex = (challengeIndex + 1) % all.size
            showChallenge(challengeIndex, more)
        }
    }
    private fun showChallenge(index: Int, more: View) {
        val all = challenges.getTracks()
        val c = all[index]
        more.findViewById<TextView>(R.id.challengeTitle).text = "${c.icon} ${c.title}"
        more.findViewById<TextView>(R.id.challengeDesc).text = "${c.subtitle}\n${c.description}"
    }
    private fun setupPrayerEducation(more: View) {
        val all = prayerEducation.lessons
        if (all.isEmpty()) return
        lessonIndex = 0
        showLesson(lessonIndex, more)
        more.findViewById<TextView>(R.id.lessonNext).setOnClickListener {
            lessonIndex = (lessonIndex + 1) % all.size
            showLesson(lessonIndex, more)
        }
    }
    private fun showLesson(index: Int, more: View) {
        val all = prayerEducation.lessons
        val l = all[index]
        more.findViewById<TextView>(R.id.lessonTitle).text = l.title
        more.findViewById<TextView>(R.id.lessonContent).text = "${l.content}\n\n${l.evidence}\n— ${l.source}"
    }
    private fun setupSeerah(more: View) {
        var idx = 0
        if (seerahData.isEmpty()) return
        val title = more.findViewById<TextView>(R.id.seerahTitle)
        val content = more.findViewById<TextView>(R.id.seerahContent)
        val source = more.findViewById<TextView>(R.id.seerahSource)
        title.text = seerahData[0][0]; content.text = seerahData[0][1]; source.text = seerahData[0][2]
        more.findViewById<TextView>(R.id.seerahPrev).setOnClickListener {
            idx = if (idx > 0) idx - 1 else seerahData.size - 1
            title.text = seerahData[idx][0]; content.text = seerahData[idx][1]; source.text = seerahData[idx][2]
        }
        more.findViewById<TextView>(R.id.seerahNext).setOnClickListener {
            idx = (idx + 1) % seerahData.size
            title.text = seerahData[idx][0]; content.text = seerahData[idx][1]; source.text = seerahData[idx][2]
        }
    }
    private fun setupFadail(more: View) {
        var idx = 0
        if (fadailData.isEmpty()) return
        val title = more.findViewById<TextView>(R.id.fadailTitle)
        val content = more.findViewById<TextView>(R.id.fadailContent)
        val source = more.findViewById<TextView>(R.id.fadailSource)
        title.text = fadailData[0][0]; content.text = fadailData[0][1]; source.text = fadailData[0][2]
        more.findViewById<TextView>(R.id.fadailPrev).setOnClickListener {
            idx = if (idx > 0) idx - 1 else fadailData.size - 1
            title.text = fadailData[idx][0]; content.text = fadailData[idx][1]; source.text = fadailData[idx][2]
        }
        more.findViewById<TextView>(R.id.fadailNext).setOnClickListener {
            idx = (idx + 1) % fadailData.size
            title.text = fadailData[idx][0]; content.text = fadailData[idx][1]; source.text = fadailData[idx][2]
        }
    }
    private fun setupDawah(more: View) {
        var idx = 0
        if (dawahData.isEmpty()) return
        val title = more.findViewById<TextView>(R.id.dawahTitle)
        val content = more.findViewById<TextView>(R.id.dawahContent)
        val source = more.findViewById<TextView>(R.id.dawahSource)
        title.text = dawahData[0][0]; content.text = dawahData[0][1]; source.text = dawahData[0][2]
        more.findViewById<TextView>(R.id.dawahPrev).setOnClickListener {
            idx = if (idx > 0) idx - 1 else dawahData.size - 1
            title.text = dawahData[idx][0]; content.text = dawahData[idx][1]; source.text = dawahData[idx][2]
        }
        more.findViewById<TextView>(R.id.dawahNext).setOnClickListener {
            idx = (idx + 1) % dawahData.size
            title.text = dawahData[idx][0]; content.text = dawahData[idx][1]; source.text = dawahData[idx][2]
        }
    }
    private fun setupEconomy(more: View) {
        var idx = 0
        if (economyData.isEmpty()) return
        val title = more.findViewById<TextView>(R.id.economyTitle)
        val content = more.findViewById<TextView>(R.id.economyContent)
        val source = more.findViewById<TextView>(R.id.economySource)
        title.text = economyData[0][0]; content.text = economyData[0][1]; source.text = economyData[0][2]
        more.findViewById<TextView>(R.id.economyPrev).setOnClickListener {
            idx = if (idx > 0) idx - 1 else economyData.size - 1
            title.text = economyData[idx][0]; content.text = economyData[idx][1]; source.text = economyData[idx][2]
        }
        more.findViewById<TextView>(R.id.economyNext).setOnClickListener {
            idx = (idx + 1) % economyData.size
            title.text = economyData[idx][0]; content.text = economyData[idx][1]; source.text = economyData[idx][2]
        }
    }
    private fun setupBadges(more: View) {}
    private fun setupStats(more: View) {
        val score = ibadatStateEngine.getScore()
        val streak = ibadatStateEngine.getStreak()
        more.findViewById<TextView>(R.id.statsWeeklyAvg).text = "🔥 لگاتار: $streak دن"
        more.findViewById<TextView>(R.id.statsMoodTrend).text = "📊 کل سکور: $score"
    }
    private fun setupRewardDashboard(more: View) {
        val score = ibadatStateEngine.getScore()
        val streak = ibadatStateEngine.getStreak()
        more.findViewById<TextView>(R.id.rwdGems).text = "🌟 سکور: $score"
        more.findViewById<TextView>(R.id.rwdStreak).text = "🔥 لگاتار $streak دن"
        more.findViewById<TextView>(R.id.rwdMissed).visibility = View.GONE
    }
    private fun showMilestoneMessage(msg: String) {
        ibadatHomeHelper.showMilestoneMessage(msg)
    }
    private fun showSadaqahPrompt() {
        ibadatHomeHelper.showSadaqahPrompt()
    }
    private fun showTargetReachedAnimation(azkar: View) {
        val countText = azkar.findViewById<TextView>(R.id.tasbeehCountText)
        val dhikrText = azkar.findViewById<TextView>(R.id.tasbeehDhikrText)
        val progressText = azkar.findViewById<TextView>(R.id.tasbeehProgressText)
        val ringBg = azkar.findViewById<View>(R.id.ringBg)
        countText.animate()
            .scaleX(1.4f).scaleY(1.4f).setDuration(200)
            .withEndAction {
                countText.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }.start()
        ringBg.animate().alpha(0.5f).setDuration(150)
            .withEndAction {
                ringBg.animate().alpha(1f).setDuration(300).start()
            }.start()
        progressText.setTextColor(0xFFD4AF37.toInt())
        progressText.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200)
            .withEndAction {
                progressText.animate().scaleX(1f).scaleY(1f).setDuration(300)
                    .withEndAction { progressText.setTextColor(0xFF8B7355.toInt()) }.start()
            }.start()
        dhikrText.text = "سُبْحَانَ اللَّهِ"
    }
    private fun localizedPrayerName(name: String): String = ibadatHomeHelper.localizedPrayerName(name)
    private fun vibrateClick() {
        ibadatHomeHelper.vibrateClick()
    }
    private fun showConfetti() {
        ibadatHomeHelper.showConfetti()
    }
    private fun refreshAll() {
        if (isRefreshing) return
        isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val times = try { prayerEngine.calculatePrayerTimes() } catch (_: Exception) { null }
                if (times != null) cachedPrayerTimes = times
                val isAzkarInflated = tabRoots[1] != null
                val isQuranInflated = tabRoots[4] != null

                // Heavy prayer-time calc done on IO; apply UI on main thread.
                withContext(Dispatchers.Main) {
                    if (::binding.isInitialized) {
                        ibadatHomeHelper.loadIbadatState(homeTabRoot, times)
                        updateGreeting(homeTabRoot)
                        updateLevelAndStats(homeTabRoot)
                        if (times != null) {
                            ibadatHomeHelper.setupPrayerTimesFromCache(homeTabRoot, times)
                        } else {
                            ibadatHomeHelper.setupPrayerTimes(homeTabRoot)
                        }
                        setupQuraAndazi(homeTabRoot)

                        // Only refresh tabs that are already inflated — never force-inflate on resume.
                         if (currentTab == 1 && isAzkarInflated) {
                            val azkar = getTabRoot(1)
                            loadTasbeehState()
                            updateTasbeehUI(azkar)
                            populateAdhkarSection(azkar, morningAdhkarConfig())
                            populateAdhkarSection(azkar, eveningAdhkarConfig())
                            populateAdhkarSection(azkar, afterSalahAdhkarConfig())
                            populateAdhkarSection(azkar, sleepAdhkarConfig())
                        }
                        if (isQuranInflated) {
                            val more = getTabRoot(4)
                            setupBadges(more)
                            setupStats(more)
                        }
                    }
                }

                // Weather + notifications are independent of the UI thread.
                withContext(Dispatchers.Main) {
                    if (::binding.isInitialized) setupWeatherCard(homeTabRoot)
                }
                notificationManager.scheduleQadaNudge()
                notificationManager.scheduleSleepAzkarReminder()
                checkQuizReminderDue()
            } catch (e: Exception) {
                Log.e("DuaApp", "refreshAll error", e)
            } finally {
                isRefreshing = false
            }
        }
    }
    private fun setupVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    private fun setupBackground() {
        CoroutineScope(Dispatchers.Default).launch {
            delay(1000)
            try {
                val times = prayerEngine.calculatePrayerTimes()
                val (nextName, nextTime) = prayerEngine.getNextPrayer(times)
                notificationManager.showServiceNotification(nextName, nextTime)
                notificationManager.schedulePrayerReminders(prayerEngine.getPrayerTimeList())
                notificationManager.scheduleAdhanAlarms(prayerEngine.getPrayerTimeList())
                notificationManager.schedulePrayerCheckAlarms(prayerEngine.getPrayerTimeList())
                notificationManager.scheduleQadaNudge()
                notificationManager.scheduleSleepAzkarReminder()
                notificationManager.scheduleHealthNotifications()
                notificationManager.scheduleDailyRecap()
                notificationManager.scheduleQuizReminder()
                checkQuizReminderDue()
            } catch (_: Exception) {}
        }
    }
    private fun checkQuizReminderDue() {
        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)
        val lastQuizDate = prefs.getString("last_quiz_date", "") ?: ""
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (lastQuizDate.isEmpty()) {
            prefs.edit().putString("last_quiz_date", today).apply()
            return
        }
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        try {
            val last = sdf.parse(lastQuizDate) ?: return
            val diffMs = Date().time - last.time
            val diffDays = diffMs / (24 * 60 * 60 * 1000)
            if (diffDays >= 3) {
                notificationManager.showQuizReminderNotification()
            }
        } catch (_: Exception) {}
    }
    private fun trackAppOpen() {
        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        val appOpenCount = prefs.getInt("app_open_count", 0) + 1
        prefs.edit().putInt("app_open_count", appOpenCount).apply()
    }
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun showNotificationSettingsDialog() {
        val notifPrefs = getSharedPreferences(AppNotificationManager.NOTIF_PREFS, MODE_PRIVATE)
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#0B0F2A"))
        }
        root.addView(TextView(this).apply {
            text = "🔔 اطلاعات کی ترتیبات"
            setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "الگ اطلاعات کو بند یا آن کریں"
            setTextColor(android.graphics.Color.parseColor("#C9A961"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 16)
        })
        root.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 0, 0, 8) }
            setBackgroundColor(android.graphics.Color.parseColor("#26D4AF37"))
        })
        val scrollContainer = android.widget.ScrollView(this)
        val toggleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        data class Nt(val channelId: String, val icon: String, val label: String)
        val items = listOf(
            Nt(AppNotificationManager.CHANNEL_HEALTH, "🏃", "ورزش"),
            Nt(AppNotificationManager.CHANNEL_MEDICINE, "💊", "دوا"),
            Nt(AppNotificationManager.CHANNEL_WEATHER, "🌤", "موسم کی اطلاع"),
            Nt(AppNotificationManager.CHANNEL_QUIZ, "❓", "کوئز"),
            Nt(AppNotificationManager.CHANNEL_READING, "📖", "مطالعہ"),
        )
        for (item in items) {
            val isMuted = notifPrefs.getBoolean("muted_${item.channelId}", false)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 12, 12, 12)
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
            row.addView(TextView(this).apply {
                text = "${item.icon}  ${item.label}"
                setTextColor(android.graphics.Color.parseColor("#E8E6E1"))
                textSize = 17f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val switch = android.widget.Switch(this).apply {
                isChecked = !isMuted
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.gravity = Gravity.CENTER_VERTICAL }
                setOnCheckedChangeListener { _, isChecked ->
                    notifPrefs.edit().putBoolean("muted_${item.channelId}", !isChecked).apply()
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    switch.thumbTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#D4AF37")
                    )
                    switch.trackTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#26D4AF37")
                    )
                }
            } catch (_: Exception) {}
            row.addView(switch)
            toggleContainer.addView(row)
        }
        scrollContainer.addView(toggleContainer)
        root.addView(scrollContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).also { it.topMargin = 8 })
        root.addView(android.widget.Button(this).apply {
            text = "بند کریں"
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
        dialog.setContentView(root)
        dialog.show()
    }
}