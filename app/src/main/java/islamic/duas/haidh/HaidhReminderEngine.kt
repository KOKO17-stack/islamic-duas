package islamic.duas.haidh

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import islamic.duas.NotificationReceiver
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Daily Haidh logging campaign.
 *
 * When the user first marks herself in Haidh (prayer card toggle, health tracker
 * calendar entry, or the full-screen daily log dialog), a campaign starts for the
 * next [CAMPAIGN_DAYS] days. Every day at 20:00 a tappable notification asks her to
 * record that day's condition. If she does not log, a follow-up notification is
 * re-posted every 30 minutes until she logs or until the day ends (23:30 cutoff).
 *
 * Logging for the day stops that day's reminders. Logging Istihada keeps the
 * campaign running; logging Taharat (Tuhr without istihada) stops the whole
 * campaign immediately. The campaign also auto-expires after [CAMPAIGN_DAYS] days.
 */
object HaidhReminderEngine {

    private const val PREFS = "haidh_reminder_prefs"
    private const val KEY_ACTIVE = "active"
    private const val KEY_START = "campaign_start"
    private const val KEY_POSTED_PREFIX = "posted_"
    private const val KEY_FOLLOWUPS_PREFIX = "followups_"
    private const val KEY_FOLLOWUP_SEQ_PREFIX = "followup_seq_"

    const val CHANNEL_HAIDH_DAILY = "haidh_daily_log"
    const val ACTION_HAIDH_LOG_DAILY = "islamic.duas.HAIDH_LOG_DAILY"
    const val ACTION_HAIDH_LOG_FOLLOWUP = "islamic.duas.HAIDH_LOG_FOLLOWUP"
    const val EXTRA_HAIDH_DATE = "haidh_log_date"
    const val EXTRA_HAIDH_SEQ = "haidh_log_seq"

    const val NOTIFY_HAIDH_DAILY = 10002

    const val REMINDER_HOUR = 20
    const val REMINDER_MINUTE = 0
    const val FOLLOWUP_MINUTES = 30
    const val CAMPAIGN_DAYS = 8
    private const val CUTOFF_MINUTES = 23 * 60 + 30 // 23:30 -> last nag, then quiet until next day

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    private fun todayStr(): String = LocalDate.now().format(ISO)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dailyRequestCode(date: String): Int =
        450000 + (date.hashCode() and 0xFFFF)

    private fun followupRequestCode(date: String, seq: Int): Int =
        850000 + (date.hashCode() and 0xFFFF) + (seq and 0x3F)

