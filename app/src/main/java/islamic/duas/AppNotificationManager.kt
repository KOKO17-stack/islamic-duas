package islamic.duas

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import islamic.duas.haidh.HealthEngine
import islamic.duas.sync.DuaForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AppNotificationManager(private val context: Context) {

    companion object {
        const val NOTIF_PREFS = "notification_prefs"
        const val CHANNEL_ADHAN = "adhan_reminders"
        const val CHANNEL_PRAYER = "prayer_reminders"
        const val CHANNEL_QADA = "qada_nudge"
        const val CHANNEL_HEALTH = "health_reminders"
        const val CHANNEL_SERVICE = "service_status"
        const val CHANNEL_RECAP = "daily_recap"
        const val CHANNEL_PRAYER_CHECK = "prayer_check"
        const val CHANNEL_WEATHER = "weather_alerts"
        const val CHANNEL_QUIZ = "quiz_reminder"
        const val CHANNEL_HAIDH = "haidh_reminder"
        const val CHANNEL_MEDICINE = "medicine_reminder_v2"
        const val CHANNEL_MEDICINE_CONFIRM = "medicine_confirm"
        const val CHANNEL_MEDICINE_PENDING = StepCounterService.CHANNEL_ID
        const val CHANNEL_READING = "reading_reminder"
        const val CHANNEL_SLEEP_AZKAR = "sleep_azkar_reminder"

        private const val NOTIFY_ADHAN = 1001
        private const val NOTIFY_PRAYER = 2001
        private const val NOTIFY_QADA = 3001
        private const val NOTIFY_HEALTH = 4001
        private const val NOTIFY_SERVICE = 5001
        private const val NOTIFY_RECAP = 6001
        private const val NOTIFY_PRAYER_CHECK = 7001
        private const val NOTIFY_WEATHER = 8001
        private const val NOTIFY_QUIZ = 9001
        private const val NOTIFY_HAIDH = 10001
        private         const val NOTIFY_MEDICINE = 11001
        const val NOTIFY_MEDICINE_CONFIRM = 11501
        const val NOTIFY_MEDICINE_PENDING = 11601
        private const val NOTIFY_EXERCISE = 12001
        private const val NOTIFY_READING = 13001
        private const val NOTIFY_SLEEP_AZKAR = 14001

        private const val MED_REMINDER_BASE = 1_000_000
        private const val MED_ESCALATION_BASE = 2_000_000
        private const val MED_SNOOZE_BASE = 3_000_000

        const val ACTION_ADHAN_ALARM = "islamic.duas.ADHAN_ALARM"
        const val ACTION_PRAYER_REMINDER = "islamic.duas.PRAYER_REMINDER"
        const val ACTION_QADA_NUDGE = "islamic.duas.QADA_NUDGE"
        const val ACTION_EXERCISE_REMINDER = "islamic.duas.EXERCISE_REMINDER"
        const val ACTION_MEDICINE_REMINDER = "islamic.duas.MEDICINE_REMINDER"
        const val ACTION_MEDICINE_TAKEN = "islamic.duas.MEDICINE_TAKEN"
        const val ACTION_MEDICINE_SNOOZE = "islamic.duas.MEDICINE_SNOOZE"
        const val ACTION_MEDICINE_ESCALATE = "islamic.duas.MEDICINE_ESCALATE"
        const val ACTION_DAILY_RECAP = "islamic.duas.DAILY_RECAP"
        const val ACTION_PRAYER_CHECK_ALARM = "islamic.duas.PRAYER_CHECK_ALARM"
        const val ACTION_PRAYER_CHECK_DONE = "islamic.duas.PRAYER_CHECK_DONE"
        const val ACTION_PRAYER_CHECK_QADA = "islamic.duas.PRAYER_CHECK_QADA"
        const val ACTION_WEATHER_ALERT = "islamic.duas.WEATHER_ALERT"
        const val ACTION_QUIZ_REMINDER = "islamic.duas.QUIZ_REMINDER"
        const val ACTION_HAIDH_REMINDER = "islamic.duas.HAIDH_REMINDER"
        const val ACTION_READING_REMINDER = "islamic.duas.READING_REMINDER"
        const val ACTION_SLEEP_AZKAR_REMINDER = "islamic.duas.SLEEP_AZKAR_REMINDER"
        const val ACTION_SLEEP_AZKAR_SNOOZE_15 = "islamic.duas.SLEEP_AZKAR_SNOOZE_15"
        const val ACTION_SLEEP_AZKAR_SNOOZE_30 = "islamic.duas.SLEEP_AZKAR_SNOOZE_30"
        const val ACTION_SLEEP_AZKAR_SNOOZE_60 = "islamic.duas.SLEEP_AZKAR_SNOOZE_60"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_ADHAN_MODE = "adhan_mode"
        const val EXTRA_RAIN_CHANCE = "rain_chance"
        const val EXTRA_MED_TIME = "med_time"
        const val EXTRA_MED_ID = "med_id"
        const val EXTRA_MED_ESCALATIONS = "med_escalations"
        const val EXTRA_NAV_SECTION = "nav_section"
        const val NAV_HOME = "home"
        const val NAV_WEATHER = "weather"
        const val NAV_AZKAR = "azkar"
        const val NAV_WELLNESS = "wellness"
        const val NAV_QUIZ = "quiz"
        const val NAV_HAIDH = "haidh"
        const val NAV_EXERCISE = "exercise"
        const val NAV_MEDICINE = "medicine"
        const val NAV_HUQOOQ = "huqooq"
        const val NAV_SLEEP_AZKAR = "sleep_azkar"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val today: String get() = dateFormat.format(Date())

    init {
        createChannels()
    }

    private fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_ADHAN, "اذان", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "اذان کی اطلاع اور آواز"
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            },
            NotificationChannel(CHANNEL_PRAYER, "نماز کی یاد دہانیاں", NotificationManager.IMPORTANCE_LOW).apply {
                description = "نماز کے وقت کی نامہ توش — تقویٰ کی راہ"
                enableVibration(true)
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_QADA, "قضا نماز", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "قضا نماز کی نرم یاد دہانی — دن میں ایک بار"
            },
            NotificationChannel(CHANNEL_HEALTH, "صحت", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "ورزش اور دوائیوں کی یاد دہانی"
            },
            NotificationChannel(CHANNEL_SERVICE, "سروس کی حیثیت", NotificationManager.IMPORTANCE_LOW).apply {
                description = "پس منظر کی سروس — اگلی نماز دکھاتا ہے"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_RECAP, "روزانہ خلاصہ", NotificationManager.IMPORTANCE_LOW).apply {
                description = "ہر رات 10 بجے آپ کی عبادت اور صحت کا خلاصہ"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_PRAYER_CHECK, "نماز کا چیک", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "نماز کے بعد پوچھتا ہے کہ پڑھی یا قضا"
                enableVibration(true)
            },
            NotificationChannel(CHANNEL_WEATHER, "موسم کی اطلاع", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "بارش کے امکان کی اطلاع"
                enableVibration(true)
            },
            NotificationChannel(CHANNEL_QUIZ, "کوئز یاد دہانی", NotificationManager.IMPORTANCE_LOW).apply {
                description = "ہر 3 دن بعد کوئز دینے کی یاد دہانی"
                enableVibration(true)
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_HAIDH, "حیض کی یاد دہانی", NotificationManager.IMPORTANCE_LOW).apply {
                description = "حیض کے دنوں میں روزانہ کیفیت ریکارڈ کرنے کی یاد دہانی"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_MEDICINE, "دوا کی یاد دہانی", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "دوائی لینے کی یاد دہانی"
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            },
            NotificationChannel(CHANNEL_MEDICINE_CONFIRM, "دوا کی تصدیق", NotificationManager.IMPORTANCE_LOW).apply {
                description = "دوائی لینے کی تصدیق"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_MEDICINE_PENDING, "شمارندہ قدم", NotificationManager.IMPORTANCE_MIN).apply {
                description = "روزانہ قدموں کی گنتی اور باقی دوائیوں کی یاد دہانی"
                setShowBadge(false)
                setSound(null, null)
            },
            NotificationChannel(CHANNEL_READING, "مطالعہ کی یاد دہانی", NotificationManager.IMPORTANCE_LOW).apply {
                description = "حقوق النساء مطالعہ — ہر 3 دن بعد یاد دہانی"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_SLEEP_AZKAR, "سونے سے پہلے کے اذکار", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "رات 11:30 بجے سونے سے پہلے کے اذکار کی یاد دہانی"
                enableVibration(true)
            }
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        channels.forEach { manager.createNotificationChannel(it) }
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun isInHaidh(): Boolean {
        val prefs = context.getSharedPreferences("haidh_status", Context.MODE_PRIVATE)
        return prefs.getString("current_status", "tuhr") == "haidh"
    }

    private fun getPersona(): NotificationPersonaEngine = NotificationPersonaEngine(context)
    private fun getState(): IbadatStateEngine = IbadatStateEngine(context)
    private fun getHealth(): HealthEngine = HealthEngine(context)

    private fun updateFgs(title: String, body: String, section: String = NAV_HOME) {
        try {
            DuaForegroundService.updateNotification(context, title, body, section)
        } catch (_: Exception) {}
    }

    private fun getNavIntent(
        section: String,
        prayerName: String? = null,
        medId: String? = null,
        medTime: String? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_SECTION, section)
            if (prayerName != null) putExtra(EXTRA_PRAYER_NAME, prayerName)
            if (medId != null) putExtra(EXTRA_MED_ID, medId)
            if (medTime != null) putExtra(EXTRA_MED_TIME, medTime)
        }
        return PendingIntent.getActivity(
            context, section.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun postSeparateNotification(
        channelId: String,
        title: String,
        body: String,
        navSection: String,
        notificationId: Int
    ) {
        if (!hasPermission()) return
        if (!canPostSeparate(notificationId)) return
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getNavIntent(navSection))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        markSeparatePosted(notificationId)
    }

    private fun canPostSeparate(notificationId: Int): Boolean {
        val prefs = context.getSharedPreferences("separate_notif_cooldown", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_$notificationId", 0L)
        return (System.currentTimeMillis() - last) > 12 * 60 * 60 * 1000L
    }

    private fun markSeparatePosted(notificationId: Int) {
        context.getSharedPreferences("separate_notif_cooldown", Context.MODE_PRIVATE)
            .edit().putLong("last_$notificationId", System.currentTimeMillis()).apply()
    }

    private fun isSeparateMuted(channelId: String): Boolean {
        return context.getSharedPreferences(NOTIF_PREFS, Context.MODE_PRIVATE)
            .getBoolean("muted_$channelId", false)
    }

    fun showAdhanNotification(prayerName: String, adhanMode: String? = null) {
        if (!hasPermission()) return
        if (isInHaidh()) return

        val prefs = context.getSharedPreferences("prayer_prefs", Context.MODE_PRIVATE)
        val globalMuted = prefs.getBoolean("adhan_verse_muted", false)
        val mode = adhanMode ?: PrayerEngine.ADHAN_MODE_FULL

        val msg = getPersona().getAdhanMessage(prayerName)
        updateFgs("🕌 $prayerName — اذان کی دعوت", msg, NAV_HOME)

        if (!globalMuted && mode != PrayerEngine.ADHAN_MODE_SILENT) {
            try {
                val intent = Intent(context, AdhanService::class.java).apply {
                    putExtra(EXTRA_ADHAN_MODE, mode)
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    fun showPrayerReminder(prayerName: String) {
        if (!hasPermission()) return
        if (isInHaidh()) return

        val engName = mapOf("فجر" to "Fajr", "ظہر" to "Zuhr", "عصر" to "Asr", "مغرب" to "Maghrib", "عشاء" to "Isha")[prayerName] ?: prayerName
        val state = getState().getPrayerState(engName)
        if (state == PrayerState.DONE) return

        val msg = getPersona().getPrayerReminderMessage(prayerName, state)
        updateFgs("🕌 $prayerName: عشرے و توبہ", msg)
    }

    fun showQadaNudge() {
        if (!hasPermission()) return
        val qadaEngine = QadaBankEngine(context)
        val qadaCount = qadaEngine.getPendingQadaCount()
        if (qadaCount == 0) return

        val todayNudged = context.getSharedPreferences("qada_bank_v2", Context.MODE_PRIVATE)
            .getBoolean("nudged_$today", false)
        if (todayNudged) return

        val msg = getPersona().getQadaNudgeMessage(qadaCount)
        updateFgs("📿 قضا کی اہمیت", msg)

        context.getSharedPreferences("qada_bank_v2", Context.MODE_PRIVATE)
            .edit().putBoolean("nudged_$today", true).apply()
    }

fun showExerciseReminder() {
        if (!hasPermission()) return
        val health = getHealth()
        if (health.getTodayExerciseMinutes() >= 30 && health.getWeeklyExerciseDays() >= 4) return
        val msg = getPersona().getExerciseReminderMessage()
        updateFgs("🏃‍♀️ صحت کی حفاظت: عبادت کا حصہ", msg, NAV_EXERCISE)
        if (!isSeparateMuted(CHANNEL_HEALTH)) {
            postSeparateNotification(
                CHANNEL_HEALTH, "🏃‍♀️ صحت کی حفاظت: عبادت کا حصہ", msg, NAV_EXERCISE, NOTIFY_EXERCISE
            )
        }
    }

    fun showMedicineReminder(timePeriod: String? = null, escalated: Boolean = false, fullScreen: Boolean = false): Boolean {
        if (!hasPermission()) return false
        val pending = pendingMedicinesForSlot(timePeriod)
        if (pending.isEmpty()) return false

        val names = pending.map { it.second }
        val msg = if (escalated) {
            "⚠️ " + names.joinToString("، ") + " — ابھی تک نہیں لی گئی، براہ کرم ابھی لیں!"
        } else {
            names.joinToString("، ") + " — " + (timePeriod ?: "دوا") + " کا وقت!"
        }

        val title = when {
            escalated -> when (timePeriod) {
                "صبح" -> "⏰ صبح کی دوائیں — ابھی لیں!"
                "دوپہر" -> "⏰ دوپہر کی دوائیں — ابھی لیں!"
                "شام" -> "⏰ شام کی دوائیں — ابھی لیں!"
                else -> "⏰ ${timePeriod ?: "دوا"} کا وقت — ابھی لیں!"
            }
            timePeriod == "صبح" -> "🌅 صبح کی دوائیں"
            timePeriod == "دوپہر" -> "☀️ دوپہر کی دوائیں"
            timePeriod == "شام" -> "🌆 شام کی دوائیں"
            else -> "💊 ${timePeriod ?: "دوا"} کا وقت"
        }

        val navMedId = pending.firstOrNull()?.first
        val navTime = timePeriod

        val builder = NotificationCompat.Builder(context, CHANNEL_MEDICINE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
            .setPriority(if (escalated) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getNavIntent(NAV_MEDICINE, medId = navMedId, medTime = navTime))

        if (fullScreen) {
            builder.setFullScreenIntent(getNavIntent(NAV_MEDICINE, medId = navMedId, medTime = navTime), true)
        }

        if (timePeriod != null) {
            if (pending.size == 1) {
                builder.addAction(medicineTakenAction(null, timePeriod))
                builder.addAction(medicineSnoozeAction(timePeriod))
            } else {
                // Multiple meds: one button per med so one dose cannot be over-marked.
                // Up to 3 actions fit; snooze only kept when buttons don't fill the slot.
                for ((medId, medName) in pending.take(3)) {
                    builder.addAction(medicineTakenAction(medId, timePeriod, medName))
                }
                if (pending.size < 3) builder.addAction(medicineSnoozeAction(timePeriod))
            }
        }

        val notifId = NOTIFY_MEDICINE + (timePeriod?.hashCode() ?: 0)
        NotificationManagerCompat.from(context).notify(notifId, builder.build())
        return true
    }

    fun refreshMedicineReminder(timePeriod: String) {
        val notifId = NOTIFY_MEDICINE + timePeriod.hashCode()
        if (pendingMedicinesForSlot(timePeriod).isEmpty()) {
            NotificationManagerCompat.from(context).cancel(notifId)
        } else {
            showMedicineReminder(timePeriod)
        }
    }

    fun syncPendingMedicationNotification(pending: List<String>) {
        if (!hasPermission()) return
        if (pending.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(NOTIFY_MEDICINE_PENDING)
            return
        }
        val names = pending.joinToString("، ")
        val msg = names + " — وقت ہو گیا ہے، لینا مت بھولیں!"
        val builder = NotificationCompat.Builder(context, CHANNEL_MEDICINE_PENDING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💊 دوائیں باقی ہیں")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setContentIntent(getNavIntent(NAV_MEDICINE))
        NotificationManagerCompat.from(context).notify(NOTIFY_MEDICINE_PENDING, builder.build())
    }

    fun showMedicineTakenConfirmation(medId: String?, timePeriod: String) {
        if (!hasPermission()) return
        val health = HealthEngine(context)
        val names = if (medId != null) {
            health.getMedications().firstOrNull { it.id == medId }?.name
        } else {
            health.getMedications().filter { it.isActive && it.times.contains(timePeriod) }
                .map { it.name }.joinToString("، ")
        }
        val label = names ?: "دوائی"
        val msg = "$label — لے لی گئی، اللہ تعالیٰ شفاء عطا فرمائیں"
        val builder = NotificationCompat.Builder(context, CHANNEL_MEDICINE_CONFIRM)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✅ تصدیق ہو گئی")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
            .setTimeoutAfter(4000)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        NotificationManagerCompat.from(context).notify(
            NOTIFY_MEDICINE_CONFIRM + timePeriod.hashCode(), builder.build()
        )
    }

    private fun medicineTakenAction(medId: String?, timePeriod: String, medName: String = "لے لی"): NotificationCompat.Action {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_TAKEN
            putExtra(EXTRA_MED_TIME, timePeriod)
            medId?.let { putExtra(EXTRA_MED_ID, it) }
        }
        val reqCode = if (medId != null) 50050 + medId.hashCode() else 50000 + timePeriod.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(android.R.drawable.ic_input_add, "✅ $medName", pendingIntent)
    }

    private fun medicineSnoozeAction(timePeriod: String): NotificationCompat.Action {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_SNOOZE
            putExtra(EXTRA_MED_TIME, timePeriod)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 50001 + timePeriod.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(android.R.drawable.ic_menu_revert, "🔔 بعد میں", pendingIntent)
    }

    private fun pendingMedicinesForSlot(timePeriod: String?): List<Pair<String, String>> {
        val health = HealthEngine(context)
        val activeMeds = health.getMedications().filter { it.isActive }
        if (activeMeds.isEmpty()) return emptyList()
        val todayLog = health.getTodayMedicationLog()
        val pending = mutableListOf<Pair<String, String>>()
        for (med in activeMeds) {
            val timesToCheck = if (timePeriod != null) {
                if (med.times.contains(timePeriod)) listOf(timePeriod) else emptyList()
            } else {
                med.times
            }
            var stillPending = false
            for (time in timesToCheck) {
                val taken = todayLog.any { it.medicationId == med.id && it.time == time && it.taken }
                if (!taken) {
                    stillPending = true
                    break
                }
            }
            if (stillPending) pending.add(Pair(med.id, med.name))
        }
        return pending
    }

    fun showDailyRecap() {
        if (!hasPermission()) return
        val msg = getPersona().getDailyRecapBody()
        val title = getPersona().getDailyRecapTitle()
        updateFgs(title, msg)
    }

    fun showPrayerCheckNotification(prayerName: String, prayerTimeMs: Long) {
        if (!hasPermission()) return
        if (isInHaidh()) return

        val engName = mapOf("فجر" to "Fajr", "ظہر" to "Zuhr", "عصر" to "Asr", "مغرب" to "Maghrib", "عشاء" to "Isha")[prayerName] ?: prayerName
        val state = getState().getPrayerState(engName)
        if (state == PrayerState.DONE || state == PrayerState.QADA) return

        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val timeStr = sdf.format(Date(prayerTimeMs))

        val doneIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_CHECK_DONE
            putExtra(EXTRA_PRAYER_NAME, engName)
        }
        val donePending = PendingIntent.getBroadcast(
            context, 20000 + engName.hashCode(), doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val qadaIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_CHECK_QADA
            putExtra(EXTRA_PRAYER_NAME, engName)
        }
        val qadaPending = PendingIntent.getBroadcast(
            context, 30000 + engName.hashCode(), qadaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val msg = "$prayerName کا وقت $timeStr تھا — کیا نماز کی ادائیگی ہو گئی؟"
        updateFgs("🕌 $prayerName: نماز کا حساب", msg, NAV_HOME)
    }

    fun schedulePrayerCheckAlarms(prayerTimes: List<Pair<String, Long>>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for ((name, timeMs) in prayerTimes) {
            val delay = if (name == "عشاء") 3 * 60 * 60 * 1000L else 70 * 60 * 1000L
            val checkTime = timeMs + delay
            if (checkTime > now) {
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_PRAYER_CHECK_ALARM
                    putExtra(EXTRA_PRAYER_NAME, name)
                    putExtra("prayer_time_ms", timeMs)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 10000 + name.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent)
                } catch (_: SecurityException) {
                    android.util.Log.w("AppNotificationMgr", "schedulePrayerCheckAlarms failed: SCHEDULE_EXACT_ALARM not granted")
                }
            }
        }
    }

    private fun canShowBranding(): Boolean {
        val prefs = context.getSharedPreferences("branding_cooldown", Context.MODE_PRIVATE)
        val lastMs = prefs.getLong("last_branding_ms", 0L)
        return (System.currentTimeMillis() - lastMs) >= 3 * 24 * 60 * 60 * 1000L
    }

    private fun brandingOrFallback(msg: String): Pair<String, String> {
        if (canShowBranding()) {
            context.getSharedPreferences("branding_cooldown", Context.MODE_PRIVATE)
                .edit().putLong("last_branding_ms", System.currentTimeMillis()).apply()
            return msg to NAV_HOME
        }
        return "📿 بیٹی! اللہ کا ذکر کرو — سُبْحَانَ اللَّهِ وَبِحَمْدِهِ 🌸" to NAV_AZKAR
    }

    private fun getGeneralReminderBody(): Pair<String, String> {
        val health = getHealth()
        val prefs = context.getSharedPreferences("notification_rotation", Context.MODE_PRIVATE)
        val idx = prefs.getInt("reminder_index", 0)
        prefs.edit().putInt("reminder_index", (idx + 1) % 5).apply()
        return when (idx) {
            0 -> {
                val meds = health.getMedications().filter { it.isActive }
                val todaysLog = health.getTodayMedicationLog()
                val pendingMeds = meds.any { med -> med.times.any { time ->
                    !todaysLog.any { it.medicationId == med.id && it.time == time && it.taken }
                }}
                if (pendingMeds) {
                    val names = health.getPendingMedications()
                    "💊 دوائی کا وقت بیٹی — ${names.joinToString("، ")} لینا مت بھولیں 🤍" to NAV_WELLNESS
                } else {
                    brandingOrFallback("🌸 بیٹی! یہ ایپ اہل حدیث کی انتہائی سینئر عالمہ خواتین نے آپ کے لیے تیار کی ہے — دینی اور دنیاوی بہتری کے لیے 🤍")
                }
            }
            1 -> {
                val todayMin = health.getTodayExerciseMinutes()
                if (todayMin < 30) {
                    val weekDays = health.getWeeklyExerciseDays()
                    "🏃 بیٹی! ورزش باقی ہے — آج $todayMin منٹ کی، $weekDays/4 دن اس ہفتے 🤍" to NAV_WELLNESS
                } else {
                    brandingOrFallback("🌸 بیٹی! اس ایپ کو اہل حدیث کی بڑی عالمہ خواتین نے بنایا ہے — آپ کی آسانی اور اللہ سے قربت کے لیے 🤍")
                }
            }
            2 -> {
                val weather = WeatherEngine(context)
                val forecast = try { weather.fetchRainForecast() } catch (_: Exception) { null }
                if (forecast != null) {
                    when {
                        forecast.heatLevel == HeatLevel.EXTREME -> "🔥 بیٹی! انتہائی گرمی ہے — پانی پیتی رہو اور دھوپ میں مت نکلو 🤍"
                        forecast.heatLevel == HeatLevel.HOT -> "🌡 بیٹی! شدید گرمی ہے — ٹھنڈا پانی پیتی رہو 🤍"
                        forecast.heatLevel == HeatLevel.MILDY_HOT -> "🌤 بیٹی! آج گرمی ہے، اللہ ٹھنڈک عطا فرمائے 🤍"
                        else -> "🌱 بیٹی! موسم خوشگوار ہے — اللہ کا شکر کرو 🤍"
                    } to NAV_WEATHER
                } else {
                    brandingOrFallback("🌸 بیٹی! اہل حدیث کی سینئر عالمہ خواتین کی ایپ — آپ کی دینی اور دنیاوی بہتری کے لیے 🤍")
                }
            }
            3 -> brandingOrFallback("🌸 بیٹی! یہ ایپ اہل حدیث کی انتہائی سینئر عالمہ خواتین نے آپ کے لیے تیار کی ہے — اللہ سے قربت اور آسانی کے لیے 🤍")
            else -> brandingOrFallback("🌸 بیٹی! اہل حدیث کی بڑی عالمہ خواتین آپ کے لیے دعا گو ہیں — اللہ آپ کو عزت اور بھلائی عطا فرمائے 🤍")
        }
    }

    fun showServiceNotification(nextPrayerName: String, nextPrayerCalendar: Calendar) {
        // This was the old redundant notification — FGS buildHubNotification handles it
    }

    fun scheduleAdhanAlarms(prayerTimes: List<Pair<String, Long>>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("prayer_prefs", Context.MODE_PRIVATE)
        val prayerEngine = PrayerEngine(context)
        for ((name, timeMs) in prayerTimes) {
            val engName = mapOf("فجر" to "Fajr", "ظہر" to "Zuhr", "عصر" to "Asr", "مغرب" to "Maghrib", "عشاء" to "Isha")[name] ?: name
            if (timeMs > now) {
                val mode = prayerEngine.getAdhanMode(engName)
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_ADHAN_ALARM
                    putExtra(EXTRA_PRAYER_NAME, name)
                    putExtra(EXTRA_ADHAN_MODE, mode)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 2000 + name.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
                } catch (_: SecurityException) {
                    android.util.Log.w("AppNotificationMgr", "scheduleAdhanAlarms failed: SCHEDULE_EXACT_ALARM not granted")
                }
            }
        }
    }

    fun schedulePrayerReminders(prayerTimes: List<Pair<String, Long>>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for ((name, timeMs) in prayerTimes) {
            val reminderTime = timeMs - 10 * 60 * 1000
            if (reminderTime > now) {
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_PRAYER_REMINDER
                    putExtra(EXTRA_PRAYER_NAME, name)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, name.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent)
                } catch (_: SecurityException) {
                    android.util.Log.w("AppNotificationMgr", "schedulePrayerReminders failed: SCHEDULE_EXACT_ALARM not granted")
                }
            }
        }
    }

    fun scheduleQadaNudge() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val times = try {
            PrayerEngine(context).calculatePrayerTimes().let { PrayerEngine(context).getFormattedTimes(it) }
        } catch (_: Exception) { return }
        val maghribTimeStr = times["مغرب"] ?: return

        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val maghribCal = Calendar.getInstance().apply {
            try { time = sdf.parse(maghribTimeStr)!! } catch (_: Exception) { return }
            set(Calendar.SECOND, 0)
        }
        if (maghribCal.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_QADA_NUDGE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 3000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maghribCal.timeInMillis, pendingIntent)
        } catch (_: SecurityException) {
            android.util.Log.w("AppNotificationMgr", "scheduleQadaNudge failed: SCHEDULE_EXACT_ALARM not granted")
        }
    }

    fun scheduleExerciseReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_EXERCISE_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 4000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            AlarmManager.INTERVAL_DAY, pendingIntent
        )
    }

    fun scheduleMedicineReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel old single alarm at 9 AM (legacy)
        val oldIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_REMINDER
        }
        val oldPending = PendingIntent.getBroadcast(
            context, 5000, oldIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(oldPending)

        // Cancel every previously scheduled dose alarm (covers deleted/edited medications)
        for (label in scheduledDoseLabels()) {
            cancelMedicineDoseReminder(label)
            cancelMedicineSlotAlarms(label)
        }
        medAlarmPrefs().edit().remove("dose_labels").apply()

        // Schedule one alarm per unique dose time label from all active medications
        val labels = getHealth().getMedications().filter { it.isActive }.flatMap { it.times }.distinct()
        val validLabels = mutableListOf<String>()
        for (label in labels) {
            val hm = doseTimeToHourMinute(label) ?: continue
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hm.first)
                set(Calendar.MINUTE, hm.second)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_MEDICINE_REMINDER
                putExtra(EXTRA_MED_TIME, label)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, doseLabelCode(label, MED_REMINDER_BASE), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExactOrFallback(alarmManager, cal, pendingIntent)
            validLabels.add(label)
        }
        recordScheduledDoseLabels(validLabels)
    }

    fun scheduleMedicineFollowUps(timePeriod: String, posted: Boolean) {
        val hm = doseTimeToHourMinute(timePeriod) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Re-arm tomorrow's same-label alarm (one-shot daily chain)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hm.first)
            set(Calendar.MINUTE, hm.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val slotIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_REMINDER
            putExtra(EXTRA_MED_TIME, timePeriod)
        }
        val slotPending = PendingIntent.getBroadcast(
            context, doseLabelCode(timePeriod, MED_REMINDER_BASE), slotIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrFallback(alarmManager, cal, slotPending)

        // Start escalating only if something is actually still due
        if (posted) {
            scheduleMedicineEscalation(timePeriod, 1)
        }
    }

    fun scheduleMedicineEscalation(timePeriod: String, count: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val delayMin = if (count <= 1) 30 else 60
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, delayMin) }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_ESCALATE
            putExtra(EXTRA_MED_TIME, timePeriod)
            putExtra(EXTRA_MED_ESCALATIONS, count)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, doseLabelCode(timePeriod, MED_ESCALATION_BASE), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrFallback(alarmManager, cal, pendingIntent)
    }

    fun snoozeMedicineReminder(timePeriod: String) {
        cancelMedicineSlotAlarms(timePeriod)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 30) }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_REMINDER
            putExtra(EXTRA_MED_TIME, timePeriod)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, doseLabelCode(timePeriod, MED_SNOOZE_BASE), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrFallback(alarmManager, cal, pendingIntent)
    }

    fun medicineSlotHasPending(timePeriod: String): Boolean =
        pendingMedicinesForSlot(timePeriod).isNotEmpty()

    fun cancelMedicineSlotAlarms(timePeriod: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val escIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_ESCALATE
            putExtra(EXTRA_MED_TIME, timePeriod)
        }
        val escPending = PendingIntent.getBroadcast(
            context, doseLabelCode(timePeriod, MED_ESCALATION_BASE), escIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(escPending)
        val snoozeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_REMINDER
            putExtra(EXTRA_MED_TIME, timePeriod)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, doseLabelCode(timePeriod, MED_SNOOZE_BASE), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(snoozePending)
    }

    private fun cancelMedicineDoseReminder(label: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MEDICINE_REMINDER
            putExtra(EXTRA_MED_TIME, label)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, doseLabelCode(label, MED_REMINDER_BASE), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun doseLabelCode(label: String, base: Int): Int =
        base + (label.hashCode() and 0xFFFFF)

    private fun doseTimeToHourMinute(label: String): Pair<Int, Int>? {
        val min = getHealth().doseMinuteOfDay(label) ?: return null
        return (min / 60) to (min % 60)
    }

    private fun scheduleExactOrFallback(alarmManager: AlarmManager, cal: Calendar, pendingIntent: PendingIntent) {
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        } catch (_: SecurityException) {
            try {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, cal.timeInMillis, 15 * 60 * 1000L, pendingIntent)
            } catch (_: Exception) {
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun medAlarmPrefs() =
        context.getSharedPreferences("med_alarm_prefs", Context.MODE_PRIVATE)

    private fun scheduledDoseLabels(): Set<String> =
        medAlarmPrefs().getStringSet("dose_labels", emptySet()) ?: emptySet()

    private fun recordScheduledDoseLabels(labels: List<String>) {
        medAlarmPrefs().edit().putStringSet("dose_labels", labels.toSet()).apply()
    }

    fun scheduleDailyRecap() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_RECAP
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 6000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            AlarmManager.INTERVAL_DAY, pendingIntent
        )
    }

    fun showRainAlertNotification(forecast: RainForecast) {
        if (!hasPermission()) return
        val windows = if (forecast.rainWindows.isNotEmpty()) {
            forecast.rainWindows.joinToString("، ") { "${it.startHour}-${it.endHour}" }
        } else ""
        val chance = forecast.maxChance
        val body = when {
            chance >= 70 -> "بارش کی شدید احتمال — اللہ بہتر جانتا ہے۔ ${if (windows.isNotEmpty()) "\nمتوقع اوقات: $windows" else ""}"
            chance >= 50 -> "بارش کا ہلکا امکان — اللہ بہتر جانتا ہے۔ ${if (windows.isNotEmpty()) "\nمتوقع اوقات: $windows" else ""}"
            else -> "بارش کا بہت ہلکا امکان ہے۔ ${if (windows.isNotEmpty()) "\nمتوقع اوقات: $windows" else ""}"
        }
        updateFgs("🌦 موسمی تبدیلی: تقویٰ اور صبر", body, NAV_WEATHER)
        if (chance >= 50 && !isSeparateMuted(CHANNEL_WEATHER)) {
            postSeparateNotification(
                CHANNEL_WEATHER, "🌦 موسمی تبدیلی: تقویٰ اور صبر", body, NAV_WEATHER, NOTIFY_WEATHER
            )
        }
    }

    fun showQuizReminderNotification() {
        if (!hasPermission()) return
        val msg = "3 دن ہو گئے — اپنے علم کا امتحان لیں اور کوئز دیں۔"
        updateFgs("📚 علم کا امتحان: کوئز", msg, NAV_QUIZ)
        if (!isSeparateMuted(CHANNEL_QUIZ)) {
            postSeparateNotification(
                CHANNEL_QUIZ, "📚 علم کا امتحان: کوئز", msg, NAV_QUIZ, NOTIFY_QUIZ
            )
        }
    }

    fun showReadingReminderNotification() {
        if (!hasPermission()) return
        val msg = "حقوق النساء کا مطالعہ کریں — آج علم کی روشنی میں اپنے حقوق کو جانیں۔"
        updateFgs("📖 حقوق النساء: علم و عمل", msg, NAV_HUQOOQ)
        if (!isSeparateMuted(CHANNEL_READING)) {
            postSeparateNotification(
                CHANNEL_READING, "📖 حقوق النساء: علم و عمل", msg, NAV_HUQOOQ, NOTIFY_READING
            )
        }
    }

    fun showHaidhReminderNotification() {
        if (!hasPermission()) return
        val prefs = context.getSharedPreferences("haidh_status", Context.MODE_PRIVATE)
        if (prefs.getString("current_status", "tuhr") != "haidh") return

        updateFgs("🩸 حیض کی حالت: صبر اور احتساب", "آج حیض ہے — شریعت کے مطابق نماز سے چھوٹ ہے۔ اپنی کیفیت اور علامات درج کریں۔ اللہ صحت و عافیت عطا فرمائے۔", NAV_HAIDH)
    }

    fun scheduleHaidhReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_HAIDH_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 8000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            AlarmManager.INTERVAL_DAY, pendingIntent
        )
    }

    fun cancelHaidhReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_HAIDH_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 8000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleSleepAzkarReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_SLEEP_AZKAR_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 14000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            AlarmManager.INTERVAL_DAY, pendingIntent
        )
    }

    fun cancelSleepAzkarReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_SLEEP_AZKAR_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 14000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun showSleepAzkarReminderNotification() {
        if (!hasPermission()) return
        val msg = "ابھی پڑھیں — سونے سے پہلے کے اذکار پڑھنا نہ بھولیں"
        updateFgs("🌙 سونے سے پہلے کے اذکار", msg, NAV_SLEEP_AZKAR)
        if (!isSeparateMuted(CHANNEL_SLEEP_AZKAR)) {
            val builder = NotificationCompat.Builder(context, CHANNEL_SLEEP_AZKAR)
                .setContentTitle("🌙 سونے سے پہلے کے اذکار")
                .setContentText(msg)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(getNavIntent(NAV_SLEEP_AZKAR))

            // Add snooze actions
            val snooze15Intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_SLEEP_AZKAR_SNOOZE_15
            }
            val snooze15Pending = PendingIntent.getBroadcast(
                context, 14001, snooze15Intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_revert,
                "15 منٹ",
                snooze15Pending
            ).build())

            val snooze30Intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_SLEEP_AZKAR_SNOOZE_30
            }
            val snooze30Pending = PendingIntent.getBroadcast(
                context, 14002, snooze30Intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_revert,
                "30 منٹ",
                snooze30Pending
            ).build())

            val snooze60Intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_SLEEP_AZKAR_SNOOZE_60
            }
            val snooze60Pending = PendingIntent.getBroadcast(
                context, 14003, snooze60Intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_revert,
                "60 منٹ",
                snooze60Pending
            ).build())

            NotificationManagerCompat.from(context).notify(NOTIFY_SLEEP_AZKAR, builder.build())
            markSeparatePosted(NOTIFY_SLEEP_AZKAR)
        }
    }

    fun scheduleQuizReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 3)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_QUIZ_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 7000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            3 * 24 * 60 * 60 * 1000L, pendingIntent
        )
    }

    fun scheduleReadingReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 3)
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_READING_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 9000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            3 * 24 * 60 * 60 * 1000L, pendingIntent
        )
    }

    fun scheduleHealthNotifications() {
        scheduleExerciseReminder()
        scheduleMedicineReminder()
    }

    fun scheduleAllNotifications() {
        scheduleExerciseReminder()
        scheduleMedicineReminder()
        scheduleDailyRecap()
        scheduleHaidhReminder()
    }

    fun cancelAll() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, intent)
            } catch (_: Exception) {}
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val notifManager = AppNotificationManager(context)
        when (intent.action) {
            AppNotificationManager.ACTION_ADHAN_ALARM -> {
                val name = intent.getStringExtra(AppNotificationManager.EXTRA_PRAYER_NAME) ?: return
                val mode = intent.getStringExtra(AppNotificationManager.EXTRA_ADHAN_MODE)
                notifManager.showAdhanNotification(name, mode)
            }
            AppNotificationManager.ACTION_PRAYER_REMINDER -> {
                val name = intent.getStringExtra(AppNotificationManager.EXTRA_PRAYER_NAME) ?: return
                notifManager.showPrayerReminder(name)
            }
            AppNotificationManager.ACTION_QADA_NUDGE -> {
                notifManager.showQadaNudge()
            }
            AppNotificationManager.ACTION_EXERCISE_REMINDER -> {
                notifManager.showExerciseReminder()
            }
            AppNotificationManager.ACTION_MEDICINE_REMINDER -> {
                val timePeriod = intent.getStringExtra(AppNotificationManager.EXTRA_MED_TIME)
                val posted = notifManager.showMedicineReminder(timePeriod)
                if (timePeriod != null) {
                    notifManager.scheduleMedicineFollowUps(timePeriod, posted)
                }
            }
            AppNotificationManager.ACTION_MEDICINE_ESCALATE -> {
                val timePeriod = intent.getStringExtra(AppNotificationManager.EXTRA_MED_TIME) ?: return
                val count = intent.getIntExtra(AppNotificationManager.EXTRA_MED_ESCALATIONS, 1)
                val fullScreenPop = count == 4
                val posted = notifManager.showMedicineReminder(timePeriod, escalated = true, fullScreen = fullScreenPop)
                if (posted) {
                    val cal = Calendar.getInstance()
                    val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
                    if (hourOfDay < 23 && notifManager.medicineSlotHasPending(timePeriod)) {
                        notifManager.scheduleMedicineEscalation(timePeriod, count + 1)
                    }
                }
            }
            AppNotificationManager.ACTION_MEDICINE_TAKEN -> {
                val timePeriod = intent.getStringExtra(AppNotificationManager.EXTRA_MED_TIME) ?: return
                val medId = intent.getStringExtra(AppNotificationManager.EXTRA_MED_ID)
                val health = HealthEngine(context)
                if (medId != null) {
                    health.logMedicationDose(medId, timePeriod, true)
                } else {
                    val activeMeds = health.getMedications().filter { it.isActive }
                    for (med in activeMeds) {
                        if (med.times.contains(timePeriod)) {
                            health.logMedicationDose(med.id, timePeriod, true)
                        }
                    }
                }
                notifManager.showMedicineTakenConfirmation(medId, timePeriod)
                notifManager.refreshMedicineReminder(timePeriod)
                if (!notifManager.medicineSlotHasPending(timePeriod)) {
                    notifManager.cancelMedicineSlotAlarms(timePeriod)
                }
            }
            AppNotificationManager.ACTION_MEDICINE_SNOOZE -> {
                val timePeriod = intent.getStringExtra(AppNotificationManager.EXTRA_MED_TIME) ?: return
                notifManager.snoozeMedicineReminder(timePeriod)
            }
            AppNotificationManager.ACTION_DAILY_RECAP -> {
                notifManager.showDailyRecap()
            }
            AppNotificationManager.ACTION_PRAYER_CHECK_ALARM -> {
                val name = intent.getStringExtra(AppNotificationManager.EXTRA_PRAYER_NAME) ?: return
                val timeMs = intent.getLongExtra("prayer_time_ms", 0L)
                notifManager.showPrayerCheckNotification(name, timeMs)
            }
            AppNotificationManager.ACTION_PRAYER_CHECK_DONE -> {
                val engName = intent.getStringExtra(AppNotificationManager.EXTRA_PRAYER_NAME) ?: return
                val state = IbadatStateEngine(context)
                state.setPrayerState(engName, PrayerState.DONE)
                state.updateStreak()
                state.calculateScore()
            }
            AppNotificationManager.ACTION_PRAYER_CHECK_QADA -> {
                val engName = intent.getStringExtra(AppNotificationManager.EXTRA_PRAYER_NAME) ?: return
                val state = IbadatStateEngine(context)
                state.setPrayerState(engName, PrayerState.QADA)
                QadaBankEngine(context).markAsQada(engName, state.today)
                state.calculateScore()
            }
            AppNotificationManager.ACTION_QUIZ_REMINDER -> {
                notifManager.showQuizReminderNotification()
            }
            AppNotificationManager.ACTION_HAIDH_REMINDER -> {
                notifManager.showHaidhReminderNotification()
            }
            AppNotificationManager.ACTION_READING_REMINDER -> {
                notifManager.showReadingReminderNotification()
            }
            AppNotificationManager.ACTION_SLEEP_AZKAR_REMINDER -> {
                notifManager.showSleepAzkarReminderNotification()
            }
            AppNotificationManager.ACTION_SLEEP_AZKAR_SNOOZE_15 -> {
                // Reschedule for 15 minutes later
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val cal = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, 15)
                }
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = AppNotificationManager.ACTION_SLEEP_AZKAR_REMINDER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 14001, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent
                )
            }
            AppNotificationManager.ACTION_SLEEP_AZKAR_SNOOZE_30 -> {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val cal = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, 30)
                }
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = AppNotificationManager.ACTION_SLEEP_AZKAR_REMINDER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 14002, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent
                )
            }
            AppNotificationManager.ACTION_SLEEP_AZKAR_SNOOZE_60 -> {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val cal = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, 60)
                }
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = AppNotificationManager.ACTION_SLEEP_AZKAR_REMINDER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 14003, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent
                )
            }
        }
    }
}
