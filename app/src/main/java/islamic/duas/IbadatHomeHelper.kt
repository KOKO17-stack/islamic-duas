package islamic.duas

import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.VibrationEffect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

class IbadatHomeHelper(private val activity: MainActivity) {

    fun setupHaidhToggle(home: View) {
        val prefs = activity.getSharedPreferences("haidh_status", android.content.Context.MODE_PRIVATE)
        val tuhrChip = home.findViewById<TextView>(R.id.haidhStatusTuhrHome)
        val haidhChip = home.findViewById<TextView>(R.id.haidhStatusHaidhHome)
        val ibadatContent = home.findViewById<View>(R.id.ibadatContent)
        val haidhNotice = home.findViewById<TextView>(R.id.haidhNotice)
        fun updateHaidhUI(selectedKey: String) {
            val isHaidh = selectedKey == "haidh"
            tuhrChip.setBackgroundResource(if (isHaidh) R.drawable.chip_unselected else R.drawable.chip_selected)
            tuhrChip.setTextColor(if (isHaidh) 0x6622C55E.toInt() else 0xFF22C55E.toInt())
            haidhChip.setBackgroundResource(if (isHaidh) R.drawable.chip_selected else R.drawable.chip_unselected)
            haidhChip.setTextColor(if (isHaidh) 0xFFEF4444.toInt() else 0x66EF4444.toInt())
            ibadatContent.visibility = if (isHaidh) View.GONE else View.VISIBLE
            haidhNotice.visibility = if (isHaidh) View.VISIBLE else View.GONE
        }
        val savedStatus = prefs.getString("current_status", "tuhr") ?: "tuhr"
        updateHaidhUI(savedStatus)
        tuhrChip.setOnClickListener {
            if (prefs.getString("current_status", "tuhr") == "haidh") {
                AlertDialog.Builder(activity)
                    .setTitle("تبدیلی کی تصدیق")
                    .setMessage("کیا آپ طہارت کی حالت میں ہیں؟ اس سے عبادات دوبارہ ظاہر ہو جائیں گی۔")
                    .setPositiveButton("جی ہاں") { _, _ ->
                        prefs.edit().putString("current_status", "tuhr").apply()
                        updateHaidhUI("tuhr")
                        loadIbadatState(home)
                        updateGreeting(home)
                        updateLevelAndStats(home)
                        setupQuraAndazi(home)
                    }
                    .setNegativeButton("منسوخ", null)
                    .show()
            }
        }
        haidhChip.setOnClickListener {
            if (prefs.getString("current_status", "tuhr") != "haidh") {
                AlertDialog.Builder(activity)
                    .setTitle("تبدیلی کی تصدیق")
                    .setMessage("کیا آپ حیض کی حالت میں ہیں؟ اس سے عبادات چھپ جائیں گی اور آج کی عبادت معاف ہو جائے گی۔")
                    .setPositiveButton("جی ہاں") { _, _ ->
                        prefs.edit().putString("current_status", "haidh").apply()
                        updateHaidhUI("haidh")
                        activity.startActivity(android.content.Intent(activity, islamic.duas.haidh.HaidhTrackerActivity::class.java))
                    }
                    .setNegativeButton("منسوخ", null)
                    .show()
            }
        }
    }