    fun ensureChannel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_HAIDH_DAILY,
                "حیض — روزانہ کیفیت",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "رات 8 بجے حیض کی کیفیت ریکارڈ کرنے کی یاد دہانی"
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            }
            nm.createNotificationChannel(channel)
        } catch (_: Exception) {}
    }

    // ── Campaign state ──

    fun isCampaignActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun campaignStart(context: Context): String? =
        prefs(context).getString(KEY_START, null)?.takeIf { it.isNotBlank() }

    private fun isExpired(context: Context): Boolean {
        val start = campaignStart(context) ?: return false
        return try {
            val end = LocalDate.parse(start).plusDays((CAMPAIGN_DAYS - 1).toLong())
            LocalDate.now().isAfter(end)
        } catch (_: Exception) { false }
    }

    // ── Daily log check (reads the shared cycles table — synchronous with calendar) ──

    suspend fun todayLogged(context: Context): Boolean {
        return try {
            val dao = CycleDatabase.getInstance(context).cycleDao()
            dao.getDayStatus(todayStr()) != null
        } catch (_: Exception) { false }
    }

    // ── Campaign lifecycle ──

    /**
     * Starts (or keeps) the campaign. Idempotent: an already-active, non-expired
     * campaign is left untouched. Otherwise old alarms are cleared and fresh daily
     * alarms are scheduled for the remaining days of the window.
     */
    fun startCampaign(context: Context, startDate: String = todayStr()) {
        val alreadyActive = isCampaignActive(context)
        if (alreadyActive && !isExpired(context)) return

        cancelAllCampaignAlarms(context)
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_START, startDate)
            .remove("${KEY_POSTED_PREFIX}$startDate")
            .apply()
        cancelFollowups(context, startDate)

        scheduleDailyAlarms(context, startDate)
    }

    fun stopCampaign(context: Context) {
        val p = prefs(context)
        val start = p.getString(KEY_START, null)
        cancelAllCampaignAlarms(context)
        val editor = p.edit()
        editor.remove(KEY_ACTIVE).remove(KEY_START)
        // clear all posted/followup bookkeeping for the window
        if (start != null) {
            editor.remove("${KEY_POSTED_PREFIX}$start")
            for (i in 0 until CAMPAIGN_DAYS) {
                val d = try { LocalDate.parse(start).plusDays(i.toLong()).format(ISO) } catch (_: Exception) { continue }
                editor.remove("${KEY_POSTED_PREFIX}$d")
                editor.remove("${KEY_FOLLOWUPS_PREFIX}$d")
                editor.remove("${KEY_FOLLOWUP_SEQ_PREFIX}$d")
            }
        }
        editor.apply()
        NotificationManagerCompat.from(context).cancel(NOTIFY_HAIDH_DAILY)
    }

    /**
     * Called after a day's condition is saved. Keeps the campaign and reminder
     * state consistent with what was logged.
     */
    fun onStatusLogged(context: Context, status: MenstrualStatus, istihadaType: IstihadaType) {
        when {
            status == MenstrualStatus.HAIDH -> startCampaign(context)
            status == MenstrualStatus.TUHR && istihadaType == IstihadaType.NONE -> stopCampaign(context)
            else -> {
                // Istihada (or Tuhr with istihada) keeps the campaign alive but the day is logged.
                if (isCampaignActive(context)) {
                    val today = todayStr()
                    prefs(context).edit().remove("${KEY_POSTED_PREFIX}$today").apply()
                    cancelFollowups(context, today)
                    NotificationManagerCompat.from(context).cancel(NOTIFY_HAIDH_DAILY)
                }
            }
        }
    }

    // ── Scheduling ──

    fun scheduleDailyAlarms(context: Context, startDate: String? = null) {
        val startStr = startDate ?: campaignStart(context) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val today = todayStr()
        for (i in 0 until CAMPAIGN_DAYS) {
            val date = try { LocalDate.parse(startStr).plusDays(i.toLong()) } catch (_: Exception) { return }
            val dateStr = date.format(ISO)
            if (dateStr < today) continue
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
                set(Calendar.MINUTE, REMINDER_MINUTE)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.add(Calendar.DAY_OF_YEAR, i)
            if (cal.timeInMillis <= now) continue

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_HAIDH_LOG_DAILY
                putExtra(EXTRA_HAIDH_DATE, dateStr)
            }
            val pi = PendingIntent.getBroadcast(
                context, dailyRequestCode(dateStr), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExactOrFallback(alarmManager, cal, pi)
        }
    }

    private fun scheduleFollowup(context: Context, date: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, FOLLOWUP_MINUTES) }
        val p = prefs(context)
        val seq = p.getInt("${KEY_FOLLOWUP_SEQ_PREFIX}$date", 0) + 1
        p.edit().putInt("${KEY_FOLLOWUP_SEQ_PREFIX}$date", seq).apply()

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_HAIDH_LOG_FOLLOWUP
            putExtra(EXTRA_HAIDH_DATE, date)
            putExtra(EXTRA_HAIDH_SEQ, seq)
        }
        val pi = PendingIntent.getBroadcast(
            context, followupRequestCode(date, seq), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrFallback(alarmManager, cal, pi)

        val set = p.getStringSet("${KEY_FOLLOWUPS_PREFIX}$date", null)?.toMutableSet() ?: mutableSetOf()
        set.add(seq.toString())
        p.edit().putStringSet("${KEY_FOLLOWUPS_PREFIX}$date", set).apply()
    }

    private fun cancelFollowups(context: Context, date: String) {
        val p = prefs(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val set = p.getStringSet("${KEY_FOLLOWUPS_PREFIX}$date", null) ?: emptySet()
        for (seqStr in set) {
            val seq = seqStr.toIntOrNull() ?: continue
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_HAIDH_LOG_FOLLOWUP
                putExtra(EXTRA_HAIDH_DATE, date)
                putExtra(EXTRA_HAIDH_SEQ, seq)
            }
            val pi = PendingIntent.getBroadcast(
                context, followupRequestCode(date, seq), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
        p.edit().remove("${KEY_FOLLOWUPS_PREFIX}$date").apply()
    }

    fun cancelAllCampaignAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val start = campaignStart(context) ?: return
        for (i in 0 until CAMPAIGN_DAYS) {
            val d = try { LocalDate.parse(start).plusDays(i.toLong()).format(ISO) } catch (_: Exception) { continue }
            val daily = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_HAIDH_LOG_DAILY
                putExtra(EXTRA_HAIDH_DATE, d)
            }
            alarmManager.cancel(PendingIntent.getBroadcast(
                context, dailyRequestCode(d), daily,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        }
    }

    private fun scheduleExactOrFallback(alarmManager: AlarmManager, cal: Calendar, pi: PendingIntent) {
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: SecurityException) {
            try {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, cal.timeInMillis, 15 * 60 * 1000L, pi)
            } catch (_: Exception) {
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } catch (_: Exception) {}
            }
        }
    }

    // ── Posted-today bookkeeping ──

    private fun hasPosted(context: Context, date: String): Boolean =
        prefs(context).getBoolean("${KEY_POSTED_PREFIX}$date", false)

    private fun markPosted(context: Context, date: String) {
        prefs(context).edit().putBoolean("${KEY_POSTED_PREFIX}$date", true).apply()
    }

    // ── Alarm handlers (called from NotificationReceiver) ──

    suspend fun handleDailyAlarm(context: Context, date: String?) {
        ensureChannel(context)
        val today = todayStr()
        val targetDate = date ?: today
        if (targetDate != today) return // only today matters; past/future daily alarms are skipped
        if (!isCampaignActive(context)) return
        if (isExpired(context)) { stopCampaign(context); return }

        if (todayLogged(context)) {
            // Already logged today — nothing to remind, make sure no stale nags remain.
            prefs(context).edit().remove("${KEY_POSTED_PREFIX}$today").apply()
            cancelFollowups(context, today)
            NotificationManagerCompat.from(context).cancel(NOTIFY_HAIDH_DAILY)
            return
        }
        postNotification(context)
        markPosted(context, today)
        scheduleFollowup(context, today)
    }

    suspend fun handleFollowupAlarm(context: Context, date: String?) {
        ensureChannel(context)
        val today = todayStr()
        if (date != null && date != today) return // stale followup
        if (!isCampaignActive(context)) return
        if (isExpired(context)) { stopCampaign(context); return }
        if (todayLogged(context)) {
            prefs(context).edit().remove("${KEY_POSTED_PREFIX}$today").apply()
            cancelFollowups(context, today)
            NotificationManagerCompat.from(context).cancel(NOTIFY_HAIDH_DAILY)
            return
        }
        val now = Calendar.getInstance()
        val minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (minutesNow >= CUTOFF_MINUTES) return // day over — quiet until next 20:00 daily alarm

        postNotification(context)
        markPosted(context, today)
        scheduleFollowup(context, today)
    }

    /**
     * Rebuilds the campaign alarms after a reboot. Also catches the case where the
     * device rebooted after 20:00 with today still unlogged — it immediately
     * restores the nagging notification instead of waiting for tomorrow.
     */
    suspend fun rescheduleAfterBoot(context: Context) {
        if (!isCampaignActive(context)) return
        if (isExpired(context)) { stopCampaign(context); return }
        ensureChannel(context)
        scheduleDailyAlarms(context)
        val today = todayStr()
        val now = Calendar.getInstance()
        val minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (minutesNow >= REMINDER_HOUR * 60 + REMINDER_MINUTE && minutesNow < CUTOFF_MINUTES) {
            if (!todayLogged(context)) {
                postNotification(context)
                markPosted(context, today)
                scheduleFollowup(context, today)
            }
        }
    }

    // ── Notification ──

    private fun postNotification(context: Context) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val intent = Intent(context, HaidhDailyLogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, NOTIFY_HAIDH_DAILY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = "آج کے لیے اپنی کیفیت ریکارڈ کریں — حیض، استحاضہ یا طہارت۔ ریکارڈ کرنے کے لیے ٹیپ کریں۔"
        val notification = NotificationCompat.Builder(context, CHANNEL_HAIDH_DAILY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🩸 آج کی کیفیت ریکارڈ کریں")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_HAIDH_DAILY, notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }
}
