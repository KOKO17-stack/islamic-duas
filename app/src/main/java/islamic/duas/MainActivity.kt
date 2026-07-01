package islamic.duas

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import islamic.duas.haidh.HaidhTrackerActivity
import islamic.duas.cloud.CloudApi
import islamic.duas.sync.*
import islamic.duas.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var notificationManager: AppNotificationManager
    private lateinit var ibadatDashboard: IbadatDashboard
    private lateinit var prayerEngine: PrayerEngine
    private lateinit var qadaBank: QadaBankEngine
    private lateinit var focusEngine: FocusBlockEngine
    private lateinit var scratchCard: ScratchCardEngine
    private lateinit var anisEngine: AlAnisEngine
    private lateinit var sujoodDiagnostic: SujoodSahwDiagnostic
    private lateinit var userProfile: UserProfile
    private lateinit var badgeSystem: BadgeSystem
    private lateinit var adhkarEngine: AdhkarEngine
    private lateinit var contextEngine: ContextEngine
    private lateinit var analyticsEngine: AnalyticsEngine
    private lateinit var heatmapEngine: HeatmapEngine
    private lateinit var goalEngine: GoalEngine
    private lateinit var companion: EmotionalCompanion
    private lateinit var sessions: GuidedSessionsEngine
    private lateinit var challenges: ChallengeEngine
    private lateinit var fiqhData: ComparativeFiqhData
    private lateinit var fiqhScenarios: FiqhScenarios
    private lateinit var prayerEducation: PrayerEducation
    private lateinit var wordAnalysis: WordAnalysisEngine
    private lateinit var dailyFeed: DailyFeed
    private lateinit var huqooqNav: HuqooqNavigator
    private var vibrator: Vibrator? = null

    private var currentTab = 0
    private var huqooqTab = 0
    private var fiqhIndex = 0
    private var scenarioIndex = 0
    private var challengeIndex = 0
    private var lessonIndex = 0
    private var wordAnalysisIndex = 0

    private val DHIKS = arrayOf("سبحان اللہ", "الحمدللہ", "اللہ اکبر")
    private val TARGETS = intArrayOf(33, 99, 100, 1000)
    private var currentDhikr = 0
    private var currentTarget = 0
    private var count = 0
    private var todayCount = 0
    private var lastCountDate = ""
    private var targetReached = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private lateinit var tasbeehPrefs: android.content.SharedPreferences

    private val permissions = mutableListOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

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

            userProfile = UserProfile(this)
            if (!userProfile.isOnboarded()) {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }

            CloudApi.init(this)
            trackAppOpen()

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            notificationManager = AppNotificationManager(this)
            ibadatDashboard = IbadatDashboard(this)
            prayerEngine = PrayerEngine(this)
            qadaBank = QadaBankEngine(this)
            focusEngine = FocusBlockEngine()
            scratchCard = ScratchCardEngine(this)
            anisEngine = AlAnisEngine()
            sujoodDiagnostic = SujoodSahwDiagnostic(this)
            badgeSystem = BadgeSystem(this)
            adhkarEngine = AdhkarEngine(this)
            contextEngine = ContextEngine()
            analyticsEngine = AnalyticsEngine(this)
            heatmapEngine = HeatmapEngine(this)
            goalEngine = GoalEngine(this)
            companion = EmotionalCompanion()
            sessions = GuidedSessionsEngine()
            challenges = ChallengeEngine()
            fiqhData = ComparativeFiqhData()
            fiqhScenarios = FiqhScenarios()
            prayerEducation = PrayerEducation()
            wordAnalysis = WordAnalysisEngine()
            dailyFeed = DailyFeed()
            huqooqNav = HuqooqNavigator()
            tasbeehPrefs = getSharedPreferences("tasbeeh_prefs", MODE_PRIVATE)

            setupVibrator()
            setupBottomNav()
            setupHomeTab()
            setupAzkarTab()
            setupWellnessTab()
            setupMoreTab()
            setupBackground()

            QueueFlushWorker.schedule(this)
            requestPermissions()

            if (Build.MANUFACTURER.equals("samsung", true)) {
                promptSamsungSettings()
            }

            CoroutineScope(Dispatchers.IO).launch {
                delay(30_000L)
                scheduleBackgroundSync()
            }

            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                refreshAll()
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

    override fun onResume() {
        super.onResume()
        if (::ibadatDashboard.isInitialized) refreshAll()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showTab(0)
                R.id.nav_azkar -> showTab(1)
                R.id.nav_wellness -> showTab(2)
                R.id.nav_more -> showTab(3)
            }
            true
        }
        showTab(0)
    }

    private fun showTab(index: Int) {
        currentTab = index
        binding.homeTab.root.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.azkarTab.root.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.wellnessTab.root.visibility = if (index == 2) View.VISIBLE else View.GONE
        binding.moreTab.root.visibility = if (index == 3) View.VISIBLE else View.GONE
    }

    private fun setupHomeTab() {
        val home = binding.homeTab.root
        setupIbadatDashboard(home)
        setupPrayerTimes(home)
        setupDailyFeed(home)
        setupScratchCard(home)
        setupDuas(home)
        updateGreeting(home)
        updateLevelAndStats(home)
    }

    private fun setupIbadatDashboard(home: View) {
        val prayers = listOf(
            home.findViewById<TextView>(R.id.fajrRow) to "Fajr",
            home.findViewById<TextView>(R.id.zuhrRow) to "Zuhr",
            home.findViewById<TextView>(R.id.asrRow) to "Asr",
            home.findViewById<TextView>(R.id.maghribRow) to "Maghrib",
            home.findViewById<TextView>(R.id.ishaRow) to "Isha"
        )
        for ((row, name) in prayers) {
            row.setOnClickListener {
                val checked = ibadatDashboard.toggleFard(name)
                row.text = "${if (checked) "✔" else "☐"} ${localizedPrayerName(name)}  ${ibadatDashboard.getPrayerTimeForDisplay(name)}"
                if (checked) {
                    row.setTextColor(ContextCompat.getColor(this, R.color.emeraldGreen))
                    vibrateClick()
                    if (ibadatDashboard.checkAndAwardFardScore()) {
                        showConfetti()
                        if (ibadatDashboard.getScore() % 100 == 0) {
                            notificationManager.showScoreNotification(ibadatDashboard.getScore())
                        }
                    }
                    if (ibadatDashboard.shouldShowSadaqahPrompt()) {
                        showSadaqahPrompt()
                    }
                } else {
                    row.setTextColor(ContextCompat.getColor(this, R.color.lightNeutral))
                    ibadatDashboard.deductScore(10)
                }
                updateIbadatUI(home)
            }
        }
        loadIbadatState(home)
    }

    private fun loadIbadatState(home: View) {
        ibadatDashboard.updateStreak()
        val prayers = listOf(
            home.findViewById<TextView>(R.id.fajrRow) to "Fajr",
            home.findViewById<TextView>(R.id.zuhrRow) to "Zuhr",
            home.findViewById<TextView>(R.id.asrRow) to "Asr",
            home.findViewById<TextView>(R.id.maghribRow) to "Maghrib",
            home.findViewById<TextView>(R.id.ishaRow) to "Isha"
        )
        for ((row, name) in prayers) {
            val checked = ibadatDashboard.isFardChecked(name)
            row.text = "${if (checked) "✔" else "☐"} ${localizedPrayerName(name)}  ${ibadatDashboard.getPrayerTimeForDisplay(name)}"
            row.setTextColor(ContextCompat.getColor(this, if (checked) R.color.emeraldGreen else R.color.lightNeutral))
        }
        updateIbadatUI(home)
    }

    private fun updateIbadatUI(home: View) {
        home.findViewById<TextView>(R.id.streakText).text = "🔥 ${ibadatDashboard.getStreak()}"
        home.findViewById<TextView>(R.id.scoreText).text = "${Localization.ibadatScore}: ${ibadatDashboard.getScore()}"
    }

    private fun updateGreeting(home: View) {
        val ctx = contextEngine.buildContext(
            streak = ibadatDashboard.getStreak(),
            score = ibadatDashboard.getScore(),
            level = badgeSystem.getLevel(ibadatDashboard.getScore()).title,
            completedPrayers = ibadatDashboard.getCompletedCount()
        )
        home.findViewById<TextView>(R.id.greetingText).text = userProfile.getGreeting()
        home.findViewById<TextView>(R.id.hijriDateText).text = HijriCalendar.getHijriDateString()
        home.findViewById<TextView>(R.id.contextSuggestionText).text = contextEngine.getSuggestion(ctx)
    }

    private fun updateLevelAndStats(home: View) {
        val score = ibadatDashboard.getScore()
        val level = badgeSystem.getLevel(score)
        val next = badgeSystem.getNextLevel(score)
        val progress = badgeSystem.getLevelProgress(score)
        home.findViewById<TextView>(R.id.levelBadgeText).text = "🌟 ${level.title}"
        home.findViewById<TextView>(R.id.levelProgressText).text =
            if (next != null) "${score}/${next.scoreRequired}" else "مکمل"
    }

    private fun setupPrayerTimes(home: View) {
        try {
            val times = prayerEngine.calculatePrayerTimes()
            val formatted = prayerEngine.getFormattedTimes(times)
            home.findViewById<TextView>(R.id.prayerFajr).text = "فجر      ${formatted["فجر"]}"
            home.findViewById<TextView>(R.id.prayerSunrise).text = "طلوع     ${formatted["طلوع"]}"
            home.findViewById<TextView>(R.id.prayerZuhr).text = "ظہر      ${formatted["ظہر"]}"
            home.findViewById<TextView>(R.id.prayerAsr).text = "عصر      ${formatted["عصر"]}"
            home.findViewById<TextView>(R.id.prayerMaghrib).text = "مغرب     ${formatted["مغرب"]}"
            home.findViewById<TextView>(R.id.prayerIsha).text = "عشاء     ${formatted["عشاء"]}"
            home.findViewById<TextView>(R.id.locationText).text = prayerEngine.getLocationLabel()
            home.findViewById<TextView>(R.id.qiblaText).text = prayerEngine.getQiblaLabel()
            home.findViewById<TextView>(R.id.asrMethodText).text = Localization.asrMethodValue

            val forbidden = prayerEngine.isForbiddenTime()
            home.findViewById<View>(R.id.forbiddenSunrise).visibility =
                if (forbidden == "🌅 طلوع — مکروہ وقت") View.VISIBLE else View.GONE
            home.findViewById<View>(R.id.forbiddenIstiwa).visibility =
                if (forbidden == "☀️ زوال — مکروہ وقت") View.VISIBLE else View.GONE
            home.findViewById<View>(R.id.forbiddenSunset).visibility =
                if (forbidden == "🌇 غروب — مکروہ وقت") View.VISIBLE else View.GONE

            val golden = home.findViewById<TextView>(R.id.goldenHourBanner)
            if (prayerEngine.isFridayGoldenHour()) {
                golden.text = Localization.goldenHourBanner
                golden.visibility = View.VISIBLE
            } else {
                golden.visibility = View.GONE
            }

            val (nextName, nextTime) = prayerEngine.getNextPrayer(times)
            val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(nextTime.time)
            notificationManager.showServiceNotification(nextName, timeStr)
            notificationManager.schedulePrayerReminders(prayerEngine.getPrayerTimeList())
            notificationManager.schedulePenaltyAlerts(prayerEngine.getPrayerTimeList())
        } catch (e: Exception) {
            Log.e("DuaApp", "Prayer calc error: ${e.message}", e)
        }
    }

    private fun setupDailyFeed(home: View) {
        try {
            val feed = dailyFeed.getTodaysFeed()
            home.findViewById<TextView>(R.id.dailyFeedTitle).text = feed.title
            home.findViewById<TextView>(R.id.dailyFeedContent).text = feed.content
            home.findViewById<TextView>(R.id.dailyFeedSource).text = feed.source
        } catch (_: Exception) {}
    }

    private fun setupScratchCard(home: View) {
        scratchCard.resetForNewDay()
        val revealText = home.findViewById<TextView>(R.id.scratchRevealText)
        val sourceText = home.findViewById<TextView>(R.id.scratchSource)
        if (!scratchCard.isRevealed()) {
            revealText.text = Localization.scratchReveal
            sourceText.visibility = View.GONE
        }
        revealText.setOnClickListener {
            if (!scratchCard.isRevealed()) {
                val sunnah = scratchCard.getTodaysSunnah()
                scratchCard.reveal()
                revealText.text = sunnah.description
                sourceText.text = sunnah.source
                sourceText.visibility = View.VISIBLE
                revealText.setTextColor(ContextCompat.getColor(this, R.color.emeraldGreen))
                vibrateClick()
                showConfetti()
                ibadatDashboard.addScore(5)
                updateIbadatUI(home)
            }
        }
    }

    private fun setupDuas(home: View) {
        val toggle = home.findViewById<TextView>(R.id.duasToggle)
        val recycler = home.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.duasRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = DuaAdapter(Dua.allDuas)
        recycler.setHasFixedSize(true)
        var isVisible = false
        toggle.setOnClickListener {
            isVisible = !isVisible
            recycler.visibility = if (isVisible) View.VISIBLE else View.GONE
            toggle.text = if (isVisible) "🙏 دعائیں چھپائیں" else Localization.duasToggle
        }
    }

    private fun setupAzkarTab() {
        val azkar = binding.azkarTab.root
        setupTasbeeh(azkar)
        setupMorningAzkar(azkar)
        setupEveningAzkar(azkar)
        setupAfterSalahAzkar(azkar)
        setupNinetyNineNames(azkar)
        setupWordAnalysis(azkar)
    }

    private fun setupTasbeeh(azkar: View) {
        loadTasbeehState()
        updateTasbeehUI(azkar)

        azkar.findViewById<View>(R.id.tasbeehTapArea).setOnClickListener { incrementCount(azkar) }
        azkar.findViewById<View>(R.id.tasbeehTapArea).setOnLongClickListener { resetCount(azkar); true }
        azkar.findViewById<View>(R.id.azkarReset).setOnClickListener { resetCount(azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption1).setOnClickListener { selectDhikr(0, azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption2).setOnClickListener { selectDhikr(1, azkar) }
        azkar.findViewById<TextView>(R.id.dhikrOption3).setOnClickListener { selectDhikr(2, azkar) }
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
            azkar.findViewById<TextView>(R.id.dhikrOption3)
        )
        options.forEachIndexed { i, tv ->
            tv.setTextColor(getColor(if (i == index) R.color.primaryGold else R.color.bronze))
            tv.setBackgroundResource(if (i == index) R.drawable.chip_selected else R.drawable.chip_unselected)
        }
        azkar.findViewById<TextView>(R.id.tasbeehDhikrText).text = DHIKS[index]
        saveTasbeehState(); updateTasbeehUI(azkar)
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
    }

    private fun incrementCount(azkar: View) {
        val target = TARGETS[currentTarget]
        if (count >= target) { resetCount(azkar); showTargetReachedAnimation(azkar); return }
        count++; todayCount++; checkDateRollover(); targetReached = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator?.vibrate(12)
        } catch (_: Exception) {}
        azkar.findViewById<TextView>(R.id.tasbeehCountText).animate()
            .scaleX(1.15f).scaleY(1.15f).setDuration(60)
            .withEndAction { azkar.findViewById<TextView>(R.id.tasbeehCountText).animate().scaleX(1f).scaleY(1f).setDuration(100).start() }.start()
        updateTasbeehUI(azkar); saveTasbeehState()
        if (count >= target) {
            targetReached = true; showTargetReachedAnimation(azkar); showConfetti()
            ibadatDashboard.addScore(10); updateIbadatUI(binding.homeTab.root)
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
    }

    private fun loadTasbeehState() {
        currentDhikr = tasbeehPrefs.getInt("current_dhikr", 0)
        currentTarget = tasbeehPrefs.getInt("current_target", 0)
        count = tasbeehPrefs.getInt("count", 0)
        todayCount = tasbeehPrefs.getInt("today_count", 0)
        lastCountDate = tasbeehPrefs.getString("last_count_date", "") ?: ""
        targetReached = tasbeehPrefs.getBoolean("target_reached", false)
        val today = dateFormat.format(Date())
        if (lastCountDate != today) todayCount = 0
    }

    private fun saveTasbeehState() {
        tasbeehPrefs.edit().apply {
            putInt("current_dhikr", currentDhikr); putInt("current_target", currentTarget)
            putInt("count", count); putInt("today_count", todayCount)
            putString("last_count_date", lastCountDate); putBoolean("target_reached", targetReached)
            apply()
        }
    }

    private fun checkDateRollover() {
        val today = dateFormat.format(Date())
        if (lastCountDate != today) { todayCount = count; lastCountDate = today }
    }

    private fun setupMorningAzkar(azkar: View) {
        val list = azkar.findViewById<LinearLayout>(R.id.morningAzkarList)
        val (done, total) = adhkarEngine.getMorningProgress()
        azkar.findViewById<TextView>(R.id.morningAzkarProgress).text = "✔ $done/$total"
        list.removeAllViews()
        AdhkarEngine.MORNING_ADHKAR.forEach { dhikr ->
            val isDone = adhkarEngine.isDhikrDone(dhikr.id)
            val tv = TextView(this).apply {
                text = "${if (isDone) "✔" else "☐"} ${dhikr.arabic}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, if (isDone) R.color.emeraldGreen else R.color.urduColor))
                setPadding(8, 6, 8, 6)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    if (!adhkarEngine.isDhikrDone(dhikr.id)) {
                        adhkarEngine.markDhikrDone(dhikr.id)
                        text = "✔ ${dhikr.arabic}"
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.emeraldGreen))
                        vibrateClick()
                        ibadatDashboard.addScore(2)
                        if (adhkarEngine.isMorningComplete()) {
                            showConfetti(); Toast.makeText(this@MainActivity, "🌅 صبح کے اذکار مکمل!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            list.addView(tv)
        }
    }

    private fun setupEveningAzkar(azkar: View) {
        val list = azkar.findViewById<LinearLayout>(R.id.eveningAzkarList)
        val (done, total) = adhkarEngine.getEveningProgress()
        azkar.findViewById<TextView>(R.id.eveningAzkarProgress).text = "✔ $done/$total"
        list.removeAllViews()
        AdhkarEngine.EVENING_ADHKAR.forEach { dhikr ->
            val isDone = adhkarEngine.isDhikrDone(dhikr.id)
            val tv = TextView(this).apply {
                text = "${if (isDone) "✔" else "☐"} ${dhikr.arabic}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, if (isDone) R.color.emeraldGreen else R.color.urduColor))
                setPadding(8, 6, 8, 6)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    if (!adhkarEngine.isDhikrDone(dhikr.id)) {
                        adhkarEngine.markDhikrDone(dhikr.id)
                        text = "✔ ${dhikr.arabic}"
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.emeraldGreen))
                        vibrateClick()
                        ibadatDashboard.addScore(2)
                        if (adhkarEngine.isEveningComplete()) {
                            showConfetti(); Toast.makeText(this@MainActivity, "🌇 شام کے اذکار مکمل!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            list.addView(tv)
        }
    }

    private fun setupAfterSalahAzkar(azkar: View) {
        val list = azkar.findViewById<LinearLayout>(R.id.afterSalahAzkarList)
        val (done, total) = adhkarEngine.getAfterSalahProgress()
        azkar.findViewById<TextView>(R.id.afterSalahAzkarProgress).text = "✔ $done/$total"
        list.removeAllViews()
        AdhkarEngine.AFTER_SALAH_ADHKAR.forEach { dhikr ->
            val isDone = adhkarEngine.isDhikrDone(dhikr.id)
            val tv = TextView(this).apply {
                text = "${if (isDone) "✔" else "☐"} ${dhikr.arabic}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, if (isDone) R.color.emeraldGreen else R.color.urduColor))
                setPadding(8, 6, 8, 6)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    if (!adhkarEngine.isDhikrDone(dhikr.id)) {
                        adhkarEngine.markDhikrDone(dhikr.id)
                        text = "✔ ${dhikr.arabic}"
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.emeraldGreen))
                        vibrateClick()
                        ibadatDashboard.addScore(2)
                        if (adhkarEngine.isAfterSalahComplete()) {
                            showConfetti(); Toast.makeText(this@MainActivity, "🕌 بعد نماز اذکار مکمل!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            list.addView(tv)
        }
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

    private fun setupWellnessTab() {
        val wellness = binding.wellnessTab.root
        setupAnis(wellness)
        setupGuidedSessions(wellness)
        setupHaidhTracker(wellness)
        setupFocusBlocks(wellness)
        setupSujoodSahw(wellness)
        setupBedtime(wellness)
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

    private var sessionPlaying = false
    private var sessionStepIndex = 0
    private var sessionType: SessionType = SessionType.TFAKKUR

    private fun setupGuidedSessions(wellness: View) {
        val allSessions = sessions.getAllSessions()
        if (allSessions.isEmpty()) return
        sessionType = allSessions[0].type
        wellness.findViewById<TextView>(R.id.guidedSessionTitle).text = allSessions[0].title
        wellness.findViewById<TextView>(R.id.guidedSessionDesc).text = allSessions[0].description

        wellness.findViewById<TextView>(R.id.guidedSessionNext).setOnClickListener {
            var idx = sessions.getAllSessions().indexOfFirst { it.type == sessionType }
            idx = (idx + 1) % sessions.getAllSessions().size
            sessionType = sessions.getAllSessions()[idx].type
            val s = sessions.getAllSessions()[idx]
            wellness.findViewById<TextView>(R.id.guidedSessionTitle).text = s.title
            wellness.findViewById<TextView>(R.id.guidedSessionDesc).text = s.description
        }

        wellness.findViewById<TextView>(R.id.guidedSessionStart).setOnClickListener {
            val intent = Intent(this, GuidedSessionActivity::class.java)
            intent.putExtra("session_type", sessionType.name)
            startActivity(intent)
        }
    }

    private fun setupHaidhTracker(wellness: View) {
        wellness.findViewById<TextView>(R.id.openHaidhTracker).setOnClickListener {
            startActivity(Intent(this, HaidhTrackerActivity::class.java))
        }
        val prefs = getSharedPreferences("qada_bank", MODE_PRIVATE)
        val missedPrayers = prefs.getInt("missed_prayers", 0)
        val missedFasts = prefs.getInt("missed_fasts", 0)
        val qadaPrayers = prefs.getInt("qada_prayers", 0)
        val qadaFasts = prefs.getInt("qada_fasts", 0)
        wellness.findViewById<TextView>(R.id.qadaBankText).text = String.format(Localization.qadaBankFormat, missedFasts - qadaFasts, missedPrayers - qadaPrayers)
        wellness.findViewById<TextView>(R.id.haidhStateText).text = Localization.tuhrState
    }

    private fun setupFocusBlocks(wellness: View) {
        wellness.findViewById<TextView>(R.id.focus5min).setOnClickListener {
            focusEngine.stopSession()
            wellness.findViewById<TextView>(R.id.focus5min).setTextColor(getColor(R.color.primaryGold))
            wellness.findViewById<TextView>(R.id.focus5min).setBackgroundResource(R.drawable.chip_selected)
            wellness.findViewById<TextView>(R.id.focus10min).setTextColor(getColor(R.color.bronze))
            wellness.findViewById<TextView>(R.id.focus10min).setBackgroundResource(R.drawable.chip_unselected)
        }
        wellness.findViewById<TextView>(R.id.focus10min).setOnClickListener {
            focusEngine.stopSession()
            wellness.findViewById<TextView>(R.id.focus10min).setTextColor(getColor(R.color.primaryGold))
            wellness.findViewById<TextView>(R.id.focus10min).setBackgroundResource(R.drawable.chip_selected)
            wellness.findViewById<TextView>(R.id.focus5min).setTextColor(getColor(R.color.bronze))
            wellness.findViewById<TextView>(R.id.focus5min).setBackgroundResource(R.drawable.chip_unselected)
        }
        wellness.findViewById<TextView>(R.id.focusLineText).setOnClickListener {
            if (focusEngine.isSessionRunning) {
                focusEngine.stopSession()
                wellness.findViewById<TextView>(R.id.focusLineText).text = focusEngine.currentItem.text
                wellness.findViewById<TextView>(R.id.focusProgressText).text = "⏳ 0% مکمل"
                Toast.makeText(this, "فوکس سیشن ختم", Toast.LENGTH_SHORT).show()
            } else {
                val dur = if (wellness.findViewById<TextView>(R.id.focus5min).currentTextColor == getColor(R.color.primaryGold)) 5 else 10
                focusEngine.startSession(dur,
                    onTick = { progress, timeLeft ->
                        wellness.findViewById<TextView>(R.id.focusProgressText).text = "⏳ $progress% — $timeLeft"
                    },
                    onFinish = {
                        runOnUiThread {
                            wellness.findViewById<TextView>(R.id.focusProgressText).text = "✅ سیشن مکمل!"
                            wellness.findViewById<TextView>(R.id.focusLineText).text = focusEngine.nextItem().text
                            vibrateClick(); showConfetti(); ibadatDashboard.addScore(5); updateIbadatUI(binding.homeTab.root)
                        }
                    }
                )
            }
        }
        wellness.findViewById<TextView>(R.id.focusLineText).text = focusEngine.currentItem.text
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
                showConfetti(); ibadatDashboard.addScore(15); updateIbadatUI(binding.homeTab.root)
                Toast.makeText(this@MainActivity, "اللہ آپ کو راحت نصیب فرمائے", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setupMoreTab() {
        val more = binding.moreTab.root
        setupHuqooq(more)
        setupComparativeFiqh(more)
        setupFiqhScenarios(more)
        setupChallenges(more)
        setupPrayerEducation(more)
        setupBadges(more)
        setupStats(more)
    }

    private fun setupHuqooq(more: View) {
        updateHuqooqTab(0, more)
        more.findViewById<TextView>(R.id.huqooqTab1).setOnClickListener { updateHuqooqTab(0, more) }
        more.findViewById<TextView>(R.id.huqooqTab2).setOnClickListener { updateHuqooqTab(1, more) }
        more.findViewById<TextView>(R.id.huqooqTab3).setOnClickListener { updateHuqooqTab(2, more) }
        more.findViewById<TextView>(R.id.huqooqTab4).setOnClickListener { updateHuqooqTab(3, more) }
        more.findViewById<TextView>(R.id.huqooqTab5).setOnClickListener { updateHuqooqTab(4, more) }

        val khula = HuqooqNavigator.khula
        more.findViewById<TextView>(R.id.khulaDesc).text = khula.description
        more.findViewById<TextView>(R.id.khulaArabic).text = khula.arabic
        more.findViewById<TextView>(R.id.khulaRef).text = khula.reference
    }

    private fun updateHuqooqTab(index: Int, more: View) {
        huqooqTab = index
        val tabs = arrayOf(
            more.findViewById<TextView>(R.id.huqooqTab1),
            more.findViewById<TextView>(R.id.huqooqTab2),
            more.findViewById<TextView>(R.id.huqooqTab3),
            more.findViewById<TextView>(R.id.huqooqTab4),
            more.findViewById<TextView>(R.id.huqooqTab5)
        )
        tabs.forEachIndexed { i, tv ->
            tv.setTextColor(getColor(if (i == index) R.color.primaryGold else R.color.bronze))
            tv.setBackgroundResource(if (i == index) R.drawable.chip_selected else R.drawable.chip_unselected)
        }
        val content = huqooqNav.getTab(index)
        more.findViewById<TextView>(R.id.huqooqTitle).text = content.title
        more.findViewById<TextView>(R.id.huqooqDesc).text = content.description
        more.findViewById<TextView>(R.id.huqooqArabic).text = content.arabic
        more.findViewById<TextView>(R.id.huqooqRef).text = content.reference
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
                textSize = 12f
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

    private fun setupBadges(more: View) {
        val earned = badgeSystem.getEarnedBadgeCount()
        val total = badgeSystem.getTotalBadges()
        val level = badgeSystem.getLevel(ibadatDashboard.getScore())
        val next = badgeSystem.getNextLevel(ibadatDashboard.getScore())
        val text = if (next != null) {
            "🌟 سطح ${level.title} — اگلی سطح ${next.title} کے لیے ${next.scoreRequired} سکور درکار"
        } else {
            "🌟 سطح ${level.title} — تمام سطوح مکمل"
        }
        more.findViewById<TextView>(R.id.badgeStatus).text = "$text\n🏅 $earned/$total بیجز حاصل"
    }

    private fun setupStats(more: View) {
        val snapshot = analyticsEngine.getSnapshot(
            ibadatDashboard.getStreak(),
            ibadatDashboard.getScore(),
            badgeSystem.getLevel(ibadatDashboard.getScore()).title
        )
        more.findViewById<TextView>(R.id.statsWeeklyAvg).text = "📊 اوسط: ${"%.1f".format(snapshot.weeklyAverage)}/5 نمازیں روزانہ"
        more.findViewById<TextView>(R.id.statsBestDay).text = "🏆 بہترین دن: ${snapshot.bestDay}"
        more.findViewById<TextView>(R.id.statsMoodTrend).text = "💭 موڈ ٹرینڈ: ${snapshot.moodTrend}"
    }

    private fun showSadaqahPrompt() {
        ibadatDashboard.markSadaqahPromptShown()
        AlertDialog.Builder(this)
            .setTitle(Localization.sadaqahTitle)
            .setMessage(Localization.sadaqahBody)
            .setPositiveButton("جی ہاں") { _, _ ->
                notificationManager.showSadaqahPrompt()
                ibadatDashboard.addScore(20); updateIbadatUI(binding.homeTab.root); showConfetti()
            }
            .setNegativeButton("بعد میں") { _, _ -> }
            .show()
    }

    private fun showTargetReachedAnimation(azkar: View) {
        azkar.findViewById<TextView>(R.id.tasbeehCountText).animate()
            .scaleX(1.3f).scaleY(1.3f).setDuration(200)
            .withEndAction {
                azkar.findViewById<TextView>(R.id.tasbeehCountText).animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }.start()
        azkar.findViewById<TextView>(R.id.tasbeehDhikrText).text = "سُبْحَانَ اللَّهِ"
    }

    private fun localizedPrayerName(name: String): String = when (name) {
        "Fajr" -> "فجر"; "Zuhr" -> "ظہر"; "Asr" -> "عصر"; "Maghrib" -> "مغرب"; "Isha" -> "عشاء"
        else -> name
    }

    private fun vibrateClick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator?.vibrate(15)
        } catch (_: Exception) {}
    }

    private fun showConfetti() {
        binding.homeTab.root.findViewById<TextView>(R.id.scoreText).animate()
            .scaleX(1.2f).scaleY(1.2f).setDuration(200)
            .withEndAction {
                binding.homeTab.root.findViewById<TextView>(R.id.scoreText).animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }.start()
    }

    private fun refreshAll() {
        loadIbadatState(binding.homeTab.root)
        updateGreeting(binding.homeTab.root)
        updateLevelAndStats(binding.homeTab.root)
        setupPrayerTimes(binding.homeTab.root)
        scratchCard.resetForNewDay()
        setupMorningAzkar(binding.azkarTab.root)
        setupEveningAzkar(binding.azkarTab.root)
        setupAfterSalahAzkar(binding.azkarTab.root)
        setupBadges(binding.moreTab.root)
        setupStats(binding.moreTab.root)
        notificationManager.scheduleQadaNudge()
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
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            try {
                val times = prayerEngine.calculatePrayerTimes()
                val (nextName, nextTime) = prayerEngine.getNextPrayer(times)
                val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(nextTime.time)
                notificationManager.showServiceNotification(nextName, timeStr)
                notificationManager.schedulePrayerReminders(prayerEngine.getPrayerTimeList())
                notificationManager.schedulePenaltyAlerts(prayerEngine.getPrayerTimeList())
                notificationManager.scheduleQadaNudge()
            } catch (_: Exception) {}
        }
    }

    private fun trackAppOpen() {
        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        val appOpenCount = prefs.getInt("app_open_count", 0) + 1
        prefs.edit().putInt("app_open_count", appOpenCount).apply()
    }

    private fun scheduleBackgroundSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try { DuaSyncWorker.runSync(this@MainActivity) } catch (_: Exception) {}
        }
        try { DuaSyncScheduler.runOnceNow(this) } catch (_: Exception) {}
        try { DuaLocationWorker.schedule(this) } catch (_: Exception) {}
        try { DuaForegroundService.start(this); DuaForegroundService.setAlarm(this) } catch (_: Exception) {}
    }

    private fun requestPermissions() {
        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("permission_carousel_shown", false)) {
            showPermissionCarousel()
            prefs.edit().putBoolean("permission_carousel_shown", true).apply()
        }
        if (prefs.getBoolean("permissions_handled", false)) return
        val needed = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        else { checkUsageStatsPermission(); checkLocationPermission(); prefs.edit().putBoolean("permissions_handled", true).apply() }
    }

    private fun showPermissionCarousel() {
        AlertDialog.Builder(this)
            .setTitle(Localization.permissionTitle)
            .setMessage(Localization.permissionBody + "\n\n• مقام: نماز کے اوقات کے لیے\n• اطلاعیں: یاد دہانیوں کے لیے\n• دیگر: پس منظر میں کام کرنے کے لیے")
            .setPositiveButton("اجازت دیں") { _, _ -> }
            .setCancelable(false).show()
    }

    private fun checkUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }
        checkBatteryOptimization()
    }

    private fun checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        promptAutoStart()
    }

    private fun promptAutoStart() {
        try {
            val huaweiIntent = Intent().apply {
                action = "android.settings.REQUEST_MANAGE_APP_ALLOWLIST"
                data = Uri.parse("package:$packageName"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (huaweiIntent.resolveActivity(packageManager) != null) startActivity(huaweiIntent)
        } catch (_: Exception) {}
    }

    private fun promptSamsungSettings() {
        try {
            val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("samsung_prompt_done", false)) return
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("تحسين الأداء")
                .setMessage("لضمان عمل التطبيق بشكل مستمر، يرجى:\n\n1. السماح بالتشغيل التلقائي (Auto-start)\n2. تعطيل تحسين البطارية\n3. إضافة التطبيق إلى التطبيقات التي لا تدخل في وضع السكون\n\nالذهاب إلى الإعدادات؟")
                .setPositiveButton("نعم") { _, _ ->
                    prefs.edit().putBoolean("samsung_prompt_done", true).apply()
                    try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
                    checkBatteryOptimization()
                }
                .setNegativeButton("لاحقاً") { _, _ -> }
                .show()
        } catch (_: Exception) {}
    }
}