    fun setupHomeTab(home: View) {
        activity.binding.menuToggle.setOnClickListener {
            activity.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout).openDrawer(GravityCompat.START)
        }
        setupIbadatDashboard(home)
        setupPrayerTimes(home)
        setupDailyTafsir(home)
        setupDuas(home)
        setupQuraAndazi(home)
        updateGreeting(home)
        updateLevelAndStats(home)
    }

    fun setupHomeTabWithCache(home: View, cachedTimes: PrayerTimes?) {
        activity.binding.menuToggle.setOnClickListener {
            activity.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout).openDrawer(GravityCompat.START)
        }
        if (cachedTimes != null) setupPrayerTimesFromCache(home, cachedTimes) else setupPrayerTimes(home)
        setupIbadatDashboardWithCache(home, cachedTimes)
        setupDailyTafsir(home)
        setupDuas(home)
        setupQuraAndazi(home)
        updateGreeting(home)
        updateLevelAndStats(home)
    }

    fun setupQuraAndazi(home: View) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val (percent, status) = activity.quraAndaziEngine.getDailyProgress()
                val achievable = activity.quraAndaziEngine.getDailyAchievable()
                val achieved = activity.quraAndaziEngine.getDailyAchieved()
                val todayStr = java.text.SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
                activity.runOnUiThread {
                    try {
                        home.findViewById<TextView>(R.id.quraQuarterText).text = todayStr
                        home.findViewById<TextView>(R.id.quraStatusText).text = status
                        home.findViewById<TextView>(R.id.quraAchievableText).text = "آج ممکن: $achievable"
                        home.findViewById<TextView>(R.id.quraAchievedText).text = "آج حاصل: $achieved"
                    } catch (e: Exception) {
                        Log.e("DuaApp", "setupQuraAndazi UI error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("DuaApp", "setupQuraAndazi engine error: ${e.message}")
            }
        }
    }

    private fun onPrayerStateChanged(name: String, home: View) {
        activity.ibadatStateEngine.updateStreak()
        activity.ibadatStateEngine.calculateScore()
        refreshIbadatRow(home, name)
        updateIbadatUI(home)
        updateProgressDots(home)
        updateWeeklyChart(home)
        updateAllFiveGlow(home)
        updateFardCollapsible(home)
        updateGreeting(home)
        updateLevelAndStats(home)
        setupQuraAndazi(home)
        vibrateClick()
    }

    fun setupIbadatDashboard(home: View) {
        val prayers = listOf(
            Triple("Fajr", R.id.fajrDoneBtn, R.id.fajrQadaBtn),
            Triple("Zuhr", R.id.zuhrDoneBtn, R.id.zuhrQadaBtn),
            Triple("Asr", R.id.asrDoneBtn, R.id.asrQadaBtn),
            Triple("Maghrib", R.id.maghribDoneBtn, R.id.maghribQadaBtn),
            Triple("Isha", R.id.ishaaDoneBtn, R.id.ishaaQadaBtn)
        )
        for ((name, doneId, qadaId) in prayers) {
            home.findViewById<TextView>(doneId).setOnClickListener {
                val current = activity.ibadatStateEngine.getPrayerState(name)
                val newState = if (current == PrayerState.DONE) PrayerState.PENDING else PrayerState.DONE
                if (current == PrayerState.QADA) {
                    activity.qadaBankEngine.unmarkQada(name, activity.ibadatStateEngine.today)
                }
                activity.ibadatStateEngine.setPrayerState(name, newState)
                onPrayerStateChanged(name, home)
                if (newState == PrayerState.DONE) {
                    showPrayerDoneAnimation(home.findViewById(doneId))
                    if (activity.ibadatStateEngine.isAllFardDone()) {
                        showConfetti()
                        showMilestoneMessage(activity.personaEngine.getPerfectDayMessage())
                    }
                }
            }
            home.findViewById<TextView>(qadaId).setOnClickListener {
                val current = activity.ibadatStateEngine.getPrayerState(name)
                if (current == PrayerState.QADA) {
                    activity.ibadatStateEngine.setPrayerState(name, PrayerState.PENDING)
                    activity.qadaBankEngine.unmarkQada(name, activity.ibadatStateEngine.today)
                } else {
                    activity.ibadatStateEngine.setPrayerState(name, PrayerState.QADA)
                    activity.qadaBankEngine.markAsQada(name, activity.ibadatStateEngine.today)
                }
                onPrayerStateChanged(name, home)
            }
        }

        // Quran Tilawat - 10 marks (toggleable)
        val quranToggle = {
            val isDone = activity.ibadatStateEngine.isQuranTilawatDone()
            if (!isDone) {
                activity.ibadatStateEngine.setQuranTilawatDone(true)
                activity.ibadatStateEngine.calculateScore()
                Toast.makeText(activity, "قرآن تلاوت مکمل! +10 پوائنٹس", Toast.LENGTH_SHORT).show()
            } else {
                activity.ibadatStateEngine.setQuranTilawatDone(false)
                activity.ibadatStateEngine.calculateScore()
                Toast.makeText(activity, "قرآن تلاوت منسوخ!", Toast.LENGTH_SHORT).show()
            }
            refreshQuranTilawatRow(home)
            updateIbadatUI(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.quranTilawatRow).setOnClickListener { quranToggle() }
        home.findViewById<TextView>(R.id.quranTilawatDoneBtn).setOnClickListener { quranToggle() }
        home.findViewById<TextView>(R.id.quranTilawatLabel).setOnClickListener { quranToggle() }

        fun toggleTahajjudAction() {
            activity.ibadatStateEngine.toggleTahajjud()
            refreshNaflRowNew(home, R.id.tahajjudRow, R.id.tahajjudDoneBtn, R.id.tahajjudLabel, "تہجد", activity.ibadatStateEngine.isTahajjudDone())
            activity.ibadatStateEngine.calculateScore()
            updateIbadatUI(home)
            updateLevelAndStats(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.tahajjudRow).setOnClickListener { toggleTahajjudAction() }
        home.findViewById<TextView>(R.id.tahajjudDoneBtn).setOnClickListener { toggleTahajjudAction() }
        home.findViewById<TextView>(R.id.tahajjudLabel).setOnClickListener { toggleTahajjudAction() }

        fun toggleSubahAzkarAction() {
            activity.ibadatStateEngine.toggleSubahAzkar()
            refreshNaflRowNew(home, R.id.subahAzkarRow, R.id.subahAzkarDoneBtn, R.id.subahAzkarLabel, "صبح کے اذکار", activity.ibadatStateEngine.isSubahAzkarDone())
            activity.ibadatStateEngine.calculateScore()
            updateIbadatUI(home)
            updateLevelAndStats(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.subahAzkarRow).setOnClickListener { toggleSubahAzkarAction() }
        home.findViewById<TextView>(R.id.subahAzkarDoneBtn).setOnClickListener { toggleSubahAzkarAction() }
        home.findViewById<TextView>(R.id.subahAzkarLabel).setOnClickListener { toggleSubahAzkarAction() }

        fun toggleShamAzkarAction() {
            activity.ibadatStateEngine.toggleShamAzkar()
            refreshNaflRowNew(home, R.id.shamAzkarRow, R.id.shamAzkarDoneBtn, R.id.shamAzkarLabel, "شام کے اذکار", activity.ibadatStateEngine.isShamAzkarDone())
            activity.ibadatStateEngine.calculateScore()
            updateIbadatUI(home)
            updateLevelAndStats(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.shamAzkarRow).setOnClickListener { toggleShamAzkarAction() }
        home.findViewById<TextView>(R.id.shamAzkarDoneBtn).setOnClickListener { toggleShamAzkarAction() }
        home.findViewById<TextView>(R.id.shamAzkarLabel).setOnClickListener { toggleShamAzkarAction() }
        home.findViewById<TextView>(R.id.qadaToggleBtn).setOnClickListener {
            val section = home.findViewById<View>(R.id.qadaBankSection)
            val isVisible = section.visibility == View.VISIBLE
            section.visibility = if (isVisible) View.GONE else View.VISIBLE
            home.findViewById<TextView>(R.id.qadaToggleBtn).text = if (isVisible) "📋 قضا بینک ▼" else "📋 قضا بینک ▲"
            if (!isVisible) refreshQadaBank(home)
        }
        setupFardExpand(home)
        setupHaidhToggle(home)
        loadIbadatState(home)
        updateProgressDots(home)
        updateWeeklyChart(home)
        updateAllFiveGlow(home)
    }

    fun setupIbadatDashboardWithCache(home: View, cachedTimes: PrayerTimes?) {
        val prayers = listOf(
            Triple("Fajr", R.id.fajrDoneBtn, R.id.fajrQadaBtn),
            Triple("Zuhr", R.id.zuhrDoneBtn, R.id.zuhrQadaBtn),
            Triple("Asr", R.id.asrDoneBtn, R.id.asrQadaBtn),
            Triple("Maghrib", R.id.maghribDoneBtn, R.id.maghribQadaBtn),
            Triple("Isha", R.id.ishaaDoneBtn, R.id.ishaaQadaBtn)
        )
        for ((name, doneId, qadaId) in prayers) {
            home.findViewById<TextView>(doneId).setOnClickListener {
                val current = activity.ibadatStateEngine.getPrayerState(name)
                val newState = if (current == PrayerState.DONE) PrayerState.PENDING else PrayerState.DONE
                if (current == PrayerState.QADA) {
                    activity.qadaBankEngine.unmarkQada(name, activity.ibadatStateEngine.today)
                }
                activity.ibadatStateEngine.setPrayerState(name, newState)
                onPrayerStateChanged(name, home)
                if (newState == PrayerState.DONE) {
                    showPrayerDoneAnimation(home.findViewById(doneId))
                    if (activity.ibadatStateEngine.isAllFardDone()) {
                        showConfetti()
                        showMilestoneMessage(activity.personaEngine.getPerfectDayMessage())
                    }
                }
            }
            home.findViewById<TextView>(qadaId).setOnClickListener {
                val current = activity.ibadatStateEngine.getPrayerState(name)
                if (current == PrayerState.QADA) {
                    activity.ibadatStateEngine.setPrayerState(name, PrayerState.PENDING)
                    activity.qadaBankEngine.unmarkQada(name, activity.ibadatStateEngine.today)
                } else {
                    activity.ibadatStateEngine.setPrayerState(name, PrayerState.QADA)
                    activity.qadaBankEngine.markAsQada(name, activity.ibadatStateEngine.today)
                }
                onPrayerStateChanged(name, home)
            }
        }

        // Quran Tilawat - 10 marks (toggleable)
        val quranToggle = {
            val isDone = activity.ibadatStateEngine.isQuranTilawatDone()
            if (!isDone) {
                activity.ibadatStateEngine.setQuranTilawatDone(true)
                activity.ibadatStateEngine.calculateScore()
                Toast.makeText(activity, "قرآن تلاوت مکمل! +10 پوائنٹس", Toast.LENGTH_SHORT).show()
            } else {
                activity.ibadatStateEngine.setQuranTilawatDone(false)
                activity.ibadatStateEngine.calculateScore()
                Toast.makeText(activity, "قرآن تلاوت منسوخ!", Toast.LENGTH_SHORT).show()
            }
            refreshQuranTilawatRow(home)
            updateIbadatUI(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.quranTilawatRow).setOnClickListener { quranToggle() }
        home.findViewById<TextView>(R.id.quranTilawatDoneBtn).setOnClickListener { quranToggle() }
        home.findViewById<TextView>(R.id.quranTilawatLabel).setOnClickListener { quranToggle() }

        fun toggleTahajjudAction() {
            activity.ibadatStateEngine.toggleTahajjud()
            refreshNaflRowNew(home, R.id.tahajjudRow, R.id.tahajjudDoneBtn, R.id.tahajjudLabel, "تہجد", activity.ibadatStateEngine.isTahajjudDone())
            activity.ibadatStateEngine.calculateScore()
            updateIbadatUI(home)
            updateLevelAndStats(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.tahajjudRow).setOnClickListener { toggleTahajjudAction() }
        home.findViewById<TextView>(R.id.tahajjudDoneBtn).setOnClickListener { toggleTahajjudAction() }
        home.findViewById<TextView>(R.id.tahajjudLabel).setOnClickListener { toggleTahajjudAction() }

        fun toggleSubahAzkarAction() {
            activity.ibadatStateEngine.toggleSubahAzkar()
            refreshNaflRowNew(home, R.id.subahAzkarRow, R.id.subahAzkarDoneBtn, R.id.subahAzkarLabel, "صبح کے اذکار", activity.ibadatStateEngine.isSubahAzkarDone())
            activity.ibadatStateEngine.calculateScore()
            updateIbadatUI(home)
            updateLevelAndStats(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.subahAzkarRow).setOnClickListener { toggleSubahAzkarAction() }
        home.findViewById<TextView>(R.id.subahAzkarDoneBtn).setOnClickListener { toggleSubahAzkarAction() }
        home.findViewById<TextView>(R.id.subahAzkarLabel).setOnClickListener { toggleSubahAzkarAction() }

        fun toggleShamAzkarAction() {
            activity.ibadatStateEngine.toggleShamAzkar()
            refreshNaflRowNew(home, R.id.shamAzkarRow, R.id.shamAzkarDoneBtn, R.id.shamAzkarLabel, "شام کے اذکار", activity.ibadatStateEngine.isShamAzkarDone())
            activity.ibadatStateEngine.calculateScore()
            updateIbadatUI(home)
            updateLevelAndStats(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.shamAzkarRow).setOnClickListener { toggleShamAzkarAction() }
        home.findViewById<TextView>(R.id.shamAzkarDoneBtn).setOnClickListener { toggleShamAzkarAction() }
        home.findViewById<TextView>(R.id.shamAzkarLabel).setOnClickListener { toggleShamAzkarAction() }

        fun toggleSleepAzkarAction() {
            activity.ibadatStateEngine.toggleSleepAzkar()
            refreshNaflRowNew(home, R.id.sleepAzkarRow, R.id.sleepAzkarDoneBtn, R.id.sleepAzkarLabel, "سونے کے اذکار", activity.ibadatStateEngine.isSleepAzkarDone())
            activity.ibadatStateEngine.calculateScore()
            updateIbadatUI(home)
            updateLevelAndStats(home)
            setupQuraAndazi(home)
            vibrateClick()
        }
        home.findViewById<View>(R.id.sleepAzkarRow).setOnClickListener { toggleSleepAzkarAction() }
        home.findViewById<TextView>(R.id.sleepAzkarDoneBtn).setOnClickListener { toggleSleepAzkarAction() }
        home.findViewById<TextView>(R.id.sleepAzkarLabel).setOnClickListener { toggleSleepAzkarAction() }

        home.findViewById<TextView>(R.id.qadaToggleBtn).setOnClickListener {
            val section = home.findViewById<View>(R.id.qadaBankSection)
            val isVisible = section.visibility == View.VISIBLE
            section.visibility = if (isVisible) View.GONE else View.VISIBLE
            home.findViewById<TextView>(R.id.qadaToggleBtn).text = if (isVisible) "📋 قضا بینک ▼" else "📋 قضا بینک ▲"
            if (!isVisible) refreshQadaBank(home)
        }
        setupFardExpand(home)
        setupHaidhToggle(home)
        loadIbadatState(home, cachedTimes, skipNonCritical = true)
        updateProgressDots(home)
        updateWeeklyChart(home)
        updateAllFiveGlow(home)
    }

    fun refreshIbadatRow(home: View, name: String) {
        val state = activity.ibadatStateEngine.getPrayerState(name)
        val label = localizedPrayerName(if (name == "Zuhr" && activity.ibadatStateEngine.isJummahToday()) "Jummah" else name)
        val time = activity.ibadatStateEngine.getPrayerTimeForDisplay(name, activity.prayerTimesMap)
        val rowId = when (name) {
            "Fajr" -> R.id.fajrRow; "Zuhr" -> R.id.zuhrRow; "Asr" -> R.id.asrRow
            "Maghrib" -> R.id.maghribRow; else -> R.id.ishaRow
        }
        val doneBtnId = when (name) {
            "Fajr" -> R.id.fajrDoneBtn; "Zuhr" -> R.id.zuhrDoneBtn; "Asr" -> R.id.asrDoneBtn
            "Maghrib" -> R.id.maghribDoneBtn; else -> R.id.ishaaDoneBtn
        }
        val qadaBtnId = when (name) {
            "Fajr" -> R.id.fajrQadaBtn; "Zuhr" -> R.id.zuhrQadaBtn; "Asr" -> R.id.asrQadaBtn
            "Maghrib" -> R.id.maghribQadaBtn; else -> R.id.ishaaQadaBtn
        }
        val labelId = when (name) {
            "Fajr" -> R.id.fajrLabel; "Zuhr" -> R.id.zuhrLabel; "Asr" -> R.id.asrLabel
            "Maghrib" -> R.id.maghribLabel; else -> R.id.ishaaLabel
        }
        val row = home.findViewById<View>(rowId)
        val doneBtn = home.findViewById<TextView>(doneBtnId)
        val qadaBtn = home.findViewById<TextView>(qadaBtnId)
        val labelView = home.findViewById<TextView>(labelId)
        labelView.text = "$label  $time"
        when (state) {
            PrayerState.DONE -> {
                row.alpha = 0.5f
                labelView.setTextColor(ContextCompat.getColor(activity, R.color.emeraldGreen))
                doneBtn.alpha = 1f
                qadaBtn.alpha = 0.3f
                doneBtn.setBackgroundColor(0xFF22C55E.toInt())
                doneBtn.setTextColor(0xFFFFFFFF.toInt())
                qadaBtn.setBackgroundResource(R.drawable.rounded_bg)
                qadaBtn.setTextColor(ContextCompat.getColor(activity, R.color.on_background_primary_text))
            }
            PrayerState.QADA -> {
                row.alpha = 0.5f
                labelView.setTextColor(ContextCompat.getColor(activity, R.color.bronze))
                doneBtn.alpha = 0.3f
                qadaBtn.alpha = 1f
                qadaBtn.setBackgroundColor(0xFFEF4444.toInt())
                qadaBtn.setTextColor(0xFFFFFFFF.toInt())
                doneBtn.setBackgroundResource(R.drawable.rounded_bg)
                doneBtn.setTextColor(ContextCompat.getColor(activity, R.color.on_background_primary_text))
            }
            PrayerState.PENDING -> {
                row.alpha = 1.0f
                labelView.setTextColor(ContextCompat.getColor(activity, R.color.lightNeutral))
                doneBtn.alpha = 0.5f
                qadaBtn.alpha = 0.5f
                doneBtn.setBackgroundResource(R.drawable.rounded_bg)
                doneBtn.setTextColor(ContextCompat.getColor(activity, R.color.on_background_primary_text))
                qadaBtn.setBackgroundResource(R.drawable.rounded_bg)
                qadaBtn.setTextColor(ContextCompat.getColor(activity, R.color.on_background_primary_text))
            }
        }
    }

    fun refreshQuranTilawatRow(home: View) {
        val isDone = activity.ibadatStateEngine.isQuranTilawatDone()
        val row = home.findViewById<View>(R.id.quranTilawatRow)
        val labelView = home.findViewById<TextView>(R.id.quranTilawatLabel)
        val doneBtn = home.findViewById<TextView>(R.id.quranTilawatDoneBtn)
        row.alpha = if (isDone) 0.5f else 1.0f
        if (isDone) {
            labelView.setTextColor(ContextCompat.getColor(activity, R.color.emeraldGreen))
            doneBtn.alpha = 1f
            doneBtn.setTextColor(ContextCompat.getColor(activity, R.color.emeraldGreen))
        } else {
            labelView.setTextColor(ContextCompat.getColor(activity, R.color.lightNeutral))
            doneBtn.alpha = 0.5f
            doneBtn.setTextColor(ContextCompat.getColor(activity, R.color.on_background_primary_text))
        }
    }

    fun refreshNaflRow(row: TextView, label: String, done: Boolean) {
        val icon = "💎"
        val status = if (done) "✅" else "☐"
        row.text = "$icon $label  $status"
        row.setTextColor(ContextCompat.getColor(activity, if (done) R.color.emeraldGreen else R.color.lightNeutral))
        row.alpha = if (done) 0.5f else 1.0f
    }

    fun refreshNaflRowNew(home: View, rowId: Int, doneBtnId: Int, labelId: Int, label: String, done: Boolean) {
        val row = home.findViewById<View>(rowId)
        val doneBtn = home.findViewById<TextView>(doneBtnId)
        val labelView = home.findViewById<TextView>(labelId)
        row.alpha = if (done) 0.5f else 1.0f
        if (done) {
            labelView.setTextColor(ContextCompat.getColor(activity, R.color.emeraldGreen))
            doneBtn.alpha = 1f
            doneBtn.setTextColor(ContextCompat.getColor(activity, R.color.emeraldGreen))
        } else {
            labelView.setTextColor(ContextCompat.getColor(activity, R.color.lightNeutral))
            doneBtn.alpha = 0.5f
            doneBtn.setTextColor(ContextCompat.getColor(activity, R.color.on_background_primary_text))
        }
    }

    fun showPrayerDoneAnimation(row: TextView) {
        row.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80)
            .withEndAction { row.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }.start()
    }

    fun updateProgressDots(home: View) {
        val dotIds = listOf(R.id.dotFajr, R.id.dotZuhr, R.id.dotAsr, R.id.dotMaghrib, R.id.dotIsha)
        val names = listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")
        for (i in dotIds.indices) {
            val dot = home.findViewById<TextView>(dotIds[i])
            val state = activity.ibadatStateEngine.getPrayerState(names[i])
            dot.text = when (state) {
                PrayerState.DONE -> "⭐"
                PrayerState.PENDING -> "⚪"
                PrayerState.QADA -> "🟡"
            }
        }
    }

    fun updateWeeklyChart(home: View) {
        val section = home.findViewById<View>(R.id.weeklyChartSection)
        val row = home.findViewById<LinearLayout>(R.id.weeklyChartRow)
        row.removeAllViews()
        val weeklyStats = activity.ibadatStateEngine.getWeeklyStats()
        var anyDone = false
        for ((_, count) in weeklyStats) {
            val dot = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(18, 18).apply { setMargins(3, 0, 3, 0) }
                background = activity.resources.getDrawable(
                    if (count >= 3) R.drawable.chip_selected else R.drawable.chip_unselected,
                    activity.theme
                )
            }
            row.addView(dot)
            if (count >= 3) anyDone = true
        }
        section.visibility = if (anyDone) View.VISIBLE else View.GONE
    }

    fun updateAllFiveGlow(home: View) {
        val card = home.findViewById<View>(R.id.ibadatDashboardCard)
        if (activity.ibadatStateEngine.isAllFardDone()) {
            card.animate().scaleX(1.01f).scaleY(1.01f).setDuration(300).start()
            card.postDelayed({ card.animate().scaleX(1f).scaleY(1f).setDuration(300).start() }, 1500)
        }
    }

    fun setupFardExpand(home: View) {
        home.findViewById<TextView>(R.id.fardExpandBtn).setOnClickListener {
            val section = home.findViewById<View>(R.id.fardSection)
            val isVisible = section.visibility == View.VISIBLE
            section.visibility = if (isVisible) View.GONE else View.VISIBLE
            home.findViewById<TextView>(R.id.fardExpandBtn).text = if (isVisible) "▼" else "▲"
        }
    }

    fun loadIbadatState(home: View, cachedTimes: PrayerTimes? = null, skipNonCritical: Boolean = false) {
        val haidhPrefs = activity.getSharedPreferences("haidh_status", android.content.Context.MODE_PRIVATE)
        val isHaidh = haidhPrefs.getString("current_status", "tuhr") == "haidh"
        activity.ibadatStateEngine.updateStreak()
        activity.ibadatStateEngine.syncAzkarFromTab()
        val now = Calendar.getInstance()
        val times = cachedTimes ?: try { activity.prayerEngine.calculatePrayerTimes() } catch (_: Exception) { null }
        if (isHaidh) {
            for (name in listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")) {
                refreshIbadatRow(home, name)
            }
            refreshNaflRowNew(home, R.id.tahajjudRow, R.id.tahajjudDoneBtn, R.id.tahajjudLabel, "تہجد", activity.ibadatStateEngine.isTahajjudDone())
            refreshNaflRowNew(home, R.id.subahAzkarRow, R.id.subahAzkarDoneBtn, R.id.subahAzkarLabel, "صبح کے اذکار", activity.ibadatStateEngine.isSubahAzkarDone())
            refreshNaflRowNew(home, R.id.shamAzkarRow, R.id.shamAzkarDoneBtn, R.id.shamAzkarLabel, "شام کے اذکار", activity.ibadatStateEngine.isShamAzkarDone())
            refreshNaflRowNew(home, R.id.sleepAzkarRow, R.id.sleepAzkarDoneBtn, R.id.sleepAzkarLabel, "سونے کے اذکار", activity.ibadatStateEngine.isSleepAzkarDone())
            activity.ibadatStateEngine.calculateScore()
            updateIbadatUI(home)
            updateProgressBar(home)
            updateSectionCounts(home)
            if (!skipNonCritical) refreshQadaBank(home)
            return
        }
        val fardNames = listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")
        for (name in fardNames) {
            if (times != null) {
                val cal = when (name) {
                    "Fajr" -> times.fajr; "Zuhr" -> times.zuhr; "Asr" -> times.asr
                    "Maghrib" -> times.maghrib; else -> times.isha
                }
                if (cal.timeInMillis < now.timeInMillis) {
                    home.findViewById<View>(when (name) {
                        "Fajr" -> R.id.fajrRow; "Zuhr" -> R.id.zuhrRow; "Asr" -> R.id.asrRow
                        "Maghrib" -> R.id.maghribRow; else -> R.id.ishaRow
                    }).visibility = View.VISIBLE
                } else {
                    home.findViewById<View>(when (name) {
                        "Fajr" -> R.id.fajrRow; "Zuhr" -> R.id.zuhrRow; "Asr" -> R.id.asrRow
                        "Maghrib" -> R.id.maghribRow; else -> R.id.ishaRow
                    }).visibility = View.GONE
                }
            }
            refreshIbadatRow(home, name)
        }
        refreshNaflRowNew(home, R.id.tahajjudRow, R.id.tahajjudDoneBtn, R.id.tahajjudLabel, "تہجد", activity.ibadatStateEngine.isTahajjudDone())
        refreshNaflRowNew(home, R.id.subahAzkarRow, R.id.subahAzkarDoneBtn, R.id.subahAzkarLabel, "صبح کے اذکار", activity.ibadatStateEngine.isSubahAzkarDone())
refreshNaflRowNew(home, R.id.shamAzkarRow, R.id.shamAzkarDoneBtn, R.id.shamAzkarLabel, "شام کے اذکار", activity.ibadatStateEngine.isShamAzkarDone())
            refreshNaflRowNew(home, R.id.sleepAzkarRow, R.id.sleepAzkarDoneBtn, R.id.sleepAzkarLabel, "سونے کے اذکار", activity.ibadatStateEngine.isSleepAzkarDone())
            activity.ibadatStateEngine.calculateScore()
        updateIbadatUI(home)
        updateFardCollapsible(home)
        if (!skipNonCritical) {
            refreshQadaBank(home)
        }
    }

    fun refreshQadaBank(home: View) {
        val summaryText = home.findViewById<TextView>(R.id.qadaSummaryText)
        val prayerList = home.findViewById<LinearLayout>(R.id.qadaPrayerList)
        val pending = activity.qadaBankEngine.getThisWeekPendingQada()
        val done = activity.qadaBankEngine.getThisWeekCompletedQada()
        summaryText.text = "باقی: ${pending.size}  |  مکمل: ${done.size}"
        prayerList.removeAllViews()
        for ((prayer, date) in pending) {
            val row = TextView(activity).apply {
                text = "🟡 $prayer ($date) — کلک کر کے مکمل کریں"
                textSize = 14f
                setTextColor(0xFFC9A961.toInt())
                setPadding(8, 6, 8, 6)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activity.qadaBankEngine.markPrayerCompletedInQada(prayer, date)
                    activity.ibadatStateEngine.calculateScore()
                    refreshQadaBank(home)
                    updateIbadatUI(home)
                    updateLevelAndStats(home)
                    setupQuraAndazi(home)
                    Toast.makeText(activity, "قضا مکمل! +10 پوائنٹس", Toast.LENGTH_SHORT).show()
                }
            }
            prayerList.addView(row)
        }
        for ((prayer, date) in done) {
            val row = TextView(activity).apply {
                text = "✅ $prayer ($date)"
                textSize = 14f
                setTextColor(0xFF6B8E6B.toInt())
                setPadding(8, 6, 8, 6)
            }
            prayerList.addView(row)
        }
    }

    fun updateIbadatUI(home: View) {
        val score = activity.ibadatStateEngine.getScore()
        home.findViewById<TextView>(R.id.streakText).text = "🔥 ${activity.ibadatStateEngine.getStreak()}"
        home.findViewById<TextView>(R.id.scoreText).text = "سکور: $score"
        home.findViewById<TextView>(R.id.perfectDaysText).text =
            if (activity.ibadatStateEngine.getPerfectDays() > 0) "🌟 کامل دن: ${activity.ibadatStateEngine.getPerfectDays()}" else ""
        home.findViewById<TextView>(R.id.scoreBreakdownText).text =
            "⭐ فرض ×5=100  📖 تلاوت=10  💎 تہجد=10  💎 اذکار صبح=5 شام=5 سونے=5  🏃 ورزش=18  💊 دوائی=5  ➕ کامل=20"
        updateProgressBar(home)
        updateSectionCounts(home)
    }

    fun updateProgressBar(home: View) {
        val engine = activity.ibadatStateEngine
        val doneCount = engine.getFardDoneCount() +
                (if (engine.isTahajjudDone()) 1 else 0) +
                (if (engine.isSubahAzkarDone()) 1 else 0) +
                (if (engine.isShamAzkarDone()) 1 else 0) +
                (if (engine.isQuranTilawatDone()) 1 else 0) +
                (if (engine.isSleepAzkarDone()) 1 else 0)
        val total = 10
        val bar = home.findViewById<View>(R.id.ibadatProgressBar)
        val fill = home.findViewById<View>(R.id.ibadatProgressBarFill)
        val text = home.findViewById<TextView>(R.id.ibadatProgressText)
        if (doneCount == 0) {
            bar.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        val pct = (doneCount * 100) / total
        text.text = "$pct%"
        fill.post {
            val parent = fill.parent
            if (parent is View) {
                val parentWidth = parent.measuredWidth.coerceAtLeast(1)
                fill.layoutParams.width = (parentWidth * pct) / 100
                fill.requestLayout()
            }
        }
    }

    fun updateSectionCounts(home: View) {
        val engine = activity.ibadatStateEngine
        val fardDone = engine.getFardDoneCount()
        home.findViewById<TextView>(R.id.fardCountText).text = "$fardDone/5"
        val azkarDone = (if (engine.isSubahAzkarDone()) 1 else 0) +
                (if (engine.isShamAzkarDone()) 1 else 0) +
                (if (engine.isQuranTilawatDone()) 1 else 0) +
                (if (engine.isSleepAzkarDone()) 1 else 0)
        home.findViewById<TextView>(R.id.azkarCountText).text = "$azkarDone/4"
    }

    fun updateFardCollapsible(home: View) {
        val allDone = activity.ibadatStateEngine.isAllFardDone()
        val summaryRow = home.findViewById<View>(R.id.fardSummaryRow)
        val fardSection = home.findViewById<View>(R.id.fardSection)
        if (allDone) {
            summaryRow.visibility = View.VISIBLE
            if (home.findViewById<TextView>(R.id.fardExpandBtn).text == "▲") {
                fardSection.visibility = View.VISIBLE
            } else {
                fardSection.visibility = View.GONE
            }
        } else {
            summaryRow.visibility = View.GONE
            fardSection.visibility = View.VISIBLE
        }
    }

    fun updateGreeting(home: View) {
        home.findViewById<TextView>(R.id.greetingText).text = activity.userProfile.getGreeting()
        home.findViewById<TextView>(R.id.hijriDateText).text = HijriCalendar.getHijriDateString()
        val score = activity.ibadatStateEngine.getScore()
        val doneCount = activity.ibadatStateEngine.getFardDoneCount()
        val remaining = 5 - doneCount
        val suggestion = if (remaining > 0) "آج $doneCount پڑھ چکی ہو — باقی $remaining رہ گئیں" else "ماشاءاللہ! تمام نمازیں مکمل"
        home.findViewById<TextView>(R.id.contextSuggestionText).text = suggestion
    }

    fun updateLevelAndStats(home: View) {}

    fun setupPrayerTimes(home: View) {
        // Clear existing blink runnables to prevent stacking
        activity.clearAllBlinkRunnables()
        // Prayer-time calculation (raw JSON parse + astronomic math) is expensive on
        // slower devices, so run it on a background thread and apply results on the UI thread.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val times = activity.prayerEngine.calculatePrayerTimes()
                val formatted = activity.prayerEngine.getFormattedTimes(times)
                val map = mapOf(
                    "Fajr" to (formatted["فجر"] ?: ""),
                    "Zuhr" to (formatted["ظہر"] ?: ""),
                    "Asr" to (formatted["عصر"] ?: ""),
                    "Maghrib" to (formatted["مغرب"] ?: ""),
                    "Isha" to (formatted["عشاء"] ?: "")
                )
                activity.runOnUiThread { applyPrayerTimes(home, times, formatted, map) }
            } catch (e: Exception) {
                android.util.Log.e("DuaApp", "Prayer calc error: ${e.message}", e)
            }
        }
    }

    private fun applyPrayerTimes(home: View, times: PrayerTimes, formatted: Map<String, String>, map: Map<String, String>) {
        try {
            activity.prayerTimesMap = map
            home.findViewById<TextView>(R.id.prayerFajr).text = "فجر      ${formatted["فجر"]}"
            home.findViewById<TextView>(R.id.prayerSunrise).text = "طلوع     ${formatted["طلوع"]}"
            home.findViewById<TextView>(R.id.prayerZuhr).text = "ظہر      ${formatted["ظہر"]}"
            home.findViewById<TextView>(R.id.prayerAsr).text = "عصر      ${formatted["عصر"]}"
            home.findViewById<TextView>(R.id.prayerMaghrib).text = "مغرب     ${formatted["مغرب"]}"
            home.findViewById<TextView>(R.id.prayerIsha).text = "عشاء     ${formatted["عشاء"]}"
            home.findViewById<TextView>(R.id.locationText).text = activity.prayerEngine.getLocationLabel()
            home.findViewById<TextView>(R.id.qiblaText).text = activity.prayerEngine.getQiblaLabel()
            home.findViewById<TextView>(R.id.asrMethodText).text = Localization.asrMethodValue
            val forbidden = activity.prayerEngine.isForbiddenTime(times)
            home.findViewById<View>(R.id.forbiddenSunrise).visibility =
                if (forbidden == "🌅 طلوع — مکروہ وقت") View.VISIBLE else View.GONE
            home.findViewById<View>(R.id.forbiddenIstiwa).visibility =
                if (forbidden == "☀️ زوال — مکروہ وقت") View.VISIBLE else View.GONE
            home.findViewById<View>(R.id.forbiddenSunset).visibility =
                if (forbidden == "🌇 غروب — مکروہ وقت") View.VISIBLE else View.GONE
            val golden = home.findViewById<TextView>(R.id.goldenHourBanner)
            if (activity.prayerEngine.isFridayGoldenHour(times)) {
                golden.text = Localization.goldenHourBanner
                golden.visibility = View.VISIBLE
            } else {
                golden.visibility = View.GONE
            }
            val prayerMap = mapOf(
                home.findViewById<TextView>(R.id.prayerFajr) to "فجر",
                home.findViewById<TextView>(R.id.prayerZuhr) to "ظہر",
                home.findViewById<TextView>(R.id.prayerAsr) to "عصر",
                home.findViewById<TextView>(R.id.prayerMaghrib) to "مغرب",
                home.findViewById<TextView>(R.id.prayerIsha) to "عشاء"
            )
            val activePrayer = activity.prayerEngine.getActivePrayer(times)
            for ((tv, name) in prayerMap) {
                if (activePrayer != null && name == activePrayer.first) {
                    tv.setTextColor(0xFFFFD700.toInt())
                    tv.setTypeface(null, android.graphics.Typeface.BOLD)
                    tv.setBackgroundColor(0x50D4AF37.toInt())
                } else {
                    tv.setTextColor(ContextCompat.getColor(activity, R.color.on_background_primary_text))
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL)
                    tv.background = null
                }
            }
            for ((tv, name) in prayerMap) {
                tv.setOnClickListener { showPrayerAdjustDialog(name) }
            }
            val adjustBtn = home.findViewById<TextView>(R.id.prayerAdjustBtn)
            if (adjustBtn != null) {
                adjustBtn.setOnClickListener {
                    val names = arrayOf("فجر", "ظہر", "عصر", "مغرب", "عشاء")
                    AlertDialog.Builder(activity)
                        .setTitle("کون سا وقت ایڈجسٹ کرنا ہے؟")
                        .setItems(names) { _, which -> showPrayerAdjustDialog(names[which]) }
                        .setNegativeButton("منسوخ", null)
                        .show()
                }
            }
            val engNames = mapOf("فجر" to "Fajr", "ظہر" to "Zuhr", "عصر" to "Asr", "مغرب" to "Maghrib", "عشاء" to "Isha")
            val muteMap = mapOf(
                "فجر" to home.findViewById<TextView>(R.id.muteFajr),
                "ظہر" to home.findViewById<TextView>(R.id.muteZuhr),
                "عصر" to home.findViewById<TextView>(R.id.muteAsr),
                "مغرب" to home.findViewById<TextView>(R.id.muteMaghrib),
                "عشاء" to home.findViewById<TextView>(R.id.muteIsha)
            )
            fun adhanModeLabel(mode: String): String = when (mode) {
                PrayerEngine.ADHAN_MODE_FULL -> "🔊 مکمل"
                PrayerEngine.ADHAN_MODE_FIRST_TWO -> "🔉 دوآیات"
                else -> "🔇 خاموش"
            }
            fun adhanModeToast(urduName: String, mode: String): String = when (mode) {
                PrayerEngine.ADHAN_MODE_FULL -> "$urduName — اذان مکمل"
                PrayerEngine.ADHAN_MODE_FIRST_TWO -> "$urduName — پہلی دو آیات"
                else -> "$urduName — اذان خاموش"
            }
            for ((urduName, muteTv) in muteMap) {
                val engName = engNames[urduName] ?: continue
                muteTv.text = adhanModeLabel(activity.prayerEngine.getAdhanMode(engName))
                muteTv.setOnClickListener {
                    val newMode = activity.prayerEngine.cycleAdhanMode(engName)
                    muteTv.text = adhanModeLabel(newMode)
                    Toast.makeText(activity, adhanModeToast(urduName, newMode), Toast.LENGTH_SHORT).show()
                }
            }
            val adhanToggle = home.findViewById<TextView>(R.id.adhanMuteToggle)
            if (adhanToggle != null) {
                val isMuted = activity.prayerEngine.isAdhanVerseMuted()
                adhanToggle.text = if (isMuted) "🔇 اذان خاموش" else "🔊 اذان آن"
                adhanToggle.setOnClickListener {
                    val newMuted = !activity.prayerEngine.isAdhanVerseMuted()
                    activity.prayerEngine.setAdhanVerseMuted(newMuted)
                    adhanToggle.text = if (newMuted) "🔇 اذان خاموش" else "🔊 اذان آن"
                    Toast.makeText(activity, if (newMuted) "🔇 اذان خاموش — نوٹیفکیشن بھیجے جائیں گے" else "🔊 اذان آن — ہر نماز پر اذان بجے گی", Toast.LENGTH_SHORT).show()
                }
            }
            val (nextName, nextTime) = activity.prayerEngine.getNextPrayer(times)
            activity.notificationManager.showServiceNotification(nextName, nextTime)
            val prayerTimeList = listOf(
                "فجر" to times.fajr.timeInMillis,
                "ظہر" to times.zuhr.timeInMillis,
                "عصر" to times.asr.timeInMillis,
                "مغرب" to times.maghrib.timeInMillis,
                "عشاء" to times.isha.timeInMillis
            )
        } catch (e: Exception) {
            Log.e("DuaApp", "Prayer calc error: ${e.message}", e)
        }
    }

    fun setupPrayerTimesFromCache(home: View, times: PrayerTimes) {
        activity.clearAllBlinkRunnables()
        try {
            val formatted = activity.prayerEngine.getFormattedTimes(times)
            val map = mapOf(
                "Fajr" to (formatted["فجر"] ?: ""),
                "Zuhr" to (formatted["ظہر"] ?: ""),
                "Asr" to (formatted["عصر"] ?: ""),
                "Maghrib" to (formatted["مغرب"] ?: ""),
                "Isha" to (formatted["عشاء"] ?: "")
            )
            applyPrayerTimes(home, times, formatted, map)
        } catch (e: Exception) {
            Log.e("DuaApp", "Prayer apply error: ${e.message}", e)
        }
    }

    fun showPrayerAdjustDialog(prayerName: String) {
        val prayerKey = when (prayerName) {
            "فجر" -> "Fajr"; "ظہر" -> "Zuhr"; "عصر" -> "Asr"; "مغرب" -> "Maghrib"; "عشاء" -> "Isha"
            else -> return
        }
        val currentOffset = activity.prayerEngine.getPrayerOffset(prayerKey)
        val options = IntArray(13) { (it - 6) * 5 } // -30 .. +30 in 5-min steps
        val labels = options.map { o ->
            when {
                o < 0 -> "$o منٹ"
                o > 0 -> "+$o منٹ"
                else -> "معیاری (0)"
            }
        }.toTypedArray()
        var initialIdx = options.indexOf(currentOffset)
        if (initialIdx < 0) {
            val nearest = options.minByOrNull { Math.abs(it - currentOffset) } ?: 0
            initialIdx = options.indexOf(nearest)
        }
        val offsetLabel = when {
            currentOffset < 0 -> "$currentOffset منٹ"
            currentOffset > 0 -> "+$currentOffset منٹ"
            else -> "معیاری"
        }
        AlertDialog.Builder(activity)
            .setTitle("$prayerName کا وقت ایڈجسٹ کریں\nموجودہ: $offsetLabel")
            .setSingleChoiceItems(labels, initialIdx) { dialog, which ->
                val offset = options[which]
                activity.prayerEngine.setPrayerOffset(prayerKey, offset)
                setupPrayerTimes(activity.homeTabRoot)
                loadIbadatState(activity.homeTabRoot)
                Toast.makeText(
                    activity,
                    "$prayerName ${labels[which]} — ایڈجسٹ ہو گیا",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .setNeutralButton("سب معیاری کریں") { _, _ ->
                for (key in arrayOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")) {
                    activity.prayerEngine.setPrayerOffset(key, 0)
                }
                setupPrayerTimes(activity.homeTabRoot)
                loadIbadatState(activity.homeTabRoot)
                Toast.makeText(activity, "تمام اوقات معیاری کر دیے گئے", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("منسوخ", null)
            .show()
    }

    fun setupDailyTafsir(home: View) {
        if (activity.tafsirData.isEmpty()) return
        val arabic = home.findViewById<TextView>(R.id.tafsirArabic)
        val translation = home.findViewById<TextView>(R.id.tafsirTranslation)
        val explanation = home.findViewById<TextView>(R.id.tafsirExplanation)
        val source = home.findViewById<TextView>(R.id.tafsirSource)
        val prevBtn = home.findViewById<TextView>(R.id.tafsirPrev)
        val nextBtn = home.findViewById<TextView>(R.id.tafsirNext)
        val tafsirTab = home.findViewById<TextView>(R.id.tafsirTab)
        val hadithTab = home.findViewById<TextView>(R.id.hadithTab)
        fun showItem() {
            val data = if (activity.isTafsirMode) activity.tafsirData else activity.hadithData
            val item = data[if (activity.isTafsirMode) activity.tafsirIndex else activity.tafsirIndex % data.size]
            if (activity.isTafsirMode) {
                arabic.text = item[0]
                translation.text = item[1]
                explanation.text = item[2]
                source.text = item[3]
            } else {
                arabic.text = item[0].substringBefore(" — ")
                translation.text = ""
                explanation.text = item[0].substringAfter(" — ", item[0])
                source.text = item[1]
            }
        }
        tafsirTab.setOnClickListener {
            activity.isTafsirMode = true
            tafsirTab.setTextColor(ContextCompat.getColor(activity, R.color.primaryGold))
            tafsirTab.setBackgroundResource(R.drawable.chip_selected)
            hadithTab.setTextColor(ContextCompat.getColor(activity, R.color.bronze))
            hadithTab.setBackgroundResource(R.drawable.chip_unselected)
            showItem()
        }
        hadithTab.setOnClickListener {
            activity.isTafsirMode = false
            hadithTab.setTextColor(ContextCompat.getColor(activity, R.color.primaryGold))
            hadithTab.setBackgroundResource(R.drawable.chip_selected)
            tafsirTab.setTextColor(ContextCompat.getColor(activity, R.color.bronze))
            tafsirTab.setBackgroundResource(R.drawable.chip_unselected)
            showItem()
        }
        prevBtn.setOnClickListener {
            val size = if (activity.isTafsirMode) activity.tafsirData.size else activity.hadithData.size
            activity.tafsirIndex = if (activity.tafsirIndex > 0) activity.tafsirIndex - 1 else size - 1
            showItem()
        }
        nextBtn.setOnClickListener {
            val size = if (activity.isTafsirMode) activity.tafsirData.size else activity.hadithData.size
            activity.tafsirIndex = (activity.tafsirIndex + 1) % size
            showItem()
        }
        showItem()
        tafsirTab.callOnClick()
    }

    fun setupDuas(home: View) {
        setupCollapsibleDuaSection(home, R.id.duasToggleBtn1, R.id.duasSection1, R.id.duasRecyclerView1, Dua.group1Duas, "🛡️ حفاظت و بخشش (${Dua.group1Duas.size}) — اللہ کی پناہ اور معافی")
        setupCollapsibleDuaSection(home, R.id.duasToggleBtn2, R.id.duasSection2, R.id.duasRecyclerView2, Dua.group2Duas, "💎 عبادت و خوبصورتی (${Dua.group2Duas.size}) — ایمان اور جامع دعائیں")
        setupCollapsibleDuaSection(home, R.id.duasToggleBtn3, R.id.duasSection3, R.id.duasRecyclerView3, Dua.group3Duas, "🌟 روزمرہ کی نئی دعائیں (${Dua.group3Duas.size}) — سنت نبوی کے اذکار")
    }

    private fun setupCollapsibleDuaSection(home: View, toggleId: Int, sectionId: Int, recyclerId: Int, duas: List<Dua>, title: String) {
        val toggleBtn = home.findViewById<TextView>(toggleId)
        val section = home.findViewById<View>(sectionId)
        val recycler = home.findViewById<androidx.recyclerview.widget.RecyclerView>(recyclerId)
        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = DuaAdapter(duas)
        recycler.visibility = View.VISIBLE
        try {
            recycler.layoutAnimation = AnimationUtils.loadLayoutAnimation(activity, R.anim.layout_slide_in)
        } catch (_: Exception) {}
        toggleBtn.setOnClickListener {
            val isVisible = section.visibility == View.VISIBLE
            if (isVisible) {
                collapseView(section)
                toggleBtn.text = "$title  ▼"
            } else {
                expandView(section)
                toggleBtn.text = "$title  ▲"
            }
        }
    }

    private fun expandView(v: View) {
        v.measure(v.layoutParams.width, v.layoutParams.height)
        val targetHeight = v.measuredHeight
        v.layoutParams.height = 0
        v.visibility = View.VISIBLE
        v.alpha = 0f
        v.translationY = v.context.resources.displayMetrics.density * 30
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .withEndAction {
                v.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                v.requestLayout()
            }
            .start()
    }

    private fun collapseView(v: View) {
        val initialHeight = v.measuredHeight
        v.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                v.visibility = View.GONE
                v.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                v.alpha = 1f
            }
            .start()
    }

    fun showMilestoneMessage(msg: String) {
        AlertDialog.Builder(activity)
            .setTitle("🌟 عبادت کا سنگ میل")
            .setMessage(msg)
            .setPositiveButton("اللہ کا شکر ہے") { _, _ -> showConfetti() }
            .show()
    }

    fun showSadaqahPrompt() {
        AlertDialog.Builder(activity)
            .setTitle(Localization.sadaqahTitle)
            .setMessage(Localization.sadaqahBody)
            .setPositiveButton("جی ہاں") { _, _ ->
                activity.ibadatStateEngine.addBonusScore(20); updateIbadatUI(activity.homeTabRoot); showConfetti()
            }
            .setNegativeButton("بعد میں") { _, _ -> }
            .show()
    }

    fun showConfetti() {
        activity.homeTabRoot.findViewById<TextView>(R.id.scoreText).animate()
            .scaleX(1.2f).scaleY(1.2f).setDuration(200)
            .withEndAction {
                activity.homeTabRoot.findViewById<TextView>(R.id.scoreText).animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }.start()
    }

    fun localizedPrayerName(name: String): String = when (name) {
        "Fajr" -> "فجر"; "Zuhr" -> "ظہر"; "Asr" -> "عصر"; "Maghrib" -> "مغرب"; "Isha" -> "عشاء"
        else -> name
    }

    fun vibrateClick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                activity.vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") activity.vibrator?.vibrate(15)
        } catch (_: Exception) {}
    }
}
