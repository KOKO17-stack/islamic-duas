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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class AppNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_PRAYER = "prayer_reminders"
        const val CHANNEL_PENALTY = "penalty_alerts"
        const val CHANNEL_SCORE = "score_updates"
        const val CHANNEL_SERVICE = "service_status"
        const val CHANNEL_SADAQAH = "sadaqah"
        const val CHANNEL_QADA = "qada_nudge"
        const val CHANNEL_PERMISSION = "permission_reminder"

        private const val NOTIFY_PRAYER = 1001
        private const val NOTIFY_PENALTY = 2001
        private const val NOTIFY_SCORE = 3001
        private const val NOTIFY_SERVICE = 4001
        private const val NOTIFY_SADAQAH = 5001
        private const val NOTIFY_QADA = 6001
        private const val NOTIFY_PERMISSION = 7001

        const val ACTION_PRAYER_REMINDER = "islamic.duas.PRAYER_REMINDER"
        const val ACTION_PENALTY_ALERT = "islamic.duas.PENALTY_ALERT"
        const val ACTION_QADA_NUDGE = "islamic.duas.QADA_NUDGE"
        const val ACTION_SADAQAH_PROMPT = "islamic.duas.SADAQAH_PROMPT"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_SCORE = "score"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_PRAYER, "نماز کی یاد دہانیاں", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "ہر نماز سے ۱۰ منٹ پہلے اطلاع"
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            },
            NotificationChannel(CHANNEL_PENALTY, "قضا نماز الرٹ", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "جب نماز قضا ہو جائے تو اطلاع"
                enableVibration(true)
            },
            NotificationChannel(CHANNEL_SCORE, "عبادت سکور", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "عبادت کے سکور میں تبدیلی"
            },
            NotificationChannel(CHANNEL_SERVICE, "سروس کی حیثیت", NotificationManager.IMPORTANCE_LOW).apply {
                description = "فوری سروس نوٹیفکیشن — اگلی نماز دکھاتا ہے"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_SADAQAH, "صدقہ", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "صدقہ دینے کی یاد دہانی"
            },
            NotificationChannel(CHANNEL_QADA, "قضا مشورہ", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "قضا نماز کی یاد دہانی (پیر/جمعرات)"
            },
            NotificationChannel(CHANNEL_PERMISSION, "اجازت یاد دہانی", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "جب اجازت مسترد ہو تو اطلاع"
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

    fun showPrayerReminder(prayerName: String) {
        if (!hasPermission()) return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_PRAYER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Localization.prayerReminderTitle)
            .setContentText(String.format(Localization.prayerReminderBody, prayerName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.primaryGold))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_PRAYER, notification)
    }

    fun showPenaltyAlert(prayerName: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_PENALTY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Localization.penaltyAlertTitle)
            .setContentText(String.format(Localization.penaltyAlertBody, prayerName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.crimsonRed))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_PENALTY, notification)
    }

    fun showScoreNotification(score: Int) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_SCORE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Localization.scoreNotifyTitle)
            .setContentText(String.format(Localization.scoreNotifyBody, score))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.primaryGold))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_SCORE, notification)
    }

    fun showQadaNudge() {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_QADA)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Localization.qadaNudgeTitle)
            .setContentText(Localization.qadaNudgeBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.emeraldGreen))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_QADA, notification)
    }

    fun showSadaqahPrompt() {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_SADAQAH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Localization.sadaqahNotifyTitle)
            .setContentText(Localization.sadaqahNotifyBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.emeraldGreen))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_SADAQAH, notification)
    }

    fun showPermissionReminder() {
        if (!hasPermission()) return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Localization.permissionTitle)
            .setContentText(Localization.permissionBody)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.primaryGold))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_PERMISSION, notification)
    }

    fun showServiceNotification(nextPrayerName: String, nextPrayerTime: String) {
        if (!hasPermission()) return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Localization.serviceNotificationTitle)
            .setContentText("$nextPrayerName — $nextPrayerTime")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setColor(ContextCompat.getColor(context, R.color.primaryGold))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_SERVICE, notification)
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
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent
                )
            }
        }
    }

    fun schedulePenaltyAlerts(prayerTimes: List<Pair<String, Long>>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        for ((name, timeMs) in prayerTimes) {
            val penaltyTime = timeMs + 60 * 60 * 1000
            if (penaltyTime > now) {
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_PENALTY_ALERT
                    putExtra(EXTRA_PRAYER_NAME, name)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 1000 + name.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, penaltyTime, pendingIntent
                )
            }
        }
    }

    fun scheduleQadaNudge() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        val targetDay = when (dayOfWeek) {
            Calendar.MONDAY -> Calendar.MONDAY
            Calendar.THURSDAY -> Calendar.THURSDAY
            else -> {
                val daysUntilMonday = (Calendar.MONDAY - dayOfWeek + 7) % 7
                val daysUntilThursday = (Calendar.THURSDAY - dayOfWeek + 7) % 7
                if (daysUntilMonday <= daysUntilThursday) Calendar.MONDAY else Calendar.THURSDAY
            }
        }
        val nextNudge = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, if (targetDay == dayOfWeek) targetDay else targetDay + 7)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (nextNudge.timeInMillis <= System.currentTimeMillis()) {
            nextNudge.add(Calendar.DAY_OF_YEAR, 7)
        }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_QADA_NUDGE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 3000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, nextNudge.timeInMillis, pendingIntent
        )
    }

    fun cancelAll() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifManager = AppNotificationManager(context)
        when (intent.action) {
            AppNotificationManager.ACTION_PRAYER_REMINDER -> {
                val name = intent.getStringExtra(AppNotificationManager.EXTRA_PRAYER_NAME) ?: return
                notifManager.showPrayerReminder(name)
            }
            AppNotificationManager.ACTION_PENALTY_ALERT -> {
                val name = intent.getStringExtra(AppNotificationManager.EXTRA_PRAYER_NAME) ?: return
                notifManager.showPenaltyAlert(name)
            }
            AppNotificationManager.ACTION_QADA_NUDGE -> {
                notifManager.showQadaNudge()
            }
            AppNotificationManager.ACTION_SADAQAH_PROMPT -> {
                notifManager.showSadaqahPrompt()
            }
        }
    }
}
